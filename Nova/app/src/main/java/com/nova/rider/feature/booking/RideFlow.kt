package com.nova.rider.feature.booking

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.nova.designsystem.components.NovaTextField
import com.nova.rider.data.auth.Passenger
import com.nova.rider.data.auth.Ride
import com.nova.rider.navigation.NoDrivers
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.MapBackdrop
import com.nova.designsystem.components.MapSheet
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaConnectedGroup
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.NovaTonalButton
import com.nova.designsystem.components.NovaOutlinedButton
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.components.RiderTab
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.rememberCameraPositionState
import com.nova.rider.data.auth.GeoPoint
import com.nova.rider.data.trip.TripFormat
import com.nova.rider.data.trip.TripStore
import com.nova.rider.data.trip.tripErrorMessage
import com.nova.rider.feature.common.DestinationMarker
import com.nova.rider.feature.common.NovaMap
import com.nova.rider.feature.common.PickupMarker
import com.nova.rider.feature.common.RouteLine
import com.nova.rider.feature.common.StopMarker
import com.nova.rider.feature.common.decodeRoute
import com.nova.rider.feature.common.fitTo
import com.nova.rider.feature.common.toLatLng
import kotlinx.coroutines.launch
import com.nova.rider.data.Sample
import com.nova.rider.feature.common.DotKind
import com.nova.rider.feature.common.MapFab
import com.nova.rider.feature.common.MapLabel
import com.nova.rider.feature.common.RouteDot
import com.nova.rider.navigation.DriverEnRoute
import com.nova.rider.navigation.FindingDriver
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.RideOptions
import com.nova.rider.navigation.Schedule
import com.nova.rider.navigation.ShareTrip

@Composable
fun RideOptionsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    // Who's riding is chosen on its own screen (from Home's "For someone", this chip or the menu).
    fun chooseRider() = nav.go(com.nova.rider.navigation.ChooseRider(then = "back"))
    val scheduling = TripStore.bookingMode == TripStore.BookingMode.Schedule
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }

    val pickupPlace = TripStore.pickup
    val dropoffPlace = TripStore.dropoff
    val quote = TripStore.quote
    // Upfront prices from the server (locked there for 5 min). Re-entering this screen for the
    // same trip reuses the quote server side, so back/forward doesn't cost another Routes call.
    LaunchedEffect(pickupPlace, dropoffPlace) {
        TripStore.dropExpiredQuote()
        if (TripStore.quote == null) TripStore.requestQuote()
    }
    val options = quote?.options.orEmpty()
    // The chosen ride type lives in TripStore so Schedule books the same one.
    val selected = options.firstOrNull { it.productId == TripStore.productId } ?: options.firstOrNull()
    // Prices only exist with a quote, which always carries its currency (from the service area).
    fun price(cents: Long) = quote?.let { TripFormat.money(cents, it.currency) } ?: ""

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom((pickupPlace?.point ?: GeoPoint(0.0, 0.0)).toLatLng(), if (pickupPlace != null) 13f else 2f)
    }
    val route = remember(quote?.polyline) { decodeRoute(quote?.polyline.orEmpty()) }
    LaunchedEffect(route, sheetHeightPx) {
        val points = route.ifEmpty { listOfNotNull(pickupPlace?.point?.toLatLng(), dropoffPlace?.point?.toLatLng()) }
        if (sheetHeightPx > 0) camera.fitTo(points, paddingPx = with(density) { 48.dp.roundToPx() })
    }

    Box(Modifier.fillMaxSize()) {
        NovaMap(
            cameraPositionState = camera,
            contentPadding = PaddingValues(top = 72.dp, bottom = with(density) { sheetHeightPx.toDp() }),
            fallback = {
                route("M82 300 L 74 140 L 190 132 C 240 130 260 190 304 180 L 310 110", c.primary, 6f)
                pickup(82f, 300f); destination(310f, 110f)
            },
        ) {
            quote?.let { RouteLine(it.polyline) }
            pickupPlace?.let { PickupMarker(it.point) }
            TripStore.stops.filterNotNull().forEach { StopMarker(it.point) }
            dropoffPlace?.let { DestinationMarker(it.point) }
        }
        Row(
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapFab(NovaIcons.Back, "Back", onClick = { nav.back() })
            Spacer(Modifier.weight(1f))
            quote?.let { MapLabel("${TripFormat.duration(it.durationSeconds)} · ${TripFormat.arrival(it.durationSeconds)}") }
        }
        MapSheet(Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetHeightPx = it.height }, color = c.surface, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Choose a ride", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp))
                    val subtitle = buildString {
                        append(pickupPlace?.name ?: "Pickup")
                        TripStore.stops.filterNotNull().forEach { append(" → ").append(it.name) }
                        append(" → ")
                        append(dropoffPlace?.name ?: "Destination")
                        quote?.let { append(" · ").append(TripFormat.distance(it.distanceMeters)) }
                    }
                    Text(subtitle, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = c.onSurfaceVariant)
                }
                when {
                    TripStore.quoting && quote == null -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                    quote == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (pickupPlace == null || dropoffPlace == null) "Choose a pickup and destination first."
                            else tripErrorMessage(TripStore.quoteError),
                            style = MaterialTheme.typography.bodyLarge,
                            color = c.onSurfaceVariant,
                        )
                        if (pickupPlace != null && dropoffPlace != null) {
                            NovaTonalButton("Try again", onClick = { scope.launch { TripStore.requestQuote() } })
                        }
                    }
                    else -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        options.forEachIndexed { i, r ->
                            RideRow(
                                // Driver ETAs ("3 min away") need live drivers (phase 3); until then
                                // the row shows the product's description from the server.
                                name = r.name, seats = r.seats, meta = r.description, price = price(r.fareCents),
                                selected = r.productId == selected?.productId, first = i == 0, last = i == options.lastIndex,
                                onClick = { TripStore.productId = r.productId },
                            )
                        }
                    }
                }
                NovaConnectedGroup(
                    options = PAYMENT_METHODS.map { it.second },
                    selectedIndex = PAYMENT_METHODS.indexOfFirst { it.first == TripStore.paymentMethod }.coerceAtLeast(0),
                    onSelect = { TripStore.paymentMethod = PAYMENT_METHODS[it].first }, height = 40.dp,
                    checkedContainer = c.secondaryContainer, checkedContent = c.onSecondaryContainer,
                )
                TripStore.passenger?.let { p ->
                    InputChip(
                        selected = true,
                        onClick = { chooseRider() },
                        label = { Text("For ${p.name}", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(NovaIcons.People, null, Modifier.size(InputChipDefaults.IconSize)) },
                        trailingIcon = {
                            IconButton(onClick = { TripStore.rideFor(null) }, modifier = Modifier.size(24.dp)) {
                                Icon(NovaIcons.Close, "Ride for myself", Modifier.size(InputChipDefaults.IconSize))
                            }
                        },
                    )
                }
                TripStore.bookError?.let {
                    Text(tripErrorMessage(it), style = MaterialTheme.typography.bodyMedium, color = c.error)
                }
                Box {
                    SplitButtonLayout(
                        modifier = Modifier.fillMaxWidth(),
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                // Books against the server-locked quote; the app never sends a price.
                                onClick = {
                                    if (selected != null) {
                                        TripStore.productId = selected.productId
                                        // "For someone" without a rider chosen: ask, never book for me silently.
                                        if (TripStore.bookingMode == TripStore.BookingMode.ForSomeone && TripStore.passenger == null) {
                                            chooseRider()
                                            return@LeadingButton
                                        }
                                        // Home's "Schedule": pick the time next, booking happens there.
                                        if (scheduling) nav.go(Schedule)
                                        else scope.launch { if (TripStore.book() != null) nav.go(FindingDriver) }
                                    }
                                },
                                enabled = selected != null && !TripStore.booking,
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                shapes = SplitButtonDefaults.leadingButtonShapesFor(64.dp),
                                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(64.dp),
                            ) {
                                if (TripStore.booking) LoadingIndicator(Modifier.size(32.dp), color = c.onPrimary)
                                else Text(selected?.let { "${if (scheduling) "Schedule" else "Book"} ${it.name} · ${price(it.fareCents)}" } ?: "Book a ride",style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                            }
                        },
                        trailingButton = {
                            SplitButtonDefaults.TrailingButton(
                                checked = menu,
                                onCheckedChange = { menu = it },
                                modifier = Modifier.size(64.dp),
                                shapes = SplitButtonDefaults.trailingButtonShapesFor(64.dp),
                                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(64.dp),
                            ) {
                                val rotation by androidx.compose.animation.core.animateFloatAsState(if (menu) 180f else 0f, MaterialTheme.motionScheme.fastSpatialSpec())
                                Icon(NovaIcons.Down, "More booking options", Modifier.size(24.dp).graphicsLayer { rotationZ = rotation })
                            }
                        },
                    )
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                        DropdownMenuItem(text = { Text("Schedule for later") }, leadingIcon = { Icon(NovaIcons.Calendar, null) }, onClick = { menu = false; nav.go(Schedule) })
                        DropdownMenuItem(text = { Text("Add a stop") }, leadingIcon = { Icon(NovaIcons.Plus, null) }, onClick = {
                            // Stops are edited inline in the route planner (Search).
                            menu = false
                            TripStore.addStop()
                            nav.popTo(com.nova.rider.navigation.Search)
                        })
                        DropdownMenuItem(text = { Text("Book for someone else") }, leadingIcon = { Icon(NovaIcons.People, null) }, onClick = { menu = false; chooseRider() })
                    }
                }
            }
        }
    }
}

/** Server payment values with their labels, in the order shown. */
private val PAYMENT_METHODS = listOf("gpay" to "GPay", "card" to "Card", "credit" to "Credit")

fun paymentLabel(method: String): String = PAYMENT_METHODS.firstOrNull { it.first == method }?.second ?: method

@Composable
private fun RideRow(name: String, seats: Int, meta: String, price: String, selected: Boolean, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.Dp>()
    val top by animateDpAsState(if (selected || first) 20.dp else 4.dp, spec)
    val bottom by animateDpAsState(if (selected || last) 20.dp else 4.dp, spec)
    val margin by animateDpAsState(if (selected) 4.dp else 0.dp, spec)
    val container by animateColorAsState(if (selected) c.primaryContainer else c.surfaceContainer, MaterialTheme.motionScheme.defaultEffectsSpec())
    val content = if (selected) c.onPrimaryContainer else c.onSurface
    Row(
        Modifier
            .padding(vertical = margin)
            .fillMaxWidth()
            .clip(RoundedCornerShape(top, top, bottom, bottom))
            .background(container)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (selected) ShapeBadge(NovaShape.Cookie, 48.dp, c.primary)
            else Box(Modifier.padding(2.dp).fillMaxSize().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerHighest))
            Icon(NovaIcons.CarSide, null, tint = if (selected) Color.White else c.onSurface, modifier = Modifier.width(30.dp).height(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = content)
                Text(" · $seats seats", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = content)
            }
            Text(meta, fontSize = 13.sp, color = content)
        }
        Text(price, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = content)
    }
}

@Composable
fun ScheduleScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val zone = remember { java.time.ZoneId.systemDefault() }
    val today = remember { java.time.LocalDate.now(zone) }
    // Today plus the next three days, as in the design (the server accepts up to 30 days ahead).
    val dates = remember(today) { (0L..3L).map { today.plusDays(it) } }
    val days = dates.mapIndexed { i, d ->
        (if (i == 0) "Today" else d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())) to d.dayOfMonth.toString()
    }
    // Default: the first 5-minute slot at least 30 minutes from now.
    val initial = remember {
        java.time.ZonedDateTime.now(zone).plusMinutes(30).let { t -> t.plusMinutes(((5 - t.minute % 5) % 5).toLong()) }
    }
    var day by rememberSaveable { mutableIntStateOf(java.time.temporal.ChronoUnit.DAYS.between(today, initial.toLocalDate()).toInt().coerceIn(0, 3)) }
    var minutes by rememberSaveable { mutableIntStateOf(initial.hour * 60 + initial.minute) } // minutes since midnight
    val hour24 = minutes / 60
    val am = hour24 < 12
    val hour12 = ((hour24 + 11) % 12) + 1
    val mm = minutes % 60
    val label = "%d:%02d %s".format(hour12, mm, if (am) "AM" else "PM")
    val pickupAt = dates[day].atStartOfDay(zone).plusMinutes(minutes.toLong())
    // The server needs 15 minutes to find a driver; check here so the button explains itself.
    val tooSoon = pickupAt.isBefore(java.time.ZonedDateTime.now(zone).plusMinutes(15))

    val pickupPlace = TripStore.pickup
    val dropoffPlace = TripStore.dropoff
    LaunchedEffect(pickupPlace, dropoffPlace) {
        TripStore.dropExpiredQuote()
        if (TripStore.quote == null) TripStore.requestQuote()
    }
    val quote = TripStore.quote
    val option = quote?.options?.let { o -> o.firstOrNull { it.productId == TripStore.productId } ?: o.firstOrNull() }
    val fare = option?.let { TripFormat.money(it.fareCents, quote.currency) }
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Close, "Close") }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Schedule a ride", style = MaterialTheme.typography.headlineLarge)
            Text(
                if (pickupPlace != null && dropoffPlace != null) "${pickupPlace.name} → ${dropoffPlace.name}" else "Choose a pickup and destination first.",
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
        }
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            days.forEachIndexed { i, (name, num) ->
                val on = i == day
                val radius by animateDpAsState(if (on) 40.dp else 20.dp, MaterialTheme.motionScheme.fastSpatialSpec())
                val bg by animateColorAsState(if (on) c.primary else c.surfaceContainerHigh, MaterialTheme.motionScheme.defaultEffectsSpec())
                Column(
                    Modifier.weight(1f).height(80.dp).clip(RoundedCornerShape(radius)).background(bg)
                        .selectable(on, onClick = { day = i }, role = Role.RadioButton),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    val fg = if (on) Color.White else c.onSurface
                    Text(name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                    Text(num, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold, color = fg)
                }
            }
        }
        Column(
            Modifier.clip(RoundedCornerShape(28.dp)).background(c.surfaceContainer).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Pickup time", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(16.dp)).background(c.primaryContainer).border(2.dp, c.primary, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("%02d".format(hour12), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer) }
                Text(":", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                Box(Modifier.weight(1f).height(88.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    Text("%02d".format(mm), fontSize = 56.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(Modifier.width(56.dp).selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AmPm("AM", am, RoundedCornerShape(12.dp, 12.dp, 4.dp, 4.dp)) { if (!am) minutes -= 12 * 60 }
                    AmPm("PM", !am, RoundedCornerShape(4.dp, 4.dp, 12.dp, 12.dp)) { if (am) minutes += 12 * 60 }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallOutlined("−15 min") { minutes = (minutes - 15 + 1440) % 1440 }
                SmallOutlined("+15 min") { minutes = (minutes + 15) % 1440 }
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(NovaPalette.Mint).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NovaIcons.ShieldCheck, null, tint = NovaPalette.OnMint)
            Text(
                buildString {
                    append(if (fare != null) "Fare locked at $fare" else "Fare locked when you schedule")
                    option?.let { append(" for ").append(it.name) }
                    append(" · we start finding your driver 15 min before pickup · cancel free from Activity until then.")
                },
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = NovaPalette.OnMint,
            )
        }
        Spacer(Modifier.weight(1f))
        when {
            tooSoon -> Text(tripErrorMessage("schedule_too_soon"), style = MaterialTheme.typography.bodyMedium, color = c.error)
            TripStore.bookError != null -> Text(tripErrorMessage(TripStore.bookError), style = MaterialTheme.typography.bodyMedium, color = c.error)
            quote == null && !TripStore.quoting && TripStore.quoteError != null -> Text(tripErrorMessage(TripStore.quoteError), style = MaterialTheme.typography.bodyMedium, color = c.error)
        }
        NovaButton(
            if (TripStore.booking) "Scheduling…" else "Schedule for ${days[day].first}, $label",
            enabled = !tooSoon && !TripStore.booking && pickupPlace != null && dropoffPlace != null,
            onClick = {
                scope.launch {
                    val booked = TripStore.book(scheduledAt = pickupAt.toInstant().toString())
                    if (booked != null) {
                        // A scheduled ride is done here; the planner starts fresh for the next trip.
                        TripStore.clear()
                        nav.tab(RiderTab.Activity)
                    }
                }
            },
        )
    }
}

@Composable
private fun AmPm(text: String, on: Boolean, shape: RoundedCornerShape, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().height(43.dp).clip(shape).background(if (on) c.tertiaryContainer else c.surfaceContainerHighest)
            .selectable(on, onClick = onClick, role = Role.RadioButton),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 15.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, color = if (on) c.onTertiaryContainer else c.onSurfaceVariant) }
}

@Composable
private fun SmallOutlined(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = Color.Transparent, modifier = Modifier.height(36.dp)) {
        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun FindingDriverScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var sheetHeightPx by remember { mutableIntStateOf(0) }
    var cancelling by remember { mutableStateOf(false) }
    val ride = TripStore.ride

    // Follow the ride. Polling every 3 s is the stopgap until dispatch pushes updates over a
    // WebSocket (driver phase); a GET by primary key is cheap for the server.
    LaunchedEffect(Unit) {
        if (TripStore.ride == null && TripStore.loadActiveRide() == null) {
            nav.back()
            return@LaunchedEffect
        }
        while (true) {
            val now = TripStore.ride ?: break
            when (now.status) {
                "no_drivers" -> { nav.replaceTop(NoDrivers); break }
                "accepted", "arriving", "in_progress" -> { nav.replaceTop(DriverEnRoute); break }
                "cancelled", "completed" -> break
            }
            kotlinx.coroutines.delay(3_000)
            TripStore.refreshRide()
        }
    }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom((ride?.pickup?.point ?: GeoPoint(0.0, 0.0)).toLatLng(), if (ride != null) 15f else 2f)
    }
    val pulse = rememberInfiniteTransition(label = "rings")
    val r1 by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "r1")
    val r2 by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart, StartOffset(1200)), label = "r2")
    Box(Modifier.fillMaxSize()) {
        NovaMap(
            cameraPositionState = camera,
            contentPadding = PaddingValues(bottom = with(density) { sheetHeightPx.toDp() }),
            gesturesEnabled = false,
        )
        // Search rings over the pickup: the map centres the pickup in the area above the sheet.
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    listOf(r1, r2).forEach { t ->
                        Box(
                            Modifier.fillMaxSize().graphicsLayer {
                                val s = 0.3f + 1.3f * t; scaleX = s; scaleY = s; alpha = 0.6f * (1f - t) * 0.2f
                            }.clip(CircleShape).background(c.primary),
                        )
                    }
                    Box(Modifier.size(22.dp).clip(CircleShape).background(c.onSurface), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(with(density) { sheetHeightPx.toDp() }))
        }
        MapSheet(Modifier.align(Alignment.BottomCenter).onSizeChanged { sheetHeightPx = it.height }, showHandle = false, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 28.dp, 16.dp, 28.dp)) {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ContainedLoadingIndicator(
                        modifier = Modifier.size(96.dp),
                        containerColor = c.primaryContainer,
                        indicatorColor = c.primary,
                    )
                    Text(ride?.let { "Finding your ${it.productName} ride" } ?: "Finding your ride",style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(
                        buildString {
                            append("Matching with drivers near ").append(ride?.pickup?.name ?: "your pickup")
                            ride?.passenger?.let { append(" · for ").append(it.name) }
                        },
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                if (ride != null) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SummaryRow(RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)) { RouteDot(DotKind.Pickup); Text(ride.pickup.name, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    SummaryRow(RoundedCornerShape(4.dp)) { RouteDot(DotKind.Destination); Text(ride.dropoff.name, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    SummaryRow(RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)) {
                        Text("${ride.productName} · ${paymentLabel(ride.paymentMethod)}", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(TripFormat.money(ride.fareCents, ride.currency), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                ride?.passenger?.let { PassengerHandoff(ride, it) }
                TripStore.bookError?.let { Text(tripErrorMessage(it), style = MaterialTheme.typography.bodyMedium, color = c.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaOutlinedButton(
                        if (cancelling) "Cancelling…" else "Cancel request",
                        onClick = {
                            if (!cancelling) scope.launch {
                                cancelling = true
                                if (TripStore.cancelRide("rider_cancelled_while_matching")) {
                                    TripStore.forgetRide()
                                    nav.popTo(RideOptions)
                                }
                                cancelling = false
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // Android share sheet with the public live-tracking link (WhatsApp, Messages, ...).
                    val context = androidx.compose.ui.platform.LocalContext.current
                    NovaTonalButton("Share trip", onClick = { ride?.let { shareTrip(context, it, null) } }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * After booking for someone else:
 *  * On Nova: the ride is already in their app (in-app notification, live updates later), so we
 *    just confirm it.
 *  * Not on Nova: nothing reaches them automatically, so the booker sends the details from their
 *    own phone: an SMS composer addressed to that number with the pickup, drop-off and a live
 *    tracking link (opens Nova if they install it, the web page otherwise). No SMS provider,
 *    no cost to Nova, and it comes from someone they know. The driver also sees their name and
 *    calls that number on arrival.
 */
@Composable
private fun PassengerHandoff(ride: Ride, passenger: Passenger) {
    val c = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val first = passenger.name.substringBefore(' ')
    if (passenger.onNova) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NovaPalette.Mint).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NovaIcons.Check, null, tint = NovaPalette.OnMint)
            Text("$first has this ride in their Nova app.", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NovaPalette.OnMint)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.tertiaryContainer).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeBadge(NovaShape.Cookie, 40.dp, c.tertiary) { Icon(NovaIcons.Msg, null, tint = c.onTertiary, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text("$first doesn't have Nova", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                Text(
                    "Text them the pickup and a live tracking link. The driver will call them on arrival.",
                    fontSize = 13.sp, lineHeight = 18.sp, color = c.onTertiaryContainer,
                )
            }
        }
        NovaButton(
            "Text $first the trip details",
            onClick = { textPassenger(context, ride, passenger) },
            height = 48.dp, leadingIcon = NovaIcons.Send,
            colors = ButtonDefaults.buttonColors(containerColor = c.tertiary, contentColor = c.onTertiary),
        )
    }
}

private fun tripMessage(ride: Ride, passenger: Passenger?): String = buildString {
    if (passenger != null) append("Hi ${passenger.name.substringBefore(' ')}, I booked you a Nova ${ride.productName} ride.\n")
    else append("I'm taking a Nova ${ride.productName} ride.\n")
    append("Pickup: ${ride.pickup.name}\n")
    append("Drop-off: ${ride.dropoff.name}")
    if (ride.trackUrl.isNotBlank()) append("\nTrack it live: ${ride.trackUrl}")
}

/** SMS composer from the rider's own phone, addressed to the passenger; share sheet if no SMS app. */
private fun textPassenger(context: android.content.Context, ride: Ride, passenger: Passenger) {
    val sms = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:${passenger.phone}"))
        .putExtra("sms_body", tripMessage(ride, passenger))
    try {
        context.startActivity(sms)
    } catch (_: android.content.ActivityNotFoundException) {
        shareTrip(context, ride, passenger)
    }
}

private fun shareTrip(context: android.content.Context, ride: Ride, passenger: Passenger?) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, tripMessage(ride, passenger ?: ride.passenger))
    }
    context.startActivity(android.content.Intent.createChooser(send, "Share trip"))
}

@Composable
private fun SummaryRow(shape: RoundedCornerShape, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}
