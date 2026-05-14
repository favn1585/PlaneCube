package com.plane.cube.di

import com.plane.cube.domain.TrackingScheduler
import com.plane.cube.tracking.WorkManagerTrackingScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTrackingScheduler(impl: WorkManagerTrackingScheduler): TrackingScheduler
}
