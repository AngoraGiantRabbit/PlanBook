package com.example.planbook.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.Notebook
import com.example.planbook.model.Task
import com.example.planbook.viewmodel.PlanBookViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanBookScreen(
    viewModel: PlanBookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { viewModel.showNotebookSelector() }) {
                        Text(
                            text = uiState.currentNotebook?.name ?: "选择计划本",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (uiState.currentNotebook == null) {
                EmptyNotebookState(onCreate = { viewModel.createNotebook("我的计划本") })
            } else {
                WeekView(
                    tasks = uiState.tasks,
                    weekStart = uiState.weekStart,
                    onToggleComplete = { viewModel.toggleTaskComplete(it) },
                    onTaskClick = { viewModel.onTaskClick(it) }
                )
            }
        }
    }

    if (uiState.showNotebookSelector) {
        NotebookSelectorDialog(
            notebooks = uiState.notebooks,
            current = uiState.currentNotebook,
            onSelect = { viewModel.switchNotebook(it) },
            onCreate = { viewModel.createNotebook("计划本 ${uiState.notebooks.size + 1}") },
            onDismiss = { viewModel.hideNotebookSelector() }
        )
    }

    if (uiState.showAddTaskDialog) {
        AddTaskDialog(
            notebookId = uiState.currentNotebook?.id ?: 0,
            onDismiss = { viewModel.hideAddTaskDialog() },
            onConfirm = { viewModel.addTask(it) }
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
    weekStart: LocalDate,
    onToggleComplete: (Task) -> Unit,
    onTaskClick: (Task) -> Unit
) {
    val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
    val formatter = DateTimeFormatter.ofPattern("MM/dd")
    val weekdayNames = listOf("一", "二", "三", "四", "五", "六", "日")

    Column(modifier = Modifier.fillMaxSize()) {
        // 表头
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(48.dp)) { }
            weekDays.forEachIndexed { index, date ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("周${weekdayNames[index]}", style = MaterialTheme.typography.labelSmall)
                    Text(date.format(formatter), style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // 时段任务区
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Column {
                val hours = 6..24
                hours.forEach { hour ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "$hour:00",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(48.dp)
                        )
                        weekDays.forEach { date ->
                            val cellTasks = tasks.filter {
                                it.startDate == date.toString() &&
                                !it.isCompleted &&
                                it.startTime != null &&
                                it.startTime.startsWith("%02d".format(hour))
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(1.dp)
                                    .background(Color.LightGray.copy(alpha = 0.2f))
                            ) {
                                cellTasks.forEach { task ->
                                    TaskBlock(
                                        task = task,
                                        modifier = Modifier.fillMaxSize(),
                                        onClick = { onTaskClick(task) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 灵活待办区域
        FlexibleTaskArea(
            tasks = tasks.filter { it.startTime == null || it.type.name == "FLEX" || it.type.name == "LONG_TERM" },
            onToggleComplete = onToggleComplete,
            onTaskClick = onTaskClick
        )
    }
}

@Composable
fun TaskBlock(
    task: Task,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = when (task.reviewType) {
        null -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Box(
        modifier = modifier
            .padding(2.dp)
            .background(color)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = task.title,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(2.dp)
        )
    }
}

@Composable
fun FlexibleTaskArea(
    tasks: List<Task>,
    onToggleComplete: (Task) -> Unit,
    onTaskClick: (Task) -> Unit
) {
    val (completed, uncompleted) = tasks.partition { it.isCompleted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Text("灵活待办", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
            items(uncompleted) { task ->
                FlexibleTaskItem(
                    task = task,
                    onToggle = { onToggleComplete(task) },
                    onClick = { onTaskClick(task) }
                )
            }
            items(completed) { task ->
                FlexibleTaskItem(
                    task = task,
                    onToggle = { onToggleComplete(task) },
                    onClick = { onTaskClick(task) },
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookSelectorDialog(
    notebooks: List<Notebook>,
    current: Notebook?,
    onSelect: (Notebook) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择计划本") },
        text = {
            Column {
                notebooks.forEach { notebook ->
                    ListItem(
                        headlineContent = { Text(notebook.name) },
                        leadingContent = {
                            RadioButton(
                                selected = notebook.id == current?.id,
                                onClick = { onSelect(notebook) }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreate) {
                Text("新建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
