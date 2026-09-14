package com.example.planbook.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.Notebook
import com.example.planbook.viewmodel.NotebookManagerViewModel

/**
 * 子计划本管理页（设置页入口，ADR-0004）：
 * 列表 + 新建 + 重命名 + 删除 + 活动切换 + 显示开关 + 颜色。
 * 旧「合并计划本」入口随 ADR-0004 废弃，已移除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookManagerScreen(
    onBack: () -> Unit,
    viewModel: NotebookManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val canDelete = uiState.subs.size > 1
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var deleting by remember { mutableStateOf<Notebook?>(null) }
    var coloring by remember { mutableStateOf<Notebook?>(null) }

    // #11：SAF 选 .ics 文件 → 读取文本 → 建导入子计划本
    val icsPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context, uri) ?: "导入课程表"
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
        }.getOrNull()
        if (content == null) {
            viewModel.importIcs(name, "") // 空内容 → 走「没有解析到事件」提示
        } else {
            viewModel.importIcs(name.removeSuffix(".ics"), content)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("子计划本管理") },
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
            Text(
                "活动子计划本是首页新建任务的落点；显示开关只控制是否在计划本页聚合显示。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(uiState.subs, key = { it.id }) { sub ->
                    SubNotebookRow(
                        notebook = sub,
                        isActive = sub.id == uiState.activeSubId,
                        canDelete = canDelete,
                        onSetActive = { viewModel.setActiveSubNotebook(sub.id) },
                        onSetVisible = { viewModel.setSubNotebookVisible(sub.id, it) },
                        onRename = { renaming = sub },
                        onDelete = { deleting = sub },
                        onPickColor = { coloring = sub }
                    )
                    HorizontalDivider()
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("新建子计划本")
                }
                OutlinedButton(
                    onClick = {
                        icsPicker.launch(arrayOf("text/calendar", "application/octet-stream", "text/plain"))
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入 ICS")
                }
            }
        }
    }

    // 新建对话框（默认名 子计划本 N，N 取未被占用的最小序号）
    if (showCreateDialog) {
        val defaultName = remember(uiState.subs) { defaultSubName(uiState.subs) }
        NameEditDialog(
            title = "新建子计划本",
            label = "子计划本名称",
            initialName = defaultName,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createSubNotebook(name)
                showCreateDialog = false
            }
        )
    }

    // 重命名对话框
    renaming?.let { nb ->
        NameEditDialog(
            title = "重命名子计划本",
            label = "子计划本名称",
            initialName = nb.name,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameSubNotebook(nb, name)
                renaming = null
            }
        )
    }

    // 删除确认对话框
    deleting?.let { nb ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除子计划本") },
            text = { Text("确定删除「${nb.name}」吗？该子计划本的所有任务将一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSubNotebook(nb)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }

    // 颜色选择对话框
    coloring?.let { nb ->
        ColorPickerDialog(
            notebook = nb,
            colors = viewModel.paletteColors,
            onPick = { color ->
                viewModel.setSubNotebookColor(nb, color)
                coloring = null
            },
            onDismiss = { coloring = null }
        )
    }

    // #11：导入结果提示
    uiState.importMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.clearImportMessage() },
            title = { Text("导入 ICS") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearImportMessage() }) { Text("知道了") }
            }
        )
    }
}

/** #11：取 SAF uri 的显示名（去扩展名由调用方处理），查不到回退 null */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

/** 默认名：子计划本 N（N 取未被占用的最小序号，避免与现存重名） */
private fun defaultSubName(subs: List<Notebook>): String {
    val names = subs.map { it.name }.toSet()
    var n = subs.size + 1
    while ("子计划本 $n" in names) n++
    return "子计划本 $n"
}

/** "#RRGGBB" → Compose Color；格式非法时回退灰色 */
private fun parseHexColor(hex: String?): Color {
    if (hex != null && Regex("^#[0-9A-Fa-f]{6}$").matches(hex)) {
        return Color(
            red = hex.substring(1, 3).toInt(16),
            green = hex.substring(3, 5).toInt(16),
            blue = hex.substring(5, 7).toInt(16)
        )
    }
    return Color(0xFF9E9E9E)
}

@Composable
private fun SubNotebookRow(
    notebook: Notebook,
    isActive: Boolean,
    canDelete: Boolean,
    onSetActive: () -> Unit,
    onSetVisible: (Boolean) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onPickColor: () -> Unit
) {
    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(notebook.color))
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    notebook.name,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (notebook.isImported) {
                    // #11：导入子计划本标识（只读快照，ADR-0005；编辑保护见 #12）
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            "导入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                if (isActive) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "活动",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                if (isActive) "首页新建任务的落点" else "点击设为活动",
                style = MaterialTheme.typography.bodySmall
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPickColor) {
                    Icon(Icons.Default.Palette, contentDescription = "颜色")
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "重命名")
                }
                IconButton(onClick = onDelete, enabled = canDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
                Switch(checked = notebook.isVisible, onCheckedChange = onSetVisible)
            }
        },
        modifier = Modifier.clickable(onClick = onSetActive)
    )
}

/** 新建 / 重命名共用：标题 + OutlinedTextField */
@Composable
private fun NameEditDialog(
    title: String,
    label: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 颜色选择：预设调色板色圈（4 个一行）+ 自定义圈（HSV 调色盘任选颜色） */
@Composable
private fun ColorPickerDialog(
    notebook: Notebook,
    colors: List<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var showCustom by remember { mutableStateOf(false) }
    if (showCustom) {
        CustomColorPickerDialog(
            initialHex = notebook.color,
            onConfirm = onPick,
            onDismiss = { showCustom = false }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                colors.chunked(4).forEach { rowColors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowColors.forEach { hex ->
                            ColorOption(
                                hex = hex,
                                selected = notebook.color == hex,
                                onClick = { onPick(hex) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                // 自定义颜色入口（彩虹渐变圈）
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFE57373), Color(0xFFFFB74D), Color(0xFFFFF176),
                                        Color(0xFF81C784), Color(0xFF4DD0E1), Color(0xFF7986CB),
                                        Color(0xFFBA68C8), Color(0xFFE57373)
                                    )
                                )
                            )
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { showCustom = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("自定义颜色", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 自定义调色盘：HSV 三滑块 + 实时预览，确定后回调 "#RRGGBB" */
@Composable
private fun CustomColorPickerDialog(
    initialHex: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember { parseHexColor(initialHex) }
    val hsvStart = remember {
        floatArrayOf(0f, 0f, 0f).also {
            android.graphics.Color.colorToHSV(initial.toArgb(), it)
        }
    }
    var hue by remember { mutableStateOf(hsvStart[0]) }
    var sat by remember { mutableStateOf(hsvStart[1]) }
    var bri by remember { mutableStateOf(hsvStart[2]) }
    val current = Color.hsv(hue, sat, bri)
    val currentHex = "#%06X".format(current.toArgb() and 0xFFFFFF)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义颜色") },
        text = {
            Column {
                // 预览
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(current)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(currentHex, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(12.dp))
                // 色相：彩虹横带
                Text("色相", style = MaterialTheme.typography.labelSmall)
                SliderTrack(
                    brush = Brush.horizontalGradient(
                        (0..360 step 60).map { Color.hsv(it.toFloat(), 1f, 1f) }
                    )
                )
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                // 饱和度：当前色相 白 → 纯色
                Text("饱和度", style = MaterialTheme.typography.labelSmall)
                SliderTrack(
                    brush = Brush.horizontalGradient(
                        listOf(Color.hsv(hue, 0f, 1f), Color.hsv(hue, 1f, 1f))
                    )
                )
                Slider(value = sat, onValueChange = { sat = it }, valueRange = 0f..1f)
                // 明度：黑 → 纯色
                Text("明度", style = MaterialTheme.typography.labelSmall)
                SliderTrack(
                    brush = Brush.horizontalGradient(
                        listOf(Color.hsv(hue, 1f, 0f), Color.hsv(hue, 1f, 1f))
                    )
                )
                Slider(value = bri, onValueChange = { bri = it }, valueRange = 0f..1f)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentHex) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 滑块上方的颜色示意条 */
@Composable
private fun SliderTrack(brush: Brush) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(brush)
    )
}

@Composable
private fun ColorOption(hex: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(parseHexColor(hex))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "当前颜色",
                tint = Color.White
            )
        }
    }
}
