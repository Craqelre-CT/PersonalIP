package com.personalip.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.personalip.app.data.local.entity.PostEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PostEntity?

    @Query("SELECT * FROM posts WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<PostEntity?>

    @Query("SELECT * FROM posts WHERE materialId = :materialId ORDER BY createdAt DESC")
    suspend fun getByMaterial(materialId: Long): List<PostEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: PostEntity): Long

    @Update
    suspend fun update(post: PostEntity)

    @Query("UPDATE posts SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: com.personalip.app.data.local.PostStatus)

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
