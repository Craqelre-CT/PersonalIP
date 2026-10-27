package com.personalip.app.data.schedule

import com.personalip.app.data.local.CategorySettingsMapper
import com.personalip.app.data.local.PostStatus
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.dao.UsageRecordDao
import com.personalip.app.data.local.dao.WeeklyPlanDao
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.local.entity.UsageRecordEntity
import com.personalip.app.data.local.entity.WeeklyPlanEntity
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 排期闭环：从素材库自动选材，生成「从某天起连续 N 天 × 每天 M 条」的计划。
 * 保证同批内同一素材不重复；冷却期内（按素材所属分类各自的冷却期）已发过的素材不再选用。
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val materialDao: MaterialDao,
    private val usageRecordDao: UsageRecordDao,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val categoryDao: CategoryDao,
    private val settingsMapper: CategorySettingsMapper
) {
    /** 观察从 [sinceDate]（含）起的排期（含历史已发/跳过）。 */
    fun observeActive(sinceDate: String): Flow<List<WeeklyPlanEntity>> =
        weeklyPlanDao.observeActive(sinceDate)

    /**
     * 生成计划：从 [startDate] 起连续 [days] 天，每天 [slotTimes] 条。
     *
     * @param days 计划天数（1-60）
     * @param categoryIds 参与选材的分类；空表示全部
     * @param globalCooldownDays 未单独设置冷却期的分类 / 未分类素材所用的兜底冷却期（天）
     */
    suspend fun generatePlan(
        startDate: LocalDate,
        days: Int,
        slotTimes: List<String>,
        categoryIds: List<Long>,
        globalCooldownDays: Int
    ): PlanResult = withContext(Dispatchers.IO) {
        val batchId = WeekIdUtil.dateKey(startDate)

        // 重新生成前，清掉从今天起、尚未发布的旧排期（保留已发/跳过历史）。
        weeklyPlanDao.deleteUnsentFrom(batchId, listOf(PostStatus.SENT, PostStatus.SKIPPED))

        val now = System.currentTimeMillis()
        val categorySet = categoryIds.toSet()

        // 分类级冷却期：分类未单独设置（null）时用全局兜底。
        val cooldownByCategory: Map<Long, Int?> = categoryDao.getAll()
            .associate { it.id to settingsMapper.decode(it.settingsJson).cooldownDays }

        // 全部候选（按使用次数升序，用得少的优先）。
        val all = materialDao.getAllSortedByUsage()
            .filter { categorySet.isEmpty() || it.categoryId in categorySet }

        // 过滤掉冷却期内（按各自分类冷却期）已发过的素材。
        val available = mutableListOf<MaterialEntity>()
        for (m in all) {
            val cooldown = cooldownByCategory[m.categoryId] ?: globalCooldownDays
            val since = now - cooldown.toLong() * 24L * 60L * 60L * 1000L
            if (usageRecordDao.countSince(m.id, since) <= 0) available += m
        }

        // days 天 × 每日条数 = 总槽位。按日期顺序填充，每个素材只取一次 → 同批不重复。
        val plans = mutableListOf<WeeklyPlanEntity>()
        var cursor = 0
        outer@ for (i in 0 until days) {
            val date = startDate.plusDays(i.toLong())
            for (time in slotTimes) {
                if (cursor >= available.size) break@outer
                val m = available[cursor++]
                plans += WeeklyPlanEntity(
                    batchId = batchId,
                    date = WeekIdUtil.dateKey(date),
                    dayOfWeek = date.dayOfWeek.value,
                    time = time,
                    materialId = m.id,
                    status = PostStatus.DRAFT
                )
            }
        }
        if (plans.isNotEmpty()) weeklyPlanDao.insertAll(plans)

        // 素材不足时给出每分类缺口。
        val totalSlots = days * slotTimes.size
        val shortfall: Shortfall? = if (plans.size < totalSlots) {
            val cats = if (categorySet.isEmpty()) categoryDao.getAll()
            else categoryDao.getAll().filter { it.id in categorySet }
            Shortfall(
                totalSlots = totalSlots,
                available = plans.size,
                byCategory = cats.map { cat -> cat.displayName to available.count { it.categoryId == cat.id } }
            )
        } else null

        PlanResult(plans = plans, shortfall = shortfall)
    }

    /**
     * 用另一个可用素材替换某条排期的素材。
     *
     * 替换策略（按优先级递减）：
     * 1. 同类素材优先：先查当前素材所在分类，在冷却期可用的同类候选里随机挑一个；
     * 2. 同类冷却期可用数 < 2（太少，会反复来回换）→ 扩大到「全部分类 + 冷却期可用」的池子里随机挑；
     * 3. 同批次已占用的素材一律排除（不管哪个池子）。
     *
     * @param globalCooldownDays 全局冷却期（分类未单独设置时的兜底），单位：天。
     */
    suspend fun replaceMaterial(planId: Long, globalCooldownDays: Int): Boolean =
        withContext(Dispatchers.IO) {
            val current = weeklyPlanDao.getById(planId) ?: return@withContext false
            val currentMaterial = materialDao.getById(current.materialId)
                ?: return@withContext false
            val batchPlans = weeklyPlanDao.getBatch(current.batchId)
            val usedIds = batchPlans.map { it.materialId }.toMutableSet()
            // 正在被替换的这张也算"已占用"，不允许替换成自己。
            usedIds += current.materialId

            // 冷却期：分类有独立设置则优先用分类的，兜底全局。
            val catSettings = categoryDao.getById(currentMaterial.categoryId)
                ?.settingsJson?.let { settingsMapper.decode(it) }
            val cooldownDays = catSettings?.cooldownDays ?: globalCooldownDays
            val since = System.currentTimeMillis() -
                cooldownDays.toLong() * 24L * 60L * 60L * 1000L
            val cooldownBlockedIds = usageRecordDao.recentlyUsedMaterialIds(since).toSet()

            // 全部基础候选 = 同批次未用 + 冷却期未用。
            val baseCandidates = materialDao.getAllSortedByUsage()
                .filter { it.id !in usedIds && it.id !in cooldownBlockedIds }

            // 池 A：同类候选（优先）。
            val sameCategory = baseCandidates.filter { it.categoryId == currentMaterial.categoryId }

            val pool: List<MaterialEntity> = if (sameCategory.size >= 2) {
                sameCategory
            } else {
                // 同类不足 → 扩大到全部分类。
                baseCandidates
            }

            val replacement = pool.randomOrNull() ?: return@withContext false
            weeklyPlanDao.update(current.copy(materialId = replacement.id))
            true
        }

    /**
     * 清除当前所有未发布的排期（草稿/待发），保留已发/跳过历史。
     * 用于用户想清空现有计划后重新生成。
     * @return 删除的排期条数。
     */
    suspend fun clearUnsentPlans(): Int = withContext(Dispatchers.IO) {
        val since = WeekIdUtil.dateKey(LocalDate.now())
        weeklyPlanDao.deleteUnsentFrom(since, listOf(PostStatus.SENT, PostStatus.SKIPPED))
    }

    /** 上移 / 下移某条排期（交换相邻两条的 date + time，保留各自 id 与 materialId）。 */
    suspend fun movePlan(planId: Long, up: Boolean): Boolean = withContext(Dispatchers.IO) {
        val current = weeklyPlanDao.getById(planId) ?: return@withContext false
        val plans = weeklyPlanDao.getBatch(current.batchId)
        val index = plans.indexOfFirst { it.id == planId }
        if (index == -1) return@withContext false
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in plans.indices) return@withContext false
        val a = plans[index]
        val b = plans[swapWith]
        weeklyPlanDao.update(a.copy(date = b.date, dayOfWeek = b.dayOfWeek, time = b.time))
        weeklyPlanDao.update(b.copy(date = a.date, dayOfWeek = a.dayOfWeek, time = a.time))
        true
    }

    /**
     * 更新排期状态。标记「已发」时写入素材使用记录（冷却期判定依据），
     * 且仅在「非已发 → 已发」时记录一次，避免重复计数。
     */
    suspend fun setStatus(planId: Long, status: PostStatus, postId: Long? = null) =
        withContext(Dispatchers.IO) {
            val plan = weeklyPlanDao.getById(planId) ?: return@withContext
            val wasSent = plan.status == PostStatus.SENT
            weeklyPlanDao.updateStatusAndPost(planId, status, postId)
            if (status == PostStatus.SENT && !wasSent) {
                usageRecordDao.insert(
                    UsageRecordEntity(
                        materialId = plan.materialId,
                        weekId = plan.batchId,
                        postId = postId
                    )
                )
                materialDao.incrementUsage(plan.materialId, System.currentTimeMillis())
            }
        }
}

/** 计划生成结果。 */
data class PlanResult(
    val plans: List<WeeklyPlanEntity>,
    val shortfall: Shortfall?
) {
    val hasShortfall: Boolean get() = shortfall != null
}

/** 素材不足缺口信息，用于提示「减少天数/条数或先导入素材」。 */
data class Shortfall(
    val totalSlots: Int,
    val available: Int,
    /** 分类显示名 -> 可用数量。 */
    val byCategory: List<Pair<String, Int>>
) {
    fun toMessage(): String {
        val catLines = byCategory.joinToString("，") { "${it.first} ${it.second} 张" }
        return "本次共需 $totalSlots 条，可用 $available 条（$catLines）。是否减少天数/条数或先导入素材？"
    }
}
