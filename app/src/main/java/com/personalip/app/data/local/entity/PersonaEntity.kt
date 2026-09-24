package com.personalip.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 人设。仅一行，固定 id = [SINGLE_ROW_ID]。
 * 人设同时会以 JSON 落盘到根目录 人设/persona.json（第 8 阶段）。
 */
@Entity(tableName = "persona")
data class PersonaEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val nickname: String = "",
    val identity: String = "",
    val background: String = "",
    val personality: String = "",
    val catchphrase: String = "",
    val targetAudience: String = "",
    val forbiddenWords: List<String> = emptyList()
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
