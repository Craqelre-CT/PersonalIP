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

        // 2. 图片识别（阶段一）：仅当素材是图片且配置了视觉模型时调用。
        val imageDescription = runCatching {
            recognizeImageIfPossible(fullUrl, visionModel, material)
        }.getOrNull()

        // 3. 组装文案生成消息（阶段二）。
        val messages = buildMessages(
            material, category?.displayName ?: "未分类", persona, tone, goal, imageDescription
        )

        // 4. 调用文案模型（失败重试一次）。
        val rawText = runCatching { callApi(fullUrl, model!!, messages) }
            .recoverCatching { callApi(fullUrl, model, messages) }
            .getOrNull()
            ?: return@withContext GenerateResult(error = "AI 调用失败，请检查网络与接口配置后重试")

        // 5. 解析 JSON 草稿。
        val draft = parseDraft(rawText)

        // 6. 本地合规扫描。
        val hits = complianceChecker.check(draft.content, persona.forbiddenWords)
        val complianceNote = buildComplianceNote(draft.complianceNote, hits)

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
     * @param imageDescription 阶段一得到的图片描述，为空时仅用 OCR 文字 + 标签。
     */
    private suspend fun buildMessages(
        material: MaterialEntity,
        categoryDisplay: String,
        persona: PersonaEntity,
        tone: Tone,
        goal: Goal,
        imageDescription: String?
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
            append("\n请严格输出 JSON，字段：content(正文)、alternative1/2/3(三个备选)、")
            append("tags(话题标签数组,带#)、imageSuggestion(配图建议)、publishTime(建议发布时间)、")
            append("complianceNote(合规提醒)。不要输出 JSON 之外的内容。")
        }

        val userText = buildString {
            append("分类：$categoryDisplay。\n")
            if (material.tags.isNotEmpty()) append("标签：${material.tags.joinToString("、")}。\n")
            if (!imageDescription.isNullOrBlank()) {
                append("素材图片识别结果（环境/主体/文字/情绪）：\n$imageDescription\n")
            }
            if (!material.ocrText.isNullOrBlank()) {
                append("素材 OCR 文字（聊天截图等）：\n${material.ocrText}\n")
            }
            append("请基于以上信息写一条朋友圈文案。")
        }
        // 文案生成阶段不再附图，仅传文本（避免视觉模型与文案模型混用）。
        val parts = listOf(ContentPart(type = "text", text = userText))

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

    private fun buildComplianceNote(aiNote: String?, hits: List<com.personalip.app.data.compliance.ComplianceHit>): String? {
        if (hits.isEmpty()) return aiNote
        val hitText = hits.joinToString("、") { "${it.word}(${it.label})" }
        return listOfNotNull(aiNote, "⚠ 命中风险词：$hitText，请修改后再发布。").joinToString("\n")
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
