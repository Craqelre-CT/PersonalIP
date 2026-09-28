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
    val complianceNote: String? = null,
    /** 模型实际看到的图片内容描述（强制输出；若没看图则为 null/空）。 */
    val imageDescription: String? = null,
    /** 置信度：模型声称自己是否真正看到了图片。0-1 之间，或 null 表示不确定。 */
    val imageSeen: Boolean? = null
)
