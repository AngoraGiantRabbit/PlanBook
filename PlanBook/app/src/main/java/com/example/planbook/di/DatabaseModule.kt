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
        ).build()
    }
}
