package com.example.planbook.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY createdAt")
    fun getAll(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE isCurrent = 1 LIMIT 1")
    fun getCurrent(): Flow<NotebookEntity?>

    @Query("SELECT * FROM notebooks WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentOnce(): NotebookEntity?

    @Insert
    suspend fun insert(notebook: NotebookEntity): Long

    @Update
    suspend fun update(notebook: NotebookEntity)

    @Query("UPDATE notebooks SET isCurrent = 0")
    suspend fun clearCurrent()

    @Query("UPDATE notebooks SET isCurrent = 1 WHERE id = :notebookId")
    suspend fun setCurrent(notebookId: Long)

    @Delete
    suspend fun delete(notebook: NotebookEntity)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId ORDER BY startDate, startTime")
    fun getByNotebook(notebookId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND startDate <= :date AND endDate >= :date ORDER BY startTime")
    suspend fun getForDate(notebookId: Long, date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId")
    suspend fun getAllOnce(notebookId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND isCompleted = 1 AND startDate <= :date AND endDate >= :date ORDER BY completedAt")
    suspend fun getCompletedForDate(notebookId: Long, date: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND type = 'LONG_TERM' AND endDate >= :today ORDER BY endDate")
    suspend fun getLongTermActive(notebookId: Long, today: String): List<TaskEntity>

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

    @Query("DELETE FROM task_completions WHERE taskId = :taskId AND date = :date")
    suspend fun delete(taskId: Long, date: String)
}
