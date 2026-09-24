package com.personalip.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalip.app.data.local.entity.MaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaterialDao {
    @Query("SELECT * FROM materials ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials ORDER BY useCount ASC, lastUsedAt ASC")
    suspend fun getAllSortedByUsage(): List<MaterialEntity>

    @Query("SELECT * FROM materials WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    fun observeByCategory(categoryId: Long): Flow<List<MaterialEntity>>

    /**
     * 分类筛选 + 自由文本搜索（匹配 filePath / ocrText / tags）。
     * 传入 categoryId = -1 表示不按分类过滤；query 空串表示不按文本过滤。
     */
    @Query(
        "SELECT * FROM materials " +
            "WHERE (:categoryId = -1 OR categoryId = :categoryId) " +
            "AND (:query = '' OR filePath LIKE '%' || :query || '%' " +
            "OR ocrText LIKE '%' || :query || '%' " +
            "OR tags LIKE '%' || :query || '%') " +
            "ORDER BY createdAt DESC"
    )
    fun observeFiltered(categoryId: Long, query: String): Flow<List<MaterialEntity>>

    @Query("SELECT * FROM materials WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MaterialEntity?

    @Query("SELECT * FROM materials WHERE filePath = :path LIMIT 1")
    suspend fun getByPath(path: String): MaterialEntity?

    /**
     * 取在给定分类下、且在 [recentlyUsedMaterialIds] 之外、按使用次数升序的候选素材，
     * 用于排期选材（用得少的优先，且冷却期内不重复）。
     */
    @Query(
        "SELECT * FROM materials WHERE categoryId = :categoryId " +
            "AND id NOT IN (:excludeIds) " +
            "ORDER BY useCount ASC, lastUsedAt IS NOT NULL, lastUsedAt ASC LIMIT :limit"
    )
    suspend fun pickCandidates(
        categoryId: Long,
        excludeIds: List<Long>,
        limit: Int
    ): List<MaterialEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: MaterialEntity): Long

    @Update
    suspend fun update(material: MaterialEntity)

    @Query("UPDATE materials SET useCount = useCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun incrementUsage(id: Long, now: Long)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM materials WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("SELECT * FROM materials WHERE categoryId = :categoryId ORDER BY createdAt DESC")
    suspend fun getByCategory(categoryId: Long): List<MaterialEntity>
}
