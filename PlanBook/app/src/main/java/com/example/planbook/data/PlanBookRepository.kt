package com.example.planbook.data

import com.example.planbook.data.local.*
import com.example.planbook.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    // region Notebook（ADR-0004：主计划本 + 子计划本）
    /** 主计划本（全局唯一；复盘、复盘设置、自动复盘待办挂它） */
    fun getMasterNotebook(): Flow<Notebook?> =
        db.notebookDao().getMaster().map { it?.toModel() }

    suspend fun getMasterNotebookOnce(): Notebook? =
        db.notebookDao().getMasterOnce()?.toModel()

    /** 当前主计划本下的全部子计划本（含隐藏） */
    fun getSubNotebooksOfMaster(): Flow<List<Notebook>> =
        db.notebookDao().getMaster().flatMapLatest { master ->
            if (master == null) flowOf(emptyList())
            else db.notebookDao().getSubNotebooks(master.id).map { list -> list.map { it.toModel() } }
        }

    /** 活动子计划本（写操作目标，同一时刻仅一个） */
    fun getActiveSubOfMaster(): Flow<Notebook?> =
        db.notebookDao().getMaster().flatMapLatest { master ->
            if (master == null) flowOf(null)
            else db.notebookDao().getSubNotebooks(master.id).map { list ->
                list.firstOrNull { it.isActive }?.toModel()
            }
        }

    /**
     * 首次启动引导：创建主计划本（带默认复盘设置）+ 第一个子计划本（活动）。
     * 仅在主计划本不存在时由 UI 空态触发。
     */
    suspend fun bootstrapNotebook(firstSubName: String): Long {
        val masterId = db.notebookDao().insert(NotebookEntity(name = "主计划本"))
        createDefaultReviewSettings(masterId)
        createSubNotebook(masterId, firstSubName)
        return masterId
    }

    /** 新建子计划本：调色板按现有数量轮转分配颜色；若当前无活动子本则设为活动 */
    suspend fun createSubNotebook(masterId: Long, name: String): Long {
        val existing = db.notebookDao().getSubNotebooksOnce(masterId)
        return db.notebookDao().insert(
            NotebookEntity(
                name = name,
                parentId = masterId,
                color = SubNotebookPalette.forIndex(existing.size),
                isVisible = true,
                isActive = existing.none { it.isActive }
            )
        )
    }

    /** 切换活动子计划本（写操作目标） */
    suspend fun setActiveSubNotebook(subNotebookId: Long) {
        val sub = db.notebookDao().getById(subNotebookId) ?: return
        val masterId = sub.parentId ?: return
        db.notebookDao().clearActive(masterId)
        db.notebookDao().setActive(subNotebookId)
    }

    /** 显示开关：只影响计划本页聚合显示，不动数据 */
    suspend fun setSubNotebookVisible(subNotebookId: Long, visible: Boolean) =
        db.notebookDao().setVisible(subNotebookId, visible)

    suspend fun renameNotebook(notebook: Notebook) {
        db.notebookDao().update(notebook.toEntity())
    }

    /** 删除子计划本（含其任务）；若删的是活动子本，把最早的剩余子本设为活动 */
    suspend fun deleteNotebook(notebook: Notebook) {
        db.taskDao().deleteByNotebook(notebook.id)
        db.notebookDao().deleteById(notebook.id)
        val masterId = notebook.parentId ?: return
        val remaining = db.notebookDao().getSubNotebooksOnce(masterId)
        if (notebook.isActive && remaining.isNotEmpty()) {
            db.notebookDao().clearActive(masterId)
            db.notebookDao().setActive(remaining.first().id)
        }
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
        // 合并产物作为主计划本下的新子计划本，并设为活动（ADR-0004）
        val master = db.notebookDao().getMasterOnce()
        val newId = db.notebookDao().insert(
            NotebookEntity(
                name = newName,
                parentId = master?.id,
                color = SubNotebookPalette.forIndex(
                    master?.let { db.notebookDao().getSubNotebooksOnce(it.id).size } ?: 0
                ),
                isVisible = true,
                isActive = master != null
            )
        )
        if (master != null) {
            db.notebookDao().clearActive(master.id)
            db.notebookDao().setActive(newId)
        }

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
    suspend fun addTask(task: Task) = db.taskDao().insert(task.toEntity())

    suspend fun getTaskById(taskId: Long): Task? = db.taskDao().getById(taskId)?.toModel()

    suspend fun updateTask(task: Task) = db.taskDao().update(task.toEntity())

    suspend fun deleteTask(task: Task) = db.taskDao().delete(task.toEntity())

    /**
     * 切换某任务的完成状态（ADR-0001 完成态双模型）。
     * - DAILY：按天记录到 task_completions（每天独立）
     * - 其它（ONE_OFF / FLEX / LONG_TERM）：整体完成，用全局 isCompleted
     * 分流只看 type，不看「是否跨多天」。
     */
    suspend fun toggleTaskComplete(task: Task, date: String) {
        if (task.type == TaskType.DAILY) {
            val already = db.taskCompletionDao().isCompleted(task.id, date)
            if (already) {
                db.taskCompletionDao().delete(task.id, date)
            } else {
                db.taskCompletionDao().insert(
                    TaskCompletionEntity(taskId = task.id, date = date)
                )
            }
        } else {
            val updated = task.copy(
                isCompleted = !task.isCompleted,
                completedAt = if (!task.isCompleted) System.currentTimeMillis() else null
            )
            db.taskDao().update(updated.toEntity())
        }
    }

    /**
     * 周视图聚合数据（#7，ADR-0004）：子计划本任务 + 主计划本的自动复盘待办。
     * @param masterId 主计划本 id（复盘待办挂它）
     * @param subNotebookIds 参与聚合的子计划本 id 列表
     */
    suspend fun getExpandedTasksForWeek(
        masterId: Long,
        subNotebookIds: List<Long>,
        weekStart: LocalDate
    ): List<Task> {
        expireLongTermTasks()
        ensureSubNotebookColors(masterId)
        val allTasks = db.taskDao().getByNotebookIdsOnce(subNotebookIds + masterId).map { it.toModel() }
        val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
        // 预取本周所有完成记录，避免逐条查询
        val completedMap = mutableMapOf<String, MutableSet<Long>>()
        weekDays.forEach { d ->
            completedMap[d.toString()] = db.taskCompletionDao().getCompletedTaskIds(d.toString()).toMutableSet()
        }
        return allTasks.flatMap { expandTaskWithCompletion(it, weekDays, completedMap) }
    }

    /** 待办列表页：某一天该显示的所有任务（四类展开后落在该天的，加上长期任务） */
    suspend fun getTasksForDay(masterId: Long, subNotebookIds: List<Long>, date: LocalDate): List<Task> {
        // 确保复盘自动待办已生成（与课表页保持一致，问题6）
        val weekStart = date.with(java.time.DayOfWeek.MONDAY)
        ensureAutoReviewTasks(masterId, weekStart)
        expireLongTermTasks()
        val allTasks = db.taskDao().getByNotebookIdsOnce(subNotebookIds + masterId).map { it.toModel() }
        val dayList = listOf(date)
        val completedIds = db.taskCompletionDao().getCompletedTaskIds(date.toString()).toSet()
        val completedMap = mapOf(date.toString() to completedIds.toMutableSet())
        return allTasks.flatMap { expandTaskWithCompletion(it, dayList, completedMap) }
    }

    /** 复盘页：当日已完成任务（PRD 4.4.4）——含按天完成记录的 */
    suspend fun getCompletedTasksForDate(masterId: Long, subNotebookIds: List<Long>, date: String): List<Task> {
        expireLongTermTasks()
        val completedIds = db.taskCompletionDao().getCompletedTaskIds(date).toSet()
        val allTasks = db.taskDao().getByNotebookIdsOnce(subNotebookIds + masterId).map { it.toModel() }
        val day = LocalDate.parse(date)
        // 该天会显示的任务中，已完成的
        val dayTasks = allTasks.flatMap { expandTaskWithCompletion(it, listOf(day), mapOf(date to completedIds)) }
        return dayTasks.filter { it.isCompleted }
    }

    /** 活跃长期任务：今天落在开始日到 DDL 之间，按 DDL 升序（ADR-0002 / PRD 4.4.4） */
    suspend fun getLongTermTasksActive(notebookId: Long, today: String): List<Task> {
        expireLongTermTasks()
        return db.taskDao().getLongTermActive(notebookId, today).map { it.toModel() }
    }

    /**
     * ADR-0003：长期任务过 DDL 自动标记完成。惰性触发——在各查询入口处调用，
     * 避免引入后台定时任务（鸿蒙/卓易通环境下不可靠且耗电）。
     */
    private suspend fun expireLongTermTasks() {
        db.taskDao().expireLongTermTasks(LocalDate.now().toString(), System.currentTimeMillis())
    }

    /**
     * #9：旧版迁移来的子计划本没有颜色，按创建序从调色板轮转补齐（幂等，补完即空转）。
     */
    private suspend fun ensureSubNotebookColors(masterId: Long) {
        val subs = db.notebookDao().getSubNotebooksOnce(masterId)
        subs.forEachIndexed { index, sub ->
            if (sub.color == null) {
                db.notebookDao().update(sub.copy(color = SubNotebookPalette.forIndex(index)))
            }
        }
    }

    private fun expandTask(task: Task, weekDays: List<LocalDate>): List<Task> {
        // 长期任务不参与按天展开：活跃区间由 UI 按 selectedDate ∈ [startDate..DDL] 过滤，
        // 这里始终单独返回原任务（ADR-0002）。
        if (task.type == TaskType.LONG_TERM) {
            return listOf(task)
        }
        val start = LocalDate.parse(task.startDate)
        val end = LocalDate.parse(task.endDate)
        return when (task.type) {
            TaskType.DAILY -> {
                // 每日任务：按重复规则在 [start..end] 区间内逐天展开，每天独立 copy
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
                    task.copy(startDate = date.toString(), endDate = date.toString())
                }
            }
            // 灵活/临时任务恒为单天（ADR-0002）：仅当查询日命中任务日期才返回，
            // 避免其它天的单天任务（如每日复盘）漏进本日待办列表
            TaskType.ONE_OFF, TaskType.FLEX ->
                if (weekDays.any { it in start..end }) listOf(task) else emptyList()
            TaskType.LONG_TERM -> listOf(task) // 不会到达，开头已 early return
        }
    }

    /**
     * 带完成态的展开版本（ADR-0001 完成态双模型）。
     * - DAILY：按天独立完成，按 completedMap 查
     * - 其它（ONE_OFF / FLEX / LONG_TERM）：整体完成态，直接用 task.isCompleted
     * 分流只看 type。
     */
    private fun expandTaskWithCompletion(
        task: Task,
        weekDays: List<LocalDate>,
        completedMap: Map<String, Set<Long>>
    ): List<Task> {
        val expanded = expandTask(task, weekDays)
        return expanded.map { t ->
            val done = if (task.type == TaskType.DAILY) {
                completedMap[t.startDate]?.contains(task.id) == true
            } else {
                task.isCompleted
            }
            t.copy(isCompleted = done)
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

    /**
     * #6：设置某类复盘的开关。
     * 关闭时同步删除该类型已生成但未完成的自动复盘待办（已完成保留作历史），
     * 使首页立即与设置一致；重新打开后由 ensureAutoReviewTasks 按查看的周重新生成。
     */
    suspend fun setReviewEnabled(notebookId: Long, type: ReviewType, enabled: Boolean) {
        val setting = getReviewSettings(notebookId).first().firstOrNull { it.reviewType == type }
            ?: return
        saveReviewSetting(setting.copy(enabled = enabled))
        if (!enabled) {
            db.taskDao().deleteIncompleteAutoReviews(notebookId, type.name)
        }
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
        // 清理历史重复生成的复盘任务（保留每组最早一条）
        val dupIds = db.taskDao().getDuplicateAutoReviewIds()
        if (dupIds.isNotEmpty()) {
            dupIds.forEach { db.taskDao().deleteById(it) }
        }
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
                // 精确查重：notebookId + reviewType + startDate（避免重复生成）
                val existing = db.taskDao().hasAutoReview(notebookId, setting.reviewType.name, date.toString())
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
private fun NotebookEntity.toModel() =
    Notebook(id, name, parentId, color, isVisible, isActive, createdAt)

private fun Notebook.toEntity() =
    NotebookEntity(id, name, parentId, color, isVisible, isActive, createdAt)

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
