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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.nova.rider.data.RideChoices
import com.nova.rider.data.Sample
import com.nova.rider.feature.common.DotKind
import com.nova.rider.feature.common.MapFab
import com.nova.rider.feature.common.MapLabel
import com.nova.rider.feature.common.RouteDot
import com.nova.rider.navigation.AddStop
import com.nova.rider.navigation.DriverEnRoute
import com.nova.rider.navigation.FindingDriver
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.RideOptions
import com.nova.rider.navigation.Schedule
import com.nova.rider.navigation.ShareTrip

@Composable
fun RideOptionsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var picked by rememberSaveable { mutableStateOf("comfort") }
    var pay by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val selected = RideChoices.first { it.id == picked }
    Box(Modifier.fillMaxSize()) {
        MapBackdrop {
            route("M82 300 L 74 140 L 190 132 C 240 130 260 190 304 180 L 310 110", c.primary, 6f)
            pickup(82f, 300f); destination(310f, 110f)
        }
        Row(
            Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MapFab(NovaIcons.Back, "Back", onClick = { nav.back() })
            Spacer(Modifier.weight(1f))
            MapLabel("28 min · 10:42")
        }
        MapSheet(Modifier.align(Alignment.BottomCenter), color = c.surface, contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Choose a ride", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp))
                    Text("Ponsonby Rd → ${Sample.destination} · ${Sample.distance}", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = c.onSurfaceVariant)
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RideChoices.forEachIndexed { i, r ->
                        RideRow(
                            name = r.name, seats = r.seats, meta = r.meta, price = r.price,
                            selected = r.id == picked, first = i == 0, last = i == RideChoices.lastIndex,
                            onClick = { picked = r.id },
                        )
                    }
                }
                NovaConnectedGroup(
                    options = listOf("GPay", "Card", "Credit"), selectedIndex = pay, onSelect = { pay = it }, height = 40.dp,
                    checkedContainer = c.secondaryContainer, checkedContent = c.onSecondaryContainer,
                )
                Box {
                    SplitButtonLayout(
                        modifier = Modifier.fillMaxWidth(),
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = { nav.go(FindingDriver) },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                                shapes = SplitButtonDefaults.leadingButtonShapesFor(64.dp),
                                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(64.dp),
                            ) { Text("Book ${selected.name} · ${selected.price}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp)) }
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
                        DropdownMenuItem(text = { Text("Add a stop") }, leadingIcon = { Icon(NovaIcons.Plus, null) }, onClick = { menu = false; nav.go(AddStop) })
                        DropdownMenuItem(text = { Text("Book for someone else") }, leadingIcon = { Icon(NovaIcons.People, null) }, onClick = { menu = false })
                    }
                }
            }
        }
    }
}

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
    val days = listOf("Today" to "14", "Sun" to "15", "Mon" to "16", "Tue" to "17")
    var day by rememberSaveable { mutableIntStateOf(1) }
    var minutes by rememberSaveable { mutableIntStateOf(6 * 60 + 30) } // minutes since midnight
    val hour24 = minutes / 60
    val am = hour24 < 12
    val hour12 = ((hour24 + 11) % 12) + 1
    val mm = minutes % 60
    val label = "%d:%02d %s".format(hour12, mm, if (am) "AM" else "PM")
    Column(
        Modifier.fillMaxSize().background(c.surface).statusBarsPadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Close, "Close") }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Schedule a ride", style = MaterialTheme.typography.headlineLarge)
            Text("Ponsonby Rd → Auckland Airport", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
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
                Surface(onClick = {}, shape = RoundedCornerShape(10.dp), color = c.secondaryContainer, contentColor = c.onSecondaryContainer, modifier = Modifier.height(36.dp)) {
                    Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text("Flight time", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(NovaPalette.Mint).padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(NovaIcons.ShieldCheck, null, tint = NovaPalette.OnMint)
            Text(
                "Fare locked at $68.50 · driver assigned 15 min before pickup · free to cancel until ${"%d:%02d %s".format(((hour24 - 1 + 11 + 24) % 12) + 1, mm, if ((minutes - 60 + 1440) % 1440 < 720) "AM" else "PM")}.",
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = NovaPalette.OnMint,
            )
        }
        Spacer(Modifier.weight(1f))
        NovaButton("Schedule for ${days[day].first}, $label", onClick = { nav.tab(RiderTab.Activity) })
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
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4500)
        nav.replaceTop(DriverEnRoute)
    }
    val pulse = rememberInfiniteTransition(label = "rings")
    val r1 by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "r1")
    val r2 by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart, StartOffset(1200)), label = "r2")
    Box(Modifier.fillMaxSize()) {
        MapBackdrop {
            car(105f, 89f); car(285f, 249f); car(255f, 129f, 86f); car(55f, 309f, 80f)
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = 90.dp).size(240.dp), contentAlignment = Alignment.Center) {
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
        MapSheet(Modifier.align(Alignment.BottomCenter), showHandle = false, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 28.dp, 16.dp, 28.dp)) {
            Column(Modifier.navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ContainedLoadingIndicator(
                        modifier = Modifier.size(96.dp),
                        containerColor = c.primaryContainer,
                        indicatorColor = c.primary,
                    )
                    Text("Finding your Comfort ride", style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("Matching with top-rated drivers near Ponsonby Rd", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SummaryRow(RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)) { RouteDot(DotKind.Pickup); Text(Sample.pickupFull, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    SummaryRow(RoundedCornerShape(4.dp)) { RouteDot(DotKind.Destination); Text(Sample.destination, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    SummaryRow(RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)) {
                        Text("Comfort · Google Pay", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(Sample.fare, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaOutlinedButton("Cancel request", onClick = { nav.popTo(RideOptions) }, modifier = Modifier.weight(1f))
                    NovaTonalButton("Share trip", onClick = { nav.go(ShareTrip) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(shape: RoundedCornerShape, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content,
    )
}
