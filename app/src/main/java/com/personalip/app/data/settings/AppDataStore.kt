package com.personalip.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用级 DataStore，用于持久化「系统级 / 启动期就需要读取」的设置：
 * - 根目录 SAF tree Uri（必须在数据库初始化前可读，且要早于任何文件操作）
 * - 加密 API Key 的相关密钥种子（第 6 阶段使用）
 *
 * 用户面向的应用设置（模型名、冷却期、每日条数、发布时间等）改由 Room 的
 * settings 表管理（第 3 阶段），避免同一份数据在两处重复。
 */
private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val store get() = context.appDataStore

    /** 用户选择的根目录 SAF tree Uri 字符串，未授权为 null。 */
    val rootTreeUriFlow: Flow<String?> = store.data.map { it[KEY_ROOT_TREE_URI] }

    suspend fun setRootTreeUri(uri: String?) {
        store.edit { prefs ->
            if (uri == null) prefs.remove(KEY_ROOT_TREE_URI)
            else prefs[KEY_ROOT_TREE_URI] = uri
        }
    }

    /** OpenAI 兼容接口 baseUrl（如 https://api.deepseek.com/v1）。 */
    val aiBaseUrlFlow: Flow<String?> = store.data.map { it[KEY_AI_BASE_URL] }
    suspend fun setAiBaseUrl(value: String?) = store.edit {
        if (value.isNullOrBlank()) it.remove(KEY_AI_BASE_URL) else it[KEY_AI_BASE_URL] = value
    }

    /** 文案生成模型名（如 deepseek-chat、gpt-4o-mini、qwen-plus）。 */
    val aiModelFlow: Flow<String?> = store.data.map { it[KEY_AI_MODEL] }
    suspend fun setAiModel(value: String?) = store.edit {
        if (value.isNullOrBlank()) it.remove(KEY_AI_MODEL) else it[KEY_AI_MODEL] = value
    }

    /** 图片识别模型名（如 qwen-vl-plus、glm-4v、gpt-4o-mini，多模态）。 */
    val aiVisionModelFlow: Flow<String?> = store.data.map { it[KEY_AI_VISION_MODEL] }
    suspend fun setAiVisionModel(value: String?) = store.edit {
        if (value.isNullOrBlank()) it.remove(KEY_AI_VISION_MODEL) else it[KEY_AI_VISION_MODEL] = value
    }

    private companion object {
        val KEY_ROOT_TREE_URI = stringPreferencesKey("root_tree_uri")
        val KEY_AI_BASE_URL = stringPreferencesKey("ai_base_url")
        val KEY_AI_MODEL = stringPreferencesKey("ai_model")
        val KEY_AI_VISION_MODEL = stringPreferencesKey("ai_vision_model")
    }
}
