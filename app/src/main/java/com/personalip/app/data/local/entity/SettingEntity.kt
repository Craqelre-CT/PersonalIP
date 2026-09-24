package com.personalip.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 通用键值设置（模型名、冷却期、每日条数、发布时间等用户面向的应用设置）。
 * 系统级设置（根 Uri、加密 API Key）仍走 DataStore，不在此表。
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
