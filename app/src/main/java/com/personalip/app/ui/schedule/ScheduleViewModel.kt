package com.personalip.app.ui.schedule

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.ai.AiRepository
import com.personalip.app.data.ai.CaptionStyle
import com.personalip.app.data.ai.GenerateResult
import com.personalip.app.data.ai.Goal
import com.personalip.app.data.ai.RecognizeResult
import com.personalip.app.data.ai.Tone
import com.personalip.app.data.local.PostStatus
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.entity.CategoryEntity
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.local.entity.WeeklyPlanEntity
import com.personalip.app.data.material.MaterialRepository
import com.personalip.app.data.schedule.ScheduleRepository
import com.personalip.app.data.schedule.WeekIdUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleRepository: ScheduleRepository,
    private val materialRepository: MaterialRepository,
    private val aiRepository: AiRepository,
    categoryDao: CategoryDao
) : ViewModel() {

    /** 计划起始日（默认今天，进入页面后固定）。 */
    private val startDate: LocalDate = LocalDate.now()
    private val todayKey: String = WeekIdUtil.dateKey(startDate)

    /** 起始日展示标签，如 "09-30 周三"。 */
    val startDateLabel: String = WeekIdUtil.dateLabel(startDate)

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _slotTimes = MutableStateFlow(listOf("08:00", "12:00", "18:00"))
    val slotTimes: StateFlow<List<String>> = _slotTimes.asStateFlow()

    /** 计划天数（从今天起连续 N 天，可跨周）。 */
    private val _days = MutableStateFlow(7)
    val days: StateFlow<Int> = _days.asStateFlow()

    /** 全局冷却期（分类未单独设置时的兜底）。 */
    private val _cooldownDays = MutableStateFlow(7)
    val cooldownDays: StateFlow<Int> = _cooldownDays.asStateFlow()

    /**
     * 参与分类及其权重。key=分类id，value=权重（1-10，默认1）。
     * 空 map 表示「全部分类、权重均为1」。
     */
    private val _categoryWeights = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val categoryWeights: StateFlow<Map<Long, Int>> = _categoryWeights.asStateFlow()

    /** 当前选中的分类 id 列表（从权重 map 派生，供 UI 判断选中态）。 */
    val selectedCategoryIds: StateFlow<List<Long>> = _categoryWeights
        .map { it.keys.toList() }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** 正在生成文案的排期 id。 */
    private val _generatingPlanId = MutableStateFlow<Long?>(null)
    val generatingPlanId: StateFlow<Long?> = _generatingPlanId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val plans: StateFlow<List<WeeklyPlanEntity>> =
        scheduleRepository.observeActive(todayKey)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    /** 排期列表项：携带素材信息与缩略图 Uri。 */
    val items: StateFlow<List<ScheduleItem>> =
        combine(plans, categories) { p, cats -> p to cats }
            .map { (planList, cats) ->
                val byId = cats.associateBy { it.id }
                planList.map { plan ->
                    val material = materialRepository.getById(plan.materialId)
                    ScheduleItem(
                        plan = plan,
                        material = material,
                        materialUri = material?.let { materialRepository.resolveMaterialUri(it) },
                        categoryDisplay = material?.categoryId?.let { byId[it]?.displayName }
                            ?: "未分类",
                        dayLabel = WeekIdUtil.dateLabelOf(plan.date),
                        statusLabel = plan.status.label
                    )
                }
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setDays(days: Int) { _days.value = days.coerceIn(1, 60) }
    fun setCooldown(days: Int) { _cooldownDays.value = days }
    fun toggleCategory(id: Long) {
        _categoryWeights.value = _categoryWeights.value.toMutableMap().apply {
            if (containsKey(id)) remove(id) else put(id, 1)
        }
    }

    /** 设置某分类的权重（1-10）。若该分类未选中，自动加入。 */
    fun setCategoryWeight(id: Long, weight: Int) {
        val w = weight.coerceIn(1, 10)
        _categoryWeights.value = _categoryWeights.value.toMutableMap().apply { put(id, w) }
    }
    fun addSlotTime() {
        // 新时段默认为最后一个时段 +1 小时（与 web 端逻辑保持一致）
        val slots = _slotTimes.value.toMutableList()
        val nextTime = if (slots.isEmpty()) "08:00"
        else {
            val (h, m) = slots.last().split(":").map { it.toIntOrNull() ?: 0 }
            val nh = (h + 1) % 24
            "%02d:%02d".format(nh, m)
        }
        slots += nextTime
        _slotTimes.value = slots.sorted()  // 自动按时间升序排序
    }
    fun setSlotTime(index: Int, time: String) {
        val list = _slotTimes.value.toMutableList()
        if (index in list.indices) list[index] = time
        _slotTimes.value = list.sorted()  // 修改后自动排序
    }
    fun removeSlotTime(index: Int) {
        val list = _slotTimes.value.toMutableList()
        if (index in list.indices) list.removeAt(index)
        _slotTimes.value = list  // 删除后保持原序（已是有序）
    }

    fun generate() {
        _generating.value = true
        viewModelScope.launch {
            val result = runCatching {
                scheduleRepository.generatePlan(
                    startDate = startDate,
                    days = _days.value,
                    slotTimes = _slotTimes.value,
                    categoryWeights = _categoryWeights.value,
                    globalCooldownDays = _cooldownDays.value
                )
            }.getOrNull()
            _generating.value = false
            _message.value = when {
                result == null -> "生成失败，请重试"
                result.shortfall != null -> result.shortfall.toMessage()
                else -> "已生成 ${result.plans.size} 条计划（从今天起 ${_days.value} 天）"
            }
        }
    }

    fun replaceMaterial(planId: Long) {
        viewModelScope.launch {
            val ok = scheduleRepository.replaceMaterial(planId, _cooldownDays.value)
            _message.value = if (ok) "已替换为其它素材" else "没有更多可用素材"
        }
    }

    /** 清除从今天起的所有排期（包括已发/跳过），用于从头重新生成。 */
    fun clearPlans() {
        viewModelScope.launch {
            val deleted = runCatching { scheduleRepository.clearAllPlans() }.getOrDefault(0)
            _message.value = "已清除 $deleted 条排期"
        }
    }

    fun movePlan(planId: Long, up: Boolean) {
        viewModelScope.launch { scheduleRepository.movePlan(planId, up) }
    }

    fun setStatus(planId: Long, status: PostStatus) {
        viewModelScope.launch { scheduleRepository.setStatus(planId, status) }
    }

    fun generatePost(planId: Long, tone: Tone, goal: Goal) {
        _generating.value = true
        _generatingPlanId.value = planId
        viewModelScope.launch {
            val result: GenerateResult = runCatching {
                aiRepository.generateForPlan(planId, tone, goal)
            }.getOrNull() ?: GenerateResult(error = "生成异常，请重试")
            _generating.value = false
            _generatingPlanId.value = null
            _message.value = when {
                result.error != null -> result.error
                result.complianceHits.isNotEmpty() ->
                    "文案已生成（⚠ 命中 ${result.complianceHits.size} 个风险词，见详情）"
                else -> "文案已生成并保存到输出目录"
            }
        }
    }

    // ==================== 两阶段交互式生成（识别 → 确认 → 写文案） ====================

    /** 当前生成步骤（两阶段流程的状态机）。 */
    private val _genStep = MutableStateFlow<GenStep>(GenStep.Idle)
    val genStep: StateFlow<GenStep> = _genStep.asStateFlow()

    /** 阶段一：调视觉模型识别图片。用户给方向，模型返回识别结果。 */
    fun recognize(materialId: Long, direction: String) {
        _genStep.value = GenStep.Recognizing(direction)
        viewModelScope.launch {
            val result: RecognizeResult = runCatching {
                aiRepository.recognizeImage(materialId, direction)
            }.getOrNull() ?: RecognizeResult(error = "识别异常，请重试")
            _genStep.value = when {
                result.isSuccess -> GenStep.Recognized(result.content!!, direction)
                else -> GenStep.Error(result.error ?: "识别失败")
            }
        }
    }

    /** 阶段二：用已确认的识别结果 + 用户意图 + 风格 → 文本 AI 写文案并落库。 */
    fun writeCaption(planId: Long, intent: String, style: CaptionStyle) {
        val recognized = (_genStep.value as? GenStep.Recognized)?.content ?: return
        _genStep.value = GenStep.Writing(recognized, intent, style)
        viewModelScope.launch {
            val result: GenerateResult = runCatching {
                aiRepository.writeCaption(planId, recognized, intent, style)
            }.getOrNull() ?: GenerateResult(error = "文案生成异常，请重试")
            _genStep.value = when {
                result.isSuccess -> {
                    _message.value = when {
                        result.complianceHits.isNotEmpty() ->
                            "文案已生成（⚠ 命中 ${result.complianceHits.size} 个风险词，见详情）"
                        else -> "文案已生成并保存到输出目录"
                    }
                    GenStep.Done(result)
                }
                else -> GenStep.Error(result.error ?: "文案生成失败")
            }
        }
    }

    /** 从 Error 状态回到 Recognized（保留上次识别结果，改方向重识别）。 */
    fun backToRecognized() {
        _genStep.value = GenStep.Idle
    }

    /** 重置两阶段流程到 Idle（关闭对话框时调用）。 */
    fun resetGen() { _genStep.value = GenStep.Idle }

    fun clearMessage() { _message.value = null }
}

/** 两阶段交互式生成的状态机。 */
sealed class GenStep {
    /** 未开始。 */
    object Idle : GenStep()
    /** 阶段一进行中：正在调视觉模型识别图片。 */
    data class Recognizing(val direction: String) : GenStep()
    /** 阶段一完成：识别结果已返回，等待用户确认或改方向重识别。 */
    data class Recognized(val content: String, val direction: String) : GenStep()
    /** 阶段二进行中：正在调文本模型写文案。 */
    data class Writing(val recognizedContent: String, val intent: String, val style: CaptionStyle) : GenStep()
    /** 阶段二完成：文案已生成并落库。 */
    data class Done(val result: GenerateResult) : GenStep()
    /** 出错（识别或写文案失败）。 */
    data class Error(val message: String) : GenStep()
}

data class ScheduleItem(
    val plan: WeeklyPlanEntity,
    val material: MaterialEntity?,
    val materialUri: Uri?,
    val categoryDisplay: String,
    val dayLabel: String,
    val statusLabel: String
)
