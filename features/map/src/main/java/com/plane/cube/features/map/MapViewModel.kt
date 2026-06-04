package com.plane.cube.features.map

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
            is MapUiIntent.DraftAltitudeAdjusting -> _viewState.update {
                it.copy(edit = it.edit.copy(adjustingAltitude = intent.adjusting))
            }
            is MapUiIntent.UpdateVisibleArea -> updateVisibleArea(intent.area)
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
                    it.copy(errorMessage = "Couldn't get your location. Enable location services and try again.")
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
                    _viewState.update {
                        it.copy(
                            edit = it.edit.copy(saving = false, errorMessage = error.message),
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
     * Query OpenSky with whatever bbox the user is currently looking at. The
     * saved tracking area, if any, is only used for the red/white coloring and
     * for the background WorkManager notification check — not for the fetch.
     */
    private fun currentQueryArea(): Area? = _viewState.value.visibleArea

    private fun maybeRestartTicker() {
        val state = _viewState.value
        if (state.edit.active) {
            Log.d(TAG, "Ticker stopped: edit mode active")
            tickerJob?.cancel()
            _viewState.update { it.copy(planes = emptyList()) }
            return
        }
        val area = currentQueryArea()
        if (area == null) {
            Log.d(
                TAG,
                "Ticker stopped: no query area (preferences=${state.preferences != null}, userLocation=${state.userLocation})",
            )
            tickerJob?.cancel()
            _viewState.update { it.copy(planes = emptyList()) }
            return
        }
        Log.d(
            TAG,
            "maybeRestartTicker bbox south=${area.south} west=${area.west} north=${area.north} east=${area.east}",
        )
        // The running ticker reads currentQueryArea() fresh on every iteration,
        // so the next tick will pick up any preferences/location change. But
        // we also fire an immediate parallel refresh so the user doesn't have
        // to wait up to 30s when their location resolves after startup.
        if (tickerJob?.isActive == true) {
            Log.d(TAG, "Ticker already running; triggering an immediate refresh")
            viewModelScope.launch { refresh() }
            return
        }
        Log.d(TAG, "Starting ticker")
        tickerJob = viewModelScope.launch {
            while (true) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        if (_viewState.value.edit.active) return
        val maxAltitude = _viewState.value.preferences?.maxAltitudeMeters
        _viewState.update { it.copy(isRefreshing = true) }
        try {
            val planes = planeRepository.fetchPlanes()
            val filtered = if (maxAltitude == null) {
                planes
            } else {
                planes.filter { plane ->
                    // Keep every plane in the bbox so the map can show both
                    // in-area (red) and out-of-area (green) markers; just drop
                    // the ones above the user's altitude ceiling.
                    val altitude = plane.altitudeMeters
                    altitude != null && altitude <= maxAltitude
                }
            }
            Log.d(TAG, "OpenSky: ${planes.size} planes in bbox, ${filtered.size} after altitude filter")
            _viewState.update {
                it.copy(planes = filtered, isRefreshing = false, errorMessage = null)
            }
        } catch (cancellation: CancellationException) {
            // Don't swallow coroutine cancellation; let it propagate so the
            // surrounding ticker can stop cleanly.
            throw cancellation
        } catch (error: Throwable) {
            Log.w(TAG, "OpenSky request failed", error)
            _viewState.update {
                it.copy(
                    isRefreshing = false,
                    errorMessage = "Plane fetch failed: ${error.message ?: error::class.simpleName}",
                )
            }
        }
    }

    companion object {
        private const val TAG = "MapViewModel"
        private const val REFRESH_INTERVAL_MS = 1_000L
    }
}
