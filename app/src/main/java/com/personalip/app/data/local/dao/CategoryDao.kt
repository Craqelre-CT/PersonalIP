package com.personalip.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalip.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY createdAt ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE folderName = :folderName LIMIT 1")
    suspend fun getByFolderName(folderName: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity): Long

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 仅更新文件夹名（重命名）。folderName 同步保持一致。 */
    @Query("UPDATE categories SET displayName = :displayName, folderName = :folderName WHERE id = :id")
    suspend fun rename(id: Long, displayName: String, folderName: String)

    /** 仅更新文件夹设置 JSON。 */
    @Query("UPDATE categories SET settingsJson = :settingsJson WHERE id = :id")
    suspend fun updateSettings(id: Long, settingsJson: String)
}
