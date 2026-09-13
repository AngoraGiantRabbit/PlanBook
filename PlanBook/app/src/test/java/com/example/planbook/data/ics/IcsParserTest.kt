package com.example.planbook.data.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [IcsParser] 单元测试：课程表场景的 RFC 5545 子集解析 + RRULE 展开。
 * 基准事实：2026-09-07 是周一，2026-09-09 是周三。
 */
class IcsParserTest {

    /** 用 CRLF 拼接 ICS 行（RFC 5545 标准换行）。 */
    private fun ics(vararg lines: String): String = lines.joinToString("\r\n")

    private fun dates(tasks: List<IcsTask>): List<LocalDate> = tasks.map { it.date }

    private fun d(year: Int, month: Int, day: Int) = LocalDate.of(year, month, day)

    // ---------- 要求覆盖 ----------

    @Test
    fun weeklySingleByDay_acrossSemester() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "BEGIN:VEVENT",
                "SUMMARY:高等数学",
                "DTSTART;TZID=Asia/Shanghai:20260907T083000",
                "DTEND;TZID=Asia/Shanghai:20260907T100000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261228T000000Z",
                "LOCATION:303教室",
                "DESCRIPTION:张老师",
                "END:VEVENT",
                "END:VCALENDAR"
            )
        )
        assertEquals(17, tasks.size) // 2026-09-07 .. 2026-12-28 共 17 个周一
        assertEquals(d(2026, 9, 7), tasks.first().date)
        assertEquals(d(2026, 12, 28), tasks.last().date)
        assertEquals("高等数学", tasks.first().title)
        assertEquals("08:30", tasks.first().startTime) // TZID 参数被忽略
        assertEquals("10:00", tasks.first().endTime)
        assertTrue(tasks.all { it.date.dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun multiDayByDay_expandsAllMatchedWeekdays() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:英语",
                "DTSTART:20260907T100000",
                "DTEND:20260907T113000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;UNTIL=20260930",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(
                d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14), d(2026, 9, 16),
                d(2026, 9, 21), d(2026, 9, 23), d(2026, 9, 28), d(2026, 9, 30)
            ),
            dates(tasks)
        )
    }

    @Test
    fun untilBound_isInclusive() {
        fun parseWith(until: String) = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:课",
                "DTSTART:20260907T083000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=$until",
                "END:VEVENT", "END:VCALENDAR"
            )
        )

        // UNTIL 当天（其时间分量 00:00:00Z 早于上课时间）仍包含当天的出现
        assertEquals(
            listOf(d(2026, 9, 7), d(2026, 9, 14)),
            dates(parseWith("20260914T000000Z"))
        )
        // 纯日期形式同样含当天
        assertEquals(
            listOf(d(2026, 9, 7), d(2026, 9, 14)),
            dates(parseWith("20260914"))
        )
        // 前一天则只剩首周
        assertEquals(listOf(d(2026, 9, 7)), dates(parseWith("20260913")))
    }

    @Test
    fun countBound_limitsTotalOccurrences() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:英语",
                "DTSTART:20260907T100000",
                "DTEND:20260907T113000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;COUNT=5",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(
                d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14),
                d(2026, 9, 16), d(2026, 9, 21)
            ),
            dates(tasks)
        )
    }

    @Test
    fun byweeknoOdd_generatesEveryOtherWeek() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:体育",
                "DTSTART:20260907T140000",
                "DTEND:20260907T154000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;BYWEEKNO=ODD;UNTIL=20261102T000000Z",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(
                d(2026, 9, 7), d(2026, 9, 21),
                d(2026, 10, 5), d(2026, 10, 19), d(2026, 11, 2)
            ),
            dates(tasks)
        )
        assertFalse(dates(tasks).contains(d(2026, 9, 14)))
        assertFalse(dates(tasks).contains(d(2026, 9, 28)))
    }

    @Test
    fun exdate_removesOccurrences_acrossLinesAndCommas() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:课",
                "DTSTART:20260907T083000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20260928T000000Z",
                "EXDATE:20260914,20260921",      // 一行多个、逗号分隔、纯日期形式
                "EXDATE:20260928T083000Z",       // 多行累积、日期时间形式
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(listOf(d(2026, 9, 7)), dates(tasks))
    }

    @Test
    fun twoVevents_sameCourse_expandIndependently() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR",
                "BEGIN:VEVENT",
                "SUMMARY:高等数学",
                "LOCATION:303教室", "DESCRIPTION:张老师",
                "DTSTART:20260907T080000", "DTEND:20260907T094000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261026",
                "END:VEVENT",
                "BEGIN:VEVENT",
                "SUMMARY:高等数学",
                "LOCATION:304教室", "DESCRIPTION:李老师",
                "DTSTART:20261102T100000", "DTEND:20261102T114000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20261228",
                "END:VEVENT",
                "END:VCALENDAR"
            )
        )
        assertEquals(17, tasks.size) // 前半学期 8 次 + 后半学期 9 次
        val firstHalf = tasks.filter { it.date <= d(2026, 10, 26) }
        val secondHalf = tasks.filter { it.date >= d(2026, 11, 2) }
        assertEquals(8, firstHalf.size)
        assertEquals(9, secondHalf.size)
        assertTrue(firstHalf.all { it.startTime == "08:00" && it.endTime == "09:40" })
        assertTrue(secondHalf.all { it.startTime == "10:00" && it.endTime == "11:40" })
        assertTrue(firstHalf.all { it.location == "303教室" && it.description == "张老师" })
        assertTrue(secondHalf.all { it.location == "304教室" && it.description == "李老师" })
    }

    @Test
    fun allDayEvent_hasNullTimes() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:军训",
                "DTSTART;VALUE=DATE:20260910",
                "DTEND;VALUE=DATE:20260910",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        val task = tasks.single()
        assertEquals(d(2026, 9, 10), task.date)
        assertNull(task.startTime)
        assertNull(task.endTime)
    }

    @Test
    fun descriptionSynthesis_locationAndNote() {
        fun event(location: String?, note: String?): IcsTask {
            val lines = mutableListOf(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT", "SUMMARY:课", "DTSTART:20260907T080000"
            )
            location?.let { lines += "LOCATION:$it" }
            note?.let { lines += "DESCRIPTION:$it" }
            lines += "END:VEVENT"
            lines += "END:VCALENDAR"
            return IcsParser.parse(lines.joinToString("\r\n")).single()
        }

        // LOCATION 独立成字段，不再拼进备注
        assertEquals("303教室", event(" 303教室 ", null).location) // trim
        assertNull(event(" ", "张老师").location)                    // 空白视为无
        // 无教师/全称模式的 DESCRIPTION 原样保留
        assertEquals("张老师", event(null, "张老师").description)
        // 教务系统典型形态：全称/教师/周次 → 重组为「教师；全称」，周次丢弃
        val registry = event(
            "305-106",
            "全称：数值计算方法；教师：王红霞；周次：第3\\,7-9\\,11-12周（第1周为8月31日）"
        )
        assertEquals("教师：王红霞；全称：数值计算方法", registry.description)
        assertEquals("305-106", registry.location)
        // 只有教师
        assertEquals("教师：王红霞", event(null, "教师：王红霞；周次：第3周").description)
    }

    @Test
    fun foldedLine_isUnfolded() {
        val text = listOf(
            "BEGIN:VCALENDAR", "BEGIN:VEVENT",
            "SUMMARY:高等数",
            " 学（一）",                 // 空格折行
            "DESCRIPTION:第1-16周",
            "\t周一第1-2节",             // 制表符折行
            "DTSTART:20260907T083000",
            "END:VEVENT", "END:VCALENDAR"
        ).joinToString("\r\n")
        val task = IcsParser.parse(text).single()
        assertEquals("高等数学（一）", task.title)
        assertEquals("第1-16周周一第1-2节", task.description)
    }

    // ---------- 补充语义锁定 ----------

    @Test
    fun escapedCharacters_areUnescaped() {
        val task = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:概率论\\,与统计\\;复习\\\\讲义\\nNote",
                "DTSTART:20260907T083000",
                "END:VEVENT", "END:VCALENDAR"
            )
        ).single()
        assertEquals("概率论,与统计;复习\\讲义\nNote", task.title)
    }

    @Test
    fun byweeknoEven_sameAsOdd_anchoredToDtStartWeek() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:体育",
                "DTSTART:20260907T140000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;BYWEEKNO=EVEN;UNTIL=20261102",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(d(2026, 9, 7), d(2026, 9, 21), d(2026, 10, 5), d(2026, 10, 19), d(2026, 11, 2)),
            dates(tasks)
        )
    }

    @Test
    fun byweeknoNumericList_keepsOnlyListedSemesterWeeks() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:课",
                "DTSTART:20260907T083000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO,WE;BYWEEKNO=1,2",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        // 学期第 1、2 周 = 09-07..09-13、09-14..09-20
        assertEquals(
            listOf(d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14), d(2026, 9, 16)),
            dates(tasks)
        )
    }

    @Test
    fun noRrule_singleOccurrence() {
        val task = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:班会",
                "DTSTART:20260908T152000",
                "DTEND:20260908T164000",
                "END:VEVENT", "END:VCALENDAR"
            )
        ).single()
        assertEquals(d(2026, 9, 8), task.date)
        assertEquals("15:20", task.startTime)
        assertEquals("16:40", task.endTime)
    }

    @Test
    fun noUntilNoCount_boundedAtDtStartPlus365Days() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:长课",
                "DTSTART:20260907T083000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        // 上界 2027-09-07（含）内最后一个周一是 2027-09-06，共 53 个周一
        assertEquals(53, tasks.size)
        assertEquals(d(2026, 9, 7), tasks.first().date)
        assertEquals(d(2027, 9, 6), tasks.last().date)
    }

    @Test
    fun bydayAbsent_usesDtStartWeekday() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:课",
                "DTSTART:20260909T101000", // 周三
                "RRULE:FREQ=WEEKLY;UNTIL=20261001T000000Z",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(d(2026, 9, 9), d(2026, 9, 16), d(2026, 9, 23), d(2026, 9, 30)),
            dates(tasks)
        )
    }

    // ---------- RDATE（教务课表常见形态：DTSTART + 一串显式日期） ----------

    @Test
    fun rdate_withoutRrule_expandsAllExplicitDates() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:数值",
                "DTSTART;TZID=Asia/Shanghai:20260915T080000",
                "DTEND;TZID=Asia/Shanghai:20260915T094000",
                "RDATE;TZID=Asia/Shanghai:20261013T080000,20261020T080000,20261027T080000,",
                " 20261110T080000,20261117T080000", // 折行 + 一行多值
                "RDATE;TZID=Asia/Shanghai:20261201T080000", // 多行累积
                "LOCATION:305-106",
                "DESCRIPTION:全称：数值计算方法；教师：王红霞；周次：第3周",
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(
                d(2026, 9, 15), d(2026, 10, 13), d(2026, 10, 20), d(2026, 10, 27),
                d(2026, 11, 10), d(2026, 11, 17), d(2026, 12, 1)
            ),
            dates(tasks)
        )
        // 实例时刻统一沿用 DTSTART；location/重组备注生效
        assertTrue(tasks.all { it.startTime == "08:00" && it.endTime == "09:40" })
        assertTrue(tasks.all { it.location == "305-106" })
        assertTrue(tasks.all { it.description == "教师：王红霞；全称：数值计算方法" })
    }

    @Test
    fun rdate_withRrule_mergedAndDeduped() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:课",
                "DTSTART:20260907T080000", // 周一
                "RRULE:FREQ=WEEKLY;BYDAY=MO;UNTIL=20260921", // 09-07、09-14、09-21
                "RDATE:20260909T080000",   // 额外补一个周三（非规则内）
                "RDATE:20260914T080000",   // 与规则重复，应去重
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(d(2026, 9, 7), d(2026, 9, 9), d(2026, 9, 14), d(2026, 9, 21)),
            dates(tasks)
        )
    }

    @Test
    fun rdate_notLimitedByCount_removedByExdate() {
        val tasks = IcsParser.parse(
            ics(
                "BEGIN:VCALENDAR", "BEGIN:VEVENT",
                "SUMMARY:课",
                "DTSTART:20260907T080000",
                "RRULE:FREQ=WEEKLY;BYDAY=MO;COUNT=2", // 09-07、09-14
                "RDATE:20261005T080000,20261102T080000", // 显式实例不受 COUNT 限制
                "EXDATE:20261102",                      // 但可被 EXDATE 排除
                "END:VEVENT", "END:VCALENDAR"
            )
        )
        assertEquals(
            listOf(d(2026, 9, 7), d(2026, 9, 14), d(2026, 10, 5)),
            dates(tasks)
        )
    }
}
