package com.personalip.app.data.local

/**
 * 朋友圈 / 排期项状态。
 * 对应需求中的「草稿、待发、已发、跳过」。
 */
enum class PostStatus {
    DRAFT,    // 草稿
    PENDING,  // 待发
    SENT,     // 已发
    SKIPPED;  // 跳过

    val label: String
        get() = when (this) {
            DRAFT -> "草稿"
            PENDING -> "待发"
            SENT -> "已发"
            SKIPPED -> "跳过"
        }
}
