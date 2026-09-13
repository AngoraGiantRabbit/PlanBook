package com.example.planbook.data.ics

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * ICS（RFC 5545 子集）解析 + RRULE 展开，面向课程表导入（见 docs/adr/0005-ics-import-expansion.md）。
 * 纯 Kotlin / JVM，无 Android 依赖。
 *
 * 每个 VEVENT 展开为若干单天 [IcsTask]，最终按 日期 升序、同日内 startTime 升序排序
 * （全天事件 startTime 为 null，排同日最前）。
 *
 * ## 支持的 ICS 子集
 * - 折行（RFC 5545 3.1）：以空格或制表符开头的行拼接回上一行（去掉一个前导空白字符），
 *   解析前先展开。
 * - VEVENT：BEGIN:VEVENT ... END:VEVENT，多个 VEVENT 相互独立展开；
 *   VEVENT 内嵌套的子块（如 VALARM）整体忽略。
 * - 字段：SUMMARY → title；LOCATION、DESCRIPTION → description，两者都有时合成为
 *   "LOCATION · DESCRIPTION"（各自 trim，空片段跳过，两者皆无则为空串）。
 *   文本值按 RFC 5545 反转义：`\n`/`\N` → 换行，`\,` → `,`，`\;` → `;`，`\\` → `\`。
 * - 日期时间值（DTSTART/DTEND/EXDATE/UNTIL 通用）支持三种形式：
 *   `YYYYMMDDTHHMMSSZ`（UTC：Z 直接去掉，按本地时间处理，不做时区换算）、
 *   `YYYYMMDDTHHMMSS`（floating 本地时间）、`YYYYMMDD`（全天 → time 为 null）。
 *   属性参数（`TZID=...`、`VALUE=DATE` 等）一律忽略，取第一个 ':' 之后的值。
 * - EXDATE：按日期排除出现（值里的时间分量忽略）。同一行逗号分隔多个值，
 *   多个 EXDATE 行累积生效。
 *
 * ## RRULE 语义（仅 FREQ=WEEKLY；其他 FREQ 或整行无法解析时退化为 DTSTART 单次出现）
 * - BYDAY=MO,...,SU：缺省用 DTSTART 自己的星期几。若 DTSTART 本身不匹配 BYDAY，
 *   则从 DTSTART（含）起第一个匹配的星期几开始展开。
 * - UNTIL=`YYYYMMDD[THHMMSS[Z]]`：闭区间上界，只按日期比较（时间分量忽略），
 *   即 UNTIL 当天仍会出现。
 * - COUNT=n：从首次出现起计的总出现数上界，在生成阶段生效；EXDATE 随后才移除，
 *   因此最终条数可能少于 n（与 RFC 5545 "先生成 recurrence set、再排除 EXDATE" 一致）。
 * - BYWEEKNO=1,2,...：显式周次白名单，采用学期相对周次——DTSTART 所在自然周
 *   （ISO 周，周一起算）为第 1 周，出现日的周次在列表内才生成。
 * - BYWEEKNO=ODD / EVEN：单双周。文件本身推不出学期第 1 周的绝对奇偶，
 *   故 ODD 与 EVEN 统一解释为"以 DTSTART 所在周为第 1 周的奇数周次"，即每 2 周
 *   从 DTSTART 所在周展开一次；BYDAY 仍在这些周内生效，DTSTART 所在周必为出现周。
 * - 无 UNTIL/COUNT：展开上界为 DTSTART + 365 天（含），防止无限展开。
 * - 无 RRULE：仅 DTSTART 单次出现。
 *
 * ## 容错
 * - VEVENT 缺少 DTSTART 或 DTSTART 无法解析：跳过该事件。
 * - UNTIL/COUNT/BYDAY 值无法解析时视为未设置（分别回落到 365 天上界 / DTSTART 星期）。
 */
object IcsParser {

    /** 无 UNTIL/COUNT 时的展开上界：DTSTART + 365 天（含）。 */
    private const val MAX_SPAN_DAYS = 365L

    /** 生成循环的周数硬上界（100 年），防御异常输入导致的长循环。 */
    private const val MAX_WEEKS = 5200

    private val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private val BASIC_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val DAY_CODES: Map<String, DayOfWeek> = mapOf(
        "MO" to DayOfWeek.MONDAY,
        "TU" to DayOfWeek.TUESDAY,
        "WE" to DayOfWeek.WEDNESDAY,
        "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY,
        "SA" to DayOfWeek.SATURDAY,
        "SU" to DayOfWeek.SUNDAY
    )

    /**
     * 解析 ICS 文本，返回全部 VEVENT 展开后的单天任务，
     * 按 日期升序、同日内 startTime 升序（全天最前）排序。
     */
    fun parse(text: String): List<IcsTask> {
        val tasks = mutableListOf<IcsTask>()
        val eventProps = mutableListOf<Pair<String, String>>() // 属性名(大写) -> 原始值
        var inEvent = false
        var nestedDepth = 0 // VEVENT 内嵌套子块（VALARM 等）的深度

        for (line in unfold(text)) {
            val upper = line.uppercase()
            when {
                !inEvent && upper == "BEGIN:VEVENT" -> {
                    inEvent = true
                    nestedDepth = 0
                    eventProps.clear()
                }
                inEvent && nestedDepth > 0 && upper.startsWith("END:") -> nestedDepth--
                inEvent && nestedDepth > 0 -> Unit // 嵌套子块内容忽略
                inEvent && upper == "END:VEVENT" -> {
                    tasks += expandEvent(eventProps)
                    inEvent = false
                }
                inEvent && upper.startsWith("BEGIN:") -> nestedDepth++
                inEvent -> splitContentLine(line)?.let { eventProps += it }
            }
        }
        return tasks.sortedWith(compareBy({ it.date }, { it.startTime ?: "" }))
    }

    /** RFC 5545 折行展开：以空格/制表符开头的行接续上一行（去掉一个前导空白字符）。 */
    private fun unfold(text: String): List<String> {
        val out = mutableListOf<String>()
        for (piece in text.split("\r\n", "\n", "\r")) {
            when {
                (piece.startsWith(" ") || piece.startsWith("\t")) && out.isNotEmpty() ->
                    out[out.size - 1] = out.last() + piece.substring(1)
                piece.isNotEmpty() -> out.add(piece)
            }
        }
        return out
    }

    /** 把一行内容拆为 (属性名[大写], 值)；属性参数（第一个 ':' 前、属性名后的部分）丢弃。 */
    private fun splitContentLine(line: String): Pair<String, String>? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val name = line.substring(0, colon).substringBefore(';').trim().uppercase()
        if (name.isEmpty()) return null
        return name to line.substring(colon + 1)
    }

    /** 把单个 VEVENT 的原始属性列表展开为单天任务列表。 */
    private fun expandEvent(props: List<Pair<String, String>>): List<IcsTask> {
        val dtStart = props.firstOrNull { it.first == "DTSTART" }?.second?.let(::parseDateTimeValue)
            ?: return emptyList() // 无 DTSTART / 无法解析：跳过该事件
        val dtEnd = props.firstOrNull { it.first == "DTEND" }?.second?.let { parseDateTimeValue(it) }
        val title = props.firstOrNull { it.first == "SUMMARY" }?.second
            ?.let { unescape(it).trim() }.orEmpty()
        val location = props.firstOrNull { it.first == "LOCATION" }?.second
            ?.let { unescape(it).trim() }.orEmpty()
        val note = props.firstOrNull { it.first == "DESCRIPTION" }?.second
            ?.let { unescape(it).trim() }.orEmpty()
        val description = listOf(location, note).filter { it.isNotEmpty() }.joinToString(" · ")
        val rule = props.firstOrNull { it.first == "RRULE" }?.second?.let(::parseRule)
        val exdates = props.filter { it.first == "EXDATE" }
            .flatMap { (_, value) -> value.split(',') }
            .mapNotNull { parseDateTimeValue(it)?.date }
            .toSet()
        val startTime = dtStart.time?.format(TIME_FORMAT)
        val endTime = dtEnd?.time?.format(TIME_FORMAT)

        return expandDates(dtStart.date, rule, exdates).map {
            IcsTask(
                title = title,
                description = description,
                date = it,
                startTime = startTime,
                endTime = endTime
            )
        }
    }

    private class DateTimeVal(val date: LocalDate, val time: LocalTime?)

    /** 解析 `20260907T083000Z` / `20260907T083000` / `20260907`（全天，time=null）。 */
    private fun parseDateTimeValue(raw: String): DateTimeVal? {
        val body = raw.trim().let { if (it.endsWith("Z", true)) it.dropLast(1) else it }
        return runCatching {
            when (body.length) {
                8 -> DateTimeVal(LocalDate.parse(body, BASIC_DATE), null)
                15 -> DateTimeVal(
                    LocalDate.parse(body.substring(0, 8), BASIC_DATE),
                    LocalTime.parse(body.substring(9), BASIC_TIME)
                )
                else -> null
            }
        }.getOrNull()
    }

    /** UNTIL 值只取日期部分（时间分量忽略）；无法解析返回 null（视为未设置）。 */
    private fun parseUntil(value: String): LocalDate? {
        val body = value.trim().let { if (it.endsWith("Z", true)) it.dropLast(1) else it }
        if (body.length < 8) return null
        return runCatching { LocalDate.parse(body.substring(0, 8), BASIC_DATE) }.getOrNull()
    }

    /** FREQ=WEEKLY 的 RRULE 子集。 */
    private class WeeklyRule(
        val byDay: List<DayOfWeek>?, // null = 用 DTSTART 自己的星期几
        val until: LocalDate?,       // 闭区间上界（只比日期）
        val count: Int?,             // 生成阶段的总出现数上界
        val weekNumbers: Set<Int>?,  // 学期相对周次白名单（第 1 周 = DTSTART 所在周）
        val biweekly: Boolean        // ODD/EVEN：每 2 周，锚定 DTSTART 所在周
    )

    /** 仅支持 FREQ=WEEKLY；其他 FREQ 返回 null（退化为 DTSTART 单次出现）。 */
    private fun parseRule(raw: String): WeeklyRule? {
        var freq: String? = null
        var byDay: List<DayOfWeek>? = null
        var until: LocalDate? = null
        var count: Int? = null
        var weekNumbers: Set<Int>? = null
        var biweekly = false
        for (part in raw.split(';')) {
            val key = part.substringBefore('=').trim().uppercase()
            val value = part.substringAfter('=', "").trim()
            when (key) {
                "FREQ" -> freq = value.uppercase()
                "BYDAY" -> byDay = value.split(',')
                    .mapNotNull { DAY_CODES[it.trim().uppercase().takeLast(2)] }
                    .ifEmpty { null }
                "UNTIL" -> until = parseUntil(value)
                "COUNT" -> count = value.toIntOrNull()?.takeIf { it > 0 }
                "BYWEEKNO" -> {
                    val tokens = value.split(',')
                    if (tokens.any {
                            val t = it.trim().uppercase()
                            t == "ODD" || t == "EVEN"
                        }
                    ) {
                        biweekly = true
                    } else {
                        weekNumbers = tokens.mapNotNull { it.trim().toIntOrNull()?.takeIf { n -> n > 0 } }
                            .toSet()
                            .ifEmpty { null }
                    }
                }
            }
        }
        if (freq != null && freq != "WEEKLY") return null
        return WeeklyRule(byDay, until, count, weekNumbers, biweekly)
    }

    /**
     * 从 DTSTART 起按周生成出现日期，再移除 EXDATE。
     * 逐周（ISO 周一为一周开始）、周内按星期升序迭代，保证整体时间有序，
     * 因此命中 UNTIL/COUNT/365 天上界即可立即停止。
     */
    private fun expandDates(dtStart: LocalDate, rule: WeeklyRule?, exdates: Set<LocalDate>): List<LocalDate> {
        val dates = if (rule == null) {
            listOf(dtStart)
        } else {
            val weekdays = (rule.byDay ?: listOf(dtStart.dayOfWeek)).sortedBy { it.value }
            val firstWeekMonday = dtStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val hardEnd = dtStart.plusDays(MAX_SPAN_DAYS)
            val maxWeekNo = rule.weekNumbers?.max()
            val out = mutableListOf<LocalDate>()
            var week = 0
            loop@ while (week <= MAX_WEEKS) {
                val weekNo = week + 1
                if (rule.biweekly && week % 2 == 1) {
                    week++
                    continue@loop
                }
                if (rule.weekNumbers != null && weekNo !in rule.weekNumbers) {
                    if (maxWeekNo != null && weekNo > maxWeekNo) break@loop // 已超过列表最大周次
                    week++
                    continue@loop
                }
                val weekMonday = firstWeekMonday.plusWeeks(week.toLong())
                for (day in weekdays) {
                    val date = weekMonday.plusDays((day.value - 1).toLong())
                    if (date < dtStart) continue // 首周里早于 DTSTART 的日期不出现
                    if (rule.count != null && out.size >= rule.count) break@loop
                    if (rule.until != null && date > rule.until) break@loop
                    if (rule.until == null && rule.count == null && date > hardEnd) break@loop
                    out += date
                }
                week++
            }
            out
        }
        return dates.filter { it !in exdates }
    }

    /** RFC 5545 文本反转义：`\n`/`\N` → 换行，`\,` → `,`，`\;` → `;`，`\\` → `\`；其他 `\x` 原样保留。 */
    private fun unescape(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (text[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    ',' -> sb.append(',')
                    ';' -> sb.append(';')
                    '\\' -> sb.append('\\')
                    else -> sb.append(c).append(text[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }
}
