package com.plane.cube.local.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint
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
            val corners = listOf(
                GeoPoint(prefs[TrackingKeys.Corner0Lat]!!, prefs[TrackingKeys.Corner0Lng]!!),
                GeoPoint(prefs[TrackingKeys.Corner1Lat]!!, prefs[TrackingKeys.Corner1Lng]!!),
                GeoPoint(prefs[TrackingKeys.Corner2Lat]!!, prefs[TrackingKeys.Corner2Lng]!!),
                GeoPoint(prefs[TrackingKeys.Corner3Lat]!!, prefs[TrackingKeys.Corner3Lng]!!),
            )
            TrackingPreferences(
                area = Area(corners),
                maxAltitudeMeters = prefs[TrackingKeys.MaxAltitude]!!,
            )
        }

    override suspend fun savePreferences(preferences: TrackingPreferences) {
        val c = preferences.area.corners
        dataStore.edit { prefs ->
            prefs[TrackingKeys.Corner0Lat] = c[0].latitude
            prefs[TrackingKeys.Corner0Lng] = c[0].longitude
            prefs[TrackingKeys.Corner1Lat] = c[1].latitude
            prefs[TrackingKeys.Corner1Lng] = c[1].longitude
            prefs[TrackingKeys.Corner2Lat] = c[2].latitude
            prefs[TrackingKeys.Corner2Lng] = c[2].longitude
            prefs[TrackingKeys.Corner3Lat] = c[3].latitude
            prefs[TrackingKeys.Corner3Lng] = c[3].longitude
            prefs[TrackingKeys.MaxAltitude] = preferences.maxAltitudeMeters
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
