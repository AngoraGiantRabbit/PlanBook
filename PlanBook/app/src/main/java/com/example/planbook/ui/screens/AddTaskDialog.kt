package com.example.planbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.planbook.model.RepeatRule
import com.example.planbook.model.Task
import com.example.planbook.model.TaskType
import java.time.LocalDate

/**
 * 复用的任务编辑表单：新建与编辑共用（PRD 4.3.3）。
 * 新建时 [existing] 传 null；编辑时传入待编辑任务。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditSheet(
    notebookId: Long,
    existing: Task? = null,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val isNew = existing == null
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var selectedType by remember { mutableStateOf(existing?.type ?: TaskType.ONE_OFF) }
    var startDate by remember { mutableStateOf(existing?.startDate ?: LocalDate.now().toString()) }
    var endDate by remember { mutableStateOf(existing?.endDate ?: LocalDate.now().toString()) }
    var startTime by remember { mutableStateOf(existing?.startTime ?: "") }
    var endTime by remember { mutableStateOf(existing?.endTime ?: "") }
    var repeatRule by remember { mutableStateOf(existing?.repeatRule ?: RepeatRule.EVERY_DAY) }
    var selectedDays by remember {
        mutableStateOf(
            existing?.weeklyDays?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet()
                ?: setOf(LocalDate.now().dayOfWeek.value)
        )
    }
    // 选择器弹窗开关（PRD 体验：日期/时间用选择器而非手输）
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加任务" else "编辑任务") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务名称") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TaskType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TaskType.entries.size
                            )
                        ) {
                            Text(typeLabel(type))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 开始日期（点击唤起 DatePicker）
                OutlinedTextField(
                    value = startDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("开始日期") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartDatePicker = true }
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = endDate,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(if (selectedType == TaskType.LONG_TERM) "截止日期 DDL" else "结束日期") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEndDatePicker = true }
                )

                if (selectedType == TaskType.ONE_OFF || selectedType == TaskType.DAILY) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("开始时间（可选，留空则无时段）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStartTimePicker = true }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("结束时间（可选）") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showEndTimePicker = true }
                    )
                }

                if (selectedType == TaskType.DAILY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("重复规则", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RepeatRule.entries.forEach { rule ->
                            FilterChip(
                                selected = repeatRule == rule,
                                onClick = { repeatRule = rule },
                                label = { Text(repeatRuleLabel(rule)) }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                    if (repeatRule == RepeatRule.WEEKDAYS) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("选择每周哪几天", style = MaterialTheme.typography.labelSmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            (1..7).forEach { day ->
                                FilterChip(
                                    selected = day in selectedDays,
                                    onClick = {
                                        selectedDays = if (day in selectedDays) {
                                            selectedDays - day
                                        } else {
                                            selectedDays + day
                                        }
                                    },
                                    label = { Text(dayName(day)) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                if (!isNew && onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("删除")
                    }
                }
                TextButton(
                    onClick = {
                        val task = Task(
                            id = existing?.id ?: 0,
                            notebookId = notebookId,
                            title = title,
                            description = description,
                            type = selectedType,
                            startDate = startDate,
                            endDate = endDate,
                            startTime = startTime.takeIf { it.isNotBlank() },
                            endTime = endTime.takeIf { it.isNotBlank() },
                            repeatRule = if (selectedType == TaskType.DAILY) repeatRule else null,
                            weeklyDays = if (selectedType == TaskType.DAILY && repeatRule == RepeatRule.WEEKDAYS) {
                                selectedDays.sorted().joinToString(",")
                            } else null,
                            isCompleted = existing?.isCompleted ?: false,
                            completedAt = existing?.completedAt,
                            isAutoReview = existing?.isAutoReview ?: false,
                            reviewType = existing?.reviewType,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis()
                        )
                        onConfirm(task)
                    },
                    enabled = title.isNotBlank()
                ) {
                    Text(if (isNew) "确定" else "保存")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )

    // 日期选择器
    if (showStartDatePicker) {
        DatePickerModal(
            initial = runCatching { LocalDate.parse(startDate) }.getOrNull(),
            onConfirm = {
                startDate = it.toString()
                showStartDatePicker = false
            },
            onDismiss = { showStartDatePicker = false }
        )
    }
    if (showEndDatePicker) {
        DatePickerModal(
            initial = runCatching { LocalDate.parse(endDate) }.getOrNull(),
            onConfirm = {
                endDate = it.toString()
                showEndDatePicker = false
            },
            onDismiss = { showEndDatePicker = false }
        )
    }
    // 时间选择器
    if (showStartTimePicker) {
        val (h, m) = parseTime(startTime) ?: 9 to 0
        TimePickerModal(
            initialHour = h,
            initialMinute = m,
            onConfirm = {
                startTime = it
                showStartTimePicker = false
            },
            onDismiss = { showStartTimePicker = false }
        )
    }
    if (showEndTimePicker) {
        val (h, m) = parseTime(endTime) ?: (h2(startTime)) to 0
        TimePickerModal(
            initialHour = h,
            initialMinute = m,
            onConfirm = {
                endTime = it
                showEndTimePicker = false
            },
            onDismiss = { showEndTimePicker = false }
        )
    }
}

private fun h2(startTime: String): Int = parseTime(startTime)?.first?.plus(1) ?: 10

/** 新建任务的便捷入口（保持向后兼容） */
@Composable
fun AddTaskDialog(
    notebookId: Long,
    prefill: Task? = null,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    TaskEditSheet(
        notebookId = notebookId,
        existing = prefill?.copy(id = 0, title = ""),
        onDismiss = onDismiss,
        onConfirm = onConfirm
    )
}

private fun typeLabel(type: TaskType): String = when (type) {
    TaskType.FLEX -> "灵活"
    TaskType.ONE_OFF -> "临时"
    TaskType.DAILY -> "每日"
    TaskType.LONG_TERM -> "长期"
}

private fun repeatRuleLabel(rule: RepeatRule): String = when (rule) {
    RepeatRule.EVERY_DAY -> "每天"
    RepeatRule.WEEKDAYS -> "每周几天"
}

private fun dayName(day: Int): String = when (day) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    7 -> "日"
    else -> "?"
}
