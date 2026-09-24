package com.personalip.app.data.schedule

import com.personalip.app.data.local.PostStatus
import com.personalip.app.data.local.dao.CategoryDao
import com.personalip.app.data.local.dao.MaterialDao
import com.personalip.app.data.local.dao.UsageRecordDao
import com.personalip.app.data.local.dao.WeeklyPlanDao
import com.personalip.app.data.local.entity.MaterialEntity
import com.personalip.app.data.local.entity.WeeklyPlanEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 一周闭环排期：从素材库自动选材，生成 7 天 × N 条的计划，
 * 保证「7 天内同一素材不重复」并尊重冷却期；素材不足时给出 shortfall。
 */
@Singleton
class ScheduleRepository @Inject constructor(
    private val materialDao: MaterialDao,
    private val usageRecordDao: UsageRecordDao,
    private val weeklyPlanDao: WeeklyPlanDao,
    private val categoryDao: CategoryDao
) {
    /** 观察某周排期。 */
    fun observeWeek(weekId: String): Flow<List<WeeklyPlanEntity>> =
        weeklyPlanDao.observeWeek(weekId)

    /**
     * 生成一周计划。
     *
     * @param weekId 形如 "2026-W38"
     * @param slotTimes 每日发布时间，如 ["08:00","12:00","18:00"]
     * @param categoryIds 参与选材的分类；空表示全部
     * @param cooldownDays 冷却期（7/14/30）；期间已发过的素材不再选用
     */
    suspend fun generateWeek(
        weekId: String,
        slotTimes: List<String>,
        categoryIds: List<Long>,
        cooldownDays: Int
    ): WeekPlanResult = withContext(Dispatchers.IO) {
        // 重新生成前清掉旧计划。
        weeklyPlanDao.deleteWeek(weekId)

        val now = System.currentTimeMillis()
        val cooldownSince = now - cooldownDays.toLong() * 24L * 60L * 60L * 1000L
        val categorySet = categoryIds.toSet()

        // 全部候选（按使用次数升序，最少用的优先）。
        val all = materialDao.getAllSortedByUsage()
            .filter { categorySet.isEmpty() || it.categoryId in categorySet }

        // 过滤冷却期内已用过的素材。
        val available = mutableListOf<MaterialEntity>()
        for (m in all) {
            val recentlyUsed = usageRecordDao.countSince(m.id, cooldownSince) > 0
            if (!recentlyUsed) available += m
        }

        // 7 天 × 每日条数 = 总槽位。按天顺序填充，每个素材只取一次 → 本周不重复。
        val usedThisPass = mutableSetOf<Long>()
        val plans = mutableListOf<WeeklyPlanEntity>()
        val iterator = available.iterator()
        outer@ for (day in 1..7) {
            for (time in slotTimes) {
                if (!iterator.hasNext()) break@outer
                val m = iterator.next()
                if (m.id in usedThisPass) continue
                usedThisPass += m.id
                plans += WeeklyPlanEntity(
                    weekId = weekId,
                    dayOfWeek = day,
                    time = time,
                    materialId = m.id,
                    status = PostStatus.DRAFT
                )
            }
        }
        if (plans.isNotEmpty()) weeklyPlanDao.insertAll(plans)

        // 素材不足时给出每分类缺口。
        val totalSlots = 7 * slotTimes.size
        val shortfall: Shortfall? = if (plans.size < totalSlots) {
            val byCategory = (if (categorySet.isEmpty()) categoryDao.getAll() else categoryDao.getAll()
                .filter { it.id in categorySet })
                .map { cat -> cat.displayName to available.count { it.categoryId == cat.id } }
                .filter { it.second > 0 || it.first.isNotEmpty() }
            Shortfall(
                totalSlots = totalSlots,
                available = plans.size,
                byCategory = byCategory
            )
        } else null

        WeekPlanResult(plans = plans, shortfall = shortfall)
    }

    /** 用另一个未占用素材替换某条排期的素材。 */
    suspend fun replaceMaterial(planId: Long, weekId: String): Boolean =
        withContext(Dispatchers.IO) {
            val weekPlans = weeklyPlanDao.getWeek(weekId)
            val current = weekPlans.firstOrNull { it.id == planId } ?: return@withContext false
            val usedIds = weekPlans.map { it.materialId }.toMutableSet()
            val candidates = materialDao.getAllSortedByUsage()
                .filter { it.id !in usedIds }
            val replacement = candidates.firstOrNull() ?: return@withContext false
            usedIds += replacement.id
            weeklyPlanDao.update(current.copy(materialId = replacement.id))
            true
        }

    /** 上移 / 下移某条排期（仅改变顺序，通过交换 dayOfWeek+time 实现）。 */
    suspend fun movePlan(planId: Long, weekId: String, up: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val plans = weeklyPlanDao.getWeek(weekId).toMutableList()
            val index = plans.indexOfFirst { it.id == planId }
            if (index == -1) return@withContext false
            val swapWith = if (up) index - 1 else index + 1
            if (swapWith !in plans.indices) return@withContext false
            val a = plans[index]
            val b = plans[swapWith]
            // 交换两者的 dayOfWeek 与 time，保留各自 id 与 materialId。
            weeklyPlanDao.update(a.copy(dayOfWeek = b.dayOfWeek, time = b.time))
            weeklyPlanDao.update(b.copy(dayOfWeek = a.dayOfWeek, time = a.time))
            true
        }

    suspend fun setStatus(planId: Long, status: PostStatus, postId: Long? = null) =
        withContext(Dispatchers.IO) {
            weeklyPlanDao.updateStatusAndPost(planId, status, postId)
        }
}

/** 一周计划生成结果。 */
data class WeekPlanResult(
    val plans: List<WeeklyPlanEntity>,
    val shortfall: Shortfall?
) {
    val hasShortfall: Boolean get() = shortfall != null
}

/** 素材不足缺口信息，用于提示「是否本周少发或先导入」。 */
data class Shortfall(
    val totalSlots: Int,
    val available: Int,
    /** 分类显示名 -> 可用数量。 */
    val byCategory: List<Pair<String, Int>>
) {
    fun toMessage(): String {
        val catLines = byCategory.joinToString("，") { "${it.first} ${it.second} 张" }
        return "本周共需 $totalSlots 条，可用 $available 条（$catLines）。是否本周少发或先导入素材？"
    }
}
