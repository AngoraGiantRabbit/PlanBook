package com.example.planbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.Notebook
import com.example.planbook.model.Task
import com.example.planbook.viewmodel.PlanBookViewModel
import com.example.planbook.widget.WidgetSync
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanBookScreen(
    onOpenReview: (String) -> Unit,
    viewModel: PlanBookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // #9：标题固定为主计划本名（切换子计划本改走顶部 chips）
                        Text(
                            text = uiState.currentNotebook?.name ?: "计划本",
                            style = MaterialTheme.typography.titleLarge
                        )
                        val weekEnd = uiState.weekStart.plusDays(6)
                        val fmt = DateTimeFormatter.ofPattern("MM/dd")
                        Text(
                            "${uiState.weekStart.format(fmt)} - ${weekEnd.format(fmt)}",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                },
                actions = {
                    // 翻周：PRD 4.2.1
                    IconButton(onClick = { viewModel.changeWeek(-1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                    }
                    TextButton(onClick = { viewModel.goToThisWeek() }) {
                        Text("本周")
                    }
                    IconButton(onClick = { viewModel.changeWeek(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
                    }
                    IconButton(onClick = {
                        viewModel.refresh()
                        // 同步刷新桌面小部件（Issue #13：手动刷新机制）
                        scope.launch { WidgetSync.updateAll(context) }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddTaskDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "添加任务")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.currentNotebook == null) {
                Box(modifier = Modifier.weight(1f)) {
                    EmptyNotebookState(onCreate = { viewModel.createNotebook("我的计划本") })
                }
            } else {
                // #9：首页子计划本 chips（显示中的子本各一枚，点按切换活动子本 = 写操作目标）
                SubNotebookChips(
                    subNotebooks = uiState.notebooks.filter { it.isVisible },
                    activeSubId = uiState.activeSubNotebook?.id,
                    onSelect = { viewModel.switchActiveSub(it) }
                )
                Box(modifier = Modifier.weight(1f)) {
                    WeekView(
                        tasks = uiState.tasks,
                        subNotebooks = uiState.notebooks,
                        weekStart = uiState.weekStart,
                        selectedDate = uiState.selectedDate,
                        onSelectDate = { viewModel.selectDate(it) },
                        onToggleComplete = { viewModel.toggleTaskComplete(it) },
                        onTaskClick = { task ->
                            // 自动复盘待办点击进入复盘编辑页（PRD 4.4.1）
                            if (task.isAutoReview) {
                                onOpenReview(task.startDate)
                            } else {
                                viewModel.openTaskEditor(task)
                            }
                        },
                        onCellClick = { date, hour ->
                            viewModel.showAddTaskDialogWithPrefill(date, hour)
                        },
                        onDelete = { viewModel.deleteTask(it) }
                    )
                }
            }
        }
    }

    if (uiState.showAddTaskDialog) {
        AddTaskDialog(
            // #7：新建任务落到活动子计划本
            notebookId = uiState.activeSubNotebook?.id
                ?: uiState.notebooks.firstOrNull()?.id ?: 0,
            prefill = uiState.prefillTask,
            onDismiss = { viewModel.hideAddTaskDialog() },
            onConfirm = { viewModel.addTask(it) }
        )
    }

    uiState.editingTask?.let { task ->
        TaskEditSheet(
            notebookId = task.notebookId,
            existing = task,
            onDismiss = { viewModel.closeTaskEditor() },
            onConfirm = { viewModel.saveEditedTask(it) },
            onDelete = { viewModel.deleteTask(task) },
            readOnly = uiState.editingTaskReadOnly,
            onToggleComplete = {
                viewModel.toggleTaskComplete(task)
                viewModel.closeTaskEditor()
            }
        )
    }
}

@Composable
fun EmptyNotebookState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("还没有计划本", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onCreate) {
            Text("新建计划本")
        }
    }
}

@Composable
fun WeekView(
    tasks: List<Task>,
    subNotebooks: List<Notebook>,
    weekStart: LocalDate,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onToggleComplete: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onCellClick: (date: String, hour: Int) -> Unit,
    onDelete: (Task) -> Unit = {}
) {
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val formatter = DateTimeFormatter.ofPattern("MM/dd")
    // 用 dayOfWeek 动态生成星期名，避免硬编码错位（问题7）
    val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")
    val today = LocalDate.now()
    // #9：子计划本颜色表（id -> Color），解析失败回退主题色；主计划本（自动复盘待办）无条目 → 主题 tertiary
    val subColorMap = remember(subNotebooks) {
        subNotebooks.mapNotNull { nb ->
            nb.color?.let { hex ->
                runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
                    ?.let { nb.id to it }
            }
        }.toMap()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 表头（可点击选中某一天）
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(48.dp)) { }
            weekDays.forEach { date ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (date == selectedDate) MaterialTheme.colorScheme.primaryContainer
                            else Color.Transparent
                        )
                        .clickable { onSelectDate(date) }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("周${weekdayNames[date.dayOfWeek.value - 1]}", style = MaterialTheme.typography.labelSmall)
                    Text(
                        date.format(formatter),
                        style = MaterialTheme.typography.labelSmall,
                        color = when (date) {
                            selectedDate -> MaterialTheme.colorScheme.primary
                            today -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (date == selectedDate) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.Normal
                    )
                }
            }
        }

        // 时段任务区：绝对定位，任务块按 startTime/endTime 精确放置 + 并列
        val hourHeight = 56.dp
        val startHourRange = 6 // 06:00 开始
        val endHourRange = 24   // 24:00 结束
        val totalHours = endHourRange - startHourRange

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Row(modifier = Modifier.height(hourHeight * totalHours)) {
                // 左侧时间轴
                Column(modifier = Modifier.width(40.dp)) {
                    for (h in startHourRange until endHourRange) {
                        Text(
                            text = "%02d:00".format(h),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.height(hourHeight)
                        )
                    }
                }
                // 7 天的列
                weekDays.forEach { date ->
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(Color.LightGray.copy(alpha = 0.15f))
                    ) {
                        val columnWidthPx = maxWidth.value // dp
                        // 背景网格线
                        Column(modifier = Modifier.fillMaxSize()) {
                            for (h in startHourRange until endHourRange) {
                                HorizontalDivider(
                                    modifier = Modifier.height(hourHeight),
                                    thickness = 0.5.dp,
                                    color = Color.LightGray.copy(alpha = 0.5f)
                                )
                            }
                        }
                        // 该天有时段的任务：日期范围覆盖该天
                        // DAILY 已按天展开（startDate==endDate==该天）；ONE_OFF/FLEX 单天；LONG_TERM 无 startTime 不进入
                        val dayTimedTasks = tasks.filter { task ->
                            task.startTime != null && runCatching {
                                val ts = java.time.LocalDate.parse(task.startDate)
                                val te = java.time.LocalDate.parse(task.endDate)
                                date in ts..te
                            }.getOrDefault(false)
                        }
                        // 按开始时间分组，同时段重叠的并列（每个块精确 x 偏移，避免重叠）
                        dayTimedTasks.forEach { task ->
                            val startMin = timeToMinutes(task.startTime!!) - startHourRange * 60
                            val endMin = timeToMinutes(task.endTime ?: task.startTime) - startHourRange * 60
                            if (endMin <= startMin) return@forEach // 跳过无效
                            // 并列：同一时段重叠的任务横向分配
                            val overlapping = dayTimedTasks.filter { other ->
                                val os = timeToMinutes(other.startTime!!) - startHourRange * 60
                                val oe = timeToMinutes(other.endTime ?: other.startTime) - startHourRange * 60
                                os < endMin && startMin < oe
                            }
                            val idx = overlapping.indexOf(task)
                            val count = overlapping.size
                            val blockWidth = (columnWidthPx / count) // dp
                            val density = androidx.compose.ui.platform.LocalDensity.current
                            val yOffset = with(density) {
                                (startMin * (hourHeight.value / 60f)).dp
                            }
                            val heightDp = with(density) {
                                ((endMin - startMin) * (hourHeight.value / 60f)).dp
                            }
                            TaskBlock(
                                task = task,
                                subColor = subColorMap[task.notebookId],
                                modifier = Modifier
                                    .offset(x = with(density) { (idx * blockWidth).dp }, y = yOffset)
                                    .width(with(density) { blockWidth.dp })
                                    .padding(end = 1.dp)
                                    .height(heightDp),
                                onClick = { onTaskClick(task) }
                            )
                        }
                    }
                }
            }
        }

        // 灵活待办区域：选中日的灵活任务 + 无时段临时任务（PRD 4.3.1：无时段→底部灵活区域；
        // ICS 全天事件即此类，#11）；两者恒单天（ADR-0002）
        val flexTasks = tasks.filter {
            (it.type == com.example.planbook.model.TaskType.FLEX ||
                (it.type == com.example.planbook.model.TaskType.ONE_OFF && it.startTime == null)) &&
                runCatching {
                    val ts = java.time.LocalDate.parse(it.startDate)
                    val te = java.time.LocalDate.parse(it.endDate)
                    selectedDate in ts..te
                }.getOrDefault(false)
        }
        FlexibleTaskArea(
            title = "灵活待办 (${selectedDate.format(formatter)})",
            tasks = flexTasks,
            onToggleComplete = onToggleComplete,
            onTaskClick = onTaskClick,
            onDelete = onDelete
        )

        // 长期待办区域：仅当查看日落在 [startDate..DDL] 区间内的长期任务（ADR-0002）
        val longTermTasks = tasks.filter {
            it.type == com.example.planbook.model.TaskType.LONG_TERM && runCatching {
                val ts = java.time.LocalDate.parse(it.startDate)
                val te = java.time.LocalDate.parse(it.endDate)
                selectedDate in ts..te
            }.getOrDefault(false)
        }
        LongTermTaskArea(
            tasks = longTermTasks,
            onToggleComplete = onToggleComplete,
            onTaskClick = onTaskClick,
            onDelete = onDelete
        )
    }
}

@Composable
fun TaskBlock(
    task: Task,
    subColor: Color? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // #9：任务块按所属子计划本的颜色着色；主计划本（自动复盘待办）与无色子本走主题色
    val baseColor = when {
        subColor != null -> subColor
        task.reviewType != null -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    // 已完成任务变透明（PRD：完成态更透明），留原位
    val alpha = if (task.isCompleted) 0.35f else 1f
    Box(
        modifier = modifier
            .padding(2.dp)
            .background(baseColor.copy(alpha = alpha))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(2.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelSmall,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 块内只显示教室（location，来自 ICS LOCATION）；教师等其余备注仅详情可见
            if (!task.location.isNullOrBlank()) {
                Text(
                    text = task.location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FlexibleTaskArea(
    title: String,
    tasks: List<Task>,
    onToggleComplete: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onDelete: (Task) -> Unit = {}
) {
    val (completed, uncompleted) = tasks.partition { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
            items(uncompleted) { task ->
                FlexibleTaskItem(
                    task = task,
                    onToggle = { onToggleComplete(task) },
                    onClick = { onTaskClick(task) },
                    onDelete = { onDelete(task) }
                )
            }
            items(completed) { task ->
                FlexibleTaskItem(
                    task = task,
                    onToggle = { onToggleComplete(task) },
                    onClick = { onTaskClick(task) },
                    onDelete = { onDelete(task) },
                    isCompleted = true
                )
            }
        }
    }
}

@Composable
fun FlexibleTaskItem(
    task: Task,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    isCompleted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCompleted) Color.Gray else Color.Unspecified,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
        // ✕ 删除（与编辑弹窗删除同效）
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 长期待办区：DDL 未过的长期任务，独立栏带勾选框，显示 DDL 日期 */
@Composable
fun LongTermTaskArea(
    tasks: List<Task>,
    onToggleComplete: (Task) -> Unit,
    onTaskClick: (Task) -> Unit,
    onDelete: (Task) -> Unit = {}
) {
    val (completed, uncompleted) = tasks.partition { it.isCompleted }
    if (tasks.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Text("长期待办", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
            items(uncompleted) { task ->
                LongTermTaskItem(
                    task,
                    onToggle = { onToggleComplete(task) },
                    onClick = { onTaskClick(task) },
                    onDelete = { onDelete(task) }
                )
            }
            items(completed) { task ->
                LongTermTaskItem(
                    task,
                    onToggle = { onToggleComplete(task) },
                    onClick = { onTaskClick(task) },
                    onDelete = { onDelete(task) },
                    isCompleted = true
                )
            }
        }
    }
}

@Composable
fun LongTermTaskItem(
    task: Task,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit = {},
    isCompleted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isCompleted,
            onCheckedChange = { onToggle() }
        )
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCompleted) Color.Gray else Color.Unspecified,
            textDecoration = if (isCompleted) TextDecoration.LineThrough else TextDecoration.None,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "DDL ${task.endDate}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        // ✕ 删除（与编辑弹窗删除同效）
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** #9：首页子计划本 chips——显示中的子本各一枚（带颜色点），点按切换活动子本（写操作目标） */
@Composable
private fun SubNotebookChips(
    subNotebooks: List<Notebook>,
    activeSubId: Long?,
    onSelect: (Notebook) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        subNotebooks.forEach { sub ->
            val dotColor = sub.color?.let {
                runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
            } ?: MaterialTheme.colorScheme.primary
            FilterChip(
                selected = sub.id == activeSubId,
                onClick = { onSelect(sub) },
                label = { Text(sub.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(dotColor)
                    )
                }
            )
        }
    }
}

/** "HH:mm" → 当日分钟数（问题1：课表按时间精确放置任务块） */
private fun timeToMinutes(hhmm: String): Int {
    val parts = hhmm.split(":")
    if (parts.size != 2) return 0
    val h = parts[0].toIntOrNull() ?: 0
    val m = parts[1].toIntOrNull() ?: 0
    return h * 60 + m
}
