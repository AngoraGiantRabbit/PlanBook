package com.example.planbook.data.ics

import java.time.LocalDate

/**
 * ICS 导入展开后的单天任务（ADR-0005：每个 VEVENT 的 RRULE/RDATE 逐次展开为
 * startDate == endDate 的单天任务快照）。
 *
 * @param title       SUMMARY（已反转义、去首尾空白）。
 * @param description DESCRIPTION 处理后的备注：命中「教师：/全称：」模式时重组为
 *                    "教师：X；全称：Y"（仅含命中的段，周次等其余内容丢弃）；
 *                    未命中时保留反转义后的原文。LOCATION 不再拼入。
 * @param location    LOCATION（教室），独立字段（块内显示用）；无则为 null。
 * @param date        出现日期，yyyy-MM-dd 语义。
 * @param startTime   HH:mm，取 DTSTART 时刻（RDATE 实例统一沿用该时刻）；全天事件为 null。
 * @param endTime     HH:mm，取 DTEND 时刻；DTEND 缺失或为全天日期时为 null。
 */
data class IcsTask(
    val title: String,
    val description: String,
    val location: String?,
    val date: LocalDate,
    val startTime: String?,
    val endTime: String?
)
