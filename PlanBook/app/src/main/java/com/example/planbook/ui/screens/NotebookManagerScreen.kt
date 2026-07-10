package com.example.planbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.MergeConflict
import kotlinx.coroutines.launch
import com.example.planbook.model.MergeResolution
import com.example.planbook.model.Notebook
import com.example.planbook.viewmodel.PlanBookViewModel

/**
 * 计划本管理页（PRD 4.1.3 / 4.1.4）：
 * 列表 + 重命名 + 删除 + 合并。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookManagerScreen(
    onBack: () -> Unit,
    viewModel: PlanBookViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMergeDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Notebook?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("计划本管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.notebooks, key = { it.id }) { notebook ->
                    NotebookManagerRow(
                        notebook = notebook,
                        isCurrent = notebook.id == uiState.currentNotebook?.id,
                        canDelete = uiState.notebooks.size > 1,
                        onRename = { renaming = notebook },
                        onDelete = { viewModel.deleteNotebook(notebook) },
                        onSwitch = { viewModel.switchNotebook(notebook) }
                    )
                    HorizontalDivider()
                }
            }
            // 合并入口（至少两个计划本才能合并）
            Button(
                onClick = { showMergeDialog = true },
                enabled = uiState.notebooks.size >= 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text("合并计划本")
            }
        }
    }

    // 重命名对话框
    renaming?.let { nb ->
        var name by remember { mutableStateOf(nb.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("重命名计划本") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("计划本名称") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameNotebook(nb, name)
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renaming = null }) { Text("取消") }
            }
        )
    }

    // 合并流程
    if (showMergeDialog) {
        MergeFlow(
            notebooks = uiState.notebooks,
            viewModel = viewModel,
            onDismiss = { showMergeDialog = false }
        )
    }
}

@Composable
private fun NotebookManagerRow(
    notebook: Notebook,
    isCurrent: Boolean,
    canDelete: Boolean,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSwitch: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                notebook.name + if (isCurrent) "（当前）" else "",
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "重命名")
                }
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        },
        modifier = Modifier.clickable(onClick = onSwitch)
    )
}

/** 合并流程：选两个计划本 → 检测冲突 → 逐条选 → 执行（PRD 4.1.4） */
@Composable
private fun MergeFlow(
    notebooks: List<Notebook>,
    viewModel: PlanBookViewModel,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(0) } // 0=选源 1=选目标 2=冲突 3=完成
    var sourceId by remember { mutableStateOf<Long?>(null) }
    var targetId by remember { mutableStateOf<Long?>(null) }
    var newName by remember { mutableStateOf("") }
    var conflicts by remember { mutableStateOf<List<MergeConflict>>(emptyList()) }
    val resolutions = remember { mutableStateMapOf<Long, MergeResolution>() }
    val scope = rememberCoroutineScope()

    when (step) {
        0, 1 -> SelectNotebookStep(
            title = if (step == 0) "选择计划本 A" else "选择计划本 B",
            notebooks = notebooks,
            excludeId = sourceId,
            onSelected = { id ->
                if (step == 0) {
                    sourceId = id
                    step = 1
                } else {
                    targetId = id
                    val a = sourceId!!
                    val aName = notebooks.first { it.id == a }.name
                    val bName = notebooks.first { it.id == id }.name
                    newName = "$aName + $bName"
                    // 检测冲突（suspend，需协程）
                    scope.launch {
                        conflicts = viewModel.detectMergeConflicts(a, id)
                        step = 2
                    }
                }
            },
            onDismiss = onDismiss
        )
        2 -> ConflictStep(
            conflicts = conflicts,
            newName = newName,
            onNewNameChange = { newName = it },
            onResolve = { taskId, res -> resolutions[taskId] = res },
            onConfirm = {
                viewModel.executeMerge(sourceId!!, targetId!!, newName, resolutions.toMap())
                step = 3
            },
            onDismiss = onDismiss
        )
        3 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("合并完成") },
            text = { Text("新计划本「$newName」已创建并设为当前计划本，原计划本保留。") },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("好的") }
            }
        )
    }
}

@Composable
private fun SelectNotebookStep(
    title: String,
    notebooks: List<Notebook>,
    excludeId: Long?,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                notebooks.filter { it.id != excludeId }.forEach { nb ->
                    ListItem(
                        headlineContent = { Text(nb.name) },
                        modifier = Modifier.clickable { onSelected(nb.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ConflictStep(
    conflicts: List<MergeConflict>,
    newName: String,
    onNewNameChange: (String) -> Unit,
    onResolve: (taskId: Long, res: MergeResolution) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (conflicts.isEmpty()) "确认合并" else "解决冲突") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = onNewNameChange,
                    label = { Text("新计划本名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (conflicts.isEmpty()) {
                    Text("无时段冲突，全部任务将复制到新计划本。")
                } else {
                    Text("以下带时段任务存在冲突，请逐条选择：", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    conflicts.forEach { c ->
                        Text(
                            "「${c.taskA.title}」(${c.taskA.startDate} ${c.taskA.startTime}) ↔ 「${c.taskB.title}」(${c.taskB.startDate} ${c.taskB.startTime})",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row {
                            AssistChip(onClick = {
                                onResolve(c.taskA.id, MergeResolution.KEEP_A)
                                onResolve(c.taskB.id, MergeResolution.DROP)
                            }, label = { Text("留 A") })
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(onClick = {
                                onResolve(c.taskA.id, MergeResolution.DROP)
                                onResolve(c.taskB.id, MergeResolution.KEEP_B)
                            }, label = { Text("留 B") })
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(onClick = {
                                onResolve(c.taskA.id, MergeResolution.DROP)
                                onResolve(c.taskB.id, MergeResolution.DROP)
                            }, label = { Text("都不留") })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("合并") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
