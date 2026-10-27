package com.personalip.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalip.app.data.local.PostStatus
import com.personalip.app.data.local.entity.WeeklyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyPlanDao {
    /** 从 [sinceDate]（含）起的所有排期（含历史已发/跳过），按日期、时间升序。 */
    @Query("SELECT * FROM weekly_plans WHERE date >= :sinceDate ORDER BY date ASC, time ASC")
    fun observeActive(sinceDate: String): Flow<List<WeeklyPlanEntity>>

    /** 某一天的排期（今日待发），按时间升序。 */
    @Query("SELECT * FROM weekly_plans WHERE date = :date ORDER BY time ASC")
    fun observeByDate(date: String): Flow<List<WeeklyPlanEntity>>

    /** 某批次的全部排期。 */
    @Query("SELECT * FROM weekly_plans WHERE weekId = :batchId ORDER BY date ASC, time ASC")
    suspend fun getBatch(batchId: String): List<WeeklyPlanEntity>

    @Query("SELECT * FROM weekly_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WeeklyPlanEntity?

    /**
     * 重新生成前，清掉「从 [sinceDate] 起、尚未发布（非 [keepStatuses]）」的旧排期，
     * 保留已发/跳过历史。
     */
    @Query("DELETE FROM weekly_plans WHERE date >= :sinceDate AND status NOT IN (:keepStatuses)")
    suspend fun deleteUnsentFrom(sinceDate: String, keepStatuses: List<PostStatus>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<WeeklyPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: WeeklyPlanEntity): Long

    @Update
    suspend fun update(plan: WeeklyPlanEntity)

    @Query("UPDATE weekly_plans SET status = :status, postId = :postId WHERE id = :id")
    suspend fun updateStatusAndPost(id: Long, status: PostStatus, postId: Long?)

    @Query("DELETE FROM weekly_plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 级联清理：删除引用某素材的所有排期。 */
    @Query("DELETE FROM weekly_plans WHERE materialId = :materialId")
    suspend fun deleteByMaterial(materialId: Long)
}
