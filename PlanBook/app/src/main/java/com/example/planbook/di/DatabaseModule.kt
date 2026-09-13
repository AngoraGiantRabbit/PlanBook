package com.example.planbook.di

import android.content.Context
import androidx.room.Room
import com.example.planbook.data.local.PlanBookDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlanBookDatabase {
        return Room.databaseBuilder(
            context,
            PlanBookDatabase::class.java,
            "planbook.db"
        )
            // v3→v4 主/子计划本迁移、v4→v5 导入标记列、v5→v6 任务教室列；更早版本仍走破坏性重建
            .addMigrations(
                PlanBookDatabase.MIGRATION_3_4,
                PlanBookDatabase.MIGRATION_4_5,
                PlanBookDatabase.MIGRATION_5_6
            )
            .fallbackToDestructiveMigration()
            .build()
    }
}
