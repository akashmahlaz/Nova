package com.nova.rider.feature.trip

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.MapBackdrop
import com.nova.designsystem.components.MapSheet
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaOutlinedButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.PlateBadge
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.components.WavyProgress
import com.nova.designsystem.components.toShape
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.rider.data.Sample
import com.nova.rider.feature.common.DriverRow
import com.nova.rider.feature.common.MapFab
import com.nova.rider.navigation.Call
import com.nova.rider.navigation.CancelRide
import com.nova.rider.navigation.Chat
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.OnTrip
import com.nova.rider.navigation.Receipt
import com.nova.rider.navigation.Safety
import com.nova.rider.navigation.ShareTrip
import com.nova.rider.navigation.TripComplete

/** Map used behind the "driver en route" state (also behind its sheets). */
@Composable
fun EnRouteMap(dimmed: Boolean = false) {
    val c = MaterialTheme.colorScheme
    MapBackdrop(dimmed = dimmed) {
        route("M296 166 L 140 158 L 133 352", c.primary, 6f)
        halo(133f, 352f, 30f, c.primary)
        pickup(133f, 352f)
        car(296f, 166f, -92f)
    }
}

@Composable
fun DriverEnRouteScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        EnRouteMap()
        Row(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MapFab(NovaIcons.Down, "Minimise", onClick = { nav.popTo(Home) })
            Spacer(Modifier.weight(1f))
            Surface(
                onClick = { nav.go(Safety) },
                shape = RoundedCornerShape(18.dp),
                color = c.errorContainer, contentColor = c.onErrorContainer,
                shadowElevation = 3.dp,
                modifier = Modifier.height(56.dp),
            ) {
                Row(Modifier.padding(start = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(NovaIcons.Shield, null, Modifier.size(22.dp)); Text("Safety", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        MapSheet(Modifier.align(Alignment.BottomCenter), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 112.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("Driver arriving", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onSurfaceVariant)
                        Text("4 min", style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp, lineHeight = 54.sp))
                    }
                    PlateBadge(Sample.plate, Modifier.padding(bottom = 6.dp))
                }
                WavyProgress(0.62f)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.surfaceContainer).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CookieAvatar(Sample.driverInitials, c.primaryContainer, c.onPrimaryContainer, size = 56.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Sample.driverFullName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("★ ${Sample.driverRating} · ${Sample.car}", fontSize = 14.sp, color = c.onSurfaceVariant)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(c.tertiaryContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Trip PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.onTertiaryContainer)
                        Text("Tell your driver to start", fontSize = 13.sp, color = c.onTertiaryContainer)
                    }
                    Text(
                        Sample.tripPin, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 8.sp, color = c.onTertiaryContainer,
                        modifier = Modifier.semantics { contentDescription = "PIN " + Sample.tripPin.toList().joinToString(" ") },
                    )
                }
            }
        }
        // M3 Expressive floating toolbar (vibrant)
        HorizontalFloatingToolbar(
            expanded = true,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp),
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(toolbarContainerColor = c.primary, toolbarContentColor = Color.White),
        ) {
            FilledIconButton(
                onClick = { nav.go(Call) },
                shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(14.dp)),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer),
            ) { Icon(NovaIcons.Phone, "Call driver") }
            IconButton(onClick = { nav.go(Chat) }) { Icon(NovaIcons.Msg, "Message driver") }
            IconButton(onClick = { nav.go(ShareTrip) }) { Icon(NovaIcons.Share, "Share trip") }
            IconButton(onClick = { nav.replaceTop(OnTrip) }) { Icon(NovaIcons.ArrowRight, "Driver picked you up") }
            IconButton(onClick = { nav.go(CancelRide) }) { Icon(NovaIcons.Close, "Cancel ride") }
        }
    }
}

@Composable
fun OnTripScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        MapBackdrop {
            shape("M-20 160 C 80 120 200 140 280 120 S 340 120 420 130 L420 210 C 340 200 260 250 180 240 S 40 210 -20 230Z", NovaPalette.MapWater)
            route("M104 470 L 102 418 L 304 404 L 310 120", c.primary, 7f)
            destination(310f, 118f)
            car(104f, 466f)
        }
        Row(
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 4.dp).fillMaxWidth().height(72.dp)
                .clip(RoundedCornerShape(24.dp)).background(c.onSurface).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text("ARRIVE BY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.outlineVariant)
                Text("10:42", fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Box(Modifier.width(1.dp).height(36.dp).background(Color.White.copy(alpha = 0.2f)))
            Column {
                Text("18 min", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text("6.2 km left", fontSize = 13.sp, color = c.outlineVariant)
            }
        }
        Column(Modifier.align(Alignment.BottomEnd)) {
            Surface(
                onClick = { nav.go(Safety) },
                modifier = Modifier.padding(end = 16.dp, bottom = 16.dp).align(Alignment.End).size(80.dp),
                shape = RoundedCornerShape(28.dp),
                color = c.error, contentColor = Color.White, shadowElevation = 6.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(NovaIcons.Shield, "Safety and SOS", Modifier.size(26.dp))
                    Text("SOS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                }
            }
            MapSheet {
                Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("On the way", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.primary)
                        Text(Sample.destination, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    }
                    WavyProgress(0.36f)
                    DriverRow()
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        ConnectedAction("Share", NovaIcons.Share, RoundedCornerShape(28.dp, 8.dp, 8.dp, 28.dp), c.primaryContainer, c.onPrimaryContainer, 1f) { nav.go(ShareTrip) }
                        ConnectedAction("Change stop", null, RoundedCornerShape(8.dp), c.surfaceContainerHighest, c.onSurface, 1.3f) { nav.go(com.nova.rider.navigation.Search) }
                        ConnectedAction("Arrived", null, RoundedCornerShape(8.dp, 28.dp, 28.dp, 8.dp), c.surfaceContainerHighest, c.onSurface, 1f) { nav.reset(TripComplete) }
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ConnectedAction(
    text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, shape: RoundedCornerShape,
    container: Color, content: Color, weight: Float, onClick: () -> Unit,
) {
    Surface(onClick = onClick, modifier = Modifier.weight(weight).height(56.dp), shape = shape, color = container, contentColor = content) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TripCompleteScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var rating by rememberSaveable { mutableIntStateOf(5) }
    var tip by rememberSaveable { mutableIntStateOf(1) }
    val tags = remember { mutableStateListOf("Smooth driving", "Clean car") }
    val tipAmounts = listOf(0, 2, 3, 5)
    Column(Modifier.fillMaxSize().background(c.primary)) {
        Box(Modifier.fillMaxWidth().statusBarsPadding()) {
            ShapeBadge(NovaShape.Burst, 220.dp, Color.White.copy(alpha = 0.08f), Modifier.align(Alignment.TopEnd).offset(x = 60.dp, y = (-40).dp))
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("You've arrived · 10:41", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.primaryContainer)
                Text(Sample.fare, style = MaterialTheme.typography.displayLarge, color = Color.White)
                Text("Paid with Google Pay · ${Sample.distance} · 27 min", fontSize = 15.sp, color = c.primaryContainer)
            }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(c.surface)
                .navigationBarsPadding().padding(16.dp, 24.dp, 16.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("How was your ride with ${Sample.driverName}?", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { n ->
                        val on = n <= rating
                        val scale by animateFloatAsState(if (n == rating) 1.12f else 1f, MaterialTheme.motionScheme.fastSpatialSpec())
                        val bg by animateColorAsState(if (on) NovaPalette.CoralBright else c.surfaceContainerHigh, MaterialTheme.motionScheme.defaultEffectsSpec())
                        Box(
                            Modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(NovaShape.Sunny.toShape()).background(bg)
                                .selectable(n == rating, onClick = { rating = n }, role = Role.RadioButton)
                                .semantics { contentDescription = "$n star" + if (n > 1) "s" else "" },
                            contentAlignment = Alignment.Center,
                        ) { Icon(NovaIcons.Star, null, tint = if (on) NovaPalette.OnCoralContainer else c.outlineVariant, modifier = Modifier.size(24.dp)) }
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                listOf("Smooth driving", "Clean car", "Great music", "Knew the route").forEach { t ->
                    val on = t in tags
                    FilterChip(
                        selected = on,
                        onClick = { if (on) tags.remove(t) else tags.add(t) },
                        label = { Text(t, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) },
                        leadingIcon = if (on) { { Icon(NovaIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                        shape = RoundedCornerShape(10.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.secondaryContainer, selectedLabelColor = c.onSecondaryContainer, selectedLeadingIconColor = c.onSecondaryContainer),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add a tip · 100% goes to ${Sample.driverName}", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                com.nova.designsystem.components.NovaConnectedGroup(
                    options = listOf("No tip", "$2", "$3", "$5"), selectedIndex = tip, onSelect = { tip = it }, height = 56.dp,
                    checkedContainer = c.tertiaryContainer, checkedContent = c.onTertiaryContainer,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NovaOutlinedButton("Receipt", onClick = { nav.go(Receipt) }, height = 64.dp)
                val amt = tipAmounts[tip]
                NovaButton(if (amt > 0) "Submit & tip $$amt" else "Submit rating", onClick = { nav.reset(Home) }, modifier = Modifier.weight(1f))
            }
        }
    }
}
