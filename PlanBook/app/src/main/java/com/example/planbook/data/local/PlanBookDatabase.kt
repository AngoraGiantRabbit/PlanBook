package com.example.planbook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotebookEntity::class,
        TaskEntity::class,
        ReviewEntity::class,
        ReviewSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PlanBookDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun taskDao(): TaskDao
    abstract fun reviewDao(): ReviewDao
    abstract fun reviewSettingDao(): ReviewSettingDao
}
