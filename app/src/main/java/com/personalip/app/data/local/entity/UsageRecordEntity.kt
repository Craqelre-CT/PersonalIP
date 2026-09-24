package com.personalip.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 素材使用记录，用于冷却期判定与「7 天内不重复」。
 * [weekId] 形如 "2026-W38"。
 */
@Entity(tableName = "usage_records", indices = [Index("materialId"), Index("weekId")])
data class UsageRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val materialId: Long,
    val usedAt: Long = System.currentTimeMillis(),
    val weekId: String,
    /** 关联的朋友圈 post，可为空（仅排期未生成文案时）。 */
    val postId: Long? = null
)
