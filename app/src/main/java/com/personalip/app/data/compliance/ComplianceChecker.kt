package com.personalip.app.data.compliance

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 基础合规检测：
 * - 绝对化承诺黑名单（根治、包瘦、无副作用、七天瘦十斤、永不反弹等）；
 * - 用户在人设中配置的禁用词。
 *
 * 第 9 阶段会扩展为更完整的检测与优化建议。
 */
@Singleton
class ComplianceChecker @Inject constructor() {

    /** 健康减重/抗衰/皮肤调理内容必须避免的绝对化承诺词。 */
    private val absoluteBanList = listOf(
        "根治", "包瘦", "无副作用", "七天瘦十斤", "7天瘦10斤",
        "永不反弹", "百分百有效", "100%有效", "彻底治愈", " guaranteed", "绝对瘦",
        "包治", "痊愈", "零风险", "一定瘦", "保证瘦", "快速暴瘦",
        "立竿见影", "药到病除", "根除", "断根", "永不复发", "终身有效",
        "无任何风险", "绝对安全", "无激素", "纯天然无添加"
    )

    /** 通用朋友圈合规风险词（提示而非硬阻断）。 */
    private val warningList = listOf(
        "代购", "处方", "医疗广告", "最有效", "第一", "国家级",
        "最佳", "最强", "顶级", "极品", "万能", "神药", "奇迹"
    )

    /**
     * 检测文案中的合规风险。
     * @param text 朋友圈正文
     * @param forbiddenWords 用户禁用词
     * @return 风险项列表（命中词 + 等级）；为空表示未发现风险。
     */
    fun check(text: String, forbiddenWords: List<String> = emptyList()): List<ComplianceHit> {
        val hits = mutableListOf<ComplianceHit>()
        for (word in absoluteBanList) {
            if (word in text) hits += ComplianceHit(word, Level.HARD_BLOCK)
        }
        for (word in forbiddenWords) {
            if (word.isNotBlank() && word in text) hits += ComplianceHit(word, Level.USER_FORBIDDEN)
        }
        for (word in warningList) {
            if (word in text) hits += ComplianceHit(word, Level.WARNING)
        }
        return hits
    }

    /** 组装给 AI 的系统提示：合规红线 + 用户禁用词。 */
    fun systemRules(forbiddenWords: List<String>): String = buildString {
        append("合规要求：")
        append("1) 健康/减重/抗衰/皮肤类内容必须客观，不得出现绝对化承诺，")
        append("禁止使用：${absoluteBanList.joinToString("、")} 等词汇；")
        append("2) 不得承诺具体效果或治愈；")
        append("3) 若涉及健康建议，需提示「因人而异，严重情况请咨询专业人士」。")
        if (forbiddenWords.isNotEmpty()) {
            append("4) 严禁出现以下禁用词：${forbiddenWords.joinToString("、")}。")
        }
    }.trimIndent()

    /**
     * 生成面向用户的合规提醒文案（用于 HomeScreen / 输出 Markdown）。
     * 健康减重/抗衰/皮肤调理类内容必须附带此提醒。
     */
    fun healthReminder(): String =
        "温馨提示：本内容仅供参考，效果因人而异。涉及健康问题请咨询专业人士，切勿自行诊断或停药。"
}

enum class Level { HARD_BLOCK, USER_FORBIDDEN, WARNING }

data class ComplianceHit(val word: String, val level: Level) {
    val label: String get() = when (level) {
        Level.HARD_BLOCK -> "硬阻断"
        Level.USER_FORBIDDEN -> "用户禁用词"
        Level.WARNING -> "提示"
    }
}
