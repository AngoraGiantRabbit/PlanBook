package com.example.planbook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.planbook.model.RepeatRule
import com.example.planbook.model.Task
import com.example.planbook.model.TaskType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    notebookId: Long,
    onDismiss: () -> Unit,
    onConfirm: (Task) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(TaskType.ONE_OFF) }
    var startDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var endDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var repeatRule by remember { mutableStateOf(RepeatRule.EVERY_DAY) }
    var selectedDays by remember { mutableStateOf(setOf(LocalDate.now().dayOfWeek.value)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加任务") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务名称") },
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

                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("开始日期 yyyy-MM-dd") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("结束日期 yyyy-MM-dd") },
                    modifier = Modifier.fillMaxWidth()
                )

                if (selectedType == TaskType.ONE_OFF || selectedType == TaskType.DAILY) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it },
                        label = { Text("开始时间 HH:mm（可选）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it },
                        label = { Text("结束时间 HH:mm（可选）") },
                        modifier = Modifier.fillMaxWidth()
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
                        Row(modifier = Modifier.fillMaxWidth()) {
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
                                Spacer(modifier = Modifier.width(2.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val task = Task(
                        notebookId = notebookId,
                        title = title,
                        type = selectedType,
                        startDate = startDate,
                        endDate = endDate,
                        startTime = startTime.takeIf { it.isNotBlank() },
                        endTime = endTime.takeIf { it.isNotBlank() },
                        repeatRule = if (selectedType == TaskType.DAILY) repeatRule else null,
                        weeklyDays = if (selectedType == TaskType.DAILY && repeatRule == RepeatRule.WEEKDAYS) {
                            selectedDays.sorted().joinToString(",")
                        } else null
                    )
                    onConfirm(task)
                },
                enabled = title.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
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
