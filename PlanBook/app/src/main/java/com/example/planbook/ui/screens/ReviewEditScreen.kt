package com.example.planbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.Task
import com.example.planbook.viewmodel.ReviewViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 复盘编辑页（PRD 4.4.4）：
 * - 顶部日期
 * - 当日已完成任务区
 * - 大文本输入框，停止输入 1 秒后自动保存
 * - 底部"待细化长期任务"区：点击拆分为临时/灵活任务，原长期保留
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewEditScreen(
    date: String,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val state by viewModel.editState.collectAsState()
    val calState by viewModel.calendarState.collectAsState()
    // 拆分长期任务时，子任务落到活动子计划本（ADR-0004）
    val notebookId = calState.activeSubNotebook?.id ?: calState.currentNotebook?.id ?: 0L

    LaunchedEffect(date) {
        viewModel.openReviewEdit(date)
    }

    var text by remember(state.date) { mutableStateOf(state.content) }
    // 当外部加载完成后，同步一次初始内容
    LaunchedEffect(state.loaded) {
        if (state.loaded) text = state.content
    }

    // 停止输入 1 秒后自动保存（PRD 4.4.4）
    LaunchedEffect(text) {
        if (state.loaded) {
            delay(1000)
            viewModel.saveReviewContent(text)
        }
    }

    val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
    val dateText = parsed?.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA)) ?: date

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                // 保存按钮：保存并返回（问题5）
                actions = {
                    TextButton(onClick = {
                        viewModel.saveReviewContent(text)
                        onBack()
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 当日已完成任务区（PRD 4.4.4）
            Text("今日完成", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (state.completedTasks.isEmpty()) {
                Text("暂无已完成任务", style = MaterialTheme.typography.bodySmall)
            } else {
                state.completedTasks.forEach { task -> CompletedTaskRow(task) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 复盘文本
            Text("复盘", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                placeholder = { Text("写下今天的复盘…") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 待细化长期任务区（PRD 4.4.4）
            Text("待细化长期任务", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            if (state.longTermTasks.isEmpty()) {
                Text("暂无临近 DDL 的长期任务", style = MaterialTheme.typography.bodySmall)
            } else {
                state.longTermTasks.forEach { task ->
                    LongTermSplitRow(task = task, notebookId = notebookId, onSplit = { subTask ->
                        viewModel.splitLongTermTask(subTask)
                    })
                }
            }
        }
    }
}

@Composable
private fun CompletedTaskRow(task: Task) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = "✓ ${task.title}",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 长期任务行：点击拆分为灵活/临时/每日任务，选中后打开编辑表单，原长期保留（PRD 4.4.4） */
@Composable
private fun LongTermSplitRow(task: Task, notebookId: Long, onSplit: (Task) -> Unit) {
    var showSplit by remember { mutableStateOf(false) }
    var editType by remember { mutableStateOf<com.example.planbook.model.TaskType?>(null) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .clickable { showSplit = true }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text("DDL: ${task.endDate}", style = MaterialTheme.typography.labelSmall)
            }
            Icon(Icons.Default.Add, contentDescription = "拆分")
        }
    }
    if (showSplit) {
        AlertDialog(
            onDismissRequest = { showSplit = false },
            title = { Text("拆分「${task.title}」") },
            text = { Text("拆分为哪种任务？可设置名称和时间，原长期任务会保留。") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        editType = com.example.planbook.model.TaskType.FLEX
                        showSplit = false
                    }) { Text("灵活任务") }
                    TextButton(onClick = {
                        editType = com.example.planbook.model.TaskType.ONE_OFF
                        showSplit = false
                    }) { Text("临时任务") }
                    TextButton(onClick = {
                        editType = com.example.planbook.model.TaskType.DAILY
                        showSplit = false
                    }) { Text("每日任务") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplit = false }) { Text("取消") }
            }
        )
    }
    // 选好类型后，打开完整编辑表单（预填标题）
    editType?.let { type ->
        TaskEditSheet(
            notebookId = notebookId,
            existing = task.copy(id = 0, title = task.title, type = type),
            onDismiss = { editType = null },
            onConfirm = { subTask ->
                onSplit(subTask)
                editType = null
            }
        )
    }
}
