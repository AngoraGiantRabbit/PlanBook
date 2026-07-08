package com.example.planbook.data.local

import androidx.room.Entity
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

@Entity(tableName = "review_settings")
data class ReviewSettingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notebookId: Long,
    val reviewType: String,    // DAILY, WEEKLY, MONTHLY
    val enabled: Boolean = true,
    val startTime: String,     // HH:mm
    val endTime: String        // HH:mm
)
