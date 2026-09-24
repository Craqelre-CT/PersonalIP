package com.personalip.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalip.app.data.local.entity.UsageRecordEntity

@Dao
interface UsageRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: UsageRecordEntity): Long

    /** 该素材在最近 [days] 天内的使用次数。 */
    @Query(
        "SELECT COUNT(*) FROM usage_records WHERE materialId = :materialId " +
            "AND usedAt >= :since"
    )
    suspend fun countSince(materialId: Long, since: Long): Int

    /** 该素材在某 weekId 周是否被用过。 */
    @Query(
        "SELECT COUNT(*) FROM usage_records WHERE materialId = :materialId AND weekId = :weekId"
    )
    suspend fun usedInWeek(materialId: Long, weekId: String): Int

    @Query("SELECT materialId FROM usage_records WHERE weekId = :weekId")
    suspend fun materialIdsUsedInWeek(weekId: String): List<Long>

    @Query("DELETE FROM usage_records WHERE materialId = :materialId")
    suspend fun deleteForMaterial(materialId: Long)

    /** 级联清理：删除某素材的所有使用记录。 */
    @Query("DELETE FROM usage_records WHERE materialId = :materialId")
    suspend fun deleteByMaterial(materialId: Long)
}
