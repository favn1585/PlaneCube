package com.plane.cube.features.area

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AreaScreen(
    onSaved: () -> Unit,
    viewModel: AreaViewModel = hiltViewModel(),
) {
    val state by viewModel.viewState.collectAsState()

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select tracking area") },
                actions = {
                    IconButton(onClick = { viewModel.onIntent(AreaUiIntent.Reset) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AreaMap(state = state, onIntent = viewModel::onIntent)
            AltitudePanel(
                state = state,
                onIntent = viewModel::onIntent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun AreaMap(
    state: AreaViewState,
    onIntent: (AreaUiIntent) -> Unit,
) {
    val initialTarget = state.firstCorner ?: GeoPoint(52.2297, 21.0122)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialTarget.toLatLng(), 9f)
    }

    val targetTilt by animateFloatAsState(
        targetValue = if (state.isAdjustingAltitude) 60f else 0f,
        label = "tilt",
    )
    LaunchedEffect(targetTilt) {
        val current = cameraState.position
        cameraState.position = CameraPosition.builder(current).tilt(targetTilt).build()
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraState,
        onMapClick = { latLng -> onIntent(AreaUiIntent.TapMap(GeoPoint(latLng.latitude, latLng.longitude))) },
        properties = MapProperties(mapType = if (state.isAdjustingAltitude) MapType.HYBRID else MapType.NORMAL),
    ) {
        state.firstCorner?.let {
            Marker(state = MarkerState(position = it.toLatLng()), title = "Corner 1")
        }
        state.secondCorner?.let {
            Marker(state = MarkerState(position = it.toLatLng()), title = "Corner 2")
        }
        state.area?.let { AreaPolygon(it) }
    }
}

@Composable
private fun AreaPolygon(area: Area) {
    val points = listOf(
        LatLng(area.south, area.west),
        LatLng(area.south, area.east),
        LatLng(area.north, area.east),
        LatLng(area.north, area.west),
    )
    Polygon(
        points = points,
        fillColor = Color(0x3322AA77),
        strokeColor = Color(0xFF22AA77),
        strokeWidth = 4f,
    )
}

@Composable
private fun AltitudePanel(
    state: AreaViewState,
    onIntent: (AreaUiIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Max altitude", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${state.maxAltitudeMeters.toInt()} m",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Slider(
                value = state.maxAltitudeMeters,
                onValueChange = { meters ->
                    onIntent(AreaUiIntent.AltitudeChange(meters))
                    if (!state.isAdjustingAltitude) onIntent(AreaUiIntent.AltitudeAdjusting(true))
                },
                onValueChangeFinished = { onIntent(AreaUiIntent.AltitudeAdjusting(false)) },
                valueRange = AreaViewState.MIN_ALTITUDE_M..AreaViewState.MAX_ALTITUDE_M,
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    enabled = state.canSave && !state.isSaving,
                    onClick = { onIntent(AreaUiIntent.Save) },
                ) {
                    Text(if (state.isSaving) "Saving..." else "Save")
                }
            }
            if (!state.canSave) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap two opposite corners on the map to define a tracking area.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

internal fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private val sampleFirstCorner = GeoPoint(52.10, 20.85)
private val sampleSecondCorner = GeoPoint(52.35, 21.20)

@Preview(showBackground = true, name = "Altitude · area set")
@Composable
private fun AltitudePanelAreaSetPreview() {
    AltitudePanel(
        state = AreaViewState(
            firstCorner = sampleFirstCorner,
            secondCorner = sampleSecondCorner,
            maxAltitudeMeters = 4_500f,
        ),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · no area yet")
@Composable
private fun AltitudePanelNoAreaPreview() {
    AltitudePanel(
        state = AreaViewState(maxAltitudeMeters = 2_000f),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · saving")
@Composable
private fun AltitudePanelSavingPreview() {
    AltitudePanel(
        state = AreaViewState(
            firstCorner = sampleFirstCorner,
            secondCorner = sampleSecondCorner,
            maxAltitudeMeters = 6_000f,
            isSaving = true,
        ),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · error")
@Composable
private fun AltitudePanelErrorPreview() {
    AltitudePanel(
        state = AreaViewState(
            firstCorner = sampleFirstCorner,
            secondCorner = sampleSecondCorner,
            maxAltitudeMeters = 3_000f,
            errorMessage = "Network unavailable",
        ),
        onIntent = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, name = "Area · scaffold (no map)", heightDp = 720)
@Composable
private fun AreaScaffoldPreview() {
    val state = AreaViewState(
        firstCorner = sampleFirstCorner,
        secondCorner = sampleSecondCorner,
        maxAltitudeMeters = 4_500f,
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select tracking area") },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = "GoogleMap renders only on a real device/emulator.",
                modifier = Modifier.align(Alignment.Center),
                style = MaterialTheme.typography.bodyMedium,
            )
            AltitudePanel(
                state = state,
                onIntent = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
