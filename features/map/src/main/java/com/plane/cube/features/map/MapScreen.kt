package com.plane.cube.features.map

import android.Manifest
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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

private const val CAMERA_IDLE_DEBOUNCE_MS = 1_000L
// Small clockwise nudge applied to the camera bearing while the altitude
// slider is being dragged. Restored when the user releases the slider.
private const val ALTITUDE_ADJUST_BEARING_DELTA = 25f

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

    val context = LocalContext.current
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = context.getString(it),
                duration = SnackbarDuration.Long,
            )
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

    // Remember the bearing captured when the user first grabbed the slider, so
    // the rotation applied during adjustment can be reversed cleanly on release.
    var preAdjustBearing by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(state.edit.adjustingAltitude, state.edit.area?.center) {
        val area = state.edit.area
        if (state.edit.adjustingAltitude && area != null) {
            val baseBearing = preAdjustBearing
                ?: cameraState.position.bearing.also { preAdjustBearing = it }
            cameraState.animate(
                update = CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(area.center.toLatLng())
                        .zoom(cameraState.position.zoom)
                        .bearing(baseBearing + ALTITUDE_ADJUST_BEARING_DELTA)
                        .tilt(50f)
                        .build(),
                ),
                durationMs = 700,
            )
        } else if (!state.edit.adjustingAltitude) {
            val restoreBearing = preAdjustBearing
            preAdjustBearing = null
            if (restoreBearing != null || cameraState.position.tilt > 0.1f) {
                cameraState.animate(
                    update = CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder(cameraState.position)
                            .tilt(0f)
                            .bearing(restoreBearing ?: cameraState.position.bearing)
                            .build(),
                    ),
                    durationMs = 500,
                )
            }
        }
    }

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = rememberFlightSheetState(state.edit.active),
        snackbarHostState = snackbarHostState,
    )

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        // Collapsed to nothing outside edit mode, so the sheet is invisible
        // until there is an altitude to pick.
        sheetPeekHeight = if (state.edit.active) FlightSheetDefaults.PeekHeight else 0.dp,
        sheetContent = {
            FlightSheetContent(state = state.edit, onIntent = viewModel::onIntent)
        },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.edit.active) R.string.map_title_edit else R.string.map_title,
                        ),
                    )
                },
                actions = {
                    if (state.edit.active) {
                        IconButton(onClick = { viewModel.onIntent(MapUiIntent.ResetDraftCorners) }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.map_action_reset_corners),
                            )
                        }
                        IconButton(onClick = { viewModel.onIntent(MapUiIntent.CancelEditing) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.map_action_cancel),
                            )
                        }
                    } else if (state.preferences != null) {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.map_action_reset_area),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        // Only the top bar inset is applied: letting the sheet's peek height
        // resize the map would shift the camera every time it opens or closes.
        Box(modifier = Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
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
                    // Hide Google Maps' default "Navigate / Open in Maps"
                    // toolbar that appears when a marker is selected.
                    mapToolbarEnabled = false,
                ),
                contentPadding = PaddingValues(top = 56.dp),
            ) {
                if (state.edit.active) {
                    state.edit.area?.let { AreaPolygon(it) }
                } else {
                    state.preferences?.let { prefs -> AreaPolygon(prefs.area) }
                    val density = LocalDensity.current.density
                    state.planes.forEach { plane ->
                        // key() ties each marker's state + icon cache to the
                        // aircraft identity, so at 1 Hz they move/update in
                        // place instead of being torn down and recreated.
                        key(plane.icao24) {
                            val heading = plane.trueTrackDegrees?.toFloat() ?: 0f
                            val altitudeM = plane.altitudeMeters?.toInt()
                            val icon = remember(heading, altitudeM, density) {
                                PlaneIcon.create(
                                    headingDegrees = heading,
                                    altitudeMeters = altitudeM,
                                    density = density,
                                )
                            }
                            val markerState = remember { MarkerState(plane.position.toLatLng()) }
                            SideEffect { markerState.position = plane.position.toLatLng() }
                            Marker(
                                state = markerState,
                                title = plane.callsign ?: plane.icao24,
                                snippet = altitudeM?.let { stringResource(R.string.map_marker_altitude, it) },
                                icon = icon,
                                flat = true,
                                anchor = Offset(0.5f, PlaneIcon.anchorY),
                            )
                        }
                    }
                }
            }

            if (state.edit.active) {
                EditOverlay(
                    editState = state.edit,
                    cameraState = cameraState,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                FloatingActionButton(
                    onClick = { viewModel.onIntent(MapUiIntent.StartEditing) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(16.dp),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.map_action_edit_area),
                    )
                }
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
        title = { Text(stringResource(R.string.map_reset_dialog_title)) },
        text = {
            Text(
                stringResource(R.string.map_reset_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.map_reset_dialog_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_action_cancel)) }
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
        title = { Text(stringResource(R.string.map_permission_dialog_title)) },
        text = {
            Text(
                stringResource(
                    if (rationale) {
                        R.string.map_permission_dialog_body_denied
                    } else {
                        R.string.map_permission_dialog_body
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onRequest) { Text(stringResource(R.string.map_permission_dialog_grant)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.map_permission_dialog_dismiss)) }
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
