package com.personalip.app.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * AI 接口配置聚合：baseUrl / model / visionModel（DataStore，非敏感）+ apiKey（加密存储）。
 * 供 [com.personalip.app.data.ai.AiRepository] 与网络拦截器使用。
 */
@Singleton
class AiConfigRepository @Inject constructor(
    private val appDataStore: AppDataStore,
    private val securePreferences: SecurePreferences
) {
    val baseUrlFlow: Flow<String?> = appDataStore.aiBaseUrlFlow
    val modelFlow: Flow<String?> = appDataStore.aiModelFlow
    val visionModelFlow: Flow<String?> = appDataStore.aiVisionModelFlow

    suspend fun setBaseUrl(value: String?) = appDataStore.setAiBaseUrl(value)
    suspend fun setModel(value: String?) = appDataStore.setAiModel(value)
    suspend fun setVisionModel(value: String?) = appDataStore.setAiVisionModel(value)
    fun setApiKey(value: String?) = securePreferences.setApiKey(value)

    /** 当前 API Key（同步读取，用于请求拦截器）。 */
    fun apiKey(): String? = securePreferences.getApiKey()

    /** 配置是否完整可调用（至少 baseUrl + 文案模型 + apiKey）。 */
    fun isConfigured(): Boolean {
        val key = securePreferences.getApiKey()
        return !key.isNullOrBlank()
    }
}
