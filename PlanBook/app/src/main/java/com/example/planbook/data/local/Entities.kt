package com.example.planbook.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 计划本（ADR-0004 两层模型）：
 * - parentId 为 null → 主计划本（全局唯一，复盘/复盘设置/自动复盘待办挂它）
 * - parentId 非 null → 子计划本（任务挂在子计划本上）
 * 子计划本有两个独立状态：isVisible（是否在计划本页聚合显示）、isActive（活动子计划本，写操作目标，同父下唯一）。
 */
@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val color: String? = null,    // 子计划本颜色 "#RRGGBB"，主计划本为 null
    val isVisible: Boolean = true,
    val isActive: Boolean = false,
    /** 非 null = 导入子计划本（只读快照，ADR-0005）；存源文件名，重导匹配用（#12） */
    val importSource: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notebookId: Long,
    val title: String,
    val description: String = "",
    val type: String,          // FLEX, ONE_OFF, DAILY, LONG_TERM
    val startDate: String,     // yyyy-MM-dd
    val endDate: String,       // yyyy-MM-dd
    val startTime: String? = null,
    val endTime: String? = null,
    val repeatRule: String? = null, // EVERY_DAY, WEEKDAYS
    val weeklyDays: String? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val isAutoReview: Boolean = false,
    val reviewType: String? = null, // DAILY, WEEKLY, MONTHLY
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "reviews")
data class ReviewEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notebookId: Long,
    val date: String,          // yyyy-MM-dd
    val content: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "review_settings",
    indices = [Index(value = ["notebookId", "reviewType"], unique = true)]
)
data class ReviewSettingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notebookId: Long,
    val reviewType: String,    // DAILY, WEEKLY, MONTHLY
    val enabled: Boolean = true,
    val startTime: String,     // HH:mm
    val endTime: String        // HH:mm
)

/**
 * 任务完成记录：按天记录某任务在某天被完成（解决问题2：跨天任务每天独立完成）。
 * 同一个 taskId 在不同 date 各有一条记录。
 */
@Entity(
    tableName = "task_completions",
    indices = [Index(value = ["taskId", "date"], unique = true)]
)
data class TaskCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val date: String,          // yyyy-MM-dd，该任务在这一天被完成
    val completedAt: Long = System.currentTimeMillis()
)
