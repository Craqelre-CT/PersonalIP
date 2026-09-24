package com.personalip.app.data.ai

/** 朋友圈语气。 */
enum class Tone(val label: String) {
    PROFESSIONAL("专业"),
    WARM("亲和"),
    AUTHENTIC("真实"),
    INSPIRATIONAL("励志"),
    KNOWLEDGE("干货");

    companion object { fun fromName(name: String?): Tone = entries.firstOrNull { it.name == name } ?: WARM }
}

/** 转化目标。 */
enum class Goal(val label: String) {
    LIKE("点赞"),
    COMMENT("评论"),
    DM("私信"),
    DEAL("成交");

    companion object { fun fromName(name: String?): Goal = entries.firstOrNull { it.name == name } ?: LIKE }
}
