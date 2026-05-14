package com.plane.cube.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

private const val DATASTORE_NAME = "plane_cube_tracking_prefs"

val Context.trackingDataStore by preferencesDataStore(name = DATASTORE_NAME)

object TrackingKeys {
    val South = doublePreferencesKey("area_south")
    val West = doublePreferencesKey("area_west")
    val North = doublePreferencesKey("area_north")
    val East = doublePreferencesKey("area_east")
    val MaxAltitude = doublePreferencesKey("max_altitude")
}

internal fun Preferences.hasArea(): Boolean =
    contains(TrackingKeys.South) &&
            contains(TrackingKeys.West) &&
            contains(TrackingKeys.North) &&
            contains(TrackingKeys.East) &&
            contains(TrackingKeys.MaxAltitude)

internal suspend fun androidx.datastore.core.DataStore<Preferences>.clearAllTracking() {
    edit { it.clear() }
}
