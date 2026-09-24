package com.personalip.app.data.ai.remote

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * OpenAI 兼容 chat completions 接口。
 * 使用 @Url 让 baseUrl 可由用户运行时配置（DeepSeek/通义/Kimi/智谱/OpenAI 等）。
 */
interface OpenAiApi {
    @POST
    suspend fun chatCompletion(
        @Url url: String,
        @Body request: ChatRequest
    ): ChatResponse
}
