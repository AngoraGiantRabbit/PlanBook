package com.example.planbook.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    /** 主计划本（parentId 为 null，全局唯一） */
    @Query("SELECT * FROM notebooks WHERE parentId IS NULL LIMIT 1")
    fun getMaster(): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE parentId IS NULL LIMIT 1")
    suspend fun getMasterOnce(): NotebookEntity?

    /** 某主计划本下的全部子计划本（含隐藏的），按创建序 */
    @Query("SELECT * FROM notebooks WHERE parentId = :masterId ORDER BY createdAt")
    fun getSubNotebooks(masterId: Long): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE parentId = :masterId ORDER BY createdAt")
    suspend fun getSubNotebooksOnce(masterId: Long): List<NotebookEntity>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId LIMIT 1")
    suspend fun getById(notebookId: Long): NotebookEntity?

    /** #12：按源文件名找已有导入子计划本（重导整本重建的匹配依据） */
    @Query("SELECT * FROM notebooks WHERE parentId = :masterId AND importSource = :sourceName LIMIT 1")
    suspend fun getImportedBySource(masterId: Long, sourceName: String): NotebookEntity?

    @Insert
    suspend fun insert(notebook: NotebookEntity): Long

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Query("UPDATE notebooks SET isActive = 0 WHERE parentId = :masterId")
    suspend fun clearActive(masterId: Long)

    @Query("UPDATE notebooks SET isActive = 1 WHERE id = :subNotebookId")
    suspend fun setActive(subNotebookId: Long)

    @Query("UPDATE notebooks SET isVisible = :visible WHERE id = :subNotebookId")
    suspend fun setVisible(subNotebookId: Long, visible: Boolean)

    @Query("DELETE FROM notebooks WHERE id = :notebookId")
    suspend fun deleteById(notebookId: Long)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId ORDER BY startDate, startTime")
    fun getByNotebook(notebookId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND startDate <= :date AND endDate >= :date ORDER BY startTime")
    suspend fun getForDate(notebookId: Long, date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId")
    suspend fun getAllOnce(notebookId: Long): List<TaskEntity>

    /** #7：多个子计划本（含主计划本 id，用于带上自动复盘待办）的任务合并查询 */
    @Query("SELECT * FROM tasks WHERE notebookId IN (:notebookIds) ORDER BY startDate, startTime")
    suspend fun getByNotebookIdsOnce(notebookIds: List<Long>): List<TaskEntity>

    /** 三页联动：响应式版本——tasks 表任何写操作（含跨页面勾选/增删）自动重发 */
    @Query("SELECT * FROM tasks WHERE notebookId IN (:notebookIds) ORDER BY startDate, startTime")
    fun getByNotebookIds(notebookIds: List<Long>): Flow<List<TaskEntity>>

    /** 三页联动：长期任务（活跃区间）响应式版本 */
    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND type = 'LONG_TERM' AND isCompleted = 0 AND startDate <= :today AND endDate >= :today ORDER BY endDate")
    fun getLongTermActiveFlow(notebookId: Long, today: String): Flow<List<TaskEntity>>

    /** #7：删除子计划本时一并删除其任务 */
    @Query("DELETE FROM tasks WHERE notebookId = :notebookId")
    suspend fun deleteByNotebook(notebookId: Long)

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND isCompleted = 1 AND startDate <= :date AND endDate >= :date ORDER BY completedAt")
    suspend fun getCompletedForDate(notebookId: Long, date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND type = 'LONG_TERM' AND isCompleted = 0 AND startDate <= :today AND endDate >= :today ORDER BY endDate")
    suspend fun getLongTermActive(notebookId: Long, today: String): List<TaskEntity>

    /**
     * ADR-0003：长期任务过 DDL 即视为放弃/过期，自动标记完成并从所有视图消失。
     * 惰性触发——由 Repository 在查询入口处调用，无需后台任务。
     */
    @Query("UPDATE tasks SET isCompleted = 1, completedAt = :now WHERE type = 'LONG_TERM' AND isCompleted = 0 AND endDate < :today")
    suspend fun expireLongTermTasks(today: String, now: Long)

    @Query("SELECT * FROM tasks WHERE id = :taskId LIMIT 1")
    suspend fun getById(taskId: Long): TaskEntity?

    /** 精确查重：某计划本在某天是否已有某类型的自动复盘任务 */
    @Query("SELECT EXISTS(SELECT 1 FROM tasks WHERE notebookId = :notebookId AND isAutoReview = 1 AND reviewType = :reviewType AND startDate = :date)")
    suspend fun hasAutoReview(notebookId: Long, reviewType: String, date: String): Boolean

    /** 查询重复的自动复盘任务（同 notebookId+reviewType+startDate 超过1条的），返回需要删除的 id */
    @Query("SELECT id FROM tasks WHERE isAutoReview = 1 AND id NOT IN (SELECT MIN(id) FROM tasks WHERE isAutoReview = 1 GROUP BY notebookId, reviewType, startDate)")
    suspend fun getDuplicateAutoReviewIds(): List<Long>

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: Long)

    /** #6：关闭某类复盘开关时，删除该类型已生成但未完成的自动待办（已完成保留作历史） */
    @Query("DELETE FROM tasks WHERE notebookId = :notebookId AND isAutoReview = 1 AND reviewType = :reviewType AND isCompleted = 0")
    suspend fun deleteIncompleteAutoReviews(notebookId: Long, reviewType: String)

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE notebookId = :notebookId AND date = :date LIMIT 1")
    suspend fun getByDate(notebookId: Long, date: String): ReviewEntity?

    @Query("SELECT * FROM reviews WHERE notebookId = :notebookId ORDER BY date")
    fun getAllByNotebook(notebookId: Long): Flow<List<ReviewEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(review: ReviewEntity)

    @Update
    suspend fun update(review: ReviewEntity)
}

@Dao
interface ReviewSettingDao {
    @Query("SELECT * FROM review_settings WHERE notebookId = :notebookId")
    fun getByNotebook(notebookId: Long): Flow<List<ReviewSettingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: ReviewSettingEntity)

    @Update
    suspend fun update(setting: ReviewSettingEntity)
}

@Dao
interface TaskCompletionDao {
    /** 某任务在某天是否已完成 */
    @Query("SELECT EXISTS(SELECT 1 FROM task_completions WHERE taskId = :taskId AND date = :date)")
    suspend fun isCompleted(taskId: Long, date: String): Boolean

    /** 批量查询某计划本在某天的所有已完成 taskId */
    @Query("SELECT taskId FROM task_completions WHERE date = :date")
    suspend fun getCompletedTaskIds(date: String): List<Long>

    /** 查询某任务所有完成的日期 */
    @Query("SELECT date FROM task_completions WHERE taskId = :taskId")
    suspend fun getDatesForTask(taskId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(completion: TaskCompletionEntity)

    /** 三页联动：指定日期集的按天完成记录（响应式，写后自动重发） */
    @Query("SELECT * FROM task_completions WHERE date IN (:dates)")
    fun getByDates(dates: List<String>): Flow<List<TaskCompletionEntity>>

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND date = :date")
    suspend fun delete(taskId: Long, date: String)
}
