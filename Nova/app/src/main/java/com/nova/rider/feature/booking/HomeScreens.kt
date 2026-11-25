package com.nova.rider.feature.booking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nova.rider.data.Sample
import com.nova.rider.data.SearchResults
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.profile.ProfileStore
import com.nova.rider.feature.common.DotKind
import com.nova.rider.feature.common.MapFab
import com.nova.rider.feature.common.RouteDot
import com.nova.rider.feature.common.RouteLine
import com.nova.rider.navigation.Account
import com.nova.rider.navigation.AddStop
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.PinMap
import com.nova.rider.navigation.RideOptions
import com.nova.rider.navigation.Schedule
import com.nova.rider.navigation.Search

@Composable
fun HomeScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var whenIndex by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        MapBackdrop {
            car(101f, 239f); car(267f, 309f, 80f); car(195f, 169f, 86f)
            userDot(196f, 252f, c.primary)
        }
        // Expressive search bar
        LaunchedEffect(Unit) { ProfileStore.refreshIfNeeded() }

        val user = AuthStore.session?.user
        val riderName = user?.firstName?.ifBlank { null } ?: Sample.riderName
        val riderInitials = listOfNotNull(
            user?.firstName?.firstOrNull()?.uppercaseChar(),
            user?.lastName?.firstOrNull()?.uppercaseChar(),
        ).joinToString("").ifBlank { Sample.riderInitials }
        val homePlace = ProfileStore.homePlace()
        val workPlace = ProfileStore.workPlace()
        val customPlaces = ProfileStore.customPlaces()

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
                CookieAvatar(riderInitials, c.tertiaryContainer, c.onTertiaryContainer, size = 40.dp)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter)) {
            MapSheet(contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Where to,\n$riderName?", style = MaterialTheme.typography.displaySmall.copy(fontSize = 40.sp, lineHeight = 44.sp))
                    NovaConnectedGroup(
                        options = listOf("Now", "Schedule", "For someone"),
                        selectedIndex = whenIndex,
                        onSelect = { whenIndex = it; if (it == 1) nav.go(Schedule) },
                        leadingIconForChecked = NovaIcons.Check,
                        weights = listOf(1f, 1f, 1.2f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SavedPlaceCard(
                            "Home",
                            homePlace?.address ?: "Tap to set",
                            c.primaryContainer,
                            c.onPrimaryContainer,
                            Modifier.weight(1f),
                            onClick = { nav.go(if (homePlace != null) RideOptions else com.nova.rider.navigation.SavedPlaces) },
                        ) {
                            ShapeBadge(NovaShape.Cookie, 44.dp, c.primary) { Icon(NovaIcons.HomeFilled, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                        }
                        SavedPlaceCard(
                            "Work",
                            workPlace?.address ?: "Tap to set",
                            c.tertiaryContainer,
                            c.onTertiaryContainer,
                            Modifier.weight(1f),
                            onClick = { nav.go(if (workPlace != null) RideOptions else com.nova.rider.navigation.SavedPlaces) },
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
                            shown.forEachIndexed { idx, place ->
                                NovaListItem(
                                    idx,
                                    shown.size,
                                    place.label,
                                    supporting = place.address,
                                    leading = { Icon(NovaIcons.Pin, null) },
                                    onClick = { nav.go(RideOptions) },
                                )
                            }
                        }
                    }
                }
            }
            RiderNavigationBar(selected = RiderTab.Ride, onSelect = nav::tab)
        }
    }
}

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
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
                Text(eta, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SearchScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("Sylvia") }
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        Row(
            Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 8.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { nav.back() }, modifier = Modifier.width(48.dp).fillMaxHeight()) { Icon(NovaIcons.Back, "Back") }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)).background(c.surfaceContainerHigh).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RouteDot(DotKind.Pickup)
                    Text("Current location · Ponsonby Rd", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)).background(c.primaryContainer)
                        .border(2.dp, c.primary, RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)).padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RouteDot(DotKind.Destination)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, color = c.onPrimaryContainer),
                        cursorBrush = SolidColor(c.primary),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { query = "" }) { Icon(NovaIcons.Close, "Clear", tint = c.onPrimaryContainer, modifier = Modifier.size(18.dp)) }
                }
            }
            Surface(
                onClick = { nav.go(AddStop) },
                modifier = Modifier.width(48.dp).fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = c.secondaryContainer,
                contentColor = c.onSecondaryContainer,
            ) { Box(contentAlignment = Alignment.Center) { Icon(NovaIcons.Plus, "Add a stop") } }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(Triple("Set on map", NovaIcons.Pin, PinMap), Triple("Home", NovaIcons.Home, RideOptions), Triple("Work", NovaIcons.Work, RideOptions)).forEach { (label, icon, route) ->
                AssistChip(
                    onClick = { nav.go(route) },
                    label = { Text(label, fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, c.outline),
                    modifier = Modifier.height(40.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Results")
            val results = SearchResults.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) || it.address.contains(query, true) }
            SegmentedColumn {
                results.forEachIndexed { i, p ->
                    val first = i == 0
                    NovaListItem(
                        i, results.size, p.name, supporting = p.address,
                        leading = {
                            IconTile(
                                NovaIcons.Pin,
                                if (first) c.primaryContainer else if (p.name.startsWith("Hoyts")) c.tertiaryContainer else c.surfaceContainerHigh,
                                if (first) c.onPrimaryContainer else if (p.name.startsWith("Hoyts")) c.onTertiaryContainer else c.onSurface,
                            )
                        },
                        trailing = { Text(p.distance, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) },
                        onClick = { nav.go(RideOptions) },
                    )
                }
            }
        }
    }
}

@Composable
fun PinMapScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var spot by rememberSaveable { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize()) {
        MapBackdrop { halo(195f, 420f, 60f, c.primary, 0.08f) }
        Row(Modifier.statusBarsPadding().padding(start = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            MapFab(NovaIcons.Back, "Back", onClick = { nav.back() })
            Box(Modifier.height(44.dp).clip(CircleShape).background(c.onSurface).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                Text("Drag the map to move the pin", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        // Centre pin
        Column(Modifier.align(Alignment.Center).offset(y = (-90).dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 6.dp) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pickup here", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                    Text("2 min walk", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = c.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(6.dp))
            ShapeBadge(NovaShape.Burst, 40.dp, c.primary)
            Box(Modifier.width(3.dp).height(24.dp).background(c.onSurface))
            Box(Modifier.width(12.dp).height(5.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.25f)))
        }
        MapSheet(Modifier.align(Alignment.BottomCenter)) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(NovaIcons.Pin, c.primaryContainer, c.onPrimaryContainer, size = 48.dp, radius = 16.dp)
                    Column(Modifier.weight(1f)) {
                        Text(Sample.pickup, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp))
                        Text(Sample.pickupArea, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                    Surface(onClick = { nav.back() }, shape = CircleShape, color = c.surfaceContainerHighest, modifier = Modifier.height(40.dp)) {
                        Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text("Edit", style = MaterialTheme.typography.labelMedium) }
                    }
                }
                SectionLabel("Suggested pickup spots")
                NovaConnectedGroup(listOf("Front door", "Bus stop", "Corner café"), spot, { spot = it })
                NovaButton("Confirm pickup", onClick = { nav.go(RideOptions) })
            }
        }
    }
}

@Composable
fun AddStopScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var hasStop by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().background(c.surface)) {
        NovaTopBar(onBack = { nav.back() }, title = "Your route")
        Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(20.dp).padding(top = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RouteDot(DotKind.Pickup); RouteLine(50.dp)
                    if (hasStop) { RouteDot(DotKind.Stop); RouteLine(50.dp) }
                    RouteDot(DotKind.Destination)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StopRow("Pickup", Sample.pickup, RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp), c.surfaceContainerHigh, c.onSurface) {
                        Icon(NovaIcons.Drag, "Reorder", tint = c.outline, modifier = Modifier.padding(end = 8.dp))
                    }
                    if (hasStop) {
                        StopRow("Stop 1 · 3 min wait included", "Countdown Ponsonby", RoundedCornerShape(4.dp), c.tertiaryContainer, c.onTertiaryContainer) {
                            IconButton(onClick = { hasStop = false }) { Icon(NovaIcons.Close, "Remove stop", Modifier.size(20.dp)) }
                        }
                    }
                    StopRow("Drop-off", Sample.destination, RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp), c.primaryContainer, c.onPrimaryContainer) {
                        Icon(NovaIcons.Drag, "Reorder", modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            Surface(
                onClick = { hasStop = true },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, c.outline),
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(NovaIcons.Plus, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add another stop", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp))
                    Text(" · ${if (hasStop) 1 else 2} of 2 left", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(c.secondaryContainer).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(NovaIcons.Route, null, tint = c.onSecondaryContainer)
                Text("Your driver will wait up to 3 minutes at each stop. Longer waits may add to the fare.", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = c.onSecondaryContainer)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                Text("New fare · Comfort", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(if (hasStop) "$32.60" else Sample.fare, style = MaterialTheme.typography.headlineSmall)
                if (hasStop) Text(" +$4.20", style = MaterialTheme.typography.labelMedium, color = c.tertiary, modifier = Modifier.padding(bottom = 4.dp))
            }
            NovaButton("Update route", onClick = { nav.go(RideOptions) }, modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun StopRow(label: String, value: String, shape: RoundedCornerShape, container: Color, content: Color, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(shape).background(container).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Normal), color = content)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp), color = content)
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides content) { trailing() }
    }
}
