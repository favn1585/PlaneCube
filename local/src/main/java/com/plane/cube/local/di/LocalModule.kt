package com.plane.cube.local.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import com.plane.cube.local.datastore.trackingDataStore
import com.plane.cube.local.repository.TrackingPreferencesLocalRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LocalBindingsModule {

    @Binds
    @Singleton
    abstract fun bindTrackingPreferencesRepository(
        impl: TrackingPreferencesLocalRepository,
    ): TrackingPreferencesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.trackingDataStore
}
