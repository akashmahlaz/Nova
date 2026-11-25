package com.nova.rider.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.IconTile
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaOutlinedButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.NovaTextField
import com.nova.designsystem.components.NovaTopBar
import com.nova.designsystem.components.RiderTab
import com.nova.designsystem.components.SectionLabel
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.Ride
import com.nova.rider.data.trip.TripFormat
import com.nova.rider.data.trip.TripStore
import com.nova.rider.data.trip.tripErrorMessage
import kotlinx.coroutines.launch
import com.nova.rider.data.Sample
import com.nova.rider.feature.common.ConnectedButton
import com.nova.rider.feature.common.MiniRouteMap
import com.nova.rider.feature.common.TabPage
import com.nova.rider.navigation.AddCard
import com.nova.rider.navigation.Help
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.LostItem
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Receipt
import com.nova.rider.navigation.Schedule
import com.nova.rider.navigation.SupportChat

@Composable
fun ActivityScreen(nav: Navigator, empty: Boolean = false) {
    val c = MaterialTheme.colorScheme
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Rides from the server, newest first (first page; older pages load with "Show more").
    var rides by androidx.compose.runtime.remember { mutableStateOf<List<Ride>?>(null) }
    var loadError by androidx.compose.runtime.remember { mutableStateOf<String?>(null) }
    var hasMore by androidx.compose.runtime.remember { mutableStateOf(false) }
    var cancelTarget by androidx.compose.runtime.remember { mutableStateOf<Ride?>(null) }
    suspend fun load() {
        loadError = null
        runCatching { NovaApi.rides(limit = 20) }
            .onSuccess { rides = it; hasMore = it.size == 20 }
            .onFailure { loadError = tripErrorMessage(it) }
    }
    androidx.compose.runtime.LaunchedEffect(empty) { if (!empty) load() else rides = emptyList() }
    val upcomingRides = rides.orEmpty().filter { it.status == "scheduled" }.sortedBy { it.scheduledAt }
    val pastRides = rides.orEmpty().filter { it.status != "scheduled" }
    TabPage("Activity", RiderTab.Activity, nav::tab) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Rides", "Scheduled").forEachIndexed { i, label ->
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
        if (rides == null) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                if (loadError != null) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(loadError!!, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    NovaOutlinedButton("Try again", onClick = { scope.launch { load() } })
                } else androidx.compose.material3.LoadingIndicator()
            }
            return@TabPage
        }
        val showTrips = filter != 2 && pastRides.isNotEmpty()
        val showUpcoming = filter != 1 && upcomingRides.isNotEmpty()
        if (!showTrips && !showUpcoming) {
            EmptyActivity(onBook = { nav.reset(Home) })
            return@TabPage
        }
        if (showUpcoming) upcomingRides.forEach { r ->
            Column(Modifier.clip(RoundedCornerShape(28.dp)).background(c.tertiaryContainer).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(NovaIcons.Calendar, c.tertiary, Color.White, size = 48.dp, radius = 16.dp, iconSize = 22.dp)
                    Column(Modifier.weight(1f)) {
                        Text("Upcoming · ${RideTime.day(r.scheduledAt)}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                        Text("${RideTime.time(r.scheduledAt)} to ${r.dropoff.name}", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer)
                        Text(
                            "${r.productName} · ${TripFormat.money(r.fareCents, r.currency)}" + (r.passenger?.let { " · for ${it.name}" } ?: ""),
                            fontSize = 13.sp, color = c.onTertiaryContainer,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(onClick = { cancelTarget = r }, shape = CircleShape, color = Color.Transparent, contentColor = c.onTertiaryContainer, border = BorderStroke(1.dp, c.tertiary), modifier = Modifier.height(40.dp)) {
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text("Cancel ride", fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        if (showTrips) {
            SectionLabel("Recent", Modifier.padding(horizontal = 4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                pastRides.forEachIndexed { i, t ->
                    val shape = when {
                        pastRides.size == 1 -> RoundedCornerShape(20.dp)
                        i == 0 -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)
                        i == pastRides.lastIndex -> RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)
                        else -> RoundedCornerShape(4.dp)
                    }
                    val (statusText, statusColor) = when (t.status) {
                        "completed" -> "Paid" to NovaTheme.colors.success
                        "cancelled" -> "Cancelled" to c.error
                        "no_drivers" -> "No driver found" to c.onSurfaceVariant
                        else -> "In progress" to c.primary
                    }
                    Surface(
                        onClick = {
                            when {
                                t.status == "completed" -> nav.go(Receipt)
                                // Finding driver resumes the active ride from the server.
                                t.isActive -> { TripStore.forgetRide(); nav.go(com.nova.rider.navigation.FindingDriver) }
                            }
                        },
                        shape = shape, color = c.surfaceContainer,
                    ) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            IconTile(NovaIcons.Car, c.primaryContainer, c.onPrimaryContainer, size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(t.dropoff.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("${RideTime.day(t.requestedAt ?: t.createdAt)} · ${RideTime.time(t.requestedAt ?: t.createdAt)} · ${t.productName}", fontSize = 13.sp, color = c.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(TripFormat.money(t.fareCents, t.currency), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                Text(statusText, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = statusColor)
                            }
                        }
                    }
                }
            }
            if (hasMore) {
                NovaOutlinedButton("Show more", onClick = {
                    scope.launch {
                        val last = rides?.lastOrNull() ?: return@launch
                        runCatching { NovaApi.rides(before = last.createdAt, limit = 20) }.onSuccess {
                            rides = rides.orEmpty() + it
                            hasMore = it.size == 20
                        }
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    cancelTarget?.let { r ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancel this ride?") },
            text = { Text("${RideTime.day(r.scheduledAt)}, ${RideTime.time(r.scheduledAt)} to ${r.dropoff.name}. You won't be charged.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    cancelTarget = null
                    scope.launch {
                        runCatching { NovaApi.cancelRide(r.id, "rider_cancelled_scheduled") }
                            .onSuccess { updated -> rides = rides?.map { if (it.id == updated.id) updated else it } }
                            .onFailure { loadError = tripErrorMessage(it) }
                    }
                }) { Text("Cancel ride") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { cancelTarget = null }) { Text("Keep ride") } },
        )
    }
}

/** Local day and time labels for server timestamps (ISO-8601 UTC). */
private object RideTime {
    private val zone get() = java.time.ZoneId.systemDefault()
    private fun parse(iso: String?) = iso?.let { runCatching { java.time.Instant.parse(it).atZone(zone) }.getOrNull() }
    fun day(iso: String?): String {
        val t = parse(iso) ?: return ""
        val today = java.time.LocalDate.now(zone)
        return when (t.toLocalDate()) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            today.minusDays(1) -> "Yesterday"
            else -> t.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
        }
    }
    fun time(iso: String?): String =
        parse(iso)?.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")) ?: ""
}

/** Empty state (also used for the "no trips yet" edge case). */
@Composable
fun EmptyActivity(onBook: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(contentAlignment = Alignment.Center) {
            ShapeBadge(NovaShape.Cookie, 168.dp, c.primaryContainer)
            ShapeBadge(NovaShape.Sunny, 72.dp, c.tertiaryContainer, Modifier.offset(x = 64.dp, y = (-56).dp))
            Icon(NovaIcons.Receipt, null, tint = c.onPrimaryContainer, modifier = Modifier.size(64.dp))
        }
        Text("No trips yet", style = MaterialTheme.typography.headlineMedium)
        Text("Your rides and receipts will show up here.\nWhere are we heading first?", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        NovaButton("Book your first ride", onClick = onBook, modifier = Modifier.padding(top = 8.dp, start = 24.dp, end = 24.dp), height = 56.dp, leadingIcon = NovaIcons.Car)
    }
}

@Composable
fun ReceiptScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(c.surface)) {
        NovaTopBar(onBack = { nav.back() }, title = "Trip details", actions = {
            IconButton(onClick = {}) { Icon(NovaIcons.Download, "Download invoice") }
        })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MiniRouteMap()
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Sat 14 Nov · 10:14 AM · Comfort", fontSize = 14.sp, color = c.onSurfaceVariant)
                    Text(Sample.fare, style = MaterialTheme.typography.displaySmall)
                }
                Box(Modifier.padding(bottom = 8.dp).height(32.dp).clip(CircleShape).background(NovaPalette.Mint).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text("Paid · Google Pay", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = NovaPalette.OnMint)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Base fare" to "$4.00", "Distance · ${Sample.distance}" to "$17.15", "Time · 27 min" to "$5.40", "Platform fee" to "$1.85").forEach { (k, v) ->
                        Row { Text(k, Modifier.weight(1f), fontSize = 15.sp); Text(v, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)).background(c.primaryContainer).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("Total", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
                    Text(Sample.fare, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = c.onPrimaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CookieAvatar(Sample.driverInitials, c.primaryContainer, c.onPrimaryContainer, size = 40.dp)
                Column {
                    Text(Sample.driverFullName, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("You rated ★ 5 · tipped $3", fontSize = 13.sp, color = c.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ConnectedButton("Lost an item", 0, 3, onClick = { nav.go(LostItem) })
            ConnectedButton("Fare issue", 1, 3, onClick = { nav.go(SupportChat) })
            ConnectedButton("Get help", 2, 3, onClick = { nav.go(Help) }, container = c.primary, content = Color.White)
        }
    }
}

@Composable
fun WalletScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var default by rememberSaveable { mutableIntStateOf(0) }
    var autoReload by rememberSaveable { mutableStateOf(false) }
    TabPage("Wallet", RiderTab.Wallet, nav::tab) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(c.onSurface)) {
            ShapeBadge(NovaShape.Burst, 180.dp, NovaPalette.Volt, Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-40).dp))
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Nova Credit", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.outlineVariant)
                    Text("$42.00", style = MaterialTheme.typography.displayMedium, color = Color.White)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    ConnectedButton("Add money", 0, 2, onClick = {}, container = NovaPalette.Volt, content = NovaPalette.OnVolt, height = 52.dp, icon = NovaIcons.Plus)
                    ConnectedButton(if (autoReload) "Auto-reload on" else "Auto-reload", 1, 2, onClick = { autoReload = !autoReload }, container = Color.White.copy(alpha = if (autoReload) 0.28f else 0.14f), content = Color.White, height = 52.dp)
                }
            }
        }
        SectionLabel("Payment methods", Modifier.padding(horizontal = 4.dp))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val methods = listOf(
                Triple("Google Pay", if (default == 0) "Default" else "Tap to make default", "GPay"),
                Triple("Visa ···· 4417", "Expires 08/28", "card"),
                Triple("Mastercard ···· 0932", if (default == 2) "Default" else null, "$"),
            )
            methods.forEachIndexed { i, (name, sub, badge) ->
                val on = i == default
                val shape = when (i) { 0 -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp); 2 -> RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp); else -> RoundedCornerShape(4.dp) }
                val fg = if (on) c.onPrimaryContainer else c.onSurface
                Row(
                    Modifier.fillMaxWidth().clip(shape).background(if (on) c.primaryContainer else c.surfaceContainer)
                        .selectable(on, onClick = { default = i }, role = Role.RadioButton).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (badge) {
                        "GPay" -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.primary), contentAlignment = Alignment.Center) { Text("GPay", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White) }
                        "card" -> IconTile(NovaIcons.Card, c.surfaceContainerHighest, c.onSurface)
                        else -> Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(c.tertiaryContainer), contentAlignment = Alignment.Center) { Text("$", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = c.onTertiaryContainer) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 16.sp, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Bold, color = fg)
                        if (sub != null) Text(sub, fontSize = 13.sp, color = if (on) fg else c.onSurfaceVariant)
                    }
                    RadioButton(selected = on, onClick = null)
                }
            }
        }
        NovaOutlinedButton("Add payment method", onClick = { nav.go(AddCard) }, modifier = Modifier.fillMaxWidth(), height = 48.dp, leadingIcon = NovaIcons.Plus)
    }
}

@Composable
fun AddCardScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var number by rememberSaveable { mutableStateOf("4242 4242 4242 ") }
    var expiry by rememberSaveable { mutableStateOf("08 / 28") }
    var cvc by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("Akash K") }
    var makeDefault by rememberSaveable { mutableStateOf(true) }
    val digits = number.filter(Char::isDigit)
    val preview = (digits + "•".repeat((16 - digits.length).coerceAtLeast(0))).take(16).chunked(4).joinToString(" ").replace('•', '·')
    val valid = digits.length >= 15 && expiry.filter(Char::isDigit).length == 4 && cvc.length in 3..4 && name.isNotBlank()
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        NovaTopBar(onBack = { nav.back() }, title = "Add a card")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)).background(c.primary)) {
                ShapeBadge(NovaShape.Burst, 200.dp, Color.White.copy(alpha = 0.14f), Modifier.align(Alignment.BottomEnd).offset(x = 50.dp, y = 70.dp))
                Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row { Text("nova", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White); Text("VISA", fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White) }
                    Text(preview, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp, color = Color.White)
                    Row { Text(name.uppercase().ifBlank { "YOUR NAME" }, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White); Text(expiry.ifBlank { "MM / YY" }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
            NovaTextField(number, { v -> number = v.filter { it.isDigit() || it == ' ' }.take(19) }, "Card number", placeholder = "1234 5678 9012 3456", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NovaTextField(expiry, { expiry = it.take(7) }, "Expiry", Modifier.weight(1f), placeholder = "MM / YY", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                NovaTextField(cvc, { cvc = it.filter(Char::isDigit).take(4) }, "CVC", Modifier.weight(1f), placeholder = "3 digits", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
            NovaTextField(name, { name = it }, "Name on card")
            Row(Modifier.toggleable(makeDefault, role = Role.Checkbox) { makeDefault = it }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Checkbox(checked = makeDefault, onCheckedChange = null)
                Text("Make this my default payment", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(NovaIcons.Lock, null, Modifier.size(16.dp), tint = c.onSurfaceVariant)
                Text("Encrypted and stored by our PCI-compliant payment partner", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
            NovaButton("Save card", onClick = { nav.back() }, enabled = valid, modifier = Modifier.alpha(1f))
        }
    }
}
