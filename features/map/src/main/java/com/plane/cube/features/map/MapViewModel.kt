package com.plane.cube.features.map

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import com.plane.cube.domain.PlaneAlerts
import com.plane.cube.domain.TrackingScheduler
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint
import com.plane.cube.domain.entity.TrackingPreferences
import com.plane.cube.domain.repository.PlaneRepository
import com.plane.cube.domain.repository.TrackingPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class MapViewModel @Inject constructor(
    private val planeRepository: PlaneRepository,
    private val trackingRepository: TrackingPreferencesRepository,
    private val locationProvider: LocationProvider,
    private val scheduler: TrackingScheduler,
    private val planeAlerts: PlaneAlerts,
) : ViewModel() {

    private val _viewState = MutableStateFlow(MapViewState())
    val viewState = _viewState.asStateFlow()

    private var tickerJob: Job? = null
    private var activeQuery: PlaneQuery? = null

    /**
     * Kept out of the view state on purpose: flipping it must not recompose
     * the map mid-gesture. While true, refreshes are skipped entirely.
     */
    private var cameraMoving = false

    /** False while the app is in the background; the ticker is paused then. */
    private var screenVisible = true

    init {
        viewModelScope.launch {
            trackingRepository.observePreferences().collectLatest { preferences ->
                _viewState.update { it.copy(preferences = preferences) }
                // Opening the app is the reliable moment to (re)start background
                // monitoring: Android won't let it start from the background.
                if (preferences != null) scheduler.schedule()
                maybeRestartTicker()
            }
        }
    }

    fun onIntent(intent: MapUiIntent) {
        when (intent) {
            MapUiIntent.PermissionGranted -> onPermissionGranted()
            MapUiIntent.RefreshNow -> refreshNow()
            MapUiIntent.StartEditing -> startEditing()
            MapUiIntent.CancelEditing -> stopEditing()
            MapUiIntent.ResetDraftCorners -> _viewState.update {
                it.copy(edit = it.edit.copy(firstCorner = null, area = null))
            }
            MapUiIntent.SaveDraft -> saveDraft()
            MapUiIntent.ClearPreferences -> clearPreferences()
            is MapUiIntent.TapFirstCorner -> _viewState.update {
                it.copy(edit = it.edit.copy(firstCorner = intent.point, area = null))
            }
            is MapUiIntent.CompleteArea -> _viewState.update {
                it.copy(edit = it.edit.copy(area = intent.area))
            }
            is MapUiIntent.DraftAltitudeChange -> _viewState.update {
                it.copy(edit = it.edit.copy(maxAltitudeMeters = intent.meters))
            }
            is MapUiIntent.DraftWarningDistanceChange -> _viewState.update {
                it.copy(edit = it.edit.copy(warningDistanceMeters = intent.meters))
            }
            is MapUiIntent.UpdateVisibleArea -> updateVisibleArea(intent.area)
            is MapUiIntent.CameraMovingChanged -> cameraMoving = intent.moving
            is MapUiIntent.ScreenVisibleChanged -> onScreenVisibleChanged(intent.visible)
        }
    }

    private fun updateVisibleArea(area: Area) {
        Log.d(
            TAG,
            "UpdateVisibleArea south=${area.south} west=${area.west} north=${area.north} east=${area.east}",
        )
        _viewState.update { it.copy(visibleArea = area) }
        maybeRestartTicker()
    }

    private fun onPermissionGranted() {
        _viewState.update { it.copy(hasLocationPermission = true) }
        viewModelScope.launch {
            val location = runCatching { locationProvider.currentLocation() }
                .onFailure { Log.w(TAG, "Location fetch failed", it) }
                .getOrNull()
            Log.d(TAG, "User location resolved: $location")
            _viewState.update { it.copy(userLocation = location) }
            if (location == null) {
                _viewState.update {
                    it.copy(errorMessage = R.string.map_error_location_unavailable)
                }
            }
            maybeRestartTicker()
        }
    }

    private fun startEditing() {
        _viewState.update { state ->
            val seed = state.preferences
            val draft = if (seed != null) {
                EditState(
                    active = true,
                    firstCorner = null,
                    area = seed.area,
                    maxAltitudeMeters = seed.maxAltitudeMeters.toFloat(),
                    warningDistanceMeters = seed.warningDistanceMeters.toFloat(),
                )
            } else {
                EditState(active = true)
            }
            state.copy(edit = draft)
        }
        stopTicker()
    }

    private fun stopEditing() {
        _viewState.update { it.copy(edit = EditState()) }
        maybeRestartTicker()
    }

    private fun saveDraft() {
        val area = _viewState.value.edit.area ?: return
        viewModelScope.launch {
            _viewState.update { it.copy(edit = it.edit.copy(saving = true, errorMessage = null)) }
            runCatching {
                trackingRepository.savePreferences(
                    TrackingPreferences(
                        area = area,
                        maxAltitudeMeters = _viewState.value.edit.maxAltitudeMeters.toDouble(),
                        warningDistanceMeters = _viewState.value.edit.warningDistanceMeters.toDouble(),
                    ),
                )
                scheduler.schedule()
            }
                .onSuccess { _viewState.update { it.copy(edit = EditState()) } }
                .onFailure { error ->
                    Log.w(TAG, "Saving tracking preferences failed", error)
                    _viewState.update {
                        it.copy(
                            edit = it.edit.copy(
                                saving = false,
                                errorMessage = R.string.map_error_save_failed,
                            ),
                        )
                    }
                }
        }
    }

    private fun clearPreferences() {
        viewModelScope.launch {
            runCatching {
                trackingRepository.clear()
                scheduler.cancel()
            }
        }
    }

    /**
     * What one tick fetches. With a saved tracking area, the feed is queried
     * around the area's center, far enough out to reach [TRACKING_BUFFER_KM]
     * past its farthest edge, and [clipTo] trims the result to planes inside
     * the area or within that buffer of its edges. Otherwise it is whatever
     * the user is looking at.
     */
    private data class PlaneQuery(
        val center: GeoPoint,
        val radiusNm: Double,
        val clipTo: Area? = null,
    )

    private fun currentQuery(): PlaneQuery? {
        val state = _viewState.value
        state.preferences?.area?.let { tracking ->
            return PlaneQuery(
                center = tracking.center,
                radiusNm = tracking.radiusNm + TRACKING_BUFFER_KM / KM_PER_NM,
                clipTo = tracking,
            )
        }
        return state.visibleArea?.let { PlaneQuery(it.center, it.radiusNm) }
    }

    private fun onScreenVisibleChanged(visible: Boolean) {
        screenVisible = visible
        if (visible) {
            maybeRestartTicker()
        } else {
            // Keep the last planes on screen so returning doesn't flash an
            // empty map; the restarted ticker refreshes them immediately.
            tickerJob?.cancel()
        }
    }

    private fun maybeRestartTicker() {
        if (!screenVisible) return
        val state = _viewState.value
        if (state.edit.active) {
            Log.d(TAG, "Ticker stopped: edit mode active")
            stopTicker()
            return
        }
        val query = currentQuery()
        if (query == null) {
            Log.d(
                TAG,
                "Ticker stopped: no query area (preferences=${state.preferences != null}, userLocation=${state.userLocation})",
            )
            stopTicker()
            return
        }
        // Panning the map doesn't change a tracking-area query, so don't
        // throw away a running loop for nothing.
        if (query == activeQuery && tickerJob?.isActive == true) return
        Log.d(TAG, "maybeRestartTicker center=${query.center} radiusNm=${query.radiusNm}")
        activeQuery = query
        restartTicker()
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        activeQuery = null
        _viewState.update { it.copy(planes = emptyList()) }
    }

    /**
     * (Re)starts the single polling loop, so its first tick fetches right away
     * for the new query. Restarting instead of firing a parallel refresh keeps
     * requests from piling up behind the rate limiter, and keeps an older
     * response from landing after a newer one.
     */
    private fun restartTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val startedAt = SystemClock.elapsedRealtime()
                refresh()
                // Fixed cadence: the request time counts toward the interval.
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                delay((REFRESH_INTERVAL_MS - elapsed).coerceAtLeast(0L))
            }
        }
    }

    private fun refreshNow() {
        if (_viewState.value.edit.active || currentQuery() == null) return
        restartTicker()
    }

    private suspend fun refresh() {
        if (_viewState.value.edit.active || cameraMoving) return
        val query = currentQuery() ?: return
        val fetched = try {
            planeRepository.fetchPlanes(query.center, query.radiusNm)
        } catch (cancellation: CancellationException) {
            // Don't swallow coroutine cancellation; let it propagate so the
            // surrounding ticker can stop cleanly.
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "adsb.fi request failed", error)
            _viewState.update { it.copy(errorMessage = R.string.map_error_plane_fetch) }
            return
        }
        // Alerts don't wait for the camera to settle.
        _viewState.value.preferences?.let { planeAlerts.onPlanesUpdated(it, fetched) }
        // A gesture started while the request was in flight: drop the result
        // rather than redraw markers under the user's finger. The next tick
        // after the camera settles will fetch fresh data.
        if (cameraMoving) return
        // Every plane in range is shown regardless of altitude; the tracking
        // area's altitude ceiling only drives the background notification check.
        val planes = query.clipTo?.let { area ->
            fetched.filter { area.distanceKmTo(it.position) <= TRACKING_BUFFER_KM }
        } ?: fetched
        Log.d(TAG, "adsb.fi: ${fetched.size} planes fetched, ${planes.size} shown")
        _viewState.update { it.copy(planes = planes, errorMessage = null) }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val REFRESH_INTERVAL_MS = 1_000L

        /** How far past the tracking area's edges planes are still shown. */
        private const val TRACKING_BUFFER_KM = 50.0
        private const val KM_PER_NM = 1.852
    }
}
