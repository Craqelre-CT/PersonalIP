package com.personalip.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalip.app.data.local.PostStatus

/**
 * 一周排期项。
 * [dayOfWeek]：ISO 1=周一 … 7=周日。
 * [time]：发布时间字符串，如 "08:00"。
 * [weekId]：形如 "2026-W38"。
 */
@Entity(tableName = "weekly_plans", indices = [Index(value = ["weekId", "dayOfWeek"])])
data class WeeklyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekId: String,
    val dayOfWeek: Int,
    val time: String,
    val materialId: Long,
    val postId: Long? = null,
    val status: PostStatus = PostStatus.DRAFT
)
