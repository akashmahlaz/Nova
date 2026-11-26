package com.nova.driver.feature.shift

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.nova.driver.data.DriverStore
import com.nova.driver.data.formatKm
import com.nova.driver.data.formatMinutes
import com.nova.driver.data.formatMoney
import com.nova.driver.data.api.ApiException
import com.nova.driver.data.api.errorMessage

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.*
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.nova.driver.data.*
import com.nova.driver.feature.common.*
import com.nova.driver.navigation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.nova.designsystem.adaptive.contentWidth

@Composable
fun HomeScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val me = DriverStore.me
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (DriverStore.me == null) runCatching { DriverStore.refreshMe() }; DriverStore.refreshEarnings() }
    val week = DriverStore.week
    val today = DriverStore.today
    // A trip still in progress (e.g. after reopening the app) comes first.
    LaunchedEffect(DriverStore.state.ride?.id) { DriverStore.rideRoute(DriverStore.state.ride)?.let(nav::go) }
    val goOnline = {
        scope.launch {
            busy = true; error = null
            runCatching { DriverStore.goOnline() }
                .onSuccess { nav.go(Online) }
                .onFailure { error = errorMessage(it) }
            busy = false
        }
        Unit
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) goOnline() else error = "Nova needs your location to send you nearby trips."
    }
    TabScaffold(DriverTab.Drive, nav::tab, Modifier.background(c.surface), barInContent = true) {
        DriverMap(Modifier.fillMaxWidth().height(440.dp), contentPadding = PaddingValues(top = 72.dp, bottom = 40.dp), fallback = { userDot(196f, 360f, c.outline) })
        Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = { nav.go(Inbox) }, shape = CircleShape, color = c.surfaceContainerHigh, modifier = Modifier.height(56.dp)) {
                Row(Modifier.padding(start = 6.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CookieAvatar(me?.user?.initials ?: "", c.secondaryContainer, NovaPalette.OnCobaltContainer, size = 44.dp)
                    // Ratings start after the first rated trips.
                    Text("New driver", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.height(40.dp).clip(CircleShape).background(c.surfaceContainerHighest).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.outline))
                Text("Offline", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Readable width over the map on large windows; scrolls in phone landscape so Go online stays reachable.
        Column(Modifier.align(Alignment.BottomCenter).contentWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(c.surfaceContainer)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(me?.user?.firstName?.let { "Kia ora, $it" } ?: "Kia ora", fontSize = 15.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Ready when you are.", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = { nav.tab(DriverTab.Earnings) }, shape = RoundedCornerShape(24.dp), color = c.surfaceContainerHigh, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("This week", fontSize = 13.sp, color = c.onSurfaceVariant)
                            Text(earnedLabel(week), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text("${week?.trips ?: 0} trips since Monday", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.primary)
                        }
                    }
                    Surface(onClick = { nav.go(Trips) }, shape = RoundedCornerShape(24.dp), color = c.tertiaryContainer, contentColor = c.onTertiaryContainer, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Today", fontSize = 13.sp)
                            Text(earnedLabel(today), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text("${today?.trips ?: 0} trips · ${(today?.onlineSeconds ?: 0) / 3600}h ${(today?.onlineSeconds ?: 0) % 3600 / 60}m online", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                ShiftError(error)
                NovaButton(
                    if (busy) "Going online…" else "Go online", enabled = !busy, height = 136.dp, leadingIcon = NovaIcons.Power,
                    onClick = {
                        if (DriverStore.hasLocationPermission()) goOnline()
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, letterSpacing = (-0.5).sp),
                    modifier = Modifier.shadow(20.dp, CircleShape, ambientColor = NovaTheme.colors.cta, spotColor = NovaTheme.colors.cta),
                )
            }
            AdaptiveNavigationBar(DriverTab.Drive, nav::tab)
        }
    }
}

@Composable
fun OnlineScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var heatmap by rememberSaveable { mutableStateOf(true) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val destination = DriverStore.me?.destination
    LaunchedEffect(Unit) { if (DriverStore.me == null) runCatching { DriverStore.refreshMe() } }
    // Surge cells around the driver, fresh every minute while online (Uber's heatmap).
    LaunchedEffect(Unit) { while (true) { DriverStore.refreshSurge(); delay(60_000) } }
    // A request arrives over the live socket (or the fallback poll) into DriverStore.state.
    LaunchedEffect(DriverStore.state.offer?.offerId) { if (DriverStore.state.offer != null) nav.go(Request) }
    LaunchedEffect(DriverStore.state.ride?.id) { DriverStore.rideRoute(DriverStore.state.ride)?.let(nav::replaceTop) }
    // Server says we're offline (heartbeats missed, account paused): don't pretend otherwise.
    LaunchedEffect(DriverStore.state.online) { if (!DriverStore.state.online && DriverStore.state.ride == null) nav.popTo(Home) }
    Box(Modifier.fillMaxSize()) {
        DriverMap(
            contentPadding = PaddingValues(top = 80.dp, bottom = 150.dp), follow = true, zoom = 13f,
            fallback = { userDot(196f, 326f, c.primary) },
        ) {
            if (heatmap) SurgeHexes(DriverStore.surge)
            destination?.let { DropoffMarker(it.lat, it.lng, c.primary) }
        }
        Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OnlinePill(online = true, onClick = { scope.launch { runCatching { DriverStore.goOffline() }; nav.popTo(Home) } })
            Spacer(Modifier.weight(1f))
            TodayChip(earnedLabel(DriverStore.today))
        }
        MapTools(Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).offset(y = 60.dp)) {
            androidx.compose.material3.IconToggleButton(checked = destination != null, onCheckedChange = { picking = true }) { Icon(if (destination != null) NovaIcons.FlagFilled else NovaIcons.Flag, "Destination filter") }
            androidx.compose.material3.FilledIconToggleButton(
                checked = heatmap, onCheckedChange = { heatmap = it },
                shapes = IconButtonDefaults.toggleableShapes(CircleShape, RoundedCornerShape(12.dp), RoundedCornerShape(16.dp)),
                colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = Color.Transparent, contentColor = c.onSurface, checkedContainerColor = NovaTheme.colors.coralBright, checkedContentColor = c.onTertiaryContainer),
            ) { Icon(NovaIcons.Flame, "Show demand heatmap") }
        }
        Surface(
            shape = RoundedCornerShape(32.dp), color = c.surfaceContainer, shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 32.dp).contentWidth(),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(c.primaryContainer), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(48.dp), color = c.primary) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Finding trips…", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        destination?.let { "Only trips toward ${it.name}" } ?: "Requests near you appear here. Keep Nova open.",
                        fontSize = 14.sp, color = c.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (picking) DestinationSheet(onDismiss = { picking = false })
}

/**
 * Uber's destination filter: pick a place, then only trips that end closer to it are offered.
 * Two a day (the server counts them and resets at local midnight); clearing is always allowed.
 */
@Composable
private fun DestinationSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val me = DriverStore.me
    val current = me?.destination
    val left = me?.destinationUsesLeft ?: 2
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<com.nova.driver.data.api.PlaceSuggestion>>(emptyList()) }
    val token = remember { java.util.UUID.randomUUID().toString() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(300) // debounce keystrokes
        val fix = DriverStore.location
        runCatching { com.nova.driver.data.api.DriverApi.autocomplete(query.trim(), token, fix?.latitude, fix?.longitude) }
            .onSuccess { results = it; error = null }.onFailure { error = errorMessage(it) }
    }
    val close: () -> Unit = { scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Destination filter", Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                Box(Modifier.height(32.dp).clip(CircleShape).background(if (left > 0) c.primaryContainer else c.errorContainer).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("$left of 2 left today", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (left > 0) c.onPrimaryContainer else c.onErrorContainer)
                }
            }
            if (current != null) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(NovaIcons.FlagFilled, c.primaryContainer, c.onPrimaryContainer, radius = 12.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Heading to", fontSize = 13.sp, color = c.onSurfaceVariant)
                        Text(current.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                }
                Text("You only get trips that end closer to this place. Clear it to get every trip again.", fontSize = 14.sp, lineHeight = 20.sp, color = c.onSurfaceVariant)
                ShiftError(error)
                NovaOutlinedButton(
                    if (busy) "Clearing…" else "Clear destination", modifier = Modifier.fillMaxWidth(), leadingIcon = NovaIcons.Close,
                    onClick = {
                        if (busy) return@NovaOutlinedButton
                        scope.launch {
                            busy = true; error = null
                            runCatching { DriverStore.clearDestination() }.onSuccess { close() }.onFailure { error = errorMessage(it) }
                            busy = false
                        }
                    },
                )
            } else {
                Text(
                    if (left > 0) "Heading home or across town? Set a place and Nova only sends trips that end closer to it."
                    else "You've used both for today. They reset at midnight.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = c.onSurfaceVariant,
                )
                if (left > 0) {
                    NovaTextField(query, { query = it }, "Where are you heading?", leading = { Icon(NovaIcons.Search, null) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    ShiftError(error)
                    if (busy) LoadingIndicator(Modifier.align(Alignment.CenterHorizontally).size(40.dp), color = c.primary)
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        results.forEachIndexed { i, p ->
                            Row(
                                Modifier.fillMaxWidth().clip(groupShape(i, results.size)).background(c.surfaceContainer)
                                    .clickable(enabled = !busy) {
                                        scope.launch {
                                            busy = true; error = null
                                            runCatching {
                                                val place = com.nova.driver.data.api.DriverApi.placeDetails(p.placeId, token)
                                                DriverStore.setDestination(place.name.ifBlank { p.primaryText }, place.lat, place.lng)
                                            }.onSuccess { close() }.onFailure { error = errorMessage(it) }
                                            busy = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(NovaIcons.Pin, null, tint = c.onSurfaceVariant)
                                Column(Modifier.weight(1f)) {
                                    Text(p.primaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    if (p.secondaryText.isNotBlank()) Text(p.secondaryText, fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                p.distanceMeters?.let { Text(formatKm(it), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SurgeTag(text: String, bg: Color, modifier: Modifier) {
    Box(modifier.height(28.dp).clip(CircleShape).background(bg).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
    }
}

@Composable
fun RequestScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val water = NovaTheme.colors.mapWater
    val scope = rememberCoroutineScope()
    val offer = DriverStore.state.offer
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val total = 15
    // Time left comes from the server (expires_in_ms), so the phone's clock can't cheat it.
    var left by remember(offer?.offerId) { mutableIntStateOf(((offer?.expiresInMs ?: 0L) / 1000).toInt().coerceIn(0, total)) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(offer?.offerId) {
        if (offer == null) { nav.replaceTop(Online); return@LaunchedEffect } // taken back or answered elsewhere
        if (com.nova.driver.data.NavPrefs.chime(context)) scope.launch {
            runCatching { android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()?.let { tone ->
                tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 400)
                delay(600); tone.release()
            }
        }
        while (left > 0) { delay(1000); left-- }
        DriverStore.dropOffer(offer.offerId)
        nav.replaceTop(Online) // timed out → keep looking
    }
    if (offer == null) return
    val accept = {
        scope.launch {
            busy = true; error = null
            runCatching { DriverStore.accept() }
                .onSuccess { nav.replaceTop(ToPickup) }
                .onFailure { error = errorMessage(it) }
            busy = false
        }
        Unit
    }
    Box(Modifier.fillMaxSize()) {
        DriverMap(
            contentPadding = PaddingValues(top = 80.dp, bottom = 460.dp),
            fit = listOf(com.google.android.gms.maps.model.LatLng(offer.pickup.lat, offer.pickup.lng), com.google.android.gms.maps.model.LatLng(offer.dropoff.lat, offer.dropoff.lng)),
            fallback = { shape("M-20 240 C 80 200 200 220 280 200 S 340 220 420 230 L420 320 C 340 310 260 360 180 350 S 40 320 -20 340Z", water); userDot(196f, 230f, c.primary) },
        ) {
            RouteLine(offer.polyline)
            PickupMarker(offer.pickup.lat, offer.pickup.lng)
            DropoffMarker(offer.dropoff.lat, offer.dropoff.lng)
        }
        Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OnlinePill(online = true, onClick = {})
            Spacer(Modifier.weight(1f))
            TodayChip(earnedLabel(DriverStore.today))
        }
        DriverSheet(Modifier.align(Alignment.BottomCenter).semantics { contentDescription = "New ride request" }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("NEW REQUEST · ${offer.productName.uppercase()}", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = c.primary)
                    // Uber shows drivers their own pay for the trip, not what the rider pays.
                    Text(formatMoney(offer.earningsCents ?: offer.fareCents, offer.currency), fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, maxLines = 1)
                    Text(
                        buildAnnotatedString {
                            append("${paymentLabel(offer.paymentMethod)} · ${formatKm(offer.distanceMeters)} · ~${formatMinutes(offer.durationSeconds)}")
                            if (offer.stopCount > 0) withStyle(SpanStyle(color = c.tertiary, fontWeight = FontWeight.Bold)) { append(" · ${offer.stopCount} stop${if (offer.stopCount > 1) "s" else ""}") }
                        },
                        fontSize = 14.sp, color = c.onSurfaceVariant,
                    )
                }
                CountdownRing(left / total.toFloat(), 92.dp, 8.dp, c.primary, c.primaryContainer) {
                    Text("$left", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = c.primary, modifier = Modifier.semantics { contentDescription = "$left seconds to respond" })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth().clip(groupShape(0, 2)).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(NovaTheme.colors.cta), contentAlignment = Alignment.Center) { Text("${etaMinutes(offer.pickupDistanceMeters)}m", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = NovaTheme.colors.onCta) }
                    Column { Text("Pickup · ${formatKm(offer.pickupDistanceMeters)} away", fontSize = 13.sp, color = c.onSurfaceVariant); Text(offer.pickup.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
                }
                Row(Modifier.fillMaxWidth().clip(groupShape(1, 2)).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(NovaIcons.FlagFilled, c.onSurface, c.surface, radius = 12.dp)
                    Column { Text("Drop-off", fontSize = 13.sp, color = c.onSurfaceVariant); Text(offer.dropoff.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2) }
                }
            }
            Text(
                buildAnnotatedString { withStyle(SpanStyle(color = c.onSurface, fontWeight = FontWeight.Bold)) { append(offer.riderName) } },
                fontSize = 14.sp, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
            )
            ShiftError(error)
            NovaButton(if (busy) "Accepting…" else "Accept", onClick = accept, enabled = !busy, height = 96.dp, leadingIcon = NovaIcons.Check, textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, letterSpacing = (-0.3).sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = { scope.launch { DriverStore.decline(); nav.replaceTop(Online) } }, enabled = !busy, shape = RoundedCornerShape(16.dp), color = c.surfaceContainerHighest, modifier = Modifier.weight(1f).height(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Decline", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
                Surface(onClick = { scope.launch { DriverStore.decline(); runCatching { DriverStore.goOffline() }; nav.reset(Home) } }, enabled = !busy, shape = RoundedCornerShape(16.dp), color = Color.Transparent, border = BorderStroke(1.dp, c.outline), modifier = Modifier.weight(1f).height(56.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text("Pause requests", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

/** Shared map for the pickup leg (also behind the cancel sheet). */
@Composable
fun PickupMap(dimmed: Boolean = false) {
    val c = MaterialTheme.colorScheme
    val ride = DriverStore.state.ride
    val route = DriverStore.route?.takeIf { it.target == "pickup" }
    DriverMap(
        dimmed = dimmed, follow = !dimmed, contentPadding = PaddingValues(top = 170.dp, bottom = 300.dp),
        fit = listOfNotNull(ride?.let { com.google.android.gms.maps.model.LatLng(it.pickup.lat, it.pickup.lng) }),
        fallback = { this.route("M198 620 L196 340 L80 334", c.primary, 10f); pickup(80f, 334f) },
    ) {
        route?.let { RouteLine(it.polyline) }
        ride?.let { PickupMarker(it.pickup.lat, it.pickup.lng) }
    }
}

/** Keeps the live route fresh: every 30 s, plus when the driver leaves a step (DriverStore). */
@Composable
private fun LiveRoute(rideId: String, status: String) {
    LaunchedEffect(rideId, status) { while (true) { DriverStore.refreshRoute(); delay(30_000) } }
}

/** Distance left in the current step, rounded the way navigation apps say it. */
private fun stepDistance(meters: Long): String = when {
    meters < 1_000 -> "${(meters / 10 * 10).coerceAtLeast(10)} m"
    else -> formatKm(meters)
}

@Composable
fun ToPickupScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var muted by remember { mutableStateOf(!com.nova.driver.data.NavPrefs.voice(context)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ride = DriverStore.state.ride ?: run { LaunchedEffect(Unit) { nav.goOnline() }; return }
    val rider = firstName(ride.riderName)
    LiveRoute(ride.id, ride.status)
    val route = DriverStore.route?.takeIf { it.target == "pickup" }
    val step = route?.steps?.getOrNull(DriverStore.stepIndex)
    val nextStep = route?.steps?.getOrNull(DriverStore.stepIndex + 1)
    Box(Modifier.fillMaxSize()) {
        PickupMap()
        // Live turn-by-turn from the route endpoint; before the first route arrives, where to go.
        if (step != null) TurnCard(stepDistance(DriverStore.stepLeftMeters), step.instruction, nextStep?.instruction?.let { "Then $it" } ?: "Pickup: ${ride.pickup.name}", Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp), maneuver = step.maneuver)
        else TurnCard(formatKm(route?.distanceMeters ?: ride.distanceMeters), "Pickup: ${ride.pickup.name}", ride.pickup.address.ifBlank { "Tap navigate for directions" }, Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp))
        MapTools(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp).offset(y = (-40).dp)) {
            FilledIconButton(onClick = { navigateTo(context, ride.pickup.lat, ride.pickup.lng) }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.onSurface, contentColor = c.surface)) { Icon(NovaIcons.Nav, "Navigate to pickup") }
            androidx.compose.material3.IconToggleButton(checked = muted, onCheckedChange = { muted = it; com.nova.driver.data.NavPrefs.setVoice(context, !it); if (it) com.nova.driver.data.Speech.stop() }) { Icon(if (muted) NovaIcons.Micoff else NovaIcons.Volume, "Mute voice guidance") }
            IconButton(onClick = { nav.go(Help) }) { Icon(NovaIcons.Warning, "Report road issue") }
        }
        DriverSheet(Modifier.align(Alignment.BottomCenter), top = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CookieAvatar(ride.riderInitials, c.secondaryContainer, c.onSecondaryContainer, size = 52.dp)
                Column {
                    Text("Picking up $rider", fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(route?.let { "${formatMinutes(it.etaSeconds)} · ${formatKm(it.distanceMeters)}" }, ride.productName, ride.bookedBy?.let { "booked by $it" }, ride.pickup.name).joinToString(" · "),
                        fontSize = 14.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            Row(Modifier.semantics { contentDescription = "Contact rider" }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ConnectedButton("Call", 0, 3, onClick = { ride.riderPhone?.let { dial(context, it) } ?: run { error = "No number for this rider." } }, icon = NovaIcons.Phone)
                ConnectedButton("Chat", 1, 3, onClick = { nav.go(Chat) }, icon = NovaIcons.Msg)
                ConnectedButton(null, 2, 3, onClick = { nav.go(CancelTrip) }, icon = NovaIcons.Dots, iconDescription = "More options", fixedWidth = 64.dp)
            }
            ShiftError(error)
            Surface(
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        runCatching { DriverStore.arrived() }.onSuccess { nav.replaceTop(Arrived) }.onFailure { error = errorMessage(it) }
                        busy = false
                    }
                },
                enabled = !busy, shape = RoundedCornerShape(28.dp), color = NovaTheme.colors.cta, contentColor = NovaTheme.colors.onCta,
                modifier = Modifier.fillMaxWidth().height(96.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(NovaIcons.Pin, null, Modifier.size(26.dp)); Spacer(Modifier.width(10.dp))
                    Text(if (busy) "Letting $rider know…" else "I've arrived", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
fun ChatScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val ride = DriverStore.state.ride
    val rider = firstName(ride?.riderName ?: "")
    // Kept in DriverStore for the whole trip; rider messages arrive over the live socket.
    val msgs = DriverStore.chat
    var readAloud by remember { mutableStateOf(com.nova.driver.data.NavPrefs.readAloud(context)) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    // Failed text, so it can still go by SMS (fallback only).
    var failed by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(ride?.id) { DriverStore.loadChat() }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }
    // Typing only while stopped (Uber: quick replies while moving).
    val moving = (DriverStore.location?.takeIf { it.hasSpeed() }?.speed ?: 0f) > 2f
    val send: (String) -> Unit = { text ->
        if (text.isNotBlank() && !sending) scope.launch {
            sending = true; failed = null
            runCatching { DriverStore.sendChat(text) }
                .onSuccess { if (text == draft) draft = "" }
                .onFailure { failed = text to errorMessage(it) }
            sending = false
        }
    }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        Row(
            Modifier.fillMaxWidth().background(c.surfaceContainerLow).statusBarsPadding().padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Back, "Back") }
            CookieAvatar(ride?.riderInitials ?: "", c.secondaryContainer, c.onSecondaryContainer, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(rider, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Pickup · ${ride?.pickup?.name ?: ""}", fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1)
            }
            FilledIconButton(
                onClick = { ride?.riderPhone?.let { dial(context, it) } },
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(16.dp), RoundedCornerShape(24.dp)),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NovaTheme.colors.cta, contentColor = NovaTheme.colors.onCta),
                modifier = Modifier.size(48.dp),
            ) { Icon(NovaIcons.Phone, "Call $rider") }
        }
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.primaryContainer)
                .toggleable(readAloud, role = Role.Switch) { readAloud = it; com.nova.driver.data.NavPrefs.setReadAloud(context, it); if (!it) com.nova.driver.data.Speech.stop() }.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NovaIcons.Speaker, null, tint = c.onPrimaryContainer)
            Text("Read new messages aloud while driving", Modifier.weight(1f), fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer)
            Switch(readAloud, null, colors = SwitchDefaults.colors(checkedTrackColor = NovaTheme.colors.cta, checkedThumbColor = NovaTheme.colors.onCta))
        }
        LazyColumn(Modifier.weight(1f).align(Alignment.CenterHorizontally).contentWidth(), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (msgs.isEmpty()) item {
                Text("Messages with $rider show up here. Nova keeps both numbers private.", fontSize = 15.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 24.dp))
            }
            items(msgs) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.text,
                        Modifier.widthIn(max = 280.dp).clip(if (m.me) RoundedCornerShape(24.dp, 6.dp, 24.dp, 24.dp) else RoundedCornerShape(6.dp, 24.dp, 24.dp, 24.dp))
                            .background(if (m.me) NovaTheme.colors.cta else c.surfaceContainerHigh).padding(horizontal = 18.dp, vertical = 14.dp),
                        fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, color = if (m.me) NovaTheme.colors.onCta else c.onSurface,
                    )
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(c.surfaceContainerLow).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (moving) "Tap a quick reply · type when you've stopped" else "Tap a quick reply or type", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
            failed?.let { (text, message) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.errorContainer).padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onErrorContainer)
                    ride?.riderPhone?.let { phone ->
                        TextButton(onClick = {
                            val sms = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$phone")).putExtra("sms_body", text)
                            if (runCatching { context.startActivity(sms) }.isSuccess) { DriverStore.addSentMessage(text); failed = null }
                        }) { Text("Send as SMS", fontWeight = FontWeight.ExtraBold, color = c.onErrorContainer) }
                    }
                }
            }
            listOf("I’m here", "Running 2 min late", "Can’t find you — call me?", "On my way").chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { q ->
                        Surface(onClick = { send(q) }, enabled = !sending, shape = RoundedCornerShape(20.dp), color = c.surfaceContainerHighest, modifier = Modifier.weight(1f).heightIn(min = 64.dp)) {
                            Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) { Text(q, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    NovaTextField(
                        draft, { if (it.length <= 1000) draft = it }, if (moving) "Stop to type" else "Message $rider",
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                FilledIconButton(
                    onClick = { send(draft) }, enabled = draft.isNotBlank() && !sending && !moving,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = NovaTheme.colors.cta, contentColor = NovaTheme.colors.onCta),
                    modifier = Modifier.size(56.dp),
                ) { Icon(NovaIcons.Send, "Send") }
            }
        }
    }
}

@Composable
fun CancelTripScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val reasons = listOf("Rider asked me to cancel", "Too many passengers", "Pickup spot feels unsafe", "Vehicle problem", "Something else")
    var reason by rememberSaveable { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        PickupMap()
        ModalBottomSheet(onDismissRequest = { nav.back() }, sheetState = sheet, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Why are you cancelling?", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tertiaryContainer).padding(horizontal = 14.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(NovaIcons.Info, null, tint = c.onTertiaryContainer)
                    Text("The rider is matched with another driver right away. Safety reasons never count against you.", fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c.onTertiaryContainer)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    reasons.forEachIndexed { i, t ->
                        val on = i == reason
                        Row(
                            Modifier.fillMaxWidth().height(60.dp).clip(groupShape(i, reasons.size)).background(if (on) c.errorContainer else c.surfaceContainer)
                                .selectable(on, onClick = { reason = i }, role = Role.RadioButton).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            RadioButton(on, null, colors = RadioButtonDefaults.colors(selectedColor = c.error, unselectedColor = c.onSurfaceVariant))
                            Text(t, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (on) c.onErrorContainer else c.onSurface)
                        }
                    }
                }
                if (DriverStore.state.ride?.status == "arriving") {
                    Text("Rider didn't show up? Use no-show instead", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = c.primary, modifier = Modifier.padding(horizontal = 4.dp).clickable { nav.replaceTop(NoShow) })
                }
                ShiftError(error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaButton("Keep trip", onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { nav.back() } }, modifier = Modifier.weight(1f))
                    NovaButton(
                        if (busy) "Cancelling…" else "Cancel trip", modifier = Modifier.weight(1f), enabled = reason >= 0 && !busy,
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                runCatching { DriverStore.cancelRide(reasons[reason]) }
                                    .onSuccess { if (DriverStore.state.online) nav.goOnline() else nav.reset(Home) }
                                    .onFailure { error = errorMessage(it) }
                                busy = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = c.errorContainer, contentColor = c.onErrorContainer),
                    )
                }
            }
        }
    }
}

@Composable
fun ArrivedScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val ride = DriverStore.state.ride ?: run { LaunchedEffect(Unit) { nav.goOnline() }; return }
    val rider = firstName(ride.riderName)
    // Free wait is set per ride by the server (2 min); after it the rider pays a wait fee by the minute.
    val freeWait = ride.waitGraceSecs.coerceAtLeast(1)
    var pin by rememberSaveable { mutableStateOf("") }
    var waited by remember { mutableStateOf(secondsSince(ride.arrivedAt)) }
    LaunchedEffect(ride.arrivedAt) { while (true) { waited = secondsSince(ride.arrivedAt); delay(1000) } }
    val waitLeft = (freeWait - waited).toInt().coerceIn(0, freeWait)
    val paidWait = (waited - freeWait).coerceAtLeast(0)
    var busy by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf(false) }
    var bad by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val full = pin.length == 4
    val startTrip = {
        scope.launch {
            busy = true; error = null
            runCatching { DriverStore.start(pin) }
                .onSuccess { ok = true; nav.replaceTop(OnTrip) }
                .onFailure { e -> if ((e as? ApiException)?.code == "wrong_pin") bad = true else error = errorMessage(e) }
            busy = false
        }
        Unit
    }
    AdaptiveScreen(
        Modifier.background(c.surface).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainer).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.surfaceContainerHighest), contentAlignment = Alignment.Center) { Text(ride.riderInitials, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f)) {
                Text("Waiting for $rider", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    if (waitLeft > 0) "${ride.pickup.name} · they've been notified" else "Wait fee running · rider is charged by the minute",
                    fontSize = 13.sp, color = if (waitLeft > 0) c.onSurfaceVariant else c.primary, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            if (waitLeft > 0) CountdownRing(waitLeft / freeWait.toFloat(), 64.dp, 5.dp, c.primary, c.surfaceContainerHighest) {
                Text("%d:%02d".format(waitLeft / 60, waitLeft % 60), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { contentDescription = "Free wait ${waitLeft / 60} minutes ${waitLeft % 60} seconds left" })
            } else CountdownRing(1f, 64.dp, 5.dp, NovaTheme.colors.coralBright, c.surfaceContainerHighest) {
                Text("+%d:%02d".format(paidWait / 60, paidWait % 60), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { contentDescription = "Paid wait ${paidWait / 60} minutes ${paidWait % 60} seconds" })
            }
        }
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enter $rider's trip PIN", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp)
            Row(Modifier.semantics { contentDescription = "Trip PIN" }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { i -> CodeBox(pin.getOrNull(i), active = i == pin.length, modifier = Modifier.width(64.dp), height = 76.dp, error = bad, success = ok, fontSize = 36) }
            }
            Text(
                when { bad -> "That PIN doesn’t match. Ask $rider to check their Nova app."; ok -> "PIN verified"; else -> "Ask the rider for the 4-digit PIN in their app" },
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = when { bad -> c.error; ok -> c.primary; else -> c.onSurfaceVariant },
            )
        }
        NumberPad(onDigit = { if (pin.length < 4) { pin += it; bad = false } }, onDelete = { pin = pin.dropLast(1); bad = false }, onClear = { pin = ""; bad = false }, keyHeight = 64.dp)
        Spacer(Modifier.weight(1f))
        ShiftError(error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(onClick = { nav.go(NoShow) }, shape = RoundedCornerShape(24.dp), color = c.surfaceContainerHigh, modifier = Modifier.height(72.dp)) {
                Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.Center) { Text("No-show", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
            }
            NovaButton(
                if (busy) "Checking…" else "Start trip", onClick = startTrip, enabled = full && !busy && !bad, height = 72.dp, modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold),
            )
        }
    }
}

@Composable
fun OnTripScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ride = DriverStore.state.ride ?: run { LaunchedEffect(Unit) { nav.goOnline() }; return }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Remaining time and distance come from the live route (driver's last fix → drop-off);
    // until the first one arrives, the quote minus time since pickup.
    LiveRoute(ride.id, ride.status)
    val route = DriverStore.route?.takeIf { it.target == "dropoff" }
    var elapsed by remember { mutableStateOf(secondsSince(ride.startedAt)) }
    LaunchedEffect(Unit) { while (true) { delay(5_000); elapsed = secondsSince(ride.startedAt) } }
    val remaining = route?.etaSeconds ?: (ride.durationSeconds - elapsed).coerceAtLeast(0)
    val remainingMeters = route?.distanceMeters ?: ride.distanceMeters
    val eta = remember(remaining / 60) { java.time.LocalTime.now().plusSeconds(remaining).format(java.time.format.DateTimeFormatter.ofPattern("H:mm")) }
    val progress = if (route != null) 1f - route.distanceMeters.toFloat() / ride.distanceMeters.coerceAtLeast(1) else elapsed.toFloat() / ride.durationSeconds.coerceAtLeast(1)
    val step = route?.steps?.getOrNull(DriverStore.stepIndex)
    val nextStep = route?.steps?.getOrNull(DriverStore.stepIndex + 1)
    Box(Modifier.fillMaxSize()) {
        DriverMap(
            follow = true, contentPadding = PaddingValues(top = 170.dp, bottom = 300.dp),
            fit = listOf(com.google.android.gms.maps.model.LatLng(ride.dropoff.lat, ride.dropoff.lng)),
            fallback = { this.route("M196 640 L196 340 L300 330 L310 120", c.primary, 10f); destination(310f, 118f) },
        ) {
            route?.let { RouteLine(it.polyline) }
            DropoffMarker(ride.dropoff.lat, ride.dropoff.lng)
        }
        val turnModifier = Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 4.dp).clickable { navigateTo(context, ride.dropoff.lat, ride.dropoff.lng) }
        if (step != null) TurnCard(stepDistance(DriverStore.stepLeftMeters), step.instruction, nextStep?.instruction?.let { "Then $it" } ?: "Drop-off: ${ride.dropoff.name}", turnModifier, maneuver = step.maneuver)
        else TurnCard(formatKm(remainingMeters), "Drop-off: ${ride.dropoff.name}", ride.dropoff.address.ifBlank { "Tap navigate for directions" }, turnModifier, turnRight = true)
        Column(Modifier.align(Alignment.BottomCenter).contentWidth()) {
            Surface(
                onClick = { nav.go(SosActive) }, shape = RoundedCornerShape(22.dp), color = c.errorContainer, contentColor = c.onErrorContainer, shadowElevation = 6.dp,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp).size(64.dp),
            ) { Box(contentAlignment = Alignment.Center) { Text("SOS", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.semantics { contentDescription = "Emergency SOS" }) } }
            DriverSheet(top = 20.dp) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Dropping ${firstName(ride.riderName)} at", fontSize = 14.sp, color = c.onSurfaceVariant)
                        Text(ride.dropoff.name, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatMinutes(remaining), fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold)
                        Text("${formatKm(remainingMeters)} · $eta", fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                }
                WavyProgress(progress.coerceIn(0.02f, 1f), color = c.primary, track = c.primaryContainer)
                ShiftError(error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = {}, shape = RoundedCornerShape(20.dp), color = c.surfaceContainerHighest, modifier = Modifier.weight(1f).height(64.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("Add stop", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    NovaButton(if (busy) "Ending…" else "End trip", enabled = !busy, modifier = Modifier.weight(2f),
                        onClick = {
                            scope.launch {
                                busy = true; error = null
                                runCatching { DriverStore.complete() }.onSuccess { nav.replaceTop(Complete) }.onFailure { error = errorMessage(it) }
                                busy = false
                            }
                        }, textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
                }
            }
        }
    }
}

@Composable
fun CompleteScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var rating by rememberSaveable { mutableIntStateOf(0) }
    val ride = DriverStore.lastCompleted ?: run { LaunchedEffect(Unit) { nav.goOnline() }; return }
    val fare = formatMoney(ride.fareCents, ride.currency)
    // Headline is the driver's own pay (Uber shows earnings, not the rider's fare).
    val pay = formatMoney(ride.driverEarningsCents ?: ride.fareCents, ride.currency)
    val adjusted = ride.fareBasis == "actual"
    val minutes = remember(ride.id) {
        runCatching { java.time.Duration.between(java.time.Instant.parse(ride.startedAt), java.time.Instant.parse(ride.completedAt)).toMinutes() }.getOrNull()
    }
    val ink = NovaTheme.colors.onCta
    AdaptiveScreen(Modifier.background(NovaTheme.colors.cta)) {
        Box(Modifier.fillMaxWidth().statusBarsPadding()) {
            ShapeBadge(NovaShape.Burst, 220.dp, ink.copy(alpha = 0.06f), Modifier.align(Alignment.TopEnd).offset(60.dp, (-40).dp))
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(listOfNotNull("Trip complete", minutes?.let { "${it.coerceAtLeast(1)} min" }).joinToString(" · "), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = ink)
                Text(pay, fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.1).sp, color = ink, maxLines = 1)
                Text(listOfNotNull(if (ride.driverEarningsCents != null) "Your earnings" else null, "${ride.productName} to ${ride.dropoff.name}").joinToString(" · "), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(c.surface)
                .navigationBarsPadding().padding(16.dp, 24.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NovaPalette.Mint).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(NovaIcons.Check, null, tint = NovaPalette.OnMint)
                Text("Paid in app (${paymentLabel(ride.paymentMethod)}) · nothing to collect", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NovaPalette.OnMint)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(Modifier.fillMaxWidth().clip(groupShape(0, 2)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FareLine("Distance", formatKm(ride.distanceMeters))
                    FareLine("Pickup", ride.pickup.name)
                    if (ride.driverEarningsCents != null) FareLine("Rider paid", fare)
                    if (adjusted) FareLine("Fare", "Adjusted to actual trip", valueColor = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth().clip(groupShape(1, 2)).background(c.primaryContainer).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(if (ride.driverEarningsCents != null) "Your earnings" else "Trip fare", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
                    Text(pay, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Rate ${firstName(ride.riderName)}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                ConnectedChoice(
                    listOf("Great rider", "Had an issue"), rating, { rating = it }, height = 56.dp, fontSize = 16,
                    container = c.surfaceContainerHigh,
                    perOption = listOf(Color(0xFFB8C3FF) to NovaPalette.OnCobaltContainer, Color(0xFFFFB4AB) to Color(0xFF690005)),
                )
            }
            Spacer(Modifier.weight(1f))
            NovaButton("Find next trip", onClick = { if (DriverStore.state.online) nav.goOnline() else nav.reset(Home) }, height = 96.dp, textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp))
        }
    }
}

@Composable
private fun FareLine(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, weight: FontWeight = FontWeight.Bold) {
    Row { Text(label, Modifier.weight(1f), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, fontSize = 15.sp, fontWeight = weight, color = valueColor) }
}

private fun paymentLabel(method: String): String = when (method) {
    "gpay" -> "Google Pay"
    "card" -> "Card"
    "credit" -> "Nova credit"
    else -> method.replaceFirstChar { it.uppercase() }
}

/** Rough minutes to drive [meters] in town (~30 km/h): the offer has a distance, not a routed ETA. */
private fun etaMinutes(meters: Number): Int = ((meters.toDouble() / 500.0) + 0.99).toInt().coerceAtLeast(1)

private fun firstName(name: String): String = name.trim().substringBefore(' ').ifBlank { "the rider" }

private fun secondsSince(iso: String?): Long =
    iso?.let { runCatching { java.time.Duration.between(java.time.Instant.parse(it), java.time.Instant.now()).seconds }.getOrNull() } ?: 0L

/** Shows a server error briefly at the bottom of a shift screen. */
@Composable
private fun ShiftError(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Text(
        message, modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 14.dp, vertical = 10.dp),
        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer,
    )
}
