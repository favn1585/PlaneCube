package com.plane.cube.domain.repository

import com.plane.cube.domain.entity.TrackingPreferences
import kotlinx.coroutines.flow.Flow

interface TrackingPreferencesRepository {

    fun observePreferences(): Flow<TrackingPreferences?>

    suspend fun savePreferences(preferences: TrackingPreferences)

    suspend fun clear()
}
