package com.personalip.app.data.ai

import com.squareup.moshi.JsonClass

/**
 * AI 返回的结构化草稿（模型被要求输出 JSON）。
 * 解析失败时回退为仅 content 有值。
 */
@JsonClass(generateAdapter = true)
data class PostDraft(
    val content: String = "",
    val alternative1: String? = null,
    val alternative2: String? = null,
    val alternative3: String? = null,
    val tags: List<String>? = null,
    val imageSuggestion: String? = null,
    val publishTime: String? = null,
    val complianceNote: String? = null
)
