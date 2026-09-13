package com.example.planbook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.planbook.model.ReviewType
import com.example.planbook.viewmodel.SettingsViewModel

/**
 * 设置 Tab（PRD 4.4.2）：
 * - 复盘设置：每类复盘开关 + 开始/结束时间（TimePicker）
 * - 后续可扩展：计划本管理入口
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageNotebooks: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("复盘设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "控制每日/每周/每月复盘待办是否自动生成，以及生成时段。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            state.reviewSettings.forEach { setting ->
                ReviewSettingItem(
                    type = setting.reviewType,
                    enabled = setting.enabled,
                    startTime = setting.startTime,
                    endTime = setting.endTime,
                    onToggle = { viewModel.toggleReview(setting.reviewType, it) },
                    onTimeChange = { s, e -> viewModel.setReviewTime(setting.reviewType, s, e) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("计划本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onManageNotebooks, modifier = Modifier.fillMaxWidth()) {
                Text("计划本管理（子计划本：新建 / 显示 / 活动 / 颜色）")
            }
        }
    }
}

@Composable
private fun ReviewSettingItem(
    type: ReviewType,
    enabled: Boolean,
    startTime: String,
    endTime: String,
    onToggle: (Boolean) -> Unit,
    onTimeChange: (start: String, end: String) -> Unit
) {
    val typeLabel = when (type) {
        ReviewType.DAILY -> "每日复盘"
        ReviewType.WEEKLY -> "每周复盘"
        ReviewType.MONTHLY -> "每月复盘"
    }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(typeLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        // #6：时段行始终显示（关闭开关时也能查看/修改时间，重开时不用重设）
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("时段", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(12.dp))
            TimeChip(startTime) { showStartPicker = true }
            Text(" ~ ")
            TimeChip(endTime) { showEndPicker = true }
        }
    }

    if (showStartPicker) {
        val (h, m) = parseTime(startTime) ?: (21 to 0)
        TimePickerModal(
            initialHour = h,
            initialMinute = m,
            onConfirm = {
                onTimeChange(it, endTime)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        val (h, m) = parseTime(endTime) ?: (21 to 30)
        TimePickerModal(
            initialHour = h,
            initialMinute = m,
            onConfirm = {
                onTimeChange(startTime, it)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

@Composable
private fun TimeChip(time: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(time) }
    )
}
