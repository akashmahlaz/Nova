package com.nova.driver.feature.edge

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
import com.nova.driver.data.DriverStore
import com.nova.driver.feature.common.*
import com.nova.driver.navigation.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.DisposableEffect

@Composable
fun NoShowScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ride = DriverStore.state.ride
    val rider = ride?.riderName?.substringBefore(' ') ?: "The rider"
    val freeWait = (ride?.noShowWaitSecs ?: 300).toLong()
    fun waitedNow() = ride?.arrivedAt?.let { runCatching { java.time.Duration.between(java.time.Instant.parse(it), java.time.Instant.now()).seconds }.getOrNull() } ?: 0L
    var waited by remember { mutableStateOf(waitedNow()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); waited = waitedNow() } }
    val canCancel = waited >= freeWait
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AdaptiveScreen(
        Modifier.background(c.surface).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Back, "Back") }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(180.dp).semantics { contentDescription = "Waited ${waited / 60} minutes ${waited % 60} seconds" }, contentAlignment = Alignment.Center) {
                ShapeBadge(NovaShape.Sunny, 180.dp, c.tertiaryContainer)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%d:%02d".format(waited / 60, waited % 60), fontSize = 48.sp, lineHeight = 50.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                    Text("WAITED", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.onTertiaryContainer)
                }
            }
            Text("$rider hasn't shown up", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp), textAlign = TextAlign.Center)
            Text(
                if (canCancel) "You've waited the full free time. You can cancel the trip now."
                else "You can cancel as a no-show after ${freeWait / 60} minutes at the pickup. Try calling $rider first.", fontSize = 16.sp, lineHeight = 23.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 310.dp))
        }
        Spacer(Modifier.weight(1f))
        error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.error) }
        NovaButton(
            if (busy) "Cancelling…" else "Cancel as no-show", enabled = canCancel && !busy, height = 72.dp,
            onClick = {
                scope.launch {
                    busy = true; error = null
                    runCatching { DriverStore.noShow() }
                        .onSuccess { if (DriverStore.state.online) nav.goOnline() else nav.reset(Home) }
                        .onFailure { error = com.nova.driver.data.api.errorMessage(it) }
                    busy = false
                }
            }, textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold))
        Row(Modifier.offset(y = (-8).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ConnectedButton("Call again", 0, 2, onClick = { ride?.riderPhone?.let { dial(context, it) } }, container = c.surfaceContainerHigh)
            ConnectedButton("Keep waiting", 1, 2, onClick = { nav.back() }, container = c.surfaceContainerHigh)
        }
    }
}

@Composable
fun RiderCancelledScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var progress by remember { mutableStateOf(0.38f) }
    val rider = remember { DriverStore.cancelledRider?.substringBefore(' ') ?: "The rider" }
    val scope = rememberCoroutineScope()
    // Still online on the server: the driver is already back in dispatch; this just says so.
    val backOnline = { DriverStore.cancelledRider = null; nav.goOnline() }
    LaunchedEffect(Unit) {
        // Back online automatically after ~5 s.
        while (progress < 1f) { delay(50); progress = (progress + 0.0124f).coerceAtMost(1f) }
        backOnline()
    }
    Box(Modifier.fillMaxSize()) {
        DriverMap(fallback = { userDot(196f, 300f, c.primary) })
        ModalBottomSheet(onDismissRequest = { backOnline() }, sheetState = sheet, containerColor = c.surfaceContainer) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(NovaIcons.Close, c.tertiaryContainer, c.onTertiaryContainer, size = 64.dp, radius = 22.dp, iconSize = 28.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("$rider cancelled", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                        Text("Not your fault — your rating isn't affected.", fontSize = 15.sp, color = c.onSurfaceVariant)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Back online in ${((1f - progress) * 8).toInt().coerceAtLeast(0) + 1} s", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                    androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = NovaTheme.colors.cta, trackColor = c.surfaceContainerHighest)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = {
                        DriverStore.cancelledRider = null
                        scope.launch { runCatching { DriverStore.goOffline() }; nav.reset(Home) }
                    }, shape = RoundedCornerShape(20.dp), color = c.surfaceContainerHighest, modifier = Modifier.weight(1f).height(64.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text("Take a break", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    NovaButton("Go online now", onClick = { backOnline() }, modifier = Modifier.weight(1.4f))
                }
            }
        }
    }
}

@Composable
fun ConnectionLostScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    // Listen for the network to come back (official ConnectivityManager callback) and resume the trip.
    DisposableEffect(Unit) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post { nav.back() }
                }
            }
        }
        cm?.registerDefaultNetworkCallback(callback)
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }
    Box(Modifier.fillMaxSize()) {
        // Map tiles already on screen stay visible offline; the trip's stop is drawn from local state.
        val ride = DriverStore.state.ride
        val stop = ride?.let { if (it.status == "in_progress") it.dropoff else it.pickup }
        DriverMap(
            follow = true, contentPadding = PaddingValues(top = 110.dp, bottom = 380.dp),
            fit = listOfNotNull(stop?.let { com.google.android.gms.maps.model.LatLng(it.lat, it.lng) }),
            fallback = { route("M196 640 L196 340 L300 330 L310 120", c.primary, 10f); destination(310f, 118f) },
        ) {
            DriverStore.route?.let { RouteLine(it.polyline) }
            stop?.let { if (ride.status == "in_progress") DropoffMarker(it.lat, it.lng) else PickupMarker(it.lat, it.lng) }
        }
        Row(
            Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 4.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp)).background(c.errorContainer).padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(NovaIcons.WifiOff, null, tint = c.onErrorContainer, modifier = Modifier.size(28.dp))
            Column {
                Text("Connection lost", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.onErrorContainer)
                Text("Keep driving — your trip is safe.", fontSize = 14.sp, color = c.onErrorContainer)
            }
        }
        DriverSheet(Modifier.align(Alignment.BottomCenter)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(c.surfaceContainerHigh), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(40.dp), color = c.primary) }
                Column {
                    Text("Reconnecting…", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("We'll pick up where you left off", fontSize = 14.sp, color = c.onSurfaceVariant)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf(NovaIcons.Route to "Google Maps keeps navigating offline", NovaIcons.Clock to "Your trip stays assigned to you", NovaIcons.Receipt to "The fare was locked when the rider booked").forEachIndexed { i, (icon, t) ->
                    Row(Modifier.fillMaxWidth().clip(groupShape(i, 3)).background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(icon, null, Modifier.size(20.dp)); Text(t, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            NovaOutlinedButton("Call Nova support", onClick = { dial(context, "0800668200") }, modifier = Modifier.fillMaxWidth(), leadingIcon = NovaIcons.Phone)
        }
    }
}

@Composable
fun DocExpiredScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    LaunchedEffect(Unit) { runCatching { DriverStore.refreshMe() } }
    val docs = DriverStore.me?.documents.orEmpty()
    val attention = docs.filter { it.required && (it.status == null || it.status == "rejected") }
    val first = attention.firstOrNull()
    AdaptiveScreen(
        Modifier.background(c.surface).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Back, "Back") }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(if (first != null) c.errorContainer else c.primaryContainer)) {
            ShapeBadge(NovaShape.Burst, 160.dp, c.error.copy(alpha = 0.08f), Modifier.align(Alignment.TopEnd).offset(40.dp, (-30).dp))
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val ink = if (first != null) c.onErrorContainer else c.onPrimaryContainer
                if (first != null) Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(c.onErrorContainer).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("ACTION NEEDED", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp, color = c.errorContainer)
                }
                Text(if (first != null) "${attention.size} document${if (attention.size > 1) "s" else ""} need${if (attention.size == 1) "s" else ""} a new upload" else "Your documents are in order",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 36.sp), color = ink)
                Text(if (first != null) "Upload a clear photo and our team will review it, usually the same day." else "Nothing to do right now.", fontSize = 15.sp, lineHeight = 22.sp, color = ink)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            docs.forEachIndexed { i, d ->
                val bad = d.required && (d.status == null || d.status == "rejected")
                Row(Modifier.fillMaxWidth().clip(groupShape(i, docs.size)).background(if (bad) c.surfaceContainerHigh else c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (bad) IconTile(NovaIcons.Doc, c.errorContainer, c.onErrorContainer)
                    else Box(Modifier.size(40.dp).clip(CircleShape).background(NovaTheme.colors.cta), contentAlignment = Alignment.Center) { Icon(NovaIcons.Check, null, tint = NovaTheme.colors.onCta) }
                    Column {
                        Text(d.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(when (d.status) { "verified" -> "Verified"; "uploaded" -> "In review"; "rejected" -> d.rejectionReason ?: "Sent back"; else -> "Not uploaded" },
                            fontSize = 13.sp, color = if (bad) c.error else c.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (first != null) NovaButton("Upload ${first.name.lowercase()}", onClick = { nav.go(Scan(first.category, first.name)) }, height = 96.dp, leadingIcon = NovaIcons.Camera, textStyle = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp))
    }
}

@Composable
fun SosActiveScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var dialled by rememberSaveable { mutableStateOf(false) }
    val ride = DriverStore.state.ride
    val where = when (ride?.status) {
        "in_progress" -> "On a trip to ${ride.dropoff.name}"
        "accepted", "arriving" -> "Heading to ${ride.pickup.name}"
        else -> null
    }
    LaunchedEffect(Unit) {
        if (!dialled) { dialled = true; dial(context, "111") } // dialer opens with 111; the driver places the call
    }
    val pulse = rememberInfiniteTransition(label = "sos")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600)), label = "t")
    AdaptiveScreen(
        Modifier.background(c.error).statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.height(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(NovaIcons.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("DRIVER EMERGENCY", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Color.White)
        }
        Box(Modifier.padding(top = 16.dp).size(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).graphicsLayer { val s = 0.8f + 0.4f * t; scaleX = s; scaleY = s; alpha = 0.35f * (1f - t) }.clip(CircleShape).background(Color.White))
            ShapeBadge(NovaShape.Sunny, 168.dp, Color.White) { Icon(NovaIcons.Phone, null, tint = c.error, modifier = Modifier.size(64.dp)) }
        }
        Text("Calling 111", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, lineHeight = 38.sp), color = Color.White, modifier = Modifier.padding(top = 8.dp))
        Text("Your phone's dialer is open with 111. Tap call, stay on the line and tell them where you are.", fontSize = 16.sp, lineHeight = 23.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        if (where != null) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.errorContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(NovaIcons.Pin, null, tint = c.onErrorContainer); Text(where, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onErrorContainer)
        }
        Surface(onClick = { dial(context, "111") }, shape = CircleShape, color = Color.White, contentColor = c.error, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("Open dialer again", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
        }
        Spacer(Modifier.weight(1f))
        Surface(onClick = { nav.back() }, shape = CircleShape, color = Color.Transparent, contentColor = Color.White, border = BorderStroke(2.dp, Color.White), modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("End emergency", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
        }
    }
}
