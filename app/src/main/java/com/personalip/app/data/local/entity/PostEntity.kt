package com.personalip.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalip.app.data.local.PostStatus

/**
 * AI 生成的朋友圈文案。
 * 含正文、3 个备选版本、话题标签、配图建议、建议发布时间、合规提醒等。
 */
@Entity(tableName = "posts", indices = [Index("materialId")])
data class PostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val content: String,
    val alternative1: String? = null,
    val alternative2: String? = null,
    val alternative3: String? = null,
    val tags: List<String> = emptyList(),
    val imageSuggestion: String? = null,
    /** 建议发布时间，如 "08:00"。 */
    val publishTime: String? = null,
    val status: PostStatus = PostStatus.DRAFT,
    val createdAt: Long = System.currentTimeMillis()
)
