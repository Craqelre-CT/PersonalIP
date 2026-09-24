package com.personalip.app.ui.schedule

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personalip.app.data.ai.AiRepository
import com.personalip.app.data.ai.GenerateResult
import com.personalip.app.data.ai.Goal
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

    val weekId: String = WeekIdUtil.weekIdOf()

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _slotTimes = MutableStateFlow(listOf("08:00", "12:00", "18:00"))
    val slotTimes: StateFlow<List<String>> = _slotTimes.asStateFlow()

    private val _cooldownDays = MutableStateFlow(7)
    val cooldownDays: StateFlow<Int> = _cooldownDays.asStateFlow()

    private val _selectedCategoryIds = MutableStateFlow<List<Long>>(emptyList())
    val selectedCategoryIds: StateFlow<List<Long>> = _selectedCategoryIds.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** 正在生成文案的排期 id。 */
    private val _generatingPlanId = MutableStateFlow<Long?>(null)
    val generatingPlanId: StateFlow<Long?> = _generatingPlanId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val plans: StateFlow<List<WeeklyPlanEntity>> =
        scheduleRepository.observeWeek(weekId)
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
                        dayLabel = WeekIdUtil.dayLabel(plan.dayOfWeek),
                        statusLabel = plan.status.label
                    )
                }
            }.flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setCooldown(days: Int) { _cooldownDays.value = days }
    fun toggleCategory(id: Long) {
        _selectedCategoryIds.value = _selectedCategoryIds.value.toMutableList()
            .apply { if (contains(id)) remove(id) else add(id) }
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
                scheduleRepository.generateWeek(
                    weekId = weekId,
                    slotTimes = _slotTimes.value,
                    categoryIds = _selectedCategoryIds.value,
                    cooldownDays = _cooldownDays.value
                )
            }.getOrNull()
            _generating.value = false
            _message.value = when {
                result == null -> "生成失败，请重试"
                result.shortfall != null -> result.shortfall.toMessage()
                else -> "已生成 ${result.plans.size} 条本周计划"
            }
        }
    }

    fun replaceMaterial(planId: Long) {
        viewModelScope.launch {
            val ok = scheduleRepository.replaceMaterial(planId, weekId)
            _message.value = if (ok) "已替换为其它素材" else "没有更多可用素材"
        }
    }

    fun movePlan(planId: Long, up: Boolean) {
        viewModelScope.launch { scheduleRepository.movePlan(planId, weekId, up) }
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

    fun clearMessage() { _message.value = null }
}

data class ScheduleItem(
    val plan: WeeklyPlanEntity,
    val material: MaterialEntity?,
    val materialUri: Uri?,
    val categoryDisplay: String,
    val dayLabel: String,
    val statusLabel: String
)
