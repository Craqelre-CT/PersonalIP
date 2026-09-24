package com.personalip.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.compliance.ComplianceChecker
import com.personalip.app.data.compliance.ComplianceHit
import com.personalip.app.data.compliance.Level
import com.personalip.app.data.local.PostStatus
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.dao.PersonaDao
import com.personalip.app.data.local.dao.PostDao
import com.personalip.app.data.local.dao.WeeklyPlanDao
import com.personalip.app.data.local.entity.CategoryEntity
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.local.entity.PostEntity
import com.personalip.app.data.local.entity.WeeklyPlanEntity
import com.personalip.app.data.material.MaterialRepository
import com.personalip.app.data.schedule.ScheduleRepository
import com.personalip.app.data.schedule.WeekIdUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOn

/**
 * 今日待发：展示当天（ISO 周几）的排期项 + 已生成的朋友圈文案，
 * 支持复制文案到剪贴板、系统分享（默认微信）、状态标记（草稿/待发/已发/跳过）。
 *
 * 不做自动发布朋友圈，只做复制到剪贴板和系统分享。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val postDao: PostDao,
    private val materialDao: MaterialDao,
    private val personaDao: PersonaDao,
    categoryDao: CategoryDao,
    private val materialRepository: MaterialRepository,
    private val scheduleRepository: ScheduleRepository,
    private val complianceChecker: ComplianceChecker
) : ViewModel() {

    private val weekId: String = WeekIdUtil.weekIdOf()
    private val dayOfWeek: Int = WeekIdUtil.dayOfWeekValue()

    private val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _copyMessage = MutableStateFlow<String?>(null)
    val copyMessage: StateFlow<String?> = _copyMessage.asStateFlow()

    /** 今日排期项 + 关联文案 + 素材。 */
    val items: StateFlow<List<HomeItem>> =
        combine(weeklyPlanDao.observeToday(weekId, dayOfWeek), categories) { plans, cats ->
            val byId = cats.associateBy { it.id }
            plans.map { plan ->
                val material = plan.materialId.takeIf { it > 0 }?.let { materialDao.getById(it) }
                val post = plan.postId?.let { postDao.getById(it) }
                HomeItem(
                    plan = plan,
                    material = material,
                    materialUri = material?.let { materialRepository.resolveMaterialUri(it) },
                    post = post,
                    categoryDisplay = material?.categoryId?.let { byId[it]?.displayName }
                        ?: "未分类",
                    dayLabel = WeekIdUtil.dayLabel(plan.dayOfWeek)
                )
            }
        }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 拼装可复制的完整文案（正文 + 话题标签）。 */
    fun buildShareText(post: PostEntity): String = buildString {
        append(post.content)
        if (post.tags.isNotEmpty()) append("\n\n").append(post.tags.joinToString(" "))
    }

    /** 复制文案到系统剪贴板。 */
    fun copyText(post: PostEntity) {
        val text = buildShareText(post)
        @Suppress("DEPRECATION")
        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("朋友圈文案", text))
        _copyMessage.value = "已复制到剪贴板"
    }

    /** 构造系统分享 Intent（微信 / 其它应用），由 UI 调 startActivity。 */
    fun buildShareIntent(post: PostEntity): Intent {
        val text = buildShareText(post)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            // 设置包名可直接拉起微信；若未安装微信则回退到系统分享选择器。
            setPackage("com.tencent.mm")
        }
    }

    /** 系统分享选择器 Intent（不限定微信，用户自选目标应用）。 */
    fun buildShareChooserIntent(post: PostEntity): Intent = Intent.createChooser(
        buildShareIntent(post).apply { setPackage(null) },
        "分享到微信"
    )

    /** 更新排期状态（草稿/待发/已发/跳过）。同时同步 posts 状态。 */
    fun setStatus(plan: WeeklyPlanEntity, status: PostStatus) {
        viewModelScope.launch {
            scheduleRepository.setStatus(plan.id, status, plan.postId)
            plan.postId?.let { postDao.updateStatus(it, status) }
        }
    }

    /**
     * 对文案做实时合规检测（用当前人设的禁用词）。
     * 在复制 / 分享前由 UI 调用，命中 HARD_BLOCK 时应提示用户修改。
     */
    suspend fun checkCompliance(post: PostEntity): List<ComplianceHit> {
        val persona = personaDao.get()
        return complianceChecker.check(post.content, persona?.forbiddenWords ?: emptyList())
    }

    fun clearMessage() { _copyMessage.value = null }
}

/** 今日待发列表项。 */
data class HomeItem(
    val plan: WeeklyPlanEntity,
    val material: MaterialEntity?,
    val materialUri: Uri?,
    val post: PostEntity?,
    val categoryDisplay: String,
    val dayLabel: String
) {
    val statusLabel: String get() = plan.status.label
    val hasPost: Boolean get() = post != null
}
