package com.personalip.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 素材分类（文件夹）。区分 displayName（中文显示名）与 folderName（实际文件夹名）。
 * 默认分类二者相同；用户新建分类时也保持同名。
 *
 * settingsJson：文件夹级设置（默认语气/目标/冷却期/自动OCR/自动标签），JSON 字符串。
 *   对应 [com.personalip.app.domain.model.CategorySettings]。
 */
@Entity(tableName = "categories", indices = [Index(value = ["folderName"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val folderName: String,
    val settingsJson: String = DEFAULT_SETTINGS_JSON,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /** 默认文件夹设置：自动OCR+自动标签开、语气亲和、目标点赞、冷却7天。 */
        const val DEFAULT_SETTINGS_JSON =
            """{"autoOcr":true,"autoTags":true,"postTone":"亲和","postGoal":"点赞","cooldownDays":7}"""
    }
}
