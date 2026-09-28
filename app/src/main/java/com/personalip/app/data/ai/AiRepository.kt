package com.personalip.app.data.ai

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.personalip.app.data.ai.remote.ChatMessage
import com.personalip.app.data.ai.remote.ChatRequest
import com.personalip.app.data.ai.remote.ContentPart
import com.personalip.app.data.ai.remote.ImageUrl
import com.personalip.app.data.ai.remote.OpenAiApi
import com.personalip.app.data.compliance.ComplianceChecker
import com.personalip.app.data.local.PostStatus
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.dao.PersonaDao
import com.personalip.app.data.local.dao.PostDao
import com.personalip.app.data.local.dao.WeeklyPlanDao
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.local.entity.PersonaEntity
import com.personalip.app.data.local.entity.PostEntity
import com.personalip.app.data.material.MaterialRepository
import com.personalip.app.data.schedule.WeekIdUtil
import com.personalip.app.data.settings.AiConfigRepository
import com.personalip.app.data.storage.FileNameUtil
import com.personalip.app.data.storage.RootFolderRepository
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * AI 生成朋友圈文案。
 *
 * 两阶段流程（对齐 moments-writer）：
 *   1) 若素材是图片且配置了图片识别模型（visionModel）→
 *      调多模态接口得到图片内容描述（环境 / 人物 / 文字 / 情绪等）。
 *   2) 把图片描述（或已有 OCR 文字）+ 人设 + 合规要求 → 文案模型，
 *      输出 JSON 草稿 → 本地合规扫描 → 落库 posts + 写 Markdown 到
 *      输出/<周>/<周几>/<时间_分类_编号>.md → 关联 weekly_plans。
 *
 * 若素材不是图片或未配置视觉模型，则直接走文案模型（仅 OCR 文字 + 标签）。
 */
@Singleton
class AiRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val openAiApi: OpenAiApi,
    private val aiConfig: AiConfigRepository,
    private val materialDao: MaterialDao,
    private val categoryDao: CategoryDao,
    private val personaDao: PersonaDao,
    private val postDao: PostDao,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val rootFolderRepository: RootFolderRepository,
    private val materialRepository: MaterialRepository,
    private val imageEncoder: ImageEncoder,
    private val complianceChecker: ComplianceChecker,
    private val moshi: Moshi
) {
    /**
     * 为某条排期生成文案。
     * @return 生成的 post id；失败返回 null 与错误信息。
     */
    suspend fun generateForPlan(
        planId: Long,
        tone: Tone = Tone.WARM,
        goal: Goal = Goal.LIKE
    ): GenerateResult = withContext(Dispatchers.IO) {
        val plan = weeklyPlanDao.getById(planId)
            ?: return@withContext GenerateResult(error = "排期不存在")
        val material = materialDao.getById(plan.materialId)
            ?: return@withContext GenerateResult(error = "素材不存在")
        val category = categoryDao.getById(material.categoryId)
        val persona = personaDao.get() ?: PersonaEntity()

        // 1. 配置校验：baseUrl / 文案模型 / apiKey 必填，视觉模型可选。
        val baseUrl = aiConfig.baseUrlFlow.first()
        val model = aiConfig.modelFlow.first()
        val visionModel = aiConfig.visionModelFlow.first()
        if (baseUrl.isNullOrBlank() || model.isNullOrBlank() || !aiConfig.isConfigured()) {
            return@withContext GenerateResult(error = "请先在设置中配置 AI 接口（baseUrl / apiKey / 文案模型）")
        }

        val fullUrl = baseUrl!!.trimEnd('/') + "/chat/completions"

        // 2. 图片处理 —— 双路径确保模型能看到图：
        //    a) 独立视觉模型：如果配置了 visionModel，先用它得到图片文字描述。
        //    b) 文案模型直接看：无论 visionModel 有没有配，都把图片 dataUri 附加到文案请求里。
        val imageDescriptionFromVision = runCatching {
            recognizeImageIfPossible(fullUrl, visionModel, material)
        }.getOrNull()

        val isImage = material.mimeType.startsWith("image/")
        val imageDataUri = if (isImage) {
            runCatching {
                materialRepository.resolveMaterialDocument(material)?.uri
                    ?.let { imageEncoder.encodeToDataUri(it) }
            }.getOrNull()
        } else null

        // 合并图片描述：独立视觉模型结果 + OCR（如果有）。
        val imageDescription = buildString {
            imageDescriptionFromVision?.let { appendLine(it) }
            material.ocrText?.takeIf { it.isNotBlank() }?.let {
                appendLine("（图片中可识别的文字：$it）")
            }
        }.trim().ifBlank { null }

        // 3. 组装文案消息（双版本：带图 / 纯文本）。
        val messagesWithImage = buildMessages(
            material, category?.displayName ?: "未分类", persona, tone, goal,
            imageDescription, imageDataUri
        )
        val messagesTextOnly = buildMessages(
            material, category?.displayName ?: "未分类", persona, tone, goal,
            imageDescription, null
        )

        // 4. 调用文案模型：优先带图（让模型自己看），失败退化为纯文本重试。
        val rawText = runCatching { callApi(fullUrl, model!!, messagesWithImage) }
            .recoverCatching { callApi(fullUrl, model, messagesTextOnly) }
            .getOrNull()
            ?: return@withContext GenerateResult(error = "AI 调用失败，请检查网络与接口配置后重试")

        // 5. 解析 JSON 草稿。
        val draft = parseDraft(rawText)

        // 6. 本地合规扫描 + 图文一致性校验。
        val hits = complianceChecker.check(draft.content, persona.forbiddenWords)
        val mismatchWarnings = checkImageContentConsistency(
            draft = draft,
            imageAvailable = isImage,
            imageDescription = imageDescription
        )
        val complianceNote = buildComplianceNote(
            aiNote = draft.complianceNote,
            hits = hits,
            mismatchWarnings = mismatchWarnings
        )

        // 7. 落库 posts。
        val post = PostEntity(
            materialId = material.id,
            content = draft.content.ifBlank { rawText },
            alternative1 = draft.alternative1,
            alternative2 = draft.alternative2,
            alternative3 = draft.alternative3,
            tags = (draft.tags ?: emptyList()),
            imageSuggestion = draft.imageSuggestion,
            publishTime = plan.time,
            status = PostStatus.PENDING
        )
        val postId = postDao.upsert(post)

        // 8. 写 Markdown 到 SAF 输出目录。
        saveMarkdown(
            weekId = plan.weekId,
            dayOfWeek = plan.dayOfWeek,
            time = plan.time,
            categoryFolderName = category?.folderName ?: category?.displayName ?: "未分类",
            post = post.copy(id = postId),
            complianceNote = complianceNote,
            rawText = rawText,
            imageDescription = imageDescription
        )

        // 9. 关联排期。
        weeklyPlanDao.updateStatusAndPost(planId, PostStatus.PENDING, postId)

        GenerateResult(postId = postId, complianceHits = hits, complianceNote = complianceNote)
    }

    /**
     * 阶段一：调视觉模型识别图片内容。
     * - 若素材不是图片、或未配置视觉模型、或编码失败 → 返回 null（跳过本阶段）。
     * - 失败时返回 null，不影响主流程（仅退化为仅文本）。
     */
    private suspend fun recognizeImageIfPossible(
        fullUrl: String,
        visionModel: String?,
        material: MaterialEntity
    ): String? {
        if (visionModel.isNullOrBlank()) return null
        if (!material.mimeType.startsWith("image/")) return null
        val dataUri = try {
            materialRepository.resolveMaterialDocument(material)?.uri
                ?.let { imageEncoder.encodeToDataUri(it) }
        } catch (e: Exception) {
            null
        } ?: return null

        val visionMessages = listOf(
            ChatMessage(
                role = "system",
                content = listOf(
                    ContentPart(
                        type = "text",
                        text = "你是图片内容识别助手。请用中文输出图片中可见的关键信息，" +
                            "包括：场景、主体（人/物/食物/截图等）、可见文字、颜色风格、情绪。" +
                            "只输出一段 200 字以内的客观描述，不要扩展或评论。"
                    )
                )
            ),
            ChatMessage(
                role = "user",
                content = listOf(
                    ContentPart(type = "text", text = "请描述这张图片的内容。"),
                    ContentPart(type = "image_url", imageUrl = ImageUrl(dataUri))
                )
            )
        )
        return runCatching { callApi(fullUrl, visionModel, visionMessages) }
            .getOrNull()
    }

    private suspend fun callApi(
        url: String,
        model: String,
        messages: List<ChatMessage>
    ): String {
        val request = ChatRequest(model = model, messages = messages, temperature = 0.85)
        val response = openAiApi.chatCompletion(url, request)
        return response.choices.firstOrNull()?.message?.content
            ?: throw IllegalStateException("空响应")
    }

    /**
     * 阶段二：组装文案生成消息。
     * @param imageDescription 图片描述（独立视觉模型结果 + OCR 合并），为空时仅用分类与标签。
     * @param imageDataUri 图片的 data URI（当文案模型支持多模态时直接附图）。
     */
    private suspend fun buildMessages(
        material: MaterialEntity,
        categoryDisplay: String,
        persona: PersonaEntity,
        tone: Tone,
        goal: Goal,
        imageDescription: String?,
        imageDataUri: String? = null
    ): List<ChatMessage> {
        val system = buildString {
            append("你是「${persona.nickname.ifBlank { "我" }}」的私域朋友圈代写助手。")
            append("人设：身份=${persona.identity.ifBlank { "未设定" }}；")
            append("专业背景=${persona.background.ifBlank { "未设定" }}；")
            append("性格=${persona.personality.ifBlank { "未设定" }}；")
            append("口头禅=${persona.catchphrase.ifBlank { "无" }}；")
            append("目标客户=${persona.targetAudience.ifBlank { "关注健康/身材的人群" }}。")
            append("\n${complianceChecker.systemRules(persona.forbiddenWords)}")
            append("\n语气要求：${tone.label}；转化目标：${goal.label}。")

            append("\n\n【最高优先级：图文必须 100% 一致】")
            append("\n你会收到一张图片（或图片描述文字）。写文案前必须遵守以下铁律：")
            append("\n1. 先仔细看图片，在 imageDescription 字段输出你实际看到的具体画面。")
            append("\n2. 正文（content）必须直接引用图片中可见的场景/物品/人物/动作。")
            append("\n3. 绝对禁止编造图片中不存在的场景：")
            append("\n   ❌ 如果图片是健身房镜子前的自拍 → 不能写'早上起来喝一杯温水'")
            append("\n   ❌ 如果图片是美食照片 → 不能写'今天跑步 5 公里'")
            append("\n   ❌ 如果图片是办公室场景 → 不能写'在海边度假'")
            append("\n   ✅ 应该写与画面直接对应的真实感受。")
            append("\n4. 文案主题由「分类名」+「图片实际内容」共同决定，两者冲突时以图片为准。")
            append("\n5. imageSeen 字段必须如实填写：true 表示你真的看到了图片，false 表示只收到文字描述。")

            append("\n\n【分类与主题参考】（仅供灵感，不是硬约束）")
            append("\n分类是「男生大体重」→ 可能的主题：健身/减脂/体重管理/身材变化/自信/饮食控制")
            append("\n分类是「美食探店」→ 可能的主题：探店感受/菜品评价/吃饭场景/生活仪式感")
            append("\n分类是「旅行日记」→ 可能的主题：旅行见闻/风景/行程/心情")
            append("\n⚠ 但具体写什么，永远以你实际看到的图片内容为准。")

            append("\n\n请严格输出 JSON：content(正文)、alternative1/2/3(三个备选)、")
            append("tags(话题标签数组)、imageDescription(你看到的图片内容描述,100字内)、")
            append("imageSeen(true/false,是否真正看到图片)、")
            append("imageSuggestion(如果知道图片内容,描述这张图;如果不知道,建议与分类匹配的配图)、")
            append("publishTime(建议发布时间)、complianceNote(合规提醒)。")
            append("不要输出 JSON 之外的任何内容。")
        }

        val hasImagePart = imageDataUri != null
        val userText = buildString {
            append("【分类】：$categoryDisplay\n")
            if (material.tags.isNotEmpty()) append("【标签】：${material.tags.joinToString("、")}\n")
            if (!imageDescription.isNullOrBlank()) {
                append("【图片内容描述】（由视觉识别得到）：\n$imageDescription\n")
            }
            if (hasImagePart) {
                append("📎 我正在附一张图片给你。请先仔细看图，然后写文案。\n")
            } else if (!material.mimeType.startsWith("image/")) {
                append("（素材不是图片）\n")
            } else {
                append("⚠ 素材是一张图片，但图片传递可能失败了。")
                append("如果上方有图片描述文字，请围绕它写；")
                append("如果没有，请只围绕分类名写通用文案，并在 imageSeen 填 false。\n")
            }
            append("请写一条符合人设、语气和转化目标的朋友圈文案。")
            append("记住：正文必须与你看到的图片内容直接相关。")
        }

        // 如果有图片 data URI，直接附图让模型自己看（同时保留文字描述做双重确认）。
        val parts = if (imageDataUri != null) {
            listOf(
                ContentPart(type = "text", text = userText),
                ContentPart(type = "image_url", imageUrl = ImageUrl(imageDataUri))
            )
        } else {
            listOf(ContentPart(type = "text", text = userText))
        }

        return listOf(
            ChatMessage(role = "system", content = listOf(ContentPart(type = "text", text = system))),
            ChatMessage(role = "user", content = parts)
        )
    }

    /** 解析模型 JSON 输出，兼容 ```json 包裹与多余文本。 */
    private fun parseDraft(raw: String): PostDraft {
        val adapter = moshi.adapter(PostDraft::class.java)
        val json = extractJson(raw) ?: return PostDraft(content = raw)
        return runCatching { adapter.fromJson(json) }.getOrNull()
            ?: PostDraft(content = raw)
    }

    private fun extractJson(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        return if (start in 0 until end) raw.substring(start, end + 1) else null
    }

    private fun buildComplianceNote(
        aiNote: String?,
        hits: List<com.personalip.app.data.compliance.ComplianceHit>,
        mismatchWarnings: List<String> = emptyList()
    ): String? {
        val parts = mutableListOf<String>()
        aiNote?.let { parts.add(it) }
        if (hits.isNotEmpty()) {
            val hitText = hits.joinToString("、") { "${it.word}(${it.label})" }
            parts.add("⚠ 命中风险词：$hitText，请修改后再发布。")
        }
        if (mismatchWarnings.isNotEmpty()) {
            parts.add("⚠ 图文一致性警告：${mismatchWarnings.joinToString("；")}")
        }
        return parts.joinToString("\n").ifBlank { null }
    }

    /**
     * 事后图文一致性校验（关键词级别）：
     * 1. 模型声称没看图 (imageSeen=false) 但素材是图片 → 警告。
     * 2. 模型看图了 (imageSeen=true) 且输出了 imageDescription，
     *    但文案正文没提到描述里的关键元素 → 警告。
     * 3. imageSuggestion 明显和 imageDescription 矛盾（如描述是健身房但建议配图是早餐）→ 警告。
     */
    private fun checkImageContentConsistency(
        draft: PostDraft,
        imageAvailable: Boolean,
        imageDescription: String?
    ): List<String> {
        val warnings = mutableListOf<String>()

        // 情况 1：素材是图片，但模型说没看到
        if (imageAvailable && draft.imageSeen == false && !imageDescription.isNullOrBlank()) {
            warnings.add("素材是图片，但模型声称没看到；独立视觉识别已得结果，建议人工复核")
        }

        // 情况 2：模型说看到了图，输出了 imageDescription，但正文完全没引用关键元素
        if (draft.imageSeen == true && !draft.imageDescription.isNullOrBlank() && draft.content.isNotBlank()) {
            val descKeywords = extractKeywords(draft.imageDescription)
            val contentLower = draft.content
            val hitCount = descKeywords.count { contentLower.contains(it) }
            // 如果描述有关键词但正文一个都没提到，且正文超过 20 字 → 可疑
            if (descKeywords.size >= 2 && hitCount == 0 && draft.content.length > 20) {
                warnings.add("模型描述图片为「${descKeywords.take(3).joinToString("/")}」，但文案正文未提及相关元素")
            }
        }

        // 情况 3：imageSuggestion 和 imageDescription 明显矛盾
        if (!draft.imageDescription.isNullOrBlank() && !draft.imageSuggestion.isNullOrBlank()
            && draft.imageSeen == true
        ) {
            val descScene = dominantScene(draft.imageDescription)
            val suggScene = dominantScene(draft.imageSuggestion)
            if (descScene != null && suggScene != null && descScene != suggScene) {
                warnings.add("模型描述图片为「$descScene」，但配图建议却是「$suggScene」，明显不一致")
            }
        }

        return warnings
    }

    /** 从描述文字里提取关键词（简单切分+过滤）。 */
    private fun extractKeywords(text: String): List<String> {
        // 先拆词：尝试按非中文/非字母数字切，然后过滤掉单字和停用词
        val stopWords = setOf(
            "一", "有", "在", "和", "是", "的", "了", "也", "人", "就",
            "都", "而", "及", "与", "这", "那", "个", "他", "她", "它",
            "我", "你", "们", "上", "下", "中", "里", "外", "到", "好",
            "不", "很", "会", "被", "从", "向", "对", "把", "让", "给"
        )
        return text
            .split(Regex("[\\s\\p{Punct}，。、；：！？（）《》【】\"'`]+"))
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
            .take(12)
    }

    /** 判断描述文字的主导场景，用于 imageSuggestion vs imageDescription 冲突检测。 */
    private fun dominantScene(text: String): String? {
        val scenes = listOf(
            "健身房", "健身", "训练", "撸铁", "哑铃", "跑步机", "镜子",
            "美食", "餐厅", "探店", "菜品", "吃饭", "早餐", "下午茶", "午餐", "晚餐",
            "旅行", "旅游", "风景", "海边", "山", "度假", "出发",
            "办公室", "工作", "工位", "电脑", "开会", "打卡",
            "家", "客厅", "卧室", "沙发", "床",
            "咖啡", "奶茶", "饮料", "饮品",
            "手机", "电脑", "书", "书本",
            "小孩", "宝宝", "宠物", "猫", "狗",
            "运动", "跑步", "骑行", "瑜伽", "户外",
            "自拍", "照片", "合影", "集体",
            "水", "杯子", "水杯", "饮品",
            "早餐", "晚餐", "午餐",
            "健身", "减脂", "增肌", "肌肉", "体重",
        )
        return scenes.firstOrNull { text.contains(it) }
    }

    /** 写 Markdown 到 输出/<周>/<周几>/<时间_分类_编号>.md。 */
    private suspend fun saveMarkdown(
        weekId: String,
        dayOfWeek: Int,
        time: String,
        categoryFolderName: String,
        post: PostEntity,
        complianceNote: String?,
        rawText: String,
        imageDescription: String?
    ) = withContext(Dispatchers.IO) {
        val outputDir = rootFolderRepository.getOutputDir() ?: return@withContext
        val weekDir = ensureNestedDir(outputDir, weekId) ?: return@withContext
        val dayDir = ensureNestedDir(weekDir, WeekIdUtil.dayLabel(dayOfWeek)) ?: return@withContext

        val timePart = FileNameUtil.sanitize(time.replace(":", "-"))
        val catPart = FileNameUtil.sanitize(categoryFolderName)
        val seq = (dayDir.listFiles().count { it.name?.endsWith(".md") == true } + 1)
            .toString().padStart(3, '0')
        val fileName = "${timePart}_${catPart}_$seq.md"

        val mdDoc = dayDir.createFile("text/markdown", fileName) ?: return@withContext
        val md = buildMarkdown(post, complianceNote, rawText, imageDescription)
        runCatching {
            context.contentResolver.openOutputStream(mdDoc.uri)?.use { it.write(md.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun buildMarkdown(
        post: PostEntity,
        complianceNote: String?,
        rawText: String,
        imageDescription: String?
    ): String = buildString {
        appendLine("# 朋友圈文案")
        appendLine()
        if (!imageDescription.isNullOrBlank()) {
            appendLine("## 图片识别（阶段一）")
            appendLine(imageDescription)
            appendLine()
        }
        appendLine("## 正文"); appendLine(post.content)
        post.alternative1?.let { appendLine("\n## 备选 1"); appendLine(it) }
        post.alternative2?.let { appendLine("\n## 备选 2"); appendLine(it) }
        post.alternative3?.let { appendLine("\n## 备选 3"); appendLine(it) }
        if (post.tags.isNotEmpty()) appendLine("\n## 话题标签\n${post.tags.joinToString(" ")}")
        post.imageSuggestion?.let { appendLine("\n## 配图建议\n$it") }
        post.publishTime?.let { appendLine("\n## 建议发布时间\n$it") }
        complianceNote?.let { appendLine("\n## 合规提醒\n$it") }
        appendLine("\n## 原始返回（调试用）\n```\n$rawText\n```")
    }.trimEnd()

    private fun ensureNestedDir(parent: DocumentFile, name: String): DocumentFile? {
        val safe = FileNameUtil.sanitize(name)
        if (safe.isBlank()) return null
        parent.findFile(safe)?.takeIf { it.isDirectory }?.let { return it }
        return parent.createDirectory(safe)
    }
}

data class GenerateResult(
    val postId: Long? = null,
    val complianceHits: List<com.personalip.app.data.compliance.ComplianceHit> = emptyList(),
    val complianceNote: String? = null,
    val error: String? = null
) {
    val isSuccess: Boolean get() = postId != null
}
