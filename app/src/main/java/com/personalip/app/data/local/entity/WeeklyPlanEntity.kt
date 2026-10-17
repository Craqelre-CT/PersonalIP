package com.personalip.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalip.app.data.local.PostStatus

/**
 * 一条排期项。按「具体日期」定位，支持「从某天起连续 N 天」，可跨周。
 *
 * [batchId]：本次生成计划的批次标识（= 计划起始日期，如 "2026-09-30"）。
 *   列名沿用旧的 weekId，以兼容旧数据库、免去重建表。（新一批计划的 batchId 即其起始日期。）
 * [date]：该条排期所属的具体日期（ISO，如 "2026-09-30"）。
 * [dayOfWeek]：ISO 1=周一 … 7=周日，由 date 得出，仅用于显示。
 * [time]：发布时间字符串，如 "08:00"。
 */
@Entity(tableName = "weekly_plans", indices = [Index(value = ["weekId", "dayOfWeek"])])
data class WeeklyPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "weekId") val batchId: String,
    @ColumnInfo(defaultValue = "") val date: String,
    val dayOfWeek: Int,
    val time: String,
    val materialId: Long,
    val postId: Long? = null,
    val status: PostStatus = PostStatus.DRAFT
)
