package com.plane.cube.features.map

import androidx.annotation.StringRes
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint
import com.plane.cube.domain.entity.Plane
import com.plane.cube.domain.entity.TrackingPreferences

data class MapViewState(
    val hasLocationPermission: Boolean = false,
    val userLocation: GeoPoint? = null,
    val preferences: TrackingPreferences? = null,
    val planes: List<Plane> = emptyList(),
    @param:StringRes val errorMessage: Int? = null,
    val edit: EditState = EditState(),
    /** The lat/lng bbox the user is currently looking at on the map. */
    val visibleArea: Area? = null,
)

data class EditState(
    val active: Boolean = false,
    val firstCorner: GeoPoint? = null,
    val area: Area? = null,
    val maxAltitudeMeters: Float = DEFAULT_ALTITUDE_M,
    val warningDistanceMeters: Float = DEFAULT_WARNING_DISTANCE_M,
    val saving: Boolean = false,
    @param:StringRes val errorMessage: Int? = null,
) {
    val canSave: Boolean get() = area != null

    companion object {
        const val MIN_ALTITUDE_M = 0f
        const val MAX_ALTITUDE_M = 2000f
        const val DEFAULT_ALTITUDE_M = 500f

        /** Spacing of the altitude slider's detents. */
        const val ALTITUDE_STEP_M = 500f

        const val MIN_WARNING_DISTANCE_M = 500f
        const val MAX_WARNING_DISTANCE_M = 5_000f
        const val WARNING_DISTANCE_STEP_M = 500f
        val DEFAULT_WARNING_DISTANCE_M =
            TrackingPreferences.DEFAULT_WARNING_DISTANCE_M.toFloat()
    }
}

sealed class MapUiIntent {
    data object PermissionGranted : MapUiIntent()
    data object RefreshNow : MapUiIntent()
    data object StartEditing : MapUiIntent()
    data object CancelEditing : MapUiIntent()
    data object ResetDraftCorners : MapUiIntent()
    data object SaveDraft : MapUiIntent()
    data object ClearPreferences : MapUiIntent()
    data class TapFirstCorner(val point: GeoPoint) : MapUiIntent()
    data class CompleteArea(val area: Area) : MapUiIntent()
    data class DraftAltitudeChange(val meters: Float) : MapUiIntent()
    data class DraftWarningDistanceChange(val meters: Float) : MapUiIntent()
    /** Emitted by the screen when the camera has been idle for the debounce window. */
    data class UpdateVisibleArea(val area: Area) : MapUiIntent()
    /** The user started or stopped panning/zooming/rotating the map. */
    data class CameraMovingChanged(val moving: Boolean) : MapUiIntent()
    /** The map screen started (true) or stopped (false) being visible. */
    data class ScreenVisibleChanged(val visible: Boolean) : MapUiIntent()
}
