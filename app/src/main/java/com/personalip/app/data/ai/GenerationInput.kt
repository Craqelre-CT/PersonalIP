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

/**
 * 文案风格快捷预设（两阶段流程第二阶段用）。
 * 用户点一个按钮选风格，再在意图框写要表达的意思。
 */
enum class CaptionStyle(val label: String, val hint: String) {
    SEED("种草", "突出推荐理由、使用感受，带一点安利感"),
    KNOWLEDGE("干货", "分享知识点/经验/方法，信息密度高"),
    EMOTION("情绪", "表达心情/感受/态度，有情绪共鸣"),
    CHECKIN("打卡", "记录当下时刻/场景/状态，轻量日常"),
    QUESTION("反问", "用提问引发互动，留悬念让读者想答");

    companion object { fun fromName(name: String?): CaptionStyle? = entries.firstOrNull { it.name == name } }
}
