package com.example.planbook.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.planbook.MainActivity
import com.example.planbook.data.PlanBookRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** #14：小部件一天的数据（该天有时段的任务，按开始时间排序） */
private data class WidgetDay(
    val weekday: String,
    val dateText: String,
    val isToday: Boolean,
    val tasks: List<WidgetTask>
)

private data class WidgetTask(
    val timeText: String,
    val title: String,
    val location: String?,
    val color: Color?
)

/** #15：今日待办条目（灵活待办 + 无时段临时任务 + 活跃长期待办，与 App 底部同口径） */
private data class WidgetTodo(
    val title: String,
    val done: Boolean
)

/** 小部件取 repository 的 Hilt 入口（无 ViewModel 环境） */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): PlanBookRepository
}

/**
 * 桌面小部件（PRD §5，只读版）：
 * - 当前一周（周一开始）的日程网格：7 列等分，每列当天有时段的任务按时段排序；
 *   任务块按所属子计划本颜色着色（与计划本页同源同色）；今天的列头高亮。
 * - 点按任意区域打开 App。
 * - 数据与计划本页同口径：聚合显示开关打开的子计划本 + 主计划本复盘待办。
 * - 刷新为手动机制：App 内点刷新按钮后由 [WidgetSync.updateAll] 同步。
 *   Glance 布局能力所限（无精确时间轴偏移），时段以文字呈现，为 App 内网格的近似。
 */
class PlanBookWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(
            context, WidgetEntryPoint::class.java
        ).repository()

        val data = runCatching { buildWeekData(repo) }.getOrNull()
        val fallbackWeekStart = LocalDate.now().let {
            it.minusDays((it.dayOfWeek.value - 1).toLong())
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color.White)
                    .clickable(actionStartActivity<MainActivity>())
                    .padding(6.dp)
            ) {
                // 标题行：主计划本名 + 周范围
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = data?.masterName ?: "计划本",
                        style = TextStyle(
                            color = ColorProvider(Color.Black),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "  ${data?.weekRangeText ?: formatWeek(fallbackWeekStart)}",
                        style = TextStyle(
                            color = ColorProvider(Color.DarkGray),
                            fontSize = 11.sp
                        )
                    )
                }
                // 7 列周网格
                Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    (data?.days ?: emptyList()).forEach { day ->
                        DayColumn(day)
                    }
                    if ((data?.days?.size ?: 0) == 0) {
                        Text(
                            text = "暂无数据，打开 App 刷新",
                            style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 12.sp)
                        )
                    }
                }
                // #15：底部今日待办区（只读；与 App 计划本页底部口径一致）
                if ((data?.todos?.size ?: 0) > 0) {
                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(Color(0xFFF5F1FA))
                            .cornerRadius(8.dp)
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "今日待办",
                            style = TextStyle(
                                color = ColorProvider(Color(0xFF4A3B8C)),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = GlanceModifier.padding(bottom = 1.dp)
                        )
                        // 最多展示 4 条（未完成优先），超出提示
                        val sorted = data!!.todos.sortedBy { it.done }
                        sorted.take(4).forEach { todo ->
                            Text(
                                text = if (todo.done) "✓ ${todo.title}" else "○ ${todo.title}",
                                style = TextStyle(
                                    color = ColorProvider(if (todo.done) Color.Gray else Color.Black),
                                    fontSize = 10.sp
                                )
                            )
                        }
                        if (sorted.size > 4) {
                            Text(
                                text = "…还有 ${sorted.size - 4} 条",
                                style = TextStyle(color = ColorProvider(Color.Gray), fontSize = 9.sp)
                            )
                        }
                    }
                }
                Text(
                    text = "在 App 内点刷新同步",
                    modifier = GlanceModifier.padding(top = 2.dp),
                    style = TextStyle(color = ColorProvider(Color(0xFF9E9E9E)), fontSize = 9.sp)
                )
            }
        }
    }

    private class WeekData(
        val masterName: String,
        val weekRangeText: String,
        val days: List<WidgetDay>,
        /** #15：今日待办（与 App 计划本页底部两区同口径合并） */
        val todos: List<WidgetTodo>
    )

    private suspend fun buildWeekData(repo: PlanBookRepository): WeekData? {
        val master = repo.getMasterNotebookOnce() ?: return null
        val subs = repo.getSubNotebooksOnce(master.id)
        val visibleSubs = subs.filter { it.isVisible }
        val today = LocalDate.now()
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        // 与计划本页同口径的聚合查询（含主计划本的自动复盘待办）
        val allTasks = repo.getExpandedTasksForWeek(
            master.id, visibleSubs.map { it.id }, weekStart
        )
        val tasks = allTasks.filter { it.startTime != null }

        // #15：今日待办 = 今天的灵活待办（FLEX + 无时段 ONE_OFF，PRD 4.3.1）+ 活跃长期待办，
        // 过滤口径与 PlanBookScreen 底部两区一致（today ∈ [start..end]）
        val todayText = today.toString()
        val todos = allTasks.filter { t ->
            val inRange = runCatching {
                today in LocalDate.parse(t.startDate)..LocalDate.parse(t.endDate)
            }.getOrDefault(false)
            inRange && (
                t.type == com.example.planbook.model.TaskType.FLEX ||
                    (t.type == com.example.planbook.model.TaskType.ONE_OFF && t.startTime == null) ||
                    t.type == com.example.planbook.model.TaskType.LONG_TERM
                )
        }.map { WidgetTodo(it.title, it.isCompleted) }

        val colorMap = visibleSubs.mapNotNull { sub ->
            sub.color?.let { hex ->
                runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
                    ?.let { sub.id to it }
            }
        }.toMap()

        val fmt = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)
        val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
        val days = (0..6).map { i ->
            val date = weekStart.plusDays(i.toLong())
            val dayTasks = tasks.filter { it.startDate == date.toString() }
                .sortedBy { it.startTime }
                .map { t ->
                    WidgetTask(
                        timeText = "${t.startTime}-${t.endTime ?: t.startTime}",
                        title = t.title,
                        location = t.location?.takeIf { it.isNotBlank() },
                        color = colorMap[t.notebookId]
                    )
                }
            WidgetDay(
                weekday = weekdayNames[i],
                dateText = date.format(fmt),
                isToday = date == today,
                tasks = dayTasks
            )
        }
        return WeekData(master.name, formatWeek(weekStart), days, todos)
    }

    private fun formatWeek(weekStart: LocalDate): String {
        val fmt = DateTimeFormatter.ofPattern("MM/dd", Locale.CHINA)
        return "${weekStart.format(fmt)} - ${weekStart.plusDays(6).format(fmt)}"
    }
}

/** 单日列：列头（周几+日期，今天高亮）+ 当天任务块列表 */
@androidx.compose.runtime.Composable
private fun androidx.glance.layout.RowScope.DayColumn(day: WidgetDay) {
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
    ) {
        // 列头
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
                .then(
                    if (day.isToday) GlanceModifier.background(Color(0xFFE8DEF8))
                    else GlanceModifier
                )
        ) {
            Text(
                text = "周${day.weekday}",
                style = TextStyle(
                    color = ColorProvider(if (day.isToday) Color(0xFF4A3B8C) else Color.Black),
                    fontSize = 10.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
                ),
                modifier = GlanceModifier.padding(top = 1.dp)
            )
            Text(
                text = day.dateText,
                style = TextStyle(color = ColorProvider(Color.DarkGray), fontSize = 9.sp)
            )
        }
        // 任务块
        day.tasks.forEach { t ->
            Column(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(vertical = 1.dp)
                    .background(ColorProvider(t.color ?: Color(0xFFE3E3E6)))
                    .cornerRadius(6.dp)
                    .padding(3.dp)
            ) {
                Text(
                    text = t.timeText,
                    style = TextStyle(color = ColorProvider(Color.DarkGray), fontSize = 8.sp)
                )
                Text(
                    text = t.title,
                    style = TextStyle(color = ColorProvider(Color.Black), fontSize = 10.sp)
                )
                // 块内只显示教室；其余备注（教师等）在 App 任务详情查看
                if (t.location != null) {
                    Text(
                        text = t.location,
                        style = TextStyle(color = ColorProvider(Color.DarkGray), fontSize = 9.sp)
                    )
                }
            }
        }
    }
}

class PlanBookWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlanBookWidget()
}

/**
 * 手动刷新入口：App 内点刷新按钮时同步所有已安装的小部件。
 * runCatching 包裹，确保小部件侧异常不会拖垮 App（PRD：手动刷新机制）。
 */
object WidgetSync {
    suspend fun updateAll(context: Context) {
        runCatching { PlanBookWidget().updateAll(context) }
    }
}
