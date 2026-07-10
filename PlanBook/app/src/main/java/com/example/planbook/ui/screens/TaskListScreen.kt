package com.example.planbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.Task
import com.example.planbook.model.TaskType
import com.example.planbook.viewmodel.TaskListViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 待办列表 Tab：日历选天 → 该天分四类显示待办（带时间/DDL）+ 勾选完成。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var displayMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    val monthFmt = DateTimeFormatter.ofPattern("yyyy年 M月")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.selectedDate.format(DateTimeFormatter.ofPattern("M月d日 待办"))) },
                actions = {
                    IconButton(onClick = { displayMonth = displayMonth.minusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上月")
                    }
                    IconButton(onClick = { displayMonth = displayMonth.plusMonths(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下月")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(8.dp)
        ) {
            Text(displayMonth.format(monthFmt), style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))

            // 星期表头
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekLabels = listOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY
                )
                weekLabels.forEach { dow ->
                    Text(
                        text = dow.getDisplayName(TextStyle.NARROW, Locale.CHINA),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = if (dow == DayOfWeek.SUNDAY || dow == DayOfWeek.SATURDAY)
                            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            // 日历网格
            MonthGrid2(
                month = displayMonth,
                today = LocalDate.now(),
                selectedDate = state.selectedDate,
                onDayClick = { viewModel.selectDate(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()

            // 选中天的四类待办
            DayTaskGroups(
                date = state.selectedDate,
                tasks = state.tasks,
                onToggle = { viewModel.toggleComplete(it) }
            )
        }
    }
}

@Composable
private fun MonthGrid2(
    month: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate,
    onDayClick: (LocalDate) -> Unit
) {
    val ym = YearMonth.from(month)
    val firstDay = ym.atDay(1)
    val offset = (firstDay.dayOfWeek.value - 1)
    val daysInMonth = ym.lengthOfMonth()

    val cells = mutableListOf<LocalDate?>()
    repeat(offset) { cells.add(null) }
    for (d in 1..daysInMonth) cells.add(ym.atDay(d))
    while (cells.size % 7 != 0) cells.add(null)

    Column(modifier = Modifier.fillMaxWidth()) {
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val isSelected = date != null && date == selectedDate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    date != null && date == today -> MaterialTheme.colorScheme.primaryContainer
                                    else -> Color.Transparent
                                }
                            )
                            .clickable(enabled = date != null) { date?.let { onDayClick(it) } },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        if (date != null) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                modifier = Modifier.padding(top = 6.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    date == today -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                fontWeight = if (isSelected || date == today) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 某天待办，按四类分组显示（带时间/DDL） */
@Composable
private fun DayTaskGroups(
    date: LocalDate,
    tasks: List<Task>,
    onToggle: (Task) -> Unit
) {
    val oneOff = tasks.filter { it.type == TaskType.ONE_OFF }
    val daily = tasks.filter { it.type == TaskType.DAILY }
    val flex = tasks.filter { it.type == TaskType.FLEX }
    // 长期任务：DDL 未过的（含今天）
    val longTerm = tasks.filter { it.type == TaskType.LONG_TERM }

    val groups = listOf(
        "临时任务" to oneOff,
        "每日任务" to daily,
        "灵活任务" to flex,
        "长期任务" to longTerm
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        groups.forEach { (label, list) ->
            if (list.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                list.forEach { task ->
                    TaskRowWithTime(task = task, onToggle = { onToggle(task) })
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (tasks.isEmpty()) {
            Text("这天没有待办", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun TaskRowWithTime(task: Task, onToggle: () -> Unit) {
    val timeText = when (task.type) {
        TaskType.ONE_OFF -> task.startTime?.let { s ->
            val e = task.endTime ?: s
            "$s - $e"
        } ?: "全天"
        TaskType.DAILY -> task.startTime?.let { s ->
            val e = task.endTime ?: s
            "$s - $e"
        } ?: "每日"
        TaskType.FLEX -> "无时段"
        TaskType.LONG_TERM -> "DDL ${task.endDate}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isCompleted) Color.Gray else Color.Unspecified,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
