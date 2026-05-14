package com.plane.cube.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore

private const val DATASTORE_NAME = "plane_cube_tracking_prefs"

val Context.trackingDataStore by preferencesDataStore(name = DATASTORE_NAME)

object TrackingKeys {
    val Corner0Lat = doublePreferencesKey("corner0_lat")
    val Corner0Lng = doublePreferencesKey("corner0_lng")
    val Corner1Lat = doublePreferencesKey("corner1_lat")
    val Corner1Lng = doublePreferencesKey("corner1_lng")
    val Corner2Lat = doublePreferencesKey("corner2_lat")
    val Corner2Lng = doublePreferencesKey("corner2_lng")
    val Corner3Lat = doublePreferencesKey("corner3_lat")
    val Corner3Lng = doublePreferencesKey("corner3_lng")
    val MaxAltitude = doublePreferencesKey("max_altitude")
}

internal fun Preferences.hasArea(): Boolean =
    contains(TrackingKeys.Corner0Lat) &&
        contains(TrackingKeys.Corner0Lng) &&
        contains(TrackingKeys.Corner1Lat) &&
        contains(TrackingKeys.Corner1Lng) &&
        contains(TrackingKeys.Corner2Lat) &&
        contains(TrackingKeys.Corner2Lng) &&
        contains(TrackingKeys.Corner3Lat) &&
        contains(TrackingKeys.Corner3Lng) &&
        contains(TrackingKeys.MaxAltitude)

internal suspend fun androidx.datastore.core.DataStore<Preferences>.clearAllTracking() {
    edit { it.clear() }
}
