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

    @Query("SELECT * FROM tasks WHERE notebookId = :notebookId AND type = 'LONG_TERM' AND endDate <= :deadline ORDER BY endDate")
    suspend fun getLongTermUntil(notebookId: Long, deadline: String): List<TaskEntity>

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
