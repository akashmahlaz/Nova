package com.nova.rider.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.IconTile
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaListItem
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.NovaTextField
import com.nova.designsystem.components.NovaTopBar
import com.nova.designsystem.components.RiderTab
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.components.SwitchRow
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.data.Sample
import com.nova.rider.feature.common.TabPage
import com.nova.rider.navigation.DriverCancelled
import com.nova.rider.navigation.EditProfile
import com.nova.rider.navigation.EmptyActivity
import com.nova.rider.navigation.Help
import com.nova.rider.navigation.LocationDenied
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.NoDrivers
import com.nova.rider.navigation.NoService
import com.nova.rider.navigation.Notifications
import com.nova.rider.navigation.Offers
import com.nova.rider.navigation.Offline
import com.nova.rider.navigation.PaymentFailed
import com.nova.rider.navigation.PinMap
import com.nova.rider.navigation.Route
import com.nova.rider.navigation.Safety
import com.nova.rider.navigation.SavedPlaces
import com.nova.rider.navigation.Settings
import com.nova.rider.navigation.SosActive
import com.nova.rider.navigation.Surge
import com.nova.rider.navigation.Welcome

@Composable
fun AccountScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var dark by rememberSaveable { mutableStateOf(false) }
    TabPage("", RiderTab.Account, nav::tab) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CookieAvatar(Sample.riderInitials, c.tertiaryContainer, c.onTertiaryContainer, size = 80.dp, shape = NovaShape.Sunny)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(Sample.riderName, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 30.sp))
                Text(Sample.riderPhone, fontSize = 14.sp, color = c.onSurfaceVariant)
                Box(Modifier.height(28.dp).clip(CircleShape).background(c.secondaryContainer).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("★ ${Sample.riderRating} rider rating", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onSecondaryContainer)
                }
            }
            Surface(onClick = { nav.go(EditProfile) }, shape = RoundedCornerShape(16.dp), color = c.surfaceContainerHigh, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(NovaIcons.Edit, "Edit profile") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickTile("Places", NovaIcons.Pin, c.primaryContainer, c.onPrimaryContainer) { nav.go(SavedPlaces) }
            QuickTile("Safety", NovaIcons.Shield, c.errorContainer, c.onErrorContainer) { nav.go(Safety) }
            QuickTile("Offers", NovaIcons.Gift, NovaPalette.Mint, NovaPalette.OnMint) { nav.go(Offers) }
        }
        SegmentedColumn {
            NovaListItem(0, 4, "Notifications", leading = { Icon(NovaIcons.Bell, null) }, onClick = { nav.go(Notifications) })
            NovaListItem(1, 4, "Language", leading = { Icon(NovaIcons.Globe, null) }, trailing = { Text("English (NZ)", fontSize = 14.sp, color = c.onSurfaceVariant) }, onClick = { nav.go(Settings) })
            SwitchRow(2, 4, NovaIcons.Moon, "Dark theme", null, dark) { dark = it }
            NovaListItem(3, 4, "Help & support", leading = { Icon(NovaIcons.Help, null) }, onClick = { nav.go(Help) })
        }
        TextButton(onClick = { nav.reset(Welcome) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Log out", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.error)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.QuickTile(label: String, icon: ImageVector, bg: Color, fg: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(24.dp), color = bg, contentColor = fg, modifier = Modifier.weight(1f).height(96.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Icon(icon, null, Modifier.size(26.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

/** Standard secondary page: back bar + scrolling content + optional bottom button. */
@Composable
fun SubPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        NovaTopBar(onBack = onBack, title = title, actions = actions)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        if (bottom != null) Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = bottom)
    }
}

@Composable
fun EditProfileScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var name by rememberSaveable { mutableStateOf(Sample.riderName) }
    var email by rememberSaveable { mutableStateOf("akash@example.co.nz") }
    SubPage("Edit profile", onBack = { nav.back() }, bottom = { NovaButton("Save changes", onClick = { nav.back() }, enabled = name.isNotBlank()) }) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).size(120.dp)) {
            CookieAvatar(Sample.riderInitials, c.tertiaryContainer, c.onTertiaryContainer, size = 120.dp, shape = NovaShape.Sunny)
            Surface(
                onClick = {}, shape = RoundedCornerShape(14.dp), color = c.primary, contentColor = Color.White,
                border = BorderStroke(3.dp, c.surface), modifier = Modifier.align(Alignment.BottomEnd).offset(y = (-4).dp).size(44.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(NovaIcons.Camera, "Change photo", Modifier.size(20.dp)) } }
        }
        NovaTextField(name, { name = it }, "Preferred name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
        NovaTextField(email, { email = it }, "Email", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        ReadOnlyField("Mobile", Sample.riderPhone) {
            Row(Modifier.height(28.dp).clip(CircleShape).background(NovaPalette.Mint).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(NovaIcons.Check, null, Modifier.size(14.dp), tint = NovaPalette.OnMint)
                Text("Verified", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = NovaPalette.OnMint)
            }
        }
        ReadOnlyField("Emergency contact", "Mum · +64 27 555 0142", onClick = { nav.go(Safety) }) { Icon(NovaIcons.Chev, null) }
    }
}

@Composable
private fun ReadOnlyField(label: String, value: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(onClick = onClick ?: {}, enabled = onClick != null, shape = RoundedCornerShape(16.dp), color = c.surfaceContainer, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSurfaceVariant)
                Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.onSurface)
            }
            trailing()
        }
    }
}

@Composable
fun SavedPlacesScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var suggestion by rememberSaveable { mutableStateOf(true) }
    var savedSylvia by rememberSaveable { mutableStateOf(false) }
    val places = buildList {
        add(Triple("Home", Sample.pickupFull, NovaIcons.Home to (c.primaryContainer to c.onPrimaryContainer)))
        add(Triple("Work", "Commercial Bay, 7 Queen St", NovaIcons.Work to (c.tertiaryContainer to c.onTertiaryContainer)))
        add(Triple("Gym", "Les Mills, 186 Victoria St W", NovaIcons.Flame to (NovaPalette.Mint to NovaPalette.OnMint)))
        if (savedSylvia) add(Triple("Sylvia Park", Sample.destinationAddress, NovaIcons.Pin to (c.secondaryContainer to c.onSecondaryContainer)))
    }
    SubPage("Saved places", onBack = { nav.back() }) {
        SegmentedColumn {
            places.forEachIndexed { i, (name, addr, style) ->
                NovaListItem(i, places.size, name, supporting = addr, leading = { IconTile(style.first, style.second.first, style.second.second) }, trailing = { Icon(NovaIcons.Edit, "Edit $name", Modifier.size(20.dp)) }, onClick = { nav.go(PinMap) })
            }
        }
        Surface(onClick = { nav.go(PinMap) }, shape = RoundedCornerShape(16.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth().height(56.dp).dashedBorder(c.outline)) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(NovaIcons.Plus, null, Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("Add a place", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (suggestion) {
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(c.secondaryContainer).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShapeBadge(NovaShape.Cookie4, 48.dp, c.primary) { Icon(NovaIcons.Star, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                    Text(
                        buildAnnotatedString { append("You've been to "); withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(Sample.destination) }; append(" 6 times this month. Save it for one-tap booking?") },
                        fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold, color = c.onSecondaryContainer,
                    )
                }
                Row(Modifier.padding(start = 60.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = { savedSylvia = true; suggestion = false }, shape = CircleShape, color = c.primary, contentColor = Color.White, modifier = Modifier.height(40.dp)) {
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Save", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                    TextButton(onClick = { suggestion = false }) { Text("Not now", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onSecondaryContainer) }
                }
            }
        }
    }
}

/** Dashed rounded outline (Compose has no dashed border modifier, so it is drawn behind). */
fun Modifier.dashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp = 16.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}

private data class Notice(val title: String, val body: String, val time: String, val icon: ImageVector, val kind: Int, val today: Boolean)

@Composable
fun NotificationsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var allRead by rememberSaveable { mutableStateOf(false) }
    val all = listOf(
        Notice("Your receipt is ready", "Comfort to Sylvia Park · ${Sample.fare}", "10:42 AM", NovaIcons.ReceiptFilled, 1, true),
        Notice("${Sample.driverName} rated you 5 stars", "Thanks for being a great rider", "10:44 AM", NovaIcons.Star, 1, true),
        Notice("Airport ride tomorrow", "Pickup 6:30 AM · driver assigned 15 min before", "Yesterday", NovaIcons.Calendar, 1, false),
        Notice("Weekend offer unlocked", "20% off up to 2 rides this weekend", "Thu", NovaIcons.Tag, 2, false),
        Notice("New: share trips automatically", "Turn on auto-share for late-night rides", "Mon", NovaIcons.Share, 0, false),
    )
    val colors = listOf(c.primaryContainer to c.onPrimaryContainer, c.tertiaryContainer to c.onTertiaryContainer, c.secondaryContainer to c.onSecondaryContainer, NovaPalette.Mint to NovaPalette.OnMint, c.surfaceContainerHigh to c.onSurface)
    val shown = all.withIndex().filter { filter == 0 || it.value.kind == filter }
    SubPage("Notifications", onBack = { nav.back() }, actions = {
        TextButton(onClick = { allRead = true }, enabled = !allRead) { Text("Mark all read", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
    }) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Trips", "Offers").forEachIndexed { i, label ->
                val on = i == filter
                FilterChip(
                    selected = on, onClick = { filter = i },
                    label = { Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) },
                    leadingIcon = if (on) { { Icon(NovaIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                    shape = if (on) CircleShape else RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.primary, selectedLabelColor = Color.White, selectedLeadingIconColor = Color.White),
                    modifier = Modifier.height(40.dp),
                )
            }
        }
        listOf(true to "Today", false to "Earlier").forEach { (today, label) ->
            val group = shown.filter { it.value.today == today }
            if (group.isNotEmpty()) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                SegmentedColumn {
                    group.forEachIndexed { i, (orig, n) ->
                        val (bg, fg) = colors[orig]
                        NovaListItem(
                            i, group.size, n.title, supporting = n.body + "\n" + n.time,
                            leading = { IconTile(n.icon, bg, fg, size = 44.dp, radius = 16.dp) },
                            trailing = if (n.today && !allRead) { { Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary)) } } else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OffersScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var code by rememberSaveable { mutableStateOf("KIAORA20") }
    var applied by rememberSaveable { mutableStateOf(false) }
    SubPage("Offers", onBack = { nav.back() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (applied) NovaPalette.Mint else c.surface)
                    .border(if (applied) 2.dp else 1.dp, if (applied) NovaTheme.colors.success else c.outline, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    code, { code = it.uppercase(); applied = false }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
            NovaButton("Apply", onClick = { applied = code.isNotBlank() }, modifier = Modifier.weight(0.5f), height = 56.dp)
        }
        if (applied) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(NovaIcons.Check, null, tint = NovaTheme.colors.success)
                Text("20% off your next 2 rides · added", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NovaTheme.colors.success)
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(min = 180.dp).clip(RoundedCornerShape(32.dp)).background(c.tertiaryContainer)) {
            ShapeBadge(NovaShape.Sunny, 180.dp, NovaPalette.CoralBright, Modifier.align(Alignment.BottomEnd).offset(x = 40.dp, y = 40.dp)) {
                Icon(NovaIcons.Flag, null, tint = c.onTertiaryContainer, modifier = Modifier.offset(x = (-24).dp, y = (-24).dp).size(40.dp))
            }
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.height(28.dp).clip(RoundedCornerShape(8.dp)).background(c.tertiary).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("WEEKDAYS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp, color = Color.White)
                }
                Text("15% off\nairport rides", fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                Text("Auto-applied · ends 30 Nov", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.onTertiaryContainer)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfferTile("$5 off Electric", "2 left this month", NovaIcons.Flash, c.primaryContainer, c.onPrimaryContainer, RoundedCornerShape(28.dp))
            OfferTile("Give $10, get $10", "Invite friends", NovaIcons.Gift, NovaPalette.Mint, NovaPalette.OnMint, RoundedCornerShape(28.dp, 28.dp, 28.dp, 8.dp))
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your invite code", fontSize = 13.sp, color = c.onSurfaceVariant)
                Text("AKASH10", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            Surface(
                onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, "Get $10 off your first Nova ride with my code AKASH10")
                    }
                    context.startActivity(android.content.Intent.createChooser(send, null))
                },
                shape = CircleShape, color = c.primary, contentColor = Color.White, modifier = Modifier.height(44.dp),
            ) { Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Share", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) } }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.OfferTile(title: String, sub: String, icon: ImageVector, bg: Color, fg: Color, shape: RoundedCornerShape) {
    Column(Modifier.weight(1f).clip(shape).background(bg).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = fg)
        Text(title, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold, color = fg)
        Text(sub, fontSize = 13.sp, color = fg)
    }
}

@Composable
fun SettingsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var dark by rememberSaveable { mutableStateOf(false) }
    var wav by rememberSaveable { mutableStateOf(false) }
    var quiet by rememberSaveable { mutableStateOf(true) }
    var tripLocation by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    SubPage("Settings", onBack = { nav.back() }) {
        Group("Preferences")
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Moon, "Dark theme", "Follow system when off", dark) { dark = it }
            NovaListItem(1, 2, "Language", supporting = "English (NZ)", leading = { Icon(NovaIcons.Globe, null, tint = c.onSurfaceVariant) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = {})
        }
        Group("Ride needs")
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Access, "Wheelchair-accessible vehicles", "Only match me with WAV cars", wav) { wav = it }
            SwitchRow(1, 2, NovaIcons.Volume, "Quiet ride", "Let drivers know you prefer silence", quiet) { quiet = it }
        }
        Group("Privacy")
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Pin, "Location only during trips", "Stops background location after drop-off", tripLocation) { tripLocation = it }
            NovaListItem(1, 2, "Download my data", supporting = "Under the NZ Privacy Act 2020", leading = { Icon(NovaIcons.Download, null, tint = c.onSurfaceVariant) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = {})
        }
        Group("Design preview · edge cases")
        val edges: List<Pair<String, Route>> = listOf(
            "No drivers nearby" to NoDrivers, "Surge pricing" to Surge, "Driver cancelled" to DriverCancelled,
            "Payment failed" to PaymentFailed, "No connection" to Offline, "Location off" to LocationDenied,
            "Outside service area" to NoService, "No trips yet" to EmptyActivity, "SOS active" to SosActive,
        )
        SegmentedColumn {
            edges.forEachIndexed { i, (label, route) ->
                NovaListItem(i, edges.size, label, leading = { Icon(NovaIcons.Warning, null, tint = c.onSurfaceVariant) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = { nav.go(route) })
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.nova.designsystem.components.NovaOutlinedButton("Log out", onClick = { nav.reset(Welcome) }, modifier = Modifier.weight(1f), height = 52.dp, leadingIcon = NovaIcons.Logout)
            com.nova.designsystem.components.NovaTonalButton(
                "Delete account", onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), height = 52.dp, leadingIcon = NovaIcons.Trash,
                containerColor = c.errorContainer, contentColor = c.onErrorContainer,
            )
        }
    }
    if (confirmDelete) {
        BasicAlertDialog(onDismissRequest = { confirmDelete = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShapeBadge(NovaShape.Cookie, 56.dp, c.errorContainer) { Icon(NovaIcons.Trash, null, tint = c.onErrorContainer) }
                    Text("Delete your account?", style = MaterialTheme.typography.headlineSmall)
                    Text("Your trips, receipts and Nova Credit ($42.00) will be removed after 30 days. You can log back in before then to cancel.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmDelete = false }) { Text("Keep account") }
                        TextButton(onClick = { confirmDelete = false; nav.reset(Welcome) }) { Text("Delete", color = c.error) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}
