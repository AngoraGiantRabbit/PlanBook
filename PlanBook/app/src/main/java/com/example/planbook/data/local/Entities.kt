package com.example.planbook.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val isCurrent: Boolean = false,
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
