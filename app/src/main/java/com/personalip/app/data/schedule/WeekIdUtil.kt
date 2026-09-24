package com.personalip.app.data.schedule

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * 周编号与星期工具（ISO 周，周一为一周第一天）。
 * 使用 java.time（API 26+，本项目 minSdk 29，无需核心库脱糖）。
 */
object WeekIdUtil {

    /** 返回某日期对应的 ISO 周标识，如 "2026-W38"。 */
    fun weekIdOf(date: LocalDate = LocalDate.now()): String {
        val wf = WeekFields.ISO
        val year = date.get(wf.weekBasedYear())
        val week = date.get(wf.weekOfWeekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    /** 当前日期的 ISO 周几：1=周一 … 7=周日。 */
    fun dayOfWeekValue(date: LocalDate = LocalDate.now()): Int = date.dayOfWeek.value

    /** 周几中文显示名。 */
    fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"; 2 -> "周二"; 3 -> "周三"; 4 -> "周四"
        5 -> "周五"; 6 -> "周六"; 7 -> "周日"; else -> "周$dayOfWeek"
    }

    fun dayLabelOf(date: LocalDate = LocalDate.now()): String = dayLabel(date.dayOfWeek.value)
}
