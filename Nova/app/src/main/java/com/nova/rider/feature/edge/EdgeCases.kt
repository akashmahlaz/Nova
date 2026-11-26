package com.nova.rider.feature.edge

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.nova.designsystem.components.IconTile
import com.nova.designsystem.components.MapBackdrop
import com.nova.designsystem.components.MapSheet
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaListItem
import com.nova.designsystem.components.NovaOutlinedButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.SectionLabel
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.rider.data.trip.TripFormat
import com.nova.rider.data.trip.TripStore
import com.nova.rider.data.trip.toGeoPlace
import androidx.compose.foundation.clickable
import com.nova.rider.feature.common.MapFab
import com.nova.rider.navigation.DriverEnRoute
import com.nova.rider.navigation.FindingDriver
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.Search
import com.nova.rider.navigation.LocationPermission
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.RideOptions
import com.nova.rider.navigation.Schedule
import com.nova.rider.navigation.TripComplete
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Burst badge with an icon on top (shared illustration for edge states). */
@Composable
private fun AlertBadge(shape: NovaShape, size: androidx.compose.ui.unit.Dp, bg: Color, fg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ShapeBadge(shape, size, bg) { Icon(icon, null, tint = fg, modifier = Modifier.size(size * 0.42f)) }
}

@Composable
fun NoDriversScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    // The ride that found no driver (TripStore keeps it until the rider moves on).
    val ride = com.nova.rider.data.trip.TripStore.ride
    val product = ride?.productName ?: "Nova"
    // Leaving this screen ends the failed request; pickup, stops and drop-off stay planned, so
    // "Try again" goes back to Ride options with a fresh price.
    fun leave(then: () -> Unit) {
        com.nova.rider.data.trip.TripStore.forgetRide()
        then()
    }
    androidx.activity.compose.BackHandler { leave { nav.popTo(com.nova.rider.navigation.RideOptions) } }
    Box(Modifier.fillMaxSize()) {
        MapBackdrop { pickup(130f, 260f); ring(130f, 260f, 110f, c.outline) }
        MapFab(NovaIcons.Back, "Back", onClick = { leave { nav.popTo(com.nova.rider.navigation.RideOptions) } }, modifier = Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp))
        MapSheet(Modifier.align(Alignment.BottomCenter), showHandle = false, contentPadding = PaddingValues(16.dp, 28.dp, 16.dp, 28.dp)) {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    AlertBadge(NovaShape.Cookie, 72.dp, c.tertiaryContainer, c.onTertiaryContainer, NovaIcons.Car)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("$product is busy right now", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "No driver accepted your request near ${ride?.pickup?.name ?: "your pickup"}. You haven't been charged.",
                            fontSize = 15.sp, color = c.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaButton("Try again", onClick = { leave { nav.popTo(com.nova.rider.navigation.RideOptions) } }, modifier = Modifier.weight(1f), height = 56.dp, leadingIcon = NovaIcons.Car)
                    NovaOutlinedButton("Schedule", onClick = { leave { nav.replaceTop(Schedule) } }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun AltRide(title: String, sub: String, price: String, shape: RoundedCornerShape, bg: Color, fg: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = shape, color = bg, contentColor = fg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(sub, fontSize = 13.sp)
            }
            Text(price, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun SurgeScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val quote = TripStore.quote
    val option = quote?.options?.firstOrNull { it.productId == TripStore.productId } ?: quote?.options?.firstOrNull()
    val surge = quote?.surgeMultiplier ?: 1.0
    fun money(cents: Long) = quote?.let { TripFormat.money(cents, it.currency) } ?: ""
    val nowPrice = option?.let { money(it.fareCents) } ?: ""
    val usualPrice = option?.let { money((it.fareCents / surge).toLong()) } ?: ""
    Box(Modifier.fillMaxSize()) {
        MapBackdrop(dimmed = true)
        BasicAlertDialog(onDismissRequest = { nav.back() }) {
            Surface(shape = RoundedCornerShape(32.dp), color = c.surfaceContainerLow, shadowElevation = 12.dp) {
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    AlertBadge(NovaShape.Burst, 80.dp, NovaPalette.CoralBright, c.onTertiaryContainer, NovaIcons.TrendUp)
                    Text("Fares are higher right now", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text("Lots of people are booking near you. Fares usually ease within 15–20 minutes.", fontSize = 15.sp, lineHeight = 22.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp, 6.dp, 6.dp, 20.dp)).background(c.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 14.dp)) {
                            Text("USUAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                            Text(usualPrice, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.onSurfaceVariant, textDecoration = TextDecoration.LineThrough)
                        }
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(6.dp, 20.dp, 20.dp, 6.dp)).background(c.tertiaryContainer).padding(horizontal = 10.dp, vertical = 14.dp)) {
                            Text("NOW · ${"%.1f".format(surge)}×", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                            Text(nowPrice, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                        }
                    }
                    NovaButton("Book for $nowPrice", onClick = { nav.popTo(RideOptions) }, height = 56.dp, enabled = option != null)
                    TextButton(onClick = { nav.popTo(RideOptions) }, modifier = Modifier.height(48.dp)) { Text("Notify me when fares drop", fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
fun DriverCancelledScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var why by rememberSaveable { mutableStateOf(false) }
    val ride = TripStore.ride
    // Dispatch is already re-matching the same ride: follow it until a new driver accepts.
    LaunchedEffect(Unit) {
        while (true) {
            val now = TripStore.ride ?: run { nav.reset(Home); return@LaunchedEffect }
            when (now.status) {
                "accepted", "arriving" -> { nav.replaceTop(DriverEnRoute); return@LaunchedEffect }
                "no_drivers" -> { nav.replaceTop(com.nova.rider.navigation.NoDrivers); return@LaunchedEffect }
                "cancelled", "completed" -> { nav.reset(Home); return@LaunchedEffect }
            }
            TripStore.awaitRideChange(now.status)
            if (TripStore.ride?.status == now.status) TripStore.refreshRide()
        }
    }
    val scope = rememberCoroutineScope()
    val spin = rememberInfiniteTransition(label = "match")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    Box(Modifier.fillMaxSize()) {
        MapBackdrop { pickup(150f, 300f); halo(150f, 300f, 80f, c.primary) }
        Row(
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().height(56.dp)
                .clip(RoundedCornerShape(16.dp)).background(c.inverseSurface).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NovaIcons.Info, null, tint = c.inverseOnSurface)
            Text("You won't be charged for this", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.inverseOnSurface)
            TextButton(onClick = { why = !why }) { Text("Why?", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.inversePrimary) }
        }
        MapSheet(Modifier.align(Alignment.BottomCenter), showHandle = false, contentPadding = PaddingValues(16.dp, 28.dp, 16.dp, 28.dp)) {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(c.tertiaryContainer).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Driver cancelled", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                    }
                    Text("Your driver couldn't make it.\nFinding you someone new.", style = MaterialTheme.typography.headlineMedium)
                    if (why) Text("Drivers who cancel after accepting are never paid for the trip, and you keep your place in line.", fontSize = 14.sp, color = c.onSurfaceVariant)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(c.primaryContainer).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
                        Icon(NovaIcons.Flash, null, tint = c.primary, modifier = Modifier.graphicsLayer { rotationZ = angle })
                    }
                    Column {
                        Text("Priority match is on", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
                        Text("You're first in line" + (ride?.let { " · same ${TripFormat.money(it.fareCents, it.currency)} fare" } ?: ""), fontSize = 14.sp, color = c.onPrimaryContainer)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaOutlinedButton("Cancel request", onClick = { scope.launch { if (TripStore.cancelRide("Driver cancelled, rider stopped the re-match")) { TripStore.forgetRide(); nav.popTo(RideOptions) } } }, modifier = Modifier.weight(1f))
                    NovaButton("Keep searching", onClick = { nav.replaceTop(FindingDriver) }, modifier = Modifier.weight(1f), height = 56.dp)
                }
            }
        }
    }
}

@Composable
fun PaymentFailedScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val methods = listOf("card", "gpay", "credit")
    var method by rememberSaveable { mutableIntStateOf(methods.indexOf(TripStore.paymentMethod).coerceAtLeast(0)) }
    val due = TripStore.ride?.let { TripFormat.money(it.fareCents, it.currency) } ?: ""
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Close, "Close") }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.errorContainer).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AlertBadge(NovaShape.Cookie, 64.dp, c.error, Color.White, NovaIcons.Card)
            Text("Card payment didn't go through", style = MaterialTheme.typography.headlineMedium, color = c.onErrorContainer)
            Text("Your bank didn't respond in time. If money was debited, it'll be refunded automatically.", fontSize = 15.sp, lineHeight = 22.sp, color = c.onErrorContainer)
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.onErrorContainer.copy(alpha = 0.16f)))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Amount due", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onErrorContainer)
                Text(due, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = c.onErrorContainer)
            }
        }
        SectionLabel("Pay another way")
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val items = listOf("Retry card" to "Or update your card details", "Google Pay" to null, "Nova Credit" to null)
            items.forEachIndexed { i, (title, sub) ->
                val on = i == method
                val shape = when (i) { 0 -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp); items.lastIndex -> RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp); else -> RoundedCornerShape(4.dp) }
                val fg = if (on) c.onPrimaryContainer else c.onSurface
                Row(
                    Modifier.fillMaxWidth().height(if (sub != null) 68.dp else 64.dp).clip(shape).background(if (on) c.primaryContainer else c.surfaceContainer)
                        .selectable(on, onClick = { method = i; TripStore.paymentMethod = methods[i] }, role = Role.RadioButton).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (i) {
                        0 -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.primary), contentAlignment = Alignment.Center) { Text("VISA", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                        1 -> IconTile(NovaIcons.Wallet, c.surfaceContainerHighest, c.onSurface)
                        else -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.tertiaryContainer), contentAlignment = Alignment.Center) { Text(com.nova.rider.data.trip.TripFormat.localCurrencySymbol(), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 16.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, color = fg)
                        if (sub != null) Text(sub, fontSize = 13.sp, color = fg)
                    }
                    RadioButton(selected = on, onClick = null)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        NovaButton(if (due.isNotEmpty()) "Pay $due" else "Pay", onClick = { nav.replaceTop(TripComplete) })
    }
}

private fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
fun OfflineScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember0(false)
    var failedOnce by remember0(false)
    val radius by animateDpAsState(if (busy) 20.dp else 32.dp, MaterialTheme.motionScheme.fastSpatialSpec())
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 72.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).dashedRing(c.outlineVariant))
            ShapeBadge(NovaShape.Cookie, 150.dp, c.surfaceContainerHigh) { Icon(NovaIcons.WifiOff, null, tint = c.onSurfaceVariant, modifier = Modifier.size(64.dp)) }
            ShapeBadge(NovaShape.Burst, 56.dp, c.tertiaryContainer, Modifier.offset(x = 70.dp, y = (-70).dp)) { Icon(NovaIcons.Signal, null, tint = c.onTertiaryContainer, modifier = Modifier.size(24.dp)) }
        }
        Text("You're offline", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            if (failedOnce && !busy) "Still no connection. Check mobile data or Wi-Fi and try again." else "Nova needs the internet to find drivers. Check mobile data or Wi-Fi and try again.",
            fontSize = 16.sp, lineHeight = 24.sp, color = c.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp),
        )
        SegmentedColumn {
            NovaListItem(0, 2, "Turn off Airplane mode", leading = { Icon(NovaIcons.Power, null) })
            NovaListItem(1, 2, "Move to a spot with better signal", leading = { Icon(NovaIcons.Signal, null) })
        }
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = {
                if (!busy) scope.launch {
                    busy = true
                    delay(1200)
                    val ok = isOnline(context)
                    busy = false
                    if (ok) nav.back() else failedOnce = true
                }
            },
            shape = RoundedCornerShape(radius), color = c.primary, contentColor = Color.White, modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (busy) { LoadingIndicator(Modifier.size(28.dp), color = Color.White); Spacer(Modifier.size(10.dp)) }
                Text(if (busy) "Checking connection…" else "Try again", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        TextButton(
            onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, "tel:111".toUri())) },
            modifier = Modifier.height(48.dp).offset(y = (-8).dp),
        ) { Text("Emergency? Call 111", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.error) }
    }
}

@Composable
private fun <T> remember0(initial: T) = rememberSaveable { mutableStateOf(initial) }

@Composable
fun LocationDeniedScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var bannerVisible by rememberSaveable { mutableStateOf(true) }
    var query by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        MapBackdrop(Modifier.graphicsLayer { alpha = 0.7f })
        if (bannerVisible) {
            Column(
                Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.tertiaryContainer).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(NovaIcons.LocationOff, c.tertiary, Color.White, size = 44.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Location is off", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                        Text("You can still book — just tell us where to pick you up.", fontSize = 14.sp, lineHeight = 20.sp, color = c.onTertiaryContainer)
                    }
                }
                Row(Modifier.padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = { nav.go(LocationPermission) }, shape = CircleShape, color = c.tertiary, contentColor = Color.White, modifier = Modifier.height(40.dp)) {
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Turn on", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                    TextButton(onClick = { bannerVisible = false }) { Text("Not now", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onTertiaryContainer) }
                }
            }
        }
        MapSheet(Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Where should we\npick you up?", style = MaterialTheme.typography.headlineLarge.copy(fontSize = 32.sp, lineHeight = 36.sp))
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clip(CircleShape).background(c.primaryContainer).border(2.dp, c.primary, CircleShape).padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(NovaIcons.Search, null, tint = c.onPrimaryContainer)
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Building, street or landmark", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer.copy(alpha = 0.6f))
                        BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.onPrimaryContainer), cursorBrush = SolidColor(c.primary))
                    }
                }
                // The rider's saved Home and Work as one-tap pickups (search when one isn't saved).
                val home = com.nova.rider.data.profile.ProfileStore.homePlace()
                val work = com.nova.rider.data.profile.ProfileStore.workPlace()
                fun pickFrom(saved: com.nova.rider.data.auth.SavedPlace?) {
                    saved?.toGeoPlace()?.let { TripStore.choosePickup(it) }
                    nav.reset(Home); nav.go(Search)
                }
                SegmentedColumn {
                    NovaListItem(0, 2, "Home", supporting = home?.address ?: "Add your home address", leading = { Icon(NovaIcons.Home, null) }, onClick = { pickFrom(home) })
                    NovaListItem(1, 2, "Work", supporting = work?.address ?: "Add your work address", leading = { Icon(NovaIcons.Work, null) }, onClick = { pickFrom(work) })
                }
            }
        }
    }
}

@Composable
fun NoServiceScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var notify by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        IconButton(onClick = { nav.back() }, modifier = Modifier.offset(x = (-12).dp)) { Icon(NovaIcons.Back, "Back") }
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            ShapeBadge(NovaShape.Cookie, 200.dp, c.surfaceContainerHigh)
            Box(Modifier.size(140.dp).dashedRing(c.outline))
            ShapeBadge(NovaShape.Burst, 64.dp, c.tertiaryContainer, Modifier.offset(x = 60.dp, y = 40.dp)) { Icon(NovaIcons.Pin, null, tint = c.onTertiaryContainer) }
            ShapeBadge(NovaShape.Cookie4, 48.dp, c.primaryContainer, Modifier.offset(x = (-50).dp, y = (-40).dp)) { Icon(NovaIcons.Car, null, tint = c.onPrimaryContainer, modifier = Modifier.size(22.dp)) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("We're not in your area — yet.", style = MaterialTheme.typography.headlineLarge)
            Text("Your pickup is outside our service area. Move the pin closer to a Nova zone, or get a heads-up when we launch here.", fontSize = 16.sp, lineHeight = 24.sp, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.weight(1f))
        NovaButton(if (notify) "You're on the list" else "Notify me at launch", onClick = { notify = true }, leadingIcon = if (notify) NovaIcons.Check else NovaIcons.Bell)
        NovaOutlinedButton("Change pickup", onClick = { nav.back() }, modifier = Modifier.fillMaxWidth().offset(y = (-8).dp))
    }
}

/** Dashed circular outline drawn behind the content. */
private fun Modifier.dashedRing(color: Color): Modifier = drawBehind {
    drawCircle(color, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
}

@Composable
fun SosActiveScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    var seconds by rememberSaveable { mutableIntStateOf(3) }
    var dialled by rememberSaveable { mutableStateOf(false) }
    val driver = TripStore.ride?.driver
    val here = com.nova.rider.data.trip.LiveRide.fixFor(TripStore.ride?.id)?.point ?: TripStore.currentLocation
    var place by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(here) { here?.let { p -> place = runCatching { com.nova.rider.data.auth.NovaApi.reverseGeocode(p) }.getOrNull()?.let { listOf(it.name, it.address).filter(String::isNotBlank).joinToString(", ") } } }
    val contacts = com.nova.rider.data.profile.ProfileStore.contacts
    LaunchedEffect(Unit) {
        while (seconds > 0) { delay(1000); seconds-- }
        if (!dialled) {
            dialled = true
            // Opens the dialer with 111 pre-filled; placing the call stays with the user / system.
            context.startActivity(Intent(Intent.ACTION_DIAL, "tel:111".toUri()))
        }
    }
    val pulse = rememberInfiniteTransition(label = "sos")
    val t by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "t")
    Column(
        Modifier.fillMaxSize().background(c.error).statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.height(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(NovaIcons.Warning, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text("EMERGENCY", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Color.White)
        }
        Box(Modifier.padding(top = 16.dp).size(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).graphicsLayer { val s = 0.8f + 0.4f * t; scaleX = s; scaleY = s; alpha = 0.35f * (1f - t) }.clip(CircleShape).background(Color.White))
            ShapeBadge(NovaShape.Sunny, 168.dp, Color.White) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (seconds > 0) "$seconds" else "111", fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, color = c.error)
                    Text(if (seconds > 0) "seconds" else "dialling", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = c.error)
                }
            }
        }
        Text("Calling 111", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, lineHeight = 38.sp), color = Color.White, modifier = Modifier.padding(top = 8.dp))
        Text(
            driver?.let { "Tell the dispatcher where you are and that your driver is ${it.firstName} in a ${it.vehicle.label}, plate ${it.vehicle.plate}." }
                ?: "Tell the dispatcher where you are.",
            fontSize = 16.sp, lineHeight = 23.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)).background(c.errorContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(NovaIcons.Pin, null, tint = c.onErrorContainer)
                Text(place ?: here?.let { "%.5f, %.5f".format(it.lat, it.lng) } ?: "Finding your location…", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onErrorContainer)
            }
            // Texts the rider's emergency contacts their location and the car, via the messaging app.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)).background(c.errorContainer)
                    .clickable(enabled = contacts.isNotEmpty()) {
                        val where = here?.let { "https://maps.google.com/?q=${it.lat},${it.lng}" }.orEmpty()
                        val body = "I need help. I'm at ${place ?: "this location"} $where" + (driver?.let { " in a ${it.vehicle.label}, plate ${it.vehicle.plate}." } ?: "")
                        val sms = Intent(Intent.ACTION_SENDTO, "smsto:${contacts.joinToString(";") { it.phone }}".toUri()).putExtra("sms_body", body)
                        runCatching { context.startActivity(sms) }
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(NovaIcons.People, null, tint = c.onErrorContainer)
                Text(
                    if (contacts.isEmpty()) "No emergency contacts saved" else "Text ${contacts.joinToString(" and ") { it.name.substringBefore(' ') }} your location",
                    Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.onErrorContainer,
                )
                if (contacts.isNotEmpty()) Icon(NovaIcons.Send, null, tint = c.onErrorContainer)
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(
            onClick = { nav.back() }, shape = CircleShape, color = Color.Transparent, contentColor = Color.White,
            border = BorderStroke(2.dp, Color.White), modifier = Modifier.fillMaxWidth().height(64.dp),
        ) { Box(contentAlignment = Alignment.Center) { Text("Cancel — I'm safe", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) } }
    }
}
