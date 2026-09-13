package com.example.planbook.ui.screens

import androidx.compose.foundation.layout.*
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
 * [readOnly] = true 时为只读呈现（#12：导入子计划本是只读快照，
 * 不可编辑保存/删除；勾选完成不受此限制），仅保留关闭按钮。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TaskEditSheet(
    notebookId: Long,
    existing: Task? = null,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit,
    onDelete: (() -> Unit)? = null,
    readOnly: Boolean = false
) {
    val isNew = existing == null
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var location by remember { mutableStateOf(existing?.location ?: "") }
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
        title = { Text(when { readOnly -> "任务详情（只读）"; isNew -> "添加任务"; else -> "编辑任务" }) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (readOnly) {
                    // #12：导入子计划本为只读快照，防止 App 内改动后与源文件失去对应
                    Text(
                        "该任务来自导入子计划本（只读快照），不支持编辑或删除。课表有变请重新导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务名称") },
                    enabled = !readOnly,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("备注（可选）") },
                    enabled = !readOnly,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("教室（可选，显示在任务块上）") },
                    enabled = !readOnly,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    TaskType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = selectedType == type,
                            enabled = !readOnly,
                            onClick = {
                                selectedType = type
                                // 切到单天类型时，把 endDate 收敛到 startDate，避免脏数据（ADR-0002）
                                if (type == TaskType.FLEX || type == TaskType.ONE_OFF) {
                                    endDate = startDate
                                }
                            },
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

                // 日期入口：灵活/临时单天（一个），每日/长期两个（ADR-0002）
                val isSingleDayType =
                    selectedType == TaskType.FLEX || selectedType == TaskType.ONE_OFF
                PickerField(
                    value = if (isSingleDayType) startDate else startDate,
                    label = if (isSingleDayType) "日期" else "开始日期",
                    onClick = { showStartDatePicker = true },
                    enabled = !readOnly
                )
                if (!isSingleDayType) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PickerField(
                        value = endDate,
                        label = if (selectedType == TaskType.LONG_TERM) "截止日期 DDL" else "结束日期",
                        onClick = { showEndDatePicker = true },
                        enabled = !readOnly
                    )
                }

                if (selectedType == TaskType.ONE_OFF || selectedType == TaskType.DAILY) {
                    Spacer(modifier = Modifier.height(4.dp))
                    PickerField(
                        value = startTime,
                        label = "开始时间（可选，留空则无时段）",
                        onClick = { showStartTimePicker = true },
                        enabled = !readOnly
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    PickerField(
                        value = endTime,
                        label = "结束时间（可选）",
                        onClick = { showEndTimePicker = true },
                        enabled = !readOnly
                    )
                }

                if (selectedType == TaskType.DAILY) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("重复规则", style = MaterialTheme.typography.labelSmall)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RepeatRule.entries.forEach { rule ->
                            FilterChip(
                                selected = repeatRule == rule,
                                enabled = !readOnly,
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
                                    enabled = !readOnly,
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
            if (readOnly) {
                // #12：只读快照——无保存/删除入口
                TextButton(onClick = onDismiss) { Text("关闭") }
            } else {
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
                            // ADR-0002：灵活/临时为单天，强制 startDate == endDate
                            val (finalStart, finalEnd) =
                                if (selectedType == TaskType.FLEX || selectedType == TaskType.ONE_OFF) {
                                    startDate to startDate
                                } else {
                                    startDate to endDate
                                }
                            val task = Task(
                                id = existing?.id ?: 0,
                                notebookId = notebookId,
                                title = title,
                                description = description,
                                location = location.takeIf { it.isNotBlank() },
                                type = selectedType,
                                startDate = finalStart,
                                endDate = finalEnd,
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
            }
        },
        dismissButton = {
            if (!readOnly) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )

    // 日期选择器
    if (showStartDatePicker) {
        DatePickerModal(
            initial = runCatching { LocalDate.parse(startDate) }.getOrNull(),
            onConfirm = {
                startDate = it.toString()
                // 单天类型：startDate 改动同步 endDate（ADR-0002）
                if (selectedType == TaskType.FLEX || selectedType == TaskType.ONE_OFF) {
                    endDate = it.toString()
                }
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
