package com.nova.rider.feature.trip

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.rider.data.trip.FareSummary
import com.nova.rider.data.trip.TripFormat
import com.nova.rider.data.trip.TripStore
import com.nova.rider.data.trip.tripErrorMessage
import com.nova.rider.navigation.Call
import com.nova.rider.navigation.Chat
import com.nova.rider.navigation.Home
import com.nova.rider.navigation.Navigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val ride = TripStore.ride
    val driver = ride?.driver
    val driverName = driver?.firstName ?: "your driver"
    // The server keeps the conversation; the driver's replies arrive over the live socket.
    val rideId = ride?.id
    val messages = com.nova.rider.data.trip.RideChat.messages.takeIf { com.nova.rider.data.trip.RideChat.rideId == rideId }.orEmpty()
    val canSend = rideId != null && ride.status in setOf("accepted", "arriving", "in_progress")
    var draft by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.nova.rider.data.trip.RideChat.open = true
        onDispose { com.nova.rider.data.trip.RideChat.open = false }
    }
    // Load on open, and again when the socket comes back (it may have missed messages).
    LaunchedEffect(rideId, com.nova.rider.data.trip.LiveRide.connected) {
        if (rideId != null && !com.nova.rider.data.trip.RideChat.load(rideId)) error = "Couldn't load messages. Check your connection."
    }
    // Opening the screen reads new pushed messages too, so mark them read on the server.
    LaunchedEffect(messages.size) {
        if (rideId != null && messages.lastOrNull()?.let { it.from == "driver" && it.readAt == null } == true) com.nova.rider.data.trip.RideChat.load(rideId)
    }
    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || rideId == null || com.nova.rider.data.trip.RideChat.sending) return
        scope.launch {
            error = null
            val failed = com.nova.rider.data.trip.RideChat.send(rideId, t)
            if (failed == null) draft = "" else error = when (failed) {
                "chat_closed" -> "Messaging is closed for this ride."
                "empty_message" -> null
                else -> tripErrorMessage(failed)
            }
        }
    }
    val eta = com.nova.rider.data.trip.LiveRide.fixFor(ride?.id)?.etaSeconds
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(c.surfaceContainerLow).statusBarsPadding().padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Back, "Back") }
            CookieAvatar(driver?.initials ?: "", c.primaryContainer, c.onPrimaryContainer, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(driver?.firstName ?: "Your driver", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    listOfNotNull(driver?.vehicle?.let { "${it.color} ${it.model}" }, driver?.vehicle?.plate, eta?.takeIf { ride?.status == "accepted" }?.let { "${TripFormat.duration(it)} away" }).joinToString(" · "),
                    fontSize = 13.sp, color = c.onSurfaceVariant, maxLines = 1,
                )
            }
            FilledIconButton(
                onClick = { nav.go(Call) },
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(16.dp), RoundedCornerShape(24.dp)),
                modifier = Modifier.size(48.dp),
            ) { Icon(NovaIcons.Phone, "Call $driverName") }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().padding(bottom = 8.dp), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clip(CircleShape).background(c.secondaryContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(NovaIcons.Lock, null, Modifier.size(14.dp), tint = c.onSecondaryContainer)
                        Text(
                            if (canSend) "Your number stays private · chats are kept for safety" else "Messaging opens once a driver is assigned",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.onSecondaryContainer,
                        )
                    }
                }
            }
            itemsIndexed(messages, key = { _, m -> m.id }) { i, m ->
                val me = m.from == "rider"
                val prevSame = i > 0 && messages[i - 1].from == m.from
                val nextSame = i < messages.lastIndex && messages[i + 1].from == m.from
                val big = 22.dp; val sm = 6.dp
                val shape = if (me) RoundedCornerShape(big, if (prevSame) sm else big, if (nextSame) sm else big, big)
                else RoundedCornerShape(if (prevSame) sm else big, big, big, if (nextSame) sm else big)
                Box(Modifier.fillMaxWidth(), contentAlignment = if (me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.text,
                        Modifier.widthIn(max = 270.dp).clip(shape).background(if (me) c.primary else c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 16.sp, lineHeight = 22.sp, color = if (me) Color.White else c.onSurface,
                    )
                }
            }
            // Read receipt under the rider's last message, like Uber's "Read 10:31".
            messages.lastOrNull()?.takeIf { it.from == "rider" }?.let { last ->
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(last.readAt?.let { "Read ${clockTime(it)}" } ?: "Sent ${clockTime(last.sentAt)}", fontSize = 12.sp, color = c.outline)
                    }
                }
            }
        }
        error?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.error) }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("On my way", "Can you wait 2 min?", "I’m at the entrance").forEach { q ->
                Surface(onClick = { send(q) }, enabled = canSend, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, c.outline), color = Color.Transparent, modifier = Modifier.height(40.dp)) {
                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) { Text(q, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.surfaceContainerLow).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f).height(56.dp).clip(CircleShape).background(c.surfaceContainerHighest).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) Text("Message $driverName", fontSize = 16.sp, color = c.onSurfaceVariant)
                BasicTextField(
                    draft, { draft = it.take(1000) }, Modifier.fillMaxWidth(), singleLine = true, enabled = canSend,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(draft) }),
                )
            }
            FilledIconButton(
                onClick = { send(draft) },
                enabled = canSend && draft.isNotBlank() && !com.nova.rider.data.trip.RideChat.sending,
                shapes = IconButtonDefaults.shapes(RoundedCornerShape(18.dp), RoundedCornerShape(28.dp)),
                modifier = Modifier.size(56.dp),
            ) { Icon(NovaIcons.Send, "Send") }
        }
    }
}

/** ISO-8601 instant -> local clock time in the device's 12/24 h style. */
private fun clockTime(iso: String): String =
    runCatching { java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(java.time.Instant.parse(iso).toEpochMilli())) }.getOrDefault("")

@Composable
fun CallScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val driver = TripStore.ride?.driver
    // Calls go through the phone's dialer (it owns mute, speaker and the call itself). This screen
    // only stays up when there's no number to call yet.
    LaunchedEffect(driver?.phone) {
        driver?.phone?.let { phone ->
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone"))) }
            nav.back()
        }
    }
    val ring = rememberInfiniteTransition(label = "ring")
    val t by ring.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val ink = c.onPrimaryContainer
    Column(
        Modifier.fillMaxSize().background(c.primaryContainer).statusBarsPadding().navigationBarsPadding().padding(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.height(32.dp).clip(CircleShape).background(ink.copy(alpha = 0.1f)).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(NovaIcons.Lock, null, Modifier.size(14.dp), tint = ink)
            Text("Calls use your phone's dialer", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
        }
        Box(Modifier.padding(top = 24.dp).size(220.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(220.dp).graphicsLayer { val s = 0.7f + 0.4f * t; scaleX = s; scaleY = s; alpha = 0.25f * (1f - t) }.clip(CircleShape).background(c.primary))
            ShapeBadge(NovaShape.Cookie, 160.dp, Color.White) { Text(driver?.initials ?: "", fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = ink) }
        }
        Text(driver?.firstName ?: "Your driver", style = MaterialTheme.typography.headlineLarge, color = ink, modifier = Modifier.padding(top = 12.dp))
        Text(if (driver == null) "You can call once a driver accepts" else "Opening your dialer…", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = ink)
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledIconButton(
                    onClick = { nav.replaceTop(Chat) },
                    shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(28.dp)),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = ink.copy(alpha = 0.1f), contentColor = ink),
                    modifier = Modifier.size(80.dp),
                ) { Icon(NovaIcons.Msg, "Message", Modifier.size(28.dp)) }
                Text("Message", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
            }
        }
        FilledIconButton(
            onClick = { nav.back() },
            shapes = IconButtonDefaults.shapes(CircleShape, RoundedCornerShape(28.dp)),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = c.error, contentColor = Color.White),
            modifier = Modifier.padding(top = 18.dp).size(width = 200.dp, height = 80.dp),
        ) { Icon(NovaIcons.Endcall, "End call", Modifier.size(32.dp)) }
    }
}

@Composable
private fun CallToggle(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean, onChange: (Boolean) -> Unit) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FilledIconToggleButton(
            checked = checked,
            onCheckedChange = onChange,
            shapes = IconButtonDefaults.toggleableShapes(CircleShape, RoundedCornerShape(20.dp), RoundedCornerShape(28.dp)),
            colors = IconButtonDefaults.filledIconToggleButtonColors(
                containerColor = ink.copy(alpha = 0.1f), contentColor = ink,
                checkedContainerColor = ink, checkedContentColor = Color.White,
            ),
            modifier = Modifier.size(80.dp),
        ) { Icon(icon, label, Modifier.size(28.dp)) }
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ink)
    }
}

@Composable
fun ShareTripScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ride = TripStore.ride
    // The ride's public live-tracking link (opens Nova if installed, the web page otherwise).
    val link = ride?.trackUrl.orEmpty()
    // Trusted contacts are the rider's emergency contacts on their Nova account.
    LaunchedEffect(Unit) { com.nova.rider.data.profile.ProfileStore.refreshIfNeeded() }
    val people = com.nova.rider.data.profile.ProfileStore.contacts
    val palette = listOf(c.primaryContainer to c.onPrimaryContainer, NovaPalette.Mint to NovaPalette.OnMint, c.tertiaryContainer to c.onTertiaryContainer)
    // The night auto-share choice and its contacts are kept on this phone.
    val prefs = remember { context.getSharedPreferences("nova_share", android.content.Context.MODE_PRIVATE) }
    val selected = remember { mutableStateListOf<String>().apply { addAll(prefs.getStringSet("contacts", emptySet()).orEmpty()) } }
    var auto by remember { mutableStateOf(prefs.getBoolean("auto_night", false)) }
    LaunchedEffect(auto, selected.toList()) { prefs.edit().putBoolean("auto_night", auto).putStringSet("contacts", selected.toSet()).apply() }
    var copied by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    fun close(after: () -> Unit = {}) = scope.launch { sheet.hide() }.invokeOnCompletion { after() }
    val message = buildString {
        append("I'm on a Nova ride")
        ride?.dropoff?.name?.let { append(" to $it") }
        ride?.driver?.let { append(" with ${it.firstName}, ${it.vehicle.label} ${it.vehicle.plate}") }
        append(". Follow it live: $link")
    }
    Box(Modifier.fillMaxSize()) {
        EnRouteMap()
        ModalBottomSheet(onDismissRequest = { nav.back() }, sheetState = sheet, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Share your trip", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    Text("They'll see your live location, car and ETA until you arrive.", fontSize = 15.sp, color = c.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    people.take(3).forEachIndexed { i, p ->
                        val on = p.id in selected
                        val (bg, fg) = palette[i % palette.size]
                        val radius by animateDpAsState(if (on) 22.dp else 32.dp, MaterialTheme.motionScheme.fastSpatialSpec())
                        Column(
                            Modifier.clip(RoundedCornerShape(16.dp)).toggleable(on, role = Role.Checkbox) { if (it) selected.add(p.id) else selected.remove(p.id) }.padding(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                Modifier.size(64.dp).then(if (on) Modifier.border(3.dp, c.primary, RoundedCornerShape(radius + 3.dp)).padding(0.dp) else Modifier)
                                    .clip(RoundedCornerShape(radius)).background(bg),
                                contentAlignment = Alignment.Center,
                            ) { Text(p.name.trim().take(1).uppercase(), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = fg) }
                            Text(p.name.substringBefore(' '), fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    // Anyone else: the system share sheet (any messaging app).
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = link.isNotBlank()) {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, message)
                            context.startActivity(android.content.Intent.createChooser(send, "Share trip"))
                        }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(64.dp).border(2.dp, c.outline, CircleShape), contentAlignment = Alignment.Center) { Icon(NovaIcons.Plus, "Share with someone else") }
                        Text(if (people.isEmpty()) "Share" else "Other", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                if (people.isEmpty()) Text("Add trusted contacts in Safety to share with them in one tap.", fontSize = 14.sp, color = c.onSurfaceVariant)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(NovaIcons.Link, null)
                    Text(link.removePrefix("https://").ifBlank { "Link appears once your ride is booked" }, Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Surface(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(androidx.compose.ui.platform.ClipEntry(android.content.ClipData.newPlainText("Nova trip link", link)))
                                copied = true
                            }
                        },
                        enabled = link.isNotBlank(),
                        shape = CircleShape, color = c.secondaryContainer, contentColor = c.onSecondaryContainer, modifier = Modifier.height(40.dp),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(if (copied) NovaIcons.Check else NovaIcons.Copy, null, Modifier.size(16.dp))
                            Text(if (copied) "Copied" else "Copy", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 4.dp).toggleable(auto, role = Role.Switch) { auto = it }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-share trips after 9 PM", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("With the people selected above", fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                    Switch(checked = auto, onCheckedChange = null)
                }
                val chosen = people.filter { it.id in selected }
                val n = chosen.size
                NovaButton(
                    if (n > 0) "Share with $n ${if (n > 1) "people" else "person"}" else "Choose who to share with",
                    onClick = {
                        // One text to all chosen contacts through the phone's messaging app.
                        val to = chosen.joinToString(";") { it.phone }
                        val sms = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:$to")).putExtra("sms_body", message)
                        runCatching { context.startActivity(sms) }
                        close { nav.back() }
                    },
                    enabled = n > 0 && link.isNotBlank(),
                )
            }
        }
    }
}

@Composable
fun CancelRideScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val reasons = listOf("Driver is taking too long", "I changed my plans", "Booked by mistake", "Driver asked me to cancel", "Something else")
    var reason by rememberSaveable { mutableIntStateOf(-1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ride = TripStore.ride
    // Ticks so the free window counts down and flips to the fee the moment it ends.
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    // Fresh fee preview and free-until from the server when the sheet opens.
    LaunchedEffect(Unit) { TripStore.refreshRide() }
    val freeLeft = ride?.let { FareSummary.freeCancelSecondsLeft(it, now) }
    val fee = ride?.let { FareSummary.cancelFeeNow(it, now) } ?: 0L
    val laterFee = ride?.cancelFeePreviewCents?.takeIf { it > 0 }
    val driverName = ride?.driver?.firstName ?: "your driver"
    fun money(cents: Long) = ride?.let { TripFormat.money(cents, it.currency) } ?: ""
    Box(Modifier.fillMaxSize()) {
        EnRouteMap()
        ModalBottomSheet(onDismissRequest = { nav.back() }, sheetState = sheet, containerColor = c.surface) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cancel this ride?", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp))
                    val free = fee == 0L
                    Row(
                        Modifier.height(32.dp).clip(RoundedCornerShape(10.dp)).background(if (free) NovaPalette.Mint else c.errorContainer).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val fg = if (free) NovaPalette.OnMint else c.onErrorContainer
                        Icon(NovaIcons.Clock, null, Modifier.size(16.dp), tint = fg)
                        Text(
                            when {
                                !free -> "A ${money(fee)} fee applies"
                                freeLeft != null && freeLeft > 0 && laterFee != null -> "Free to cancel for %d:%02d more".format(freeLeft / 60, freeLeft % 60)
                                else -> "Free to cancel"
                            },
                            fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = fg,
                        )
                    }
                    val detail = when {
                        ride == null -> null
                        !free && ride.status == "arriving" -> "$driverName is waiting at your pickup. The fee pays for their time."
                        !free -> "$driverName is already on the way. The fee pays for their time."
                        ride.driver == null -> "No driver has accepted yet, so there's no fee."
                        freeLeft != null && freeLeft > 0 && laterFee != null -> "After that, a ${money(laterFee)} fee goes to $driverName for their time."
                        else -> null
                    }
                    if (detail != null) Text(detail, fontSize = 14.sp, color = c.onSurfaceVariant)
                    error?.let { Text(it, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.error) }
                }
                Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    reasons.forEachIndexed { i, t ->
                        val on = i == reason
                        val shape = when (i) {
                            0 -> RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp)
                            reasons.lastIndex -> RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp)
                            else -> RoundedCornerShape(4.dp)
                        }
                        Row(
                            Modifier.fillMaxWidth().height(56.dp).clip(shape).background(if (on) c.errorContainer else c.surfaceContainer)
                                .selectable(on, onClick = { reason = i }, role = Role.RadioButton).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = on, onClick = null,
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = c.error, unselectedColor = c.onSurfaceVariant),
                            )
                            Text(t, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) c.onErrorContainer else c.onSurface)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NovaButton("Keep my ride", onClick = { scope.launch { sheet.hide() }.invokeOnCompletion { nav.back() } }, modifier = Modifier.weight(1f), height = 56.dp)
                    NovaButton(
                        when { busy -> "Cancelling…"; fee > 0 -> "Cancel · ${money(fee)}"; else -> "Cancel ride" },
                        onClick = {
                            scope.launch {
                                busy = true
                                error = null
                                if (TripStore.cancelRide(reasons[reason])) {
                                    TripStore.forgetRide()
                                    nav.reset(Home)
                                } else {
                                    error = tripErrorMessage(TripStore.bookError)
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.weight(1f), height = 56.dp, enabled = reason >= 0 && !busy && ride != null,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.errorContainer, contentColor = c.onErrorContainer),
                    )
                }
            }
        }
    }
}
