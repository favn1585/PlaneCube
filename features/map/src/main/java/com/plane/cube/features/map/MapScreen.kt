package com.plane.cube.features.map

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import android.graphics.Point
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.Projection
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val CAMERA_IDLE_DEBOUNCE_MS = 5_000L

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

    var showResetDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(locationGranted) {
        if (locationGranted) viewModel.onIntent(MapUiIntent.PermissionGranted)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(message = it, duration = SnackbarDuration.Long)
        }
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

    // When the user stops panning/zooming, debounce 5s, then push the current
    // visible region to the VM so it can fetch planes. While editing, the VM
    // ignores fetches, so we skip the side effect entirely.
    LaunchedEffect(cameraState.isMoving, state.edit.active) {
        if (state.edit.active) return@LaunchedEffect
        if (cameraState.isMoving) return@LaunchedEffect
        kotlinx.coroutines.delay(CAMERA_IDLE_DEBOUNCE_MS)
        val projection = cameraState.projection ?: return@LaunchedEffect
        val bounds = projection.visibleRegion.latLngBounds
        val area = Area.of(
            GeoPoint(bounds.southwest.latitude, bounds.southwest.longitude),
            GeoPoint(bounds.northeast.latitude, bounds.northeast.longitude),
        )
        viewModel.onIntent(MapUiIntent.UpdateVisibleArea(area))
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.edit.active) "Select tracking area" else "PlaneCube") },
                actions = {
                    if (state.edit.active) {
                        IconButton(onClick = { viewModel.onIntent(MapUiIntent.ResetDraftCorners) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset corners")
                        }
                        IconButton(onClick = { viewModel.onIntent(MapUiIntent.CancelEditing) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                    } else if (state.preferences != null) {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Reset tracking area")
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
                    if (state.edit.active) {
                        handleEditTap(
                            tap = GeoPoint(latLng.latitude, latLng.longitude),
                            edit = state.edit,
                            cameraState = cameraState,
                            onIntent = viewModel::onIntent,
                        )
                    }
                },
                properties = MapProperties(
                    isMyLocationEnabled = state.hasLocationPermission,
                    mapType = MapType.HYBRID,
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = !state.edit.active,
                ),
                contentPadding = PaddingValues(top = 56.dp),
            ) {
                if (state.edit.active) {
                    state.edit.area?.let { AreaPolygon(it) }
                } else {
                    state.preferences?.let { prefs -> AreaPolygon(prefs.area) }
                    val area = state.preferences?.area
                    val density = LocalDensity.current.density
                    state.planes.forEach { plane ->
                        val inside = area?.contains(plane.position) == true
                        val heading = plane.trueTrackDegrees?.toFloat() ?: 0f
                        val altitudeM = plane.altitudeMeters?.toInt()
                        val icon = remember(plane.icao24, heading, altitudeM, inside, density) {
                            PlaneIcon.create(
                                headingDegrees = heading,
                                altitudeMeters = altitudeM,
                                inside = inside,
                                density = density,
                            )
                        }
                        Marker(
                            state = MarkerState(position = plane.position.toLatLng()),
                            title = plane.callsign ?: plane.icao24,
                            snippet = altitudeM?.let { "alt ${it} m" },
                            icon = icon,
                            flat = true,
                            anchor = Offset(0.5f, PlaneIcon.anchorY),
                        )
                    }
                }
            }

            if (state.edit.active) {
                EditOverlay(
                    editState = state.edit,
                    cameraState = cameraState,
                    modifier = Modifier.fillMaxSize(),
                )
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

    if (showResetDialog) {
        ResetAreaDialog(
            onConfirm = {
                viewModel.onIntent(MapUiIntent.ClearPreferences)
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

@Composable
private fun ResetAreaDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        title = { Text("Reset tracking area?") },
        text = {
            Text(
                "Your saved area and altitude will be cleared. Background alerts will stop until you set a new area.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Reset") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
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
private fun EditOverlay(
    editState: EditState,
    cameraState: CameraPositionState,
    modifier: Modifier = Modifier,
) {
    // Subscribe to camera changes so we repaint on pan / zoom / tilt.
    val position = cameraState.position
    val projection = cameraState.projection ?: return

    val area = editState.area
    val cornerScreens: List<Offset> = when {
        area != null -> area.corners.map {
            val p = projection.toScreenLocation(it.toLatLng())
            Offset(p.x.toFloat(), p.y.toFloat())
        }
        editState.firstCorner != null -> listOf(
            projection.toScreenLocation(editState.firstCorner.toLatLng())
                .let { Offset(it.x.toFloat(), it.y.toFloat()) },
        )
        else -> emptyList()
    }

    val boxScreen: Pair<List<Offset>, List<Offset>>? = if (area != null) {
        val ground = cornerScreens
        // Local pixels-per-meter at the area centre, sampled along the axis
        // perpendicular to the current camera bearing (i.e. the screen's
        // horizontal axis on the ground plane). That axis is the only one
        // unaffected by tilt foreshortening, and using it keeps the scale
        // stable as the user rotates the map.
        val centerLat = area.center.latitude
        val centerLng = area.center.longitude
        val centerLatRad = Math.toRadians(centerLat)
        val perpBearingRad = Math.toRadians(position.bearing.toDouble() + 90.0)
        val sampleMeters = 100.0
        val dLat = (sampleMeters / 111_000.0) * cos(perpBearingRad)
        val dLng = (sampleMeters / (111_000.0 * cos(centerLatRad))) * sin(perpBearingRad)
        val c = projection.toScreenLocation(LatLng(centerLat, centerLng))
        val e = projection.toScreenLocation(LatLng(centerLat + dLat, centerLng + dLng))
        val pixelsPerMeter = (hypot((e.x - c.x).toFloat(), (e.y - c.y).toFloat()) /
                sampleMeters.toFloat()).coerceAtLeast(0f)
        val tiltRadians = Math.toRadians(position.tilt.toDouble()).toFloat()
        val dy = editState.maxAltitudeMeters * pixelsPerMeter * sin(tiltRadians)
        ground to ground.map { Offset(it.x, it.y - dy) }
    } else null

    Canvas(modifier = modifier) {
        val fillColor = Color(0x3322AA77)
        val strokeColor = Color(0xCC22AA77)
        val strokeWidth = 2.dp.toPx()

        boxScreen?.let { (ground, top) ->
            // 4 side faces.
            for (i in 0 until 4) {
                val next = (i + 1) % 4
                val side = Path().apply {
                    moveTo(ground[i].x, ground[i].y)
                    lineTo(ground[next].x, ground[next].y)
                    lineTo(top[next].x, top[next].y)
                    lineTo(top[i].x, top[i].y)
                    close()
                }
                drawPath(side, fillColor)
                drawPath(side, strokeColor, style = Stroke(width = strokeWidth))
            }
            // Top face.
            val topPath = Path().apply {
                moveTo(top[0].x, top[0].y)
                for (i in 1 until 4) lineTo(top[i].x, top[i].y)
                close()
            }
            drawPath(topPath, fillColor)
            drawPath(topPath, strokeColor, style = Stroke(width = strokeWidth))
            // Vertical edges for emphasis.
            for (i in 0 until 4) {
                drawLine(
                    color = strokeColor,
                    start = ground[i],
                    end = top[i],
                    strokeWidth = strokeWidth,
                )
            }
        }

        // Fixed-pixel corner markers (do not zoom with the map).
        val cornerFill = Color(0xFF22AA77)
        val cornerHalo = Color(0x5522AA77)
        val cornerStroke = Color(0xFFFFFFFF)
        val cornerStrokeWidth = 2.dp.toPx()
        val coreRadius = 6.dp.toPx()
        val haloRadius = 14.dp.toPx()
        cornerScreens.forEach { centre ->
            drawCircle(cornerHalo, haloRadius, centre)
            drawCircle(cornerFill, coreRadius, centre)
            drawCircle(cornerStroke, coreRadius, centre, style = Stroke(width = cornerStrokeWidth))
        }
    }
}

@Composable
private fun AreaPolygon(area: Area) {
    Polygon(
        points = area.corners.map { it.toLatLng() },
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

/**
 * Handle a tap in edit mode. The first tap stores one corner; the second tap
 * projects both corners to screen, lays out a screen-aligned rectangle, then
 * unprojects the four screen corners back to lat/lng so the saved rectangle
 * is oriented to whatever direction the user is looking at — not to north.
 */
private fun handleEditTap(
    tap: GeoPoint,
    edit: EditState,
    cameraState: CameraPositionState,
    onIntent: (MapUiIntent) -> Unit,
) {
    val first = edit.firstCorner
    val hasArea = edit.area != null
    if (first == null || hasArea) {
        onIntent(MapUiIntent.TapFirstCorner(tap))
        return
    }
    val projection = cameraState.projection
    if (projection == null) {
        onIntent(MapUiIntent.CompleteArea(Area.of(first, tap)))
        return
    }
    val p1 = projection.toScreenLocation(first.toLatLng())
    val p3 = projection.toScreenLocation(tap.toLatLng())
    onIntent(MapUiIntent.CompleteArea(screenAlignedAreaFromDiagonals(p1, p3, projection)))
}

/** Build a screen-aligned rectangle from two diagonal screen points. */
private fun screenAlignedAreaFromDiagonals(
    p1: Point,
    p3: Point,
    projection: Projection,
): Area {
    val p2 = Point(p3.x, p1.y)
    val p4 = Point(p1.x, p3.y)
    val ll1 = projection.fromScreenLocation(p1)
    val ll2 = projection.fromScreenLocation(p2)
    val ll3 = projection.fromScreenLocation(p3)
    val ll4 = projection.fromScreenLocation(p4)
    return Area(
        listOf(
            GeoPoint(ll1.latitude, ll1.longitude),
            GeoPoint(ll2.latitude, ll2.longitude),
            GeoPoint(ll3.latitude, ll3.longitude),
            GeoPoint(ll4.latitude, ll4.longitude),
        ),
    )
}

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

@Preview(showBackground = true, name = "Reset dialog")
@Composable
private fun ResetAreaDialogPreview() {
    ResetAreaDialog(onConfirm = {}, onDismiss = {})
}

private val sampleArea = Area.of(GeoPoint(52.10, 20.85), GeoPoint(52.35, 21.20))

@Preview(showBackground = true, name = "Altitude · area set")
@Composable
private fun AltitudePanelAreaSetPreview() {
    AltitudePanel(
        state = EditState(active = true, area = sampleArea, maxAltitudeMeters = 1_500f),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · no area yet")
@Composable
private fun AltitudePanelNoAreaPreview() {
    AltitudePanel(
        state = EditState(active = true, maxAltitudeMeters = 500f),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · saving")
@Composable
private fun AltitudePanelSavingPreview() {
    AltitudePanel(
        state = EditState(active = true, area = sampleArea, maxAltitudeMeters = 1_800f, saving = true),
        onIntent = {},
    )
}

@Preview(showBackground = true, name = "Altitude · error")
@Composable
private fun AltitudePanelErrorPreview() {
    AltitudePanel(
        state = EditState(
            active = true,
            area = sampleArea,
            maxAltitudeMeters = 1_000f,
            errorMessage = "Network unavailable",
        ),
        onIntent = {},
    )
}
