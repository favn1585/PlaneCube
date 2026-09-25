package com.plane.cube.features.map

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.coroutines.cancellation.CancellationException
import com.plane.cube.domain.TrackingScheduler
import com.plane.cube.domain.entity.Area
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
) : ViewModel() {

    private val _viewState = MutableStateFlow(MapViewState())
    val viewState = _viewState.asStateFlow()

    private var tickerJob: Job? = null

    /**
     * Kept out of the view state on purpose: flipping it must not recompose
     * the map mid-gesture. While true, refreshes are skipped entirely.
     */
    private var cameraMoving = false

    init {
        viewModelScope.launch {
            trackingRepository.observePreferences().collectLatest { preferences ->
                _viewState.update { it.copy(preferences = preferences) }
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
            is MapUiIntent.UpdateVisibleArea -> updateVisibleArea(intent.area)
            is MapUiIntent.CameraMovingChanged -> cameraMoving = intent.moving
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
                )
            } else {
                EditState(active = true)
            }
            state.copy(edit = draft)
        }
        tickerJob?.cancel()
        _viewState.update { it.copy(planes = emptyList()) }
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
     * Areas to poll on each tick: the region the user is looking at, plus the
     * saved tracking area (if any) so it stays monitored even when it is
     * panned off-screen. The tracking area is dropped when the visible-area
     * query already covers it, since adsb.fi only allows ~1 request/second
     * and every extra request slows the refresh rate down.
     */
    private fun currentQueryAreas(): List<Area> {
        val state = _viewState.value
        val visible = state.visibleArea
        val tracking = state.preferences?.area
        return when {
            visible == null -> listOfNotNull(tracking)
            tracking == null || visible.covers(tracking) -> listOf(visible)
            else -> listOf(visible, tracking)
        }
    }

    /** Whether this area's feed query (a radius-capped circle) includes all of [other]. */
    private fun Area.covers(other: Area): Boolean {
        val reach = minOf(radiusNm, PlaneRepository.MAX_QUERY_RADIUS_NM)
        return other.corners.all { center.distanceNmTo(it) <= reach }
    }

    private fun maybeRestartTicker() {
        val state = _viewState.value
        if (state.edit.active) {
            Log.d(TAG, "Ticker stopped: edit mode active")
            tickerJob?.cancel()
            _viewState.update { it.copy(planes = emptyList()) }
            return
        }
        val areas = currentQueryAreas()
        if (areas.isEmpty()) {
            Log.d(
                TAG,
                "Ticker stopped: no query area (preferences=${state.preferences != null}, userLocation=${state.userLocation})",
            )
            tickerJob?.cancel()
            _viewState.update { it.copy(planes = emptyList()) }
            return
        }
        Log.d(TAG, "maybeRestartTicker areas=${areas.size}")
        restartTicker()
    }

    /**
     * (Re)starts the single polling loop, so its first tick fetches right away
     * for the new area. Restarting instead of firing a parallel refresh keeps
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
        if (_viewState.value.edit.active || currentQueryAreas().isEmpty()) return
        restartTicker()
    }

    private suspend fun refresh() {
        if (_viewState.value.edit.active || cameraMoving) return
        val areas = currentQueryAreas()
        if (areas.isEmpty()) return
        var lastError: Throwable? = null
        val results = areas.mapNotNull { area ->
            try {
                planeRepository.fetchPlanes(area)
            } catch (cancellation: CancellationException) {
                // Don't swallow coroutine cancellation; let it propagate so the
                // surrounding ticker can stop cleanly.
                throw cancellation
            } catch (error: Throwable) {
                Log.w(TAG, "adsb.fi request failed", error)
                lastError = error
                null
            }
        }
        // A gesture started while the request was in flight: drop the result
        // rather than redraw markers under the user's finger. The next tick
        // after the camera settles will fetch fresh data.
        if (cameraMoving) return
        if (results.isEmpty() && lastError != null) {
            _viewState.update { it.copy(errorMessage = R.string.map_error_plane_fetch) }
            return
        }
        // Every plane is shown regardless of altitude; the tracking area's
        // altitude ceiling only drives the background notification check.
        val planes = results.flatten().distinctBy { it.icao24 }
        Log.d(TAG, "adsb.fi: ${planes.size} planes across ${areas.size} area(s)")
        _viewState.update { it.copy(planes = planes, errorMessage = null) }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val REFRESH_INTERVAL_MS = 1_000L
    }
}
