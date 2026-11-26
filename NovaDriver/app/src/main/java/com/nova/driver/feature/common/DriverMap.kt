package com.nova.driver.feature.common

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polygon
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.nova.designsystem.components.MapBackdrop
import com.nova.designsystem.components.MapOverlayScope
import com.nova.designsystem.theme.NovaTheme
import com.nova.driver.BuildConfig
import com.nova.driver.data.DriverStore
import com.nova.driver.data.api.SurgeCell
import kotlinx.coroutines.delay

/**
 * The real Google map, in Nova's map palette (same practices as the rider app's NovaMap):
 * map chrome off, north up, the driver's own blue location dot, and [contentPadding] so camera fits
 * and the Google logo stay clear of sheets. [fit] are the points the camera frames (route, stops);
 * with [follow] the camera then tracks the driver, pausing for 10 s after they pan the map.
 * With no MAPS_API_KEY the drawn [MapBackdrop] is shown, so development builds still work.
 */
@Composable
fun DriverMap(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    fit: List<LatLng> = emptyList(),
    follow: Boolean = false,
    /** Zoom when centring on the driver alone (15 = streets, 13 = the surge area around them). */
    zoom: Float = 15f,
    dimmed: Boolean = false,
    cameraPositionState: CameraPositionState = rememberCameraPositionState(),
    fallback: MapOverlayScope.() -> Unit = {},
    content: @Composable @GoogleMapComposable () -> Unit = {},
) {
    if (!BuildConfig.MAPS_ENABLED) {
        MapBackdrop(modifier, dimmed = dimmed, overlay = fallback)
        return
    }
    val me = DriverStore.location
    LaunchedEffect(Unit) { if (DriverStore.location == null) DriverStore.currentLocation() }
    val nova = NovaTheme.colors
    val scheme = MaterialTheme.colorScheme
    val style = remember(nova.mapLand, nova.mapWater, nova.mapPark, nova.mapRoad, scheme.onSurfaceVariant, scheme.surface) {
        MapStyleOptions(novaMapStyle(nova.mapLand, nova.mapWater, nova.mapPark, nova.mapRoad, scheme.onSurfaceVariant, scheme.surface))
    }
    // Frame the route/stops once they are known; otherwise centre on the driver.
    val fitKey = fit.joinToString { "%.4f,%.4f".format(it.latitude, it.longitude) }
    var placed by remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(fitKey, me != null) {
        val points = fit + listOfNotNull(me?.toLatLng())
        if (points.isEmpty()) return@LaunchedEffect
        if (fit.isEmpty() && placed) return@LaunchedEffect
        cameraPositionState.fitTo(points, singleZoom = zoom)
        placed = true
    }
    var pannedAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) pannedAt = System.currentTimeMillis()
    }
    if (follow && me != null) LaunchedEffect(me.latitude, me.longitude) {
        val idle = System.currentTimeMillis() - pannedAt
        if (idle < 10_000) delay(10_000 - idle)
        // Keep the driver's own zoom once they've chosen one; start from [zoom].
        val current = cameraPositionState.position.zoom
        val z = if (pannedAt > 0 && current >= 10f) current else zoom
        runCatching { cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(CameraPosition(me.toLatLng(), z, 0f, 0f)), 800) }
    }
    Box(modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            contentPadding = contentPadding,
            properties = MapProperties(isMyLocationEnabled = DriverStore.hasLocationPermission(), mapStyleOptions = style),
            uiSettings = MapUiSettings(
                compassEnabled = false, indoorLevelPickerEnabled = false, mapToolbarEnabled = false,
                myLocationButtonEnabled = false, rotationGesturesEnabled = false, tiltGesturesEnabled = false, zoomControlsEnabled = false,
            ),
            content = content,
        )
        if (dimmed) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
    }
}

/** Pickup: the design's hollow ring. */
@Composable
@GoogleMapComposable
fun PickupMarker(lat: Double, lng: Double) {
    val ink = MaterialTheme.colorScheme.onSurface
    MarkerComposable(ink, state = rememberUpdatedMarkerState(LatLng(lat, lng)), anchor = Offset(0.5f, 0.5f)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(5.dp, ink, CircleShape))
    }
}

/** Drop-off (and the destination filter's place): the design's rounded square with a white core. */
@Composable
@GoogleMapComposable
fun DropoffMarker(lat: Double, lng: Double, fill: Color = MaterialTheme.colorScheme.onSurface) {
    MarkerComposable(fill, state = rememberUpdatedMarkerState(LatLng(lat, lng)), anchor = Offset(0.5f, 0.5f)) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(fill), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}

/** Route line from a Google encoded polyline. */
@Composable
@GoogleMapComposable
fun RouteLine(encoded: String) {
    val points = remember(encoded) { decodeRoute(encoded) }
    if (points.size < 2) return
    Polyline(points = points, color = MaterialTheme.colorScheme.primary, width = 14f, jointType = JointType.ROUND, startCap = RoundCap(), endCap = RoundCap())
}

/**
 * Surge hexagons, tinted from warm to hot by multiplier (Uber's heatmap), with the multiplier
 * label at each cell's centre.
 */
@Composable
@GoogleMapComposable
fun SurgeHexes(cells: List<SurgeCell>) {
    val warm = NovaTheme.colors.coralBright
    val hot = MaterialTheme.colorScheme.error
    val ink = MaterialTheme.colorScheme.onTertiaryContainer
    cells.forEach { cell ->
        val ring = cell.boundary.mapNotNull { p -> if (p.size >= 2) LatLng(p[0], p[1]) else null }
        if (ring.size < 3) return@forEach
        val heat = ((cell.multiplier - 1.0) / 1.0).toFloat().coerceIn(0f, 1f)
        val tint = lerp(warm, hot, heat)
        Polygon(points = ring, fillColor = tint.copy(alpha = 0.18f + 0.22f * heat), strokeColor = tint.copy(alpha = 0.7f), strokeWidth = 2f)
        val centre = LatLng(ring.sumOf { it.latitude } / ring.size, ring.sumOf { it.longitude } / ring.size)
        val label = "%.1f×".format(cell.multiplier)
        MarkerComposable(cell.cell, label, state = rememberUpdatedMarkerState(centre), anchor = Offset(0.5f, 0.5f)) {
            Box(Modifier.height(28.dp).clip(CircleShape).background(tint).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = ink)
            }
        }
    }
}

fun Location.toLatLng() = LatLng(latitude, longitude)

fun decodeRoute(encoded: String): List<LatLng> =
    if (encoded.isBlank()) emptyList() else runCatching { PolyUtil.decode(encoded) }.getOrDefault(emptyList())

/** Animate so every point is visible inside the map's content padding. */
suspend fun CameraPositionState.fitTo(points: List<LatLng>, paddingPx: Int = 140, singleZoom: Float = 15f) {
    when (points.size) {
        0 -> return
        1 -> runCatching { animate(CameraUpdateFactory.newLatLngZoom(points.first(), singleZoom)) }
        else -> {
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            // newLatLngBounds throws before the map has been laid out; fall back to centring.
            runCatching { animate(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)) }
                .onFailure { move(CameraUpdateFactory.newLatLngZoom(bounds.center, 13f)) }
        }
    }
}

/** Google Maps JSON style from Nova's design tokens (POI and transit icons hidden). */
private fun novaMapStyle(land: Color, water: Color, park: Color, road: Color, label: Color, labelHalo: Color): String {
    fun hex(c: Color) = String.format("#%06X", c.toArgb() and 0xFFFFFF)
    fun rule(feature: String?, element: String, stylers: String): String {
        val f = feature?.let { "\"featureType\":\"$it\"," }.orEmpty()
        return "{$f\"elementType\":\"$element\",\"stylers\":[$stylers]}"
    }
    return listOf(
        rule(null, "geometry", "{\"color\":\"${hex(land)}\"}"),
        rule("water", "geometry", "{\"color\":\"${hex(water)}\"}"),
        rule("poi.park", "geometry", "{\"color\":\"${hex(park)}\"}"),
        rule("road", "geometry", "{\"color\":\"${hex(road)}\"}"),
        rule(null, "labels.text.fill", "{\"color\":\"${hex(label)}\"}"),
        rule(null, "labels.text.stroke", "{\"color\":\"${hex(labelHalo)}\"}"),
        rule("poi", "labels.icon", "{\"visibility\":\"off\"}"),
        rule("transit", "labels.icon", "{\"visibility\":\"off\"}"),
    ).joinToString(",", "[", "]")
}
