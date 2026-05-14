package com.plane.cube.features.map

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.rememberCameraPositionState
import com.plane.cube.domain.entity.Area
import com.plane.cube.domain.entity.GeoPoint

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val state by viewModel.viewState.collectAsState()

    val permissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permissionState = rememberMultiplePermissionsState(permissions)

    val locationGranted = permissionState.permissions.any {
        (it.permission == Manifest.permission.ACCESS_FINE_LOCATION ||
                it.permission == Manifest.permission.ACCESS_COARSE_LOCATION) && it.status.isGranted
    }

    var permissionDismissed by remember { mutableStateOf(false) }
    val showPermissionDialog = !locationGranted && !permissionDismissed

    LaunchedEffect(locationGranted) {
        if (locationGranted) viewModel.onIntent(MapUiIntent.PermissionGranted)
    }

    val initialTarget = state.userLocation
        ?: state.preferences?.area?.center
        ?: GeoPoint(52.2297, 21.0122)
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(initialTarget.toLatLng(), 9f)
    }

    LaunchedEffect(state.userLocation) {
        if (!state.edit.active) {
            state.userLocation?.let { loc ->
                cameraState.animate(
                    CameraUpdateFactory.newLatLngZoom(loc.toLatLng(), 10f),
                    durationMs = 600,
                )
            }
        }
    }

    LaunchedEffect(state.edit.adjustingAltitude, state.edit.area?.center) {
        val area = state.edit.area
        if (state.edit.adjustingAltitude && area != null) {
            cameraState.animate(
                update = CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(area.center.toLatLng())
                        .zoom(cameraState.position.zoom)
                        .bearing(cameraState.position.bearing)
                        .tilt(50f)
                        .build(),
                ),
                durationMs = 700,
            )
        } else if (!state.edit.adjustingAltitude && cameraState.position.tilt > 0.1f) {
            cameraState.animate(
                update = CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(cameraState.position).tilt(0f).build(),
                ),
                durationMs = 500,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.edit.active) "Select tracking area" else "PlaneCube") },
                actions = {
                    if (state.edit.active) {
                        IconButton(onClick = { viewModel.onIntent(MapUiIntent.ResetDraftCorners) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                        IconButton(onClick = { viewModel.onIntent(MapUiIntent.CancelEditing) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.edit.active) {
                FloatingActionButton(onClick = { viewModel.onIntent(MapUiIntent.StartEditing) }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit area")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraState,
                onMapClick = { latLng ->
                    viewModel.onIntent(
                        MapUiIntent.TapMap(GeoPoint(latLng.latitude, latLng.longitude)),
                    )
                },
                properties = MapProperties(
                    isMyLocationEnabled = state.hasLocationPermission,
                    mapType = MapType.SATELLITE,
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = !state.edit.active,
                ),
                contentPadding = PaddingValues(top = 56.dp),
            ) {
                if (state.edit.active) {
                    state.edit.firstCorner?.let { CornerCircle(it.toLatLng()) }
                    state.edit.secondCorner?.let { CornerCircle(it.toLatLng()) }
                    state.edit.area?.let { AreaPolygon(it) }
                } else {
                    state.preferences?.let { prefs -> AreaPolygon(prefs.area) }
                    state.planes.forEach { plane ->
                        Marker(
                            state = MarkerState(position = plane.position.toLatLng()),
                            title = plane.callsign ?: plane.icao24,
                            snippet = plane.altitudeMeters?.let { "alt ${it.toInt()} m" },
                        )
                    }
                }
            }

            if (state.edit.active) {
                AltitudePanel(
                    state = state.edit,
                    onIntent = viewModel::onIntent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(
            rationale = !permissionState.shouldShowRationale &&
                    permissionState.revokedPermissions.isNotEmpty(),
            onRequest = {
                permissionState.launchMultiplePermissionRequest()
                permissionDismissed = true
            },
            onDismiss = { permissionDismissed = true },
        )
    }
}

@Composable
private fun PermissionDialog(
    rationale: Boolean,
    onRequest: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Location permission needed") },
        text = {
            Text(
                if (rationale) {
                    "Permission was denied. Grant it from system settings to track planes near you."
                } else {
                    "PlaneCube needs your location to show planes near you."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onRequest) { Text("Grant") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun CornerCircle(center: LatLng) {
    Circle(
        center = center,
        radius = 1_800.0,
        fillColor = Color(0x5522AA77),
        strokeColor = Color(0x00000000),
        strokeWidth = 0f,
    )
    Circle(
        center = center,
        radius = 600.0,
        fillColor = Color(0xFF22AA77),
        strokeColor = Color(0xFFFFFFFF),
        strokeWidth = 4f,
    )
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
    state: EditState,
    onIntent: (MapUiIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(16.dp),
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
                    onIntent(MapUiIntent.DraftAltitudeChange(meters))
                    if (!state.adjustingAltitude) onIntent(MapUiIntent.DraftAltitudeAdjusting(true))
                },
                onValueChangeFinished = { onIntent(MapUiIntent.DraftAltitudeAdjusting(false)) },
                valueRange = EditState.MIN_ALTITUDE_M..EditState.MAX_ALTITUDE_M,
            )
            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                OutlinedButton(
                    enabled = !state.saving,
                    onClick = { onIntent(MapUiIntent.CancelEditing) },
                ) { Text("Cancel") }
                Button(
                    enabled = state.canSave && !state.saving,
                    onClick = { onIntent(MapUiIntent.SaveDraft) },
                ) { Text(if (state.saving) "Saving..." else "Save") }
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

@Preview(showBackground = true, name = "Permission dialog · initial")
@Composable
private fun PermissionDialogInitialPreview() {
    PermissionDialog(rationale = false, onRequest = {}, onDismiss = {})
}

@Preview(showBackground = true, name = "Permission dialog · denied")
@Composable
private fun PermissionDialogDeniedPreview() {
    PermissionDialog(rationale = true, onRequest = {}, onDismiss = {})
}

private val sampleFirstCorner = GeoPoint(52.10, 20.85)
private val sampleSecondCorner = GeoPoint(52.35, 21.20)

@Preview(showBackground = true, name = "Altitude · area set")
@Composable
private fun AltitudePanelAreaSetPreview() {
    AltitudePanel(
        state = EditState(
            active = true,
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
        state = EditState(active = true, maxAltitudeMeters = 2_000f),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · saving")
@Composable
private fun AltitudePanelSavingPreview() {
    AltitudePanel(
        state = EditState(
            active = true,
            firstCorner = sampleFirstCorner,
            secondCorner = sampleSecondCorner,
            maxAltitudeMeters = 6_000f,
            saving = true,
        ),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · error")
@Composable
private fun AltitudePanelErrorPreview() {
    AltitudePanel(
        state = EditState(
            active = true,
            firstCorner = sampleFirstCorner,
            secondCorner = sampleSecondCorner,
            maxAltitudeMeters = 3_000f,
            errorMessage = "Network unavailable",
        ),
        onIntent = {},
    )
}
