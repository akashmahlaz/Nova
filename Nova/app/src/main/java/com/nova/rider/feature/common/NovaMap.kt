package com.nova.rider.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.nova.designsystem.components.MapBackdrop
import com.nova.designsystem.components.MapOverlayScope
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.BuildConfig
import com.nova.rider.data.auth.GeoPoint

/**
 * The real Google map, dressed in Nova's map palette so it reads like the designed MapBackdrop.
 *
 * Practices applied:
 *   * Map chrome is off (zoom buttons, toolbar, compass, POI icons): sheets and FABs own the UI, as
 *     in every major ride-hailing app. Tilt/rotate are off so north stays up and pins stay legible.
 *   * `contentPadding` tells the map which part is hidden under the bottom sheet, so camera fits
 *     and the Google logo (required attribution) stay in the visible area.
 *   * With no MAPS_API_KEY configured the drawn [MapBackdrop] is shown instead of a blank grey map,
 *     so the app still works in development.
 */
@Composable
fun NovaMap(
    cameraPositionState: CameraPositionState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    showMyLocation: Boolean = false,
    gesturesEnabled: Boolean = true,
    fallback: MapOverlayScope.() -> Unit = {},
    content: @Composable @GoogleMapComposable () -> Unit = {},
) {
    if (!BuildConfig.MAPS_ENABLED) {
        MapBackdrop(modifier, overlay = fallback)
        return
    }
    val nova = NovaTheme.colors
    val scheme = MaterialTheme.colorScheme
    val style = remember(nova.mapLand, nova.mapWater, nova.mapPark, nova.mapRoad, scheme.onSurfaceVariant, scheme.surface) {
        MapStyleOptions(
            novaMapStyle(
                land = nova.mapLand, water = nova.mapWater, park = nova.mapPark, road = nova.mapRoad,
                label = scheme.onSurfaceVariant, labelHalo = scheme.surface,
            ),
        )
    }
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        properties = MapProperties(isMyLocationEnabled = showMyLocation, mapStyleOptions = style),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
            zoomControlsEnabled = false,
            scrollGesturesEnabled = gesturesEnabled,
            zoomGesturesEnabled = gesturesEnabled,
        ),
        content = content,
    )
}

/** Pickup: the design's hollow ring. */
@Composable
@GoogleMapComposable
fun PickupMarker(at: GeoPoint) {
    val ink = MaterialTheme.colorScheme.onSurface
    MarkerComposable(ink, state = rememberUpdatedMarkerState(at.toLatLng()), anchor = Offset(0.5f, 0.5f)) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(5.dp, ink, CircleShape))
    }
}

/** Intermediate stop: the design's tertiary dot. */
@Composable
@GoogleMapComposable
fun StopMarker(at: GeoPoint) {
    val fill = MaterialTheme.colorScheme.tertiary
    MarkerComposable(fill, state = rememberUpdatedMarkerState(at.toLatLng()), anchor = Offset(0.5f, 0.5f)) {
        Box(Modifier.size(20.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(fill))
        }
    }
}

/** Drop-off: the design's rounded square with a white core. */
@Composable
@GoogleMapComposable
fun DestinationMarker(at: GeoPoint) {
    val ink = MaterialTheme.colorScheme.onSurface
    MarkerComposable(ink, state = rememberUpdatedMarkerState(at.toLatLng()), anchor = Offset(0.5f, 0.5f)) {
        Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(ink), contentAlignment = Alignment.Center) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        }
    }
}

/** Route line from a Google encoded polyline (as returned by our /v1/rides/quote). */
@Composable
@GoogleMapComposable
fun RouteLine(encoded: String) {
    val points = remember(encoded) { decodeRoute(encoded) }
    if (points.size < 2) return
    Polyline(
        points = points,
        color = MaterialTheme.colorScheme.primary,
        width = 14f,
        jointType = JointType.ROUND,
        startCap = RoundCap(),
        endCap = RoundCap(),
    )
}

fun GeoPoint.toLatLng() = LatLng(lat, lng)
fun LatLng.toGeoPoint() = GeoPoint(latitude, longitude)

fun decodeRoute(encoded: String): List<LatLng> =
    if (encoded.isBlank()) emptyList() else runCatching { PolyUtil.decode(encoded) }.getOrDefault(emptyList())

/** Animate so every point is visible inside the map's content padding. */
suspend fun CameraPositionState.fitTo(points: List<LatLng>, paddingPx: Int = 120) {
    when (points.size) {
        0 -> return
        1 -> animate(CameraUpdateFactory.newLatLngZoom(points.first(), 15f))
        else -> {
            val bounds = LatLngBounds.builder().apply { points.forEach(::include) }.build()
            // newLatLngBounds throws before the map has been laid out; fall back to centring.
            runCatching { animate(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx)) }
                .onFailure { move(CameraUpdateFactory.newLatLngZoom(bounds.center, 12f)) }
        }
    }
}

/**
 * Google Maps JSON style built from Nova's design tokens, so dark mode and palette changes in the
 * design system flow into the map. POI and transit icons are hidden to keep the map calm under
 * pickup/drop-off markers.
 */
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
