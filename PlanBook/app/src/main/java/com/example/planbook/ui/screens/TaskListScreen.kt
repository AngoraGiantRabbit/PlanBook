package com.example.planbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.Task
import com.example.planbook.model.TaskType
import com.example.planbook.viewmodel.TaskListViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 待办列表 Tab（问题4重做）：
 * - 移除日历，直接显示当天待办
 * - 标题处日期可点击 → 弹 DatePicker 选日期
 * - 加号 FAB 新建任务（预填选中日期）
 * - 分四类显示，带时间/DDL，打勾完成
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    viewModel: TaskListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val dateFmt = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("${state.selectedDate.format(dateFmt)} 待办")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "添加任务")
            }
        }
    ) { padding ->
        val groups = remember(state.tasks) {
            listOf(
                "临时任务" to state.tasks.filter { it.type == TaskType.ONE_OFF },
                "每日任务" to state.tasks.filter { it.type == TaskType.DAILY },
                "灵活任务" to state.tasks.filter { it.type == TaskType.FLEX },
                "长期任务" to state.tasks.filter { it.type == TaskType.LONG_TERM }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (state.tasks.isEmpty() && state.loaded) {
                item { Text("这天没有待办", style = MaterialTheme.typography.bodyMedium) }
            }
            groups.forEach { (label, list) ->
                if (list.isNotEmpty()) {
                    item {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(list) { task ->
                        TaskRowWithTime(
                            task = task,
                            onToggle = { viewModel.toggleComplete(task) },
                            onClick = { viewModel.openTaskEditor(task) },
                            onDelete = { viewModel.deleteTask(task) }
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            initial = state.selectedDate,
            onConfirm = {
                viewModel.selectDate(it)
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false }
        )
    }

    if (state.showAddDialog) {
        AddTaskDialog(
            notebookId = state.currentNotebook?.id ?: 0,
            prefill = state.prefillTask,
            onDismiss = { viewModel.hideAddDialog() },
            onConfirm = { viewModel.addTask(it) }
        )
    }

    // 条目点击打开的编辑弹窗（与计划本页同款，含完成/删除；写库后三页观察管道联动）
    state.editingTask?.let { task ->
        TaskEditSheet(
            notebookId = task.notebookId,
            existing = task,
            onDismiss = { viewModel.closeTaskEditor() },
            onConfirm = { viewModel.saveEditedTask(it) },
            onDelete = { viewModel.deleteTask(task) },
            readOnly = state.editingTaskReadOnly,
            onToggleComplete = {
                viewModel.toggleComplete(task)
                viewModel.closeTaskEditor()
            }
        )
    }
}

@Composable
private fun TaskRowWithTime(
    task: Task,
    onToggle: () -> Unit,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val timeText = when (task.type) {
        TaskType.ONE_OFF -> task.startTime?.let { s -> "${s} - ${task.endTime ?: s}" } ?: "全天"
        TaskType.DAILY -> task.startTime?.let { s -> "${s} - ${task.endTime ?: s}" } ?: "每日"
        TaskType.FLEX -> "无时段"
        TaskType.LONG_TERM -> "DDL ${task.endDate}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = task.isCompleted, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isCompleted) Color.Gray else Color.Unspecified,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
            )
            Text(timeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
