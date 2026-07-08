package com.example.planbook.data

import com.example.planbook.data.local.*
import com.example.planbook.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlanBookRepository @Inject constructor(
    private val db: PlanBookDatabase
) {
    // region Notebook
    fun getAllNotebooks(): Flow<List<Notebook>> =
        db.notebookDao().getAll().map { list -> list.map { it.toModel() } }

    fun getCurrentNotebook(): Flow<Notebook?> =
        db.notebookDao().getCurrent().map { it?.toModel() }

    suspend fun createNotebook(name: String): Long {
        db.notebookDao().clearCurrent()
        val id = db.notebookDao().insert(NotebookEntity(name = name, isCurrent = true))
        createDefaultReviewSettings(id)
        return id
    }

    suspend fun switchCurrentNotebook(notebookId: Long) {
        db.notebookDao().clearCurrent()
        db.notebookDao().setCurrent(notebookId)
    }

    suspend fun renameNotebook(notebook: Notebook) {
        db.notebookDao().update(notebook.toEntity())
    }

    suspend fun deleteNotebook(notebook: Notebook) {
        db.notebookDao().delete(notebook.toEntity())
    }
    // endregion

    // region Task
    fun getTasksByNotebook(notebookId: Long): Flow<List<Task>> =
        db.taskDao().getByNotebook(notebookId).map { list -> list.map { it.toModel() } }

    suspend fun addTask(task: Task) = db.taskDao().insert(task.toEntity())

    suspend fun updateTask(task: Task) = db.taskDao().update(task.toEntity())

    suspend fun deleteTask(task: Task) = db.taskDao().delete(task.toEntity())

    suspend fun toggleTaskComplete(task: Task) {
        val updated = task.copy(
            isCompleted = !task.isCompleted,
            completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
        )
        db.taskDao().update(updated.toEntity())
    }

    suspend fun getTasksForDate(notebookId: Long, date: String): List<Task> =
        db.taskDao().getForDate(notebookId, date).map { it.toModel() }

    suspend fun getExpandedTasksForWeek(notebookId: Long, weekStart: LocalDate): List<Task> {
        val allTasks = db.taskDao().getByNotebook(notebookId).first()
        val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
        return allTasks.flatMap { expandTask(it.toModel(), weekDays) }
    }

    private fun expandTask(task: Task, weekDays: List<LocalDate>): List<Task> {
        return when (task.type) {
            TaskType.DAILY -> {
                weekDays.filter { date ->
                    val start = LocalDate.parse(task.startDate)
                    val end = LocalDate.parse(task.endDate)
                    date in start..end && when (task.repeatRule) {
                        RepeatRule.EVERY_DAY -> true
                        RepeatRule.WEEKDAYS -> {
                            val days = task.weeklyDays?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                            date.dayOfWeek.value in days
                        }
                        null -> false
                    }
                }.map { date ->
                    task.copy(
                        startDate = date.toString(),
                        endDate = date.toString()
                    )
                }
            }
            else -> listOf(task)
        }
    }
    // endregion

    // region Review
    suspend fun getReview(notebookId: Long, date: String): Review? =
        db.reviewDao().getByDate(notebookId, date)?.toModel()

    fun getReviewsByNotebook(notebookId: Long): Flow<List<Review>> =
        db.reviewDao().getAllByNotebook(notebookId).map { list -> list.map { it.toModel() } }

    suspend fun saveReview(review: Review) {
        val existing = db.reviewDao().getByDate(review.notebookId, review.date)
        if (existing == null) {
            db.reviewDao().insert(review.toEntity())
        } else {
            db.reviewDao().update(review.copy(id = existing.id).toEntity())
        }
    }
    // endregion

    // region Review Settings
    fun getReviewSettings(notebookId: Long): Flow<List<ReviewSetting>> =
        db.reviewSettingDao().getByNotebook(notebookId).map { list -> list.map { it.toModel() } }

    suspend fun saveReviewSetting(setting: ReviewSetting) {
        db.reviewSettingDao().insert(setting.toEntity())
    }

    private suspend fun createDefaultReviewSettings(notebookId: Long) {
        ReviewType.entries.forEach { type ->
            db.reviewSettingDao().insert(
                ReviewSettingEntity(
                    notebookId = notebookId,
                    reviewType = type.name,
                    enabled = true,
                    startTime = "21:00",
                    endTime = "21:30"
                )
            )
        }
    }
    // endregion

    // region Auto review tasks generation
    suspend fun ensureAutoReviewTasks(notebookId: Long, weekStart: LocalDate) {
        val settings = db.reviewSettingDao().getByNotebook(notebookId).first()
        val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }

        settings.forEach { setting ->
            val targetDate = when (setting.reviewType) {
                ReviewType.DAILY -> null
                ReviewType.WEEKLY -> weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                ReviewType.MONTHLY -> weekStart.with(TemporalAdjusters.lastDayOfMonth())
            }

            val datesToCreate = if (targetDate != null) listOf(targetDate) else weekDays

            datesToCreate.forEach { date ->
                val title = when (setting.reviewType) {
                    ReviewType.DAILY -> "📝 每日复盘"
                    ReviewType.WEEKLY -> "📝 每周复盘"
                    ReviewType.MONTHLY -> "📝 每月复盘"
                }
                val existing = db.taskDao().getForDate(notebookId, date.toString())
                    .any { it.isAutoReview && it.reviewType == setting.reviewType.name }
                if (!existing && setting.enabled) {
                    db.taskDao().insert(
                        TaskEntity(
                            notebookId = notebookId,
                            title = title,
                            type = TaskType.ONE_OFF.name,
                            startDate = date.toString(),
                            endDate = date.toString(),
                            startTime = setting.startTime,
                            endTime = setting.endTime,
                            isAutoReview = true,
                            reviewType = setting.reviewType.name
                        )
                    )
                }
            }
        }
    }
    // endregion
}

// Mapper functions
private fun NotebookEntity.toModel() = Notebook(id, name, isCurrent, createdAt)
private fun Notebook.toEntity() = NotebookEntity(id, name, isCurrent, createdAt)

private fun TaskEntity.toModel() = Task(
    id = id,
    notebookId = notebookId,
    title = title,
    description = description,
    type = TaskType.valueOf(type),
    startDate = startDate,
    endDate = endDate,
    startTime = startTime,
    endTime = endTime,
    repeatRule = repeatRule?.let { RepeatRule.valueOf(it) },
    weeklyDays = weeklyDays,
    isCompleted = isCompleted,
    completedAt = completedAt,
    isAutoReview = isAutoReview,
    reviewType = reviewType?.let { ReviewType.valueOf(it) },
    createdAt = createdAt
)

private fun Task.toEntity() = TaskEntity(
    id = id,
    notebookId = notebookId,
    title = title,
    description = description,
    type = type.name,
    startDate = startDate,
    endDate = endDate,
    startTime = startTime,
    endTime = endTime,
    repeatRule = repeatRule?.name,
    weeklyDays = weeklyDays,
    isCompleted = isCompleted,
    completedAt = completedAt,
    isAutoReview = isAutoReview,
    reviewType = reviewType?.name,
    createdAt = createdAt
)

private fun ReviewEntity.toModel() = Review(id, notebookId, date, content, updatedAt)
private fun Review.toEntity() = ReviewEntity(id, notebookId, date, content, updatedAt)

private fun ReviewSettingEntity.toModel() = ReviewSetting(
    id = id,
    notebookId = notebookId,
    reviewType = ReviewType.valueOf(reviewType),
    enabled = enabled,
    startTime = startTime,
    endTime = endTime
)

private fun ReviewSetting.toEntity() = ReviewSettingEntity(
    id = id,
    notebookId = notebookId,
    reviewType = reviewType.name,
    enabled = enabled,
    startTime = startTime,
    endTime = endTime
)
