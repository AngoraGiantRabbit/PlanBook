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

    /**
     * 合并两个计划本（PRD 4.1.4）。
     * 流程：
     * 1. 先调 [detectMergeConflicts] 取得带时段任务的冲突列表。
     * 2. UI 逐条让用户选择。
     * 3. 再调 [executeMerge] 完成合并。
     * 原两个计划本保留不变。
     */

    /** 检测两个计划本之间带时段任务的冲突。返回冲突任务对。 */
    suspend fun detectMergeConflicts(
        notebookA: Long,
        notebookB: Long
    ): List<MergeConflict> {
        val timedA = db.taskDao().getAllOnce(notebookA)
            .map { it.toModel() }
            .filter { it.startTime != null }
        val timedB = db.taskDao().getAllOnce(notebookB)
            .map { it.toModel() }
            .filter { it.startTime != null }

        val conflicts = mutableListOf<MergeConflict>()
        for (a in timedA) for (b in timedB) {
            if (tasksOverlap(a, b)) {
                conflicts.add(MergeConflict(a, b))
            }
        }
        return conflicts
    }

    private fun tasksOverlap(a: Task, b: Task): Boolean {
        val aStart = LocalDate.parse(a.startDate)
        val aEnd = LocalDate.parse(a.endDate)
        val bStart = LocalDate.parse(b.startDate)
        val bEnd = LocalDate.parse(b.endDate)
        // 日期无交集
        if (aEnd < bStart || bEnd < aStart) return false
        // 有交集的日期里，时段是否重叠
        val overlapStart = maxOf(aStart, bStart)
        val overlapEnd = minOf(aEnd, bEnd)
        var d = overlapStart
        while (d <= overlapEnd) {
            val aTime = timeOverlapOnDate(a, d)
            val bTime = timeOverlapOnDate(b, d)
            if (aTime != null && bTime != null && aTime.first < bTime.second && bTime.first < aTime.second) {
                return true
            }
            d = d.plusDays(1)
        }
        return false
    }

    private fun timeOverlapOnDate(task: Task, date: LocalDate): Pair<Int, Int>? {
        val s = task.startTime ?: return null
        val e = task.endTime ?: task.startTime
        return minutes(s) to minutes(e)
    }

    private fun minutes(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return h * 60 + m
    }

    /**
     * 执行合并。
     * @param resolutions 冲突任务的处理结果：任务 id -> KEEP_A / KEEP_B / DROP
     */
    suspend fun executeMerge(
        notebookA: Long,
        notebookB: Long,
        newName: String,
        resolutions: Map<Long, MergeResolution>
    ): Long {
        // 新建计划本并设为当前
        db.notebookDao().clearCurrent()
        val newId = db.notebookDao().insert(NotebookEntity(name = newName, isCurrent = true))
        createDefaultReviewSettings(newId)

        val tasksA = db.taskDao().getAllOnce(notebookA).map { it.toModel() }
        val tasksB = db.taskDao().getAllOnce(notebookB).map { it.toModel() }

        // 冲突任务只保留被选中的一方；未涉及冲突任务全部复制
        val conflictIds = resolutions.keys

        tasksA.forEach { task ->
            val decision = resolutions[task.id]
            val shouldCopy = when {
                task.id !in conflictIds -> true
                decision == MergeResolution.KEEP_A -> true
                else -> false
            }
            if (shouldCopy) {
                db.taskDao().insert(task.copy(id = 0, notebookId = newId).toEntity())
            }
        }
        tasksB.forEach { task ->
            val decision = resolutions[task.id]
            val shouldCopy = when {
                task.id !in conflictIds -> true
                decision == MergeResolution.KEEP_B -> true
                else -> false
            }
            if (shouldCopy) {
                db.taskDao().insert(task.copy(id = 0, notebookId = newId).toEntity())
            }
        }
        return newId
    }
    // endregion

    // region Task
    fun getTasksByNotebook(notebookId: Long): Flow<List<Task>> =
        db.taskDao().getByNotebook(notebookId).map { list -> list.map { it.toModel() } }

    suspend fun addTask(task: Task) = db.taskDao().insert(task.toEntity())

    suspend fun getTaskById(taskId: Long): Task? = db.taskDao().getById(taskId)?.toModel()

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

    /** 待办列表页：某一天该显示的所有任务（四类展开后落在该天的，加上长期任务） */
    suspend fun getTasksForDay(notebookId: Long, date: LocalDate): List<Task> {
        val allTasks = db.taskDao().getByNotebook(notebookId).first().map { it.toModel() }
        val dayList = listOf(date)
        return allTasks.flatMap { expandTask(it, dayList) }
    }

    /** 复盘页：当日已完成任务（PRD 4.4.4） */
    suspend fun getCompletedTasksForDate(notebookId: Long, date: String): List<Task> =
        db.taskDao().getCompletedForDate(notebookId, date).map { it.toModel() }

    /** DDL 未过的长期任务（endDate >= today），按 DDL 升序（PRD 4.4.4） */
    suspend fun getLongTermTasksActive(notebookId: Long, today: String): List<Task> =
        db.taskDao().getLongTermActive(notebookId, today).map { it.toModel() }

    private fun expandTask(task: Task, weekDays: List<LocalDate>): List<Task> {
        // 长期任务只有 DDL，不参与按周过滤，始终单独返回（底部长期待办栏显示）
        if (task.type == TaskType.LONG_TERM) {
            return listOf(task)
        }
        val start = LocalDate.parse(task.startDate)
        val end = LocalDate.parse(task.endDate)
        return when (task.type) {
            TaskType.DAILY -> {
                weekDays.filter { date ->
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
            // 跨天任务在周内每一天显示（PRD 4.2.1：跨天的任务跨格显示）
            else -> {
                weekDays.filter { it in start..end }.map { date ->
                    task.copy(
                        startDate = date.toString(),
                        endDate = date.toString()
                    )
                }
            }
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
        val settings = getReviewSettings(notebookId).first()
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
