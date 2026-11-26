package com.nova.rider.feature.booking

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.InputChip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import com.nova.rider.data.auth.GeoPlace
import com.nova.rider.data.trip.metersBetween
import com.nova.rider.data.trip.toGeoPlace
import com.nova.rider.feature.common.DestinationMarker
import com.nova.rider.feature.common.MapLabel
import com.nova.rider.feature.common.PickupMarker
import com.nova.rider.feature.common.StopMarker
import com.nova.rider.feature.common.decodeRoute
import com.nova.rider.feature.common.fitTo
import kotlin.math.roundToInt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.LoadingIndicator
import com.nova.designsystem.components.NovaTonalButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.IconTile
import com.nova.designsystem.components.MapBackdrop
import com.nova.designsystem.components.MapSheet
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaConnectedGroup
import com.nova.designsystem.components.NovaListItem
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.NovaTopBar
import com.nova.designsystem.components.RiderNavigationBar
import com.nova.designsystem.components.RiderTab
import com.nova.designsystem.components.SectionLabel
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.PlaceSuggestion
import com.nova.rider.data.trip.TripFormat
import com.nova.rider.data.trip.TripStore
import com.nova.rider.data.trip.tripErrorMessage
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nova.rider.feature.common.NovaMap
import com.nova.rider.feature.common.toGeoPoint
import com.nova.rider.feature.common.toLatLng
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.profile.ProfileStore
import com.nova.rider.feature.common.DotKind
import com.nova.rider.feature.common.MapFab
import com.nova.rider.feature.common.RouteDot
import com.nova.rider.feature.common.RouteLine
import com.nova.rider.navigation.Account
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.RideOptions
import com.nova.rider.navigation.Schedule
import com.nova.rider.navigation.Search

@Composable
fun HomeScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Location: permission -> device location switched on -> most accurate fix.
    var locationGranted by remember { mutableStateOf(TripStore.hasLocationPermission(context)) }
    var locationOnTick by remember { mutableIntStateOf(0) } // bumps when the rider turns location on
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        locationGranted = result.values.any { it }
    }
    val turnOnLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == android.app.Activity.RESULT_OK) locationOnTick++
    }
    LaunchedEffect(Unit) {
        if (!locationGranted) {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val camera = rememberCameraPositionState {
        position = TripStore.currentLocation
            ?.let { CameraPosition.fromLatLngZoom(it.toLatLng(), 16f) }
            ?: CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }
    LaunchedEffect(locationGranted, locationOnTick) {
        if (!locationGranted) return@LaunchedEffect
        // With the phone's Location switched off no app gets a fix: offer the one-tap system dialog.
        TripStore.locationSettingsResolution(context)?.let { turnOnLocationLauncher.launch(it); return@LaunchedEffect }
        var first = TripStore.currentLocation == null
        TripStore.locate(context) { fix ->
            val target = CameraPosition.fromLatLngZoom(fix.toLatLng(), 16f)
            // Jump on the first fix (works before the map has loaded), glide for refinements.
            if (first) { camera.position = target; first = false }
            else runCatching { camera.animate(CameraUpdateFactory.newCameraPosition(target), 600) }
        }
    }

    // ---- Draggable "Where to" sheet --------------------------------------------------------
    // offset 0 = fully open; offset maxOffset = collapsed to the handle + title ("peek").
    // Drag or fling to move it, tap the handle to toggle. The nav bar is drawn after the sheet,
    // so a collapsed sheet slides underneath it.
    var stackHeightPx by remember { mutableIntStateOf(0) }   // sheet + nav bar
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var sheetTopPx by remember { mutableFloatStateOf(0f) }
    var titleBottomPx by remember { mutableFloatStateOf(0f) }
    val peekPx = (titleBottomPx - sheetTopPx + with(density) { 20.dp.toPx() }).coerceAtLeast(0f)
    val maxOffset = (sheetHeightPx - peekPx).coerceAtLeast(0f)
    val sheetOffset = remember { Animatable(0f) }
    var expanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(maxOffset) {
        sheetOffset.updateBounds(0f, maxOffset)
        sheetOffset.snapTo(if (expanded) 0f else maxOffset)
    }
    fun settle(velocity: Float) {
        expanded = when {
            velocity > 1200f -> false
            velocity < -1200f -> true
            else -> sheetOffset.value < maxOffset / 2
        }
        scope.launch {
            sheetOffset.animateTo(if (expanded) 0f else maxOffset, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow), velocity)
        }
    }
    val dragState = rememberDraggableState { delta ->
        scope.launch { sheetOffset.snapTo((sheetOffset.value + delta).coerceIn(0f, maxOffset)) }
    }
    // The map treats what the sheet covers (when settled) as padding, so the rider's dot and the
    // Google logo stay in the visible part.
    val mapBottomPx = (stackHeightPx - if (expanded) 0f else maxOffset).coerceAtLeast(0f)

    Box(Modifier.fillMaxSize()) {
        // Real Google map (falls back to the drawn backdrop without a MAPS_API_KEY). The fake
        // nearby cars are gone: live driver positions arrive with the driver app in phase 3.
        NovaMap(
            cameraPositionState = camera,
            showMyLocation = locationGranted,
            contentPadding = PaddingValues(top = 80.dp, bottom = with(density) { mapBottomPx.toDp() }),
            fallback = { userDot(196f, 252f, c.primary) },
        )
        LaunchedEffect(Unit) {
            // Home starts every trip: an earlier, cancelled or abandoned plan doesn't carry over.
            TripStore.startNewPlan()
            ProfileStore.refreshIfNeeded()
            // A request still open on the server (app was closed mid-booking): go back to following it.
            if (TripStore.loadActiveRide()?.status == "requested") nav.go(com.nova.rider.navigation.FindingDriver)
        }

        val user = AuthStore.session?.user
        val riderName = user?.firstName?.trim()?.ifBlank { null }
        val riderInitials = listOfNotNull(
            user?.firstName?.firstOrNull()?.uppercaseChar(),
            user?.lastName?.firstOrNull()?.uppercaseChar(),
        ).joinToString("").ifBlank { user?.email?.take(1)?.uppercase().orEmpty() }
        val homePlace = ProfileStore.homePlace()
        val workPlace = ProfileStore.workPlace()
        val customPlaces = ProfileStore.customPlaces()

        // Expressive search bar
        Surface(
            onClick = { nav.go(Search) },
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(56.dp),
            shape = CircleShape,
            color = NovaTheme.colors.navBar,
            shadowElevation = 3.dp,
        ) {
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { nav.go(Account) }) { Icon(NovaIcons.Menu, "Open menu") }
                Text("Search places, addresses", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                // Avatar opens the account, like the menu button (the rest of the bar opens search).
                Box(
                    Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "Open account") { nav.go(Account) },
                    contentAlignment = Alignment.Center,
                ) { CookieAvatar(riderInitials, c.tertiaryContainer, c.onTertiaryContainer, size = 40.dp) }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).onSizeChanged { stackHeightPx = it.height }) {
            Box(
                Modifier
                    .offset { IntOffset(0, sheetOffset.value.roundToInt()) }
                    .draggable(dragState, Orientation.Vertical, onDragStopped = { settle(it) })
                    .onGloballyPositioned { sheetTopPx = it.positionInRoot().y; sheetHeightPx = it.size.height },
            ) {
                MapSheet(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        GreetingTitle(
                            firstName = riderName,
                            modifier = Modifier.onGloballyPositioned { titleBottomPx = it.positionInRoot().y + it.size.height },
                        )
                        NovaConnectedGroup(
                            options = listOf("Now", "Schedule", "For someone"),
                            // Now / Schedule / For someone set how this trip is booked, then the
                            // rider plans the route as usual; Ride options finishes accordingly.
                            selectedIndex = TripStore.bookingMode.ordinal,
                            onSelect = {
                                when (TripStore.BookingMode.entries[it]) {
                                    TripStore.BookingMode.Now -> TripStore.rideFor(null)
                                    TripStore.BookingMode.Schedule -> { TripStore.bookingMode = TripStore.BookingMode.Schedule; nav.go(Search) }
                                    // Who's riding comes first, then the route (pickup asks for them).
                                    TripStore.BookingMode.ForSomeone -> nav.go(com.nova.rider.navigation.ChooseRider(then = "search"))
                                }
                            },
                            leadingIconForChecked = NovaIcons.Check,
                            weights = listOf(1f, 1f, 1.2f),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SavedPlaceCard(
                                "Home",
                                homePlace?.let { shortAddress(it.address) } ?: "Add address",
                                c.primaryContainer,
                                c.onPrimaryContainer,
                                Modifier.weight(1f),
                                // Set: ride there. Not set (or saved without coordinates): open the Home editor.
                                onClick = { nav.go(if (homePlace != null && TripStore.chooseDropoff(homePlace)) Search else com.nova.rider.navigation.SetUpPlace("home")) },
                            ) {
                                ShapeBadge(NovaShape.Cookie, 44.dp, c.primary) { Icon(NovaIcons.HomeFilled, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            }
                            SavedPlaceCard(
                                "Work",
                                workPlace?.let { shortAddress(it.address) } ?: "Add address",
                                c.tertiaryContainer,
                                c.onTertiaryContainer,
                                Modifier.weight(1f),
                                onClick = { nav.go(if (workPlace != null && TripStore.chooseDropoff(workPlace)) Search else com.nova.rider.navigation.SetUpPlace("work")) },
                            ) {
                                IconTile(NovaIcons.Work, c.tertiary, Color.White, size = 44.dp)
                            }
                        }
                        SegmentedColumn {
                            if (customPlaces.isEmpty()) {
                                NovaListItem(
                                    0,
                                    1,
                                    "Save favourite places",
                                    supporting = "Add frequent spots for one-tap rides",
                                    leading = { Icon(NovaIcons.Pin, null) },
                                    onClick = { nav.go(com.nova.rider.navigation.SavedPlaces) },
                                )
                            } else {
                                val shown = customPlaces.take(2)
                                // More than two favourites: the last row opens the full list.
                                val rows = shown.size + if (customPlaces.size > 2) 1 else 0
                                shown.forEachIndexed { idx, place ->
                                    NovaListItem(
                                        idx,
                                        rows,
                                        place.label,
                                        supporting = shortAddress(place.address),
                                        leading = { Icon(NovaIcons.Pin, null) },
                                        onClick = { nav.go(if (TripStore.chooseDropoff(place)) Search else com.nova.rider.navigation.SavedPlaces) },
                                    )
                                }
                                if (customPlaces.size > 2) {
                                    NovaListItem(
                                        rows - 1,
                                        rows,
                                        "All saved places",
                                        supporting = "${customPlaces.size} places",
                                        leading = { Icon(NovaIcons.Pin, null) },
                                        trailing = { Icon(NovaIcons.Chev, null) },
                                        onClick = { nav.go(com.nova.rider.navigation.SavedPlaces) },
                                    )
                                }
                            }
                        }
                    }
                }
                // Tap target over the sheet's drag handle: toggles open/collapsed.
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(36.dp)
                        .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { settle(if (expanded) 2000f else -2000f) },
                )
            }
            RiderNavigationBar(selected = RiderTab.Ride, onSelect = nav::tab)
        }
    }
}

/**
 * "Where to, Akash?" The name line shrinks (40 sp down to 28 sp) to fit one line, and a name too
 * long even then is cut with an ellipsis while the "?" stays. No name yet: just "Where to?".
 */
@Composable
private fun GreetingTitle(firstName: String?, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp, color = MaterialTheme.colorScheme.onSurface)
    Column(modifier.semantics(mergeDescendants = true) {}) {
        if (firstName == null) {
            Text("Where to?", style = style)
            return@Column
        }
        Text("Where to,", style = style)
        Row(verticalAlignment = Alignment.Bottom) {
            androidx.compose.foundation.text.BasicText(
                firstName,
                modifier = Modifier.weight(1f, fill = false),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 40.sp, stepSize = 2.sp),
            )
            Text("?", style = style)
        }
    }
}

/**
 * The part of an address a rider recognises: "12 Queen Street" from
 * "12 Queen Street, Auckland CBD, Auckland 1010, New Zealand".
 */
internal fun shortAddress(address: String): String =
    address.substringBefore(',').trim().ifBlank { address.trim() }

@Composable
private fun SavedPlaceCard(
    title: String,
    eta: String,
    container: Color,
    content: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(onClick = onClick, modifier = modifier.height(72.dp), shape = RoundedCornerShape(24.dp), color = container, contentColor = content) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            icon()
            // One line each, cut with an ellipsis: the card is a fixed 72 dp tall.
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(eta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Route planner: pickup (auto-filled with the device's location), up to 3 stops, destination.
 * Typing in any field shows Places suggestions for that field; when nothing is being typed, the
 * map below previews pickup -> stops -> destination with the real route.
 */
@Composable
fun SearchScreen(nav: Navigator) {
    val context = LocalContext.current
    val planner = rememberRoutePlannerState()

    LaunchedEffect(Unit) {
        ProfileStore.refreshIfNeeded()
        // Open on the first empty field: destination, else an empty stop. A complete route opens on the map.
        val emptyStop = TripStore.stops.indexOfFirst { it == null }
        when {
            // Booking for someone: ask where to pick them up before anything else.
            TripStore.passenger != null && TripStore.pickup == null -> planner.startEditing(RouteField.Pickup)
            TripStore.dropoff == null -> planner.startEditing(RouteField.Dropoff)
            emptyStop >= 0 -> planner.startEditing(RouteField.Stop(emptyStop))
        }
        // Pickup = most accurate current location, unless the rider already chose another place.
        if (TripStore.pickupIsCurrentLocation) TripStore.locate(context)
        // Nearest landmarks as suggestions before typing (one Nearby Search per position).
        TripStore.loadNearby()
    }
    // While typing, back closes the keyboard/suggestions first; then it leaves the screen.
    BackHandler(enabled = planner.isEditing) { planner.stopEditing() }

    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        Row(
            Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { nav.back() }, modifier = Modifier.width(48.dp)) { Icon(NovaIcons.Back, "Back") }
            RouteInputs(planner, Modifier.weight(1f))
        }

        // Quick fills: Home/Work go into the field being edited (destination by default).
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Who's riding, when it isn't the rider: tap to change, ✕ to ride yourself.
            TripStore.passenger?.let { p ->
                InputChip(
                    selected = true,
                    onClick = { nav.go(com.nova.rider.navigation.ChooseRider(then = "back")) },
                    label = { Text("For ${p.name}", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(NovaIcons.People, null, Modifier.size(InputChipDefaults.IconSize)) },
                    trailingIcon = {
                        Icon(
                            NovaIcons.Close, "Ride myself",
                            Modifier.size(InputChipDefaults.IconSize).clickable(onClickLabel = "Ride myself") { TripStore.rideFor(null) },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(40.dp).widthIn(max = 220.dp),
                )
            }
            listOf("Home" to (NovaIcons.Home to ProfileStore.homePlace()), "Work" to (NovaIcons.Work to ProfileStore.workPlace())).forEach { (label, pair) ->
                val (icon, saved) = pair
                AssistChip(
                    onClick = {
                        val place = saved?.toGeoPlace()
                        if (place == null) nav.go(com.nova.rider.navigation.SetUpPlace(label.lowercase())) else planner.assign(place)
                    },
                    label = { Text(label, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, c.outline),
                    modifier = Modifier.height(40.dp),
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            // While a field is being edited: nearby/saved suggestions, then search results.
            // Once every field is filled, the map shows the route from pickup to destination.
            if (planner.isEditing) {
                RouteSuggestions(planner, Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()))
            } else {
                RoutePreview(Modifier.fillMaxSize(), onContinue = { nav.go(RideOptions) })
            }
        }
    }
}

/**
 * Map under the route inputs: pickup, stops and destination markers plus the real route once
 * the trip is complete. Fetching the route is the quote call, which also locks prices, so the
 * Ride options screen opens instantly with the same quote.
 */
@Composable
private fun RoutePreview(modifier: Modifier, onContinue: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val density = LocalDensity.current
    var buttonHeightPx by remember { mutableIntStateOf(0) }
    val pickup = TripStore.pickup
    val stops = TripStore.stops.filterNotNull()
    val dropoff = TripStore.dropoff
    val quote = TripStore.quote
    val complete = TripStore.routeComplete

    LaunchedEffect(pickup, TripStore.stops, dropoff) {
        TripStore.dropExpiredQuote()
        if (complete && TripStore.quote == null && !TripStore.quoting) TripStore.requestQuote()
    }
    val camera = rememberCameraPositionState {
        position = (pickup?.point ?: TripStore.currentLocation)
            ?.let { CameraPosition.fromLatLngZoom(it.toLatLng(), 15f) }
            ?: CameraPosition.fromLatLngZoom(LatLng(0.0, 0.0), 2f)
    }
    val route = remember(quote?.polyline) { decodeRoute(quote?.polyline.orEmpty()) }
    LaunchedEffect(route, pickup, stops, dropoff, buttonHeightPx) {
        val points: List<LatLng> = route.ifEmpty {
            (listOfNotNull(pickup?.point) + stops.map { it.point } + listOfNotNull(dropoff?.point)).map { it.toLatLng() }
        }
        runCatching { camera.fitTo(points, paddingPx = with(density) { 56.dp.roundToPx() }) }
    }

    Box(modifier) {
        NovaMap(
            cameraPositionState = camera,
            showMyLocation = TripStore.hasLocationPermission(context),
            contentPadding = PaddingValues(bottom = with(density) { buttonHeightPx.toDp() }),
        ) {
            quote?.let { RouteLine(it.polyline) }
            pickup?.let { PickupMarker(it.point) }
            stops.forEach { StopMarker(it.point) }
            dropoff?.let { DestinationMarker(it.point) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)
                .onSizeChanged { buttonHeightPx = it.height },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when {
                !complete -> {}
                TripStore.quoting && quote == null -> NovaButton("Finding the best route…", onClick = {}, enabled = false)
                quote == null -> Surface(shape = RoundedCornerShape(16.dp), color = c.errorContainer, contentColor = c.onErrorContainer) {
                    Text(tripErrorMessage(TripStore.quoteError), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                else -> {
                    val cheapest = quote.options.minByOrNull { it.fareCents }
                    MapLabel("${TripFormat.distance(quote.distanceMeters)} · ${TripFormat.duration(quote.durationSeconds)}")
                    NovaButton(
                        cheapest?.let { "Choose a ride · from ${TripFormat.money(it.fareCents, quote.currency)}" } ?: "Choose a ride",
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}
