package com.example.planbook.model

enum class TaskType {
    FLEX,      // 灵活任务：只设置起止日期
    ONE_OFF,   // 临时任务：设置起止日期，可选精确到分钟的时间
    DAILY,     // 每日任务：按重复规则发生
    LONG_TERM  // 长期任务：只设置 DDL
}

enum class RepeatRule {
    EVERY_DAY,
    WEEKDAYS // 每周指定几天，具体用 weeklyDays 字段存储
}

data class Notebook(
    val id: Long = 0,
    val name: String,
    val isCurrent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class Task(
    val id: Long = 0,
    val notebookId: Long,
    val title: String,
    val description: String = "",
    val type: TaskType,
    val startDate: String,     // yyyy-MM-dd
    val endDate: String,       // yyyy-MM-dd
    val startTime: String? = null, // HH:mm，可选
    val endTime: String? = null,   // HH:mm，可选
    val repeatRule: RepeatRule? = null,
    val weeklyDays: String? = null, // 例如 "1,3,5" 表示周一、三、五
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val isAutoReview: Boolean = false, // 是否是由复盘系统生成的自动待办
    val reviewType: ReviewType? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ReviewType {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class Review(
    val id: Long = 0,
    val notebookId: Long,
    val date: String,          // yyyy-MM-dd
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class ReviewSetting(
    val id: Long = 0,
    val notebookId: Long,
    val reviewType: ReviewType,
    val enabled: Boolean = true,
    val startTime: String,     // HH:mm
    val endTime: String        // HH:mm
)

/** 合并计划本时的时段冲突任务对（PRD 4.1.4） */
data class MergeConflict(val taskA: Task, val taskB: Task)

/** 冲突任务的处理结果 */
enum class MergeResolution { KEEP_A, KEEP_B, DROP }
