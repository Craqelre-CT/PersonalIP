package com.personalip.app.data.local

import com.personalip.app.data.local.entity.CategoryEntity
import com.personalip.app.domain.model.CategorySettings
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CategorySettings ↔ JSON 字符串互转，用于 CategoryEntity.settingsJson 序列化。
 */
@Singleton
class CategorySettingsMapper @Inject constructor(
    private val moshi: Moshi
) {
    private val adapter = moshi.adapter(CategorySettings::class.java)

    /** 解析 JSON；解析失败返回默认值。 */
    fun decode(json: String?): CategorySettings {
        if (json.isNullOrBlank()) return CategorySettings.DEFAULT
        return runCatching { adapter.fromJson(json) }.getOrNull() ?: CategorySettings.DEFAULT
    }

    /** 序列化为 JSON；失败返回 CategoryEntity.DEFAULT_SETTINGS_JSON。 */
    fun encode(settings: CategorySettings): String {
        return runCatching { adapter.toJson(settings) }.getOrDefault(CategoryEntity.DEFAULT_SETTINGS_JSON)
    }
}
