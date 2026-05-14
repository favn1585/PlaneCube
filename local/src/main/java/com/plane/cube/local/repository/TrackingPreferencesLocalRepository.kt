package com.plane.cube.local.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.TrackingPreferences
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import com.plane.cube.local.datastore.TrackingKeys
import com.plane.cube.local.datastore.hasArea
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class TrackingPreferencesLocalRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TrackingPreferencesRepository {

    override fun observePreferences(): Flow<TrackingPreferences?> =
        dataStore.data.map { prefs ->
            if (!prefs.hasArea()) return@map null
            TrackingPreferences(
                area = Area(
                    south = prefs[TrackingKeys.South] ?: return@map null,
                    west = prefs[TrackingKeys.West] ?: return@map null,
                    north = prefs[TrackingKeys.North] ?: return@map null,
                    east = prefs[TrackingKeys.East] ?: return@map null,
                ),
                maxAltitudeMeters = prefs[TrackingKeys.MaxAltitude] ?: return@map null,
            )
        }

    override suspend fun savePreferences(preferences: TrackingPreferences) {
        dataStore.edit { prefs ->
            prefs[TrackingKeys.South] = preferences.area.south
            prefs[TrackingKeys.West] = preferences.area.west
            prefs[TrackingKeys.North] = preferences.area.north
            prefs[TrackingKeys.East] = preferences.area.east
            prefs[TrackingKeys.MaxAltitude] = preferences.maxAltitudeMeters
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
