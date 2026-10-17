package com.personalip.app.data.schedule

import java.time.LocalDate

/**
 * 日期与星期工具（ISO 标准，周一为一周第一天）。
 * 使用 java.time（API 26+，本项目 minSdk 29，无需核心库脱糖）。
 */
object WeekIdUtil {

    /** 日期的稳定字符串键（ISO，如 "2026-09-30"），可直接用于字典序比较与存储。 */
    fun dateKey(date: LocalDate = LocalDate.now()): String = date.toString()

    /** 解析日期键；非法返回 null。 */
    fun parseDate(key: String): LocalDate? = runCatching { LocalDate.parse(key) }.getOrNull()

    /** 周几中文显示名。 */
    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "周$dayOfWeek"
    }

    /** 某日期的周几中文名。 */
    fun dayLabelOf(date: LocalDate): String = dayLabel(date.dayOfWeek.value)

    /** 日期展示标签，如 "09-30 周三"。 */
    fun dateLabel(date: LocalDate): String =
        "%02d-%02d %s".format(date.monthValue, date.dayOfMonth, dayLabelOf(date))

    /** 日期键的展示标签；无法解析时原样返回。 */
    fun dateLabelOf(key: String): String = parseDate(key)?.let { dateLabel(it) } ?: key
}
