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
    @Query("SELECT * FROM weekly_plans WHERE weekId = :weekId ORDER BY dayOfWeek ASC, time ASC")
    fun observeWeek(weekId: String): Flow<List<WeeklyPlanEntity>>

    @Query("SELECT * FROM weekly_plans WHERE weekId = :weekId ORDER BY dayOfWeek ASC, time ASC")
    suspend fun getWeek(weekId: String): List<WeeklyPlanEntity>

    @Query("SELECT * FROM weekly_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): WeeklyPlanEntity?

    /** 取今日（dayOfWeek）的排期。 */
    @Query("SELECT * FROM weekly_plans WHERE weekId = :weekId AND dayOfWeek = :dayOfWeek ORDER BY time ASC")
    fun observeToday(weekId: String, dayOfWeek: Int): Flow<List<WeeklyPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plans: List<WeeklyPlanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: WeeklyPlanEntity): Long

    @Update
    suspend fun update(plan: WeeklyPlanEntity)

    @Query("UPDATE weekly_plans SET status = :status, postId = :postId WHERE id = :id")
    suspend fun updateStatusAndPost(id: Long, status: PostStatus, postId: Long?)

    @Query("DELETE FROM weekly_plans WHERE weekId = :weekId")
    suspend fun deleteWeek(weekId: String)

    @Query("DELETE FROM weekly_plans WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 级联清理：删除引用某素材的所有排期。 */
    @Query("DELETE FROM weekly_plans WHERE materialId = :materialId")
    suspend fun deleteByMaterial(materialId: Long)
}
