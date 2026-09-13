package com.example.planbook.data.ics

import java.time.LocalDate

/**
 * ICS 导入展开后的单天任务（ADR-0005：每个 VEVENT 的 RRULE 逐次展开为
 * startDate == endDate 的单天任务快照）。
 *
 * @param title       SUMMARY（已反转义、去首尾空白）。
 * @param description LOCATION 与 DESCRIPTION 合成："LOCATION · DESCRIPTION"
 *                    （各自 trim，空片段跳过；两者皆无则为空串）。
 * @param date        出现日期，yyyy-MM-dd 语义。
 * @param startTime   HH:mm，取 DTSTART 时刻；全天事件为 null。
 * @param endTime     HH:mm，取 DTEND 时刻；DTEND 缺失或为全天日期时为 null。
 */
data class IcsTask(
    val title: String,
    val description: String,
    val date: LocalDate,
    val startTime: String?,
    val endTime: String?
)
