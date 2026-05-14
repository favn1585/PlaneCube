package com.plane.cube.local.di

import android.content.Context
import androidx.room.Room
import com.plane.cube.local.PlaneCubeDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    private const val DATABASE_NAME = "plane_cube_database.db"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PlaneCubeDatabase =
        Room.databaseBuilder(context, PlaneCubeDatabase::class.java, DATABASE_NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
}
