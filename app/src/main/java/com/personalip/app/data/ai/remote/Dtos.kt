package com.personalip.app.data.ai.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * OpenAI 兼容接口 DTO（DeepSeek / 通义 / Kimi / 智谱 / OpenAI 等）。
 * 消息 content 使用数组形式以兼容多模态（图片 + 文本）。
 */
@JsonClass(generateAdapter = true)
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.8,
    @Json(name = "max_tokens") val maxTokens: Int? = null
    // 部分提供方支持 response_format，但兼容性不一，故仅靠 prompt 约束 JSON。
)

@JsonClass(generateAdapter = true)
data class ChatMessage(
    val role: String,
    val content: List<ContentPart>
)

@JsonClass(generateAdapter = true)
data class ContentPart(
    val type: String,               // "text" 或 "image_url"
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: ImageUrl? = null
)

@JsonClass(generateAdapter = true)
data class ImageUrl(val url: String)

@JsonClass(generateAdapter = true)
data class ChatResponse(
    val choices: List<Choice> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Choice(
    val message: ResponseMessage? = null,
    @Json(name = "finish_reason") val finishReason: String? = null
)

@JsonClass(generateAdapter = true)
data class ResponseMessage(
    val role: String? = null,
    val content: String? = null
)
