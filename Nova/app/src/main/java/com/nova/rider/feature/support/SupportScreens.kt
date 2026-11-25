package com.nova.rider.feature.support

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
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
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.data.Sample
import com.nova.rider.data.auth.authErrorMessage
import com.nova.rider.data.profile.ProfileStore
import com.nova.rider.feature.account.SubPage
import com.nova.rider.navigation.LostItem
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.ShareTrip
import com.nova.rider.navigation.SosActive
import com.nova.rider.navigation.SupportChat
import kotlinx.coroutines.launch

private val SafetySurface = Color(0xFFFFF8F7)
private val SafetyContainer = Color(0xFFF5EDEC)
private val SafetyInk = Color(0xFF201A19)
private val SafetyVariant = Color(0xFF534341)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val hold = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var showContactsSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ProfileStore.refreshIfNeeded() }
    val contacts = ProfileStore.contacts
    val watchingSummary = when (contacts.size) {
        0 -> "Add a trusted contact to share trips"
        1 -> "${contacts[0].name} can watch your trip"
        else -> "${contacts[0].name} and ${contacts.size - 1} other${if (contacts.size > 2) "s" else ""}"
    }

    Column(Modifier.fillMaxSize().background(SafetySurface)) {
        NovaTopBar(onBack = { nav.back() }, title = "Safety")
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Press and hold for 2 seconds; the ring fills while held and resets on release.
                Box(
                    Modifier.size(200.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription = "SOS. Hold for 2 seconds to call 111"
                            onClick(label = "Call 111") { nav.go(SosActive); true }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                val job = scope.launch {
                                    hold.animateTo(1f, tween(2000, easing = LinearEasing))
                                    nav.go(SosActive)
                                }
                                tryAwaitRelease()
                                if (hold.value < 1f) { job.cancel(); scope.launch { hold.animateTo(0f, tween(250)) } }
                            })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    ShapeBadge(NovaShape.Sunny, 200.dp, c.errorContainer)
                    Canvas(Modifier.size(172.dp)) {
                        drawArc(c.error, -90f, 360f * hold.value, false, Offset.Zero, Size(size.width, size.height), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(
                        Modifier.size(148.dp).shadow(16.dp, CircleShape, ambientColor = c.error, spotColor = c.error).clip(CircleShape).background(c.error),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text("SOS", fontSize = 44.sp, lineHeight = 44.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp, color = Color.White)
                        Text("Hold to call 111", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Text(
                    "We'll share your live location, car and driver details with emergency services and your contacts.",
                    fontSize = 14.sp, lineHeight = 20.sp, color = SafetyVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Surface(onClick = { nav.go(ShareTrip) }, shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 4.dp), color = c.errorContainer, contentColor = c.onErrorContainer, modifier = Modifier.fillMaxWidth().height(68.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(NovaIcons.Share, null)
                        Column(Modifier.weight(1f)) {
                            Text("Share live trip", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                            Text(watchingSummary, fontSize = 13.sp)
                        }
                        if (contacts.isNotEmpty()) {
                            Row {
                                contacts.take(2).forEachIndexed { idx, contact ->
                                    MiniAvatar(
                                        contact.name.firstOrNull()?.uppercase() ?: "?",
                                        if (idx == 0) c.primaryContainer else NovaPalette.Mint,
                                        if (idx == 0) c.onPrimaryContainer else NovaPalette.OnMint,
                                        c.errorContainer,
                                        if (idx == 1) Modifier.offset(x = (-8).dp) else Modifier,
                                    )
                                }
                            }
                        }
                    }
                }
                SafetyRow(NovaIcons.Mic, "Record trip audio", RoundedCornerShape(4.dp)) {}
                SafetyRow(NovaIcons.Flag, "Report a safety issue", RoundedCornerShape(4.dp)) { nav.go(SupportChat) }
                SafetyRow(NovaIcons.People, "Trusted contacts", RoundedCornerShape(4.dp, 4.dp, 20.dp, 20.dp), trailing = contacts.size.toString()) {
                    showContactsSheet = true
                }
            }
        }
    }

    if (showContactsSheet) {
        TrustedContactsSheet(onDismiss = { showContactsSheet = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrustedContactsSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val contacts = ProfileStore.contacts
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Trusted contacts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (contacts.isNotEmpty()) {
                SegmentedColumn {
                    contacts.forEachIndexed { idx, contact ->
                        val sub = listOfNotNull(contact.relationship.ifBlank { null }, contact.phone).joinToString(" · ")
                        NovaListItem(
                            idx,
                            contacts.size,
                            contact.name,
                            supporting = sub,
                            trailing = {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            runCatching { ProfileStore.deleteContact(contact.id) }
                                                .onFailure { error = authErrorMessage(it) }
                                        }
                                    },
                                ) { Icon(NovaIcons.Close, "Remove ${contact.name}", Modifier.size(18.dp)) }
                            },
                        )
                    }
                }
            }
            NovaTextField(name, { name = it }, "Contact name", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            NovaTextField(phone, { phone = it }, "Mobile (+country code)", placeholder = com.nova.rider.feature.account.exampleInternationalMobile(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            NovaTextField(relationship, { relationship = it }, "Relationship (optional)", placeholder = "Mum, Partner, Friend", keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
            NovaButton(
                if (busy) "Adding…" else "Add trusted contact",
                enabled = name.isNotBlank() && phone.trim().startsWith("+") && !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            ProfileStore.addContact(name.trim(), phone.trim(), relationship.trim())
                            name = ""
                            phone = ""
                            relationship = ""
                        } catch (e: Throwable) {
                            error = authErrorMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MiniAvatar(text: String, bg: Color, fg: Color, ring: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(32.dp).clip(CircleShape).background(ring).padding(2.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = fg)
    }
}

@Composable
private fun SafetyRow(icon: ImageVector, text: String, shape: RoundedCornerShape, trailing: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = shape, color = SafetyContainer, contentColor = SafetyInk, modifier = Modifier.fillMaxWidth().height(60.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null)
            Text(text, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (trailing != null) Text(trailing, fontSize = 14.sp, color = SafetyVariant)
        }
    }
}

@Composable
fun HelpScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var query by rememberSaveable { mutableStateOf("") }
    Box(Modifier.fillMaxSize()) {
        SubPage("Help", onBack = { nav.back() }) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(c.surfaceContainerHigh).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(NovaIcons.Search, null, tint = c.onSurfaceVariant)
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search help articles", fontSize = 16.sp, color = c.onSurfaceVariant)
                    BasicTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface), cursorBrush = SolidColor(c.primary))
                }
            }
            Surface(onClick = { nav.go(LostItem) }, shape = RoundedCornerShape(28.dp), color = c.primaryContainer, contentColor = c.onPrimaryContainer) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ShapeBadge(NovaShape.Cookie, 52.dp, c.primary) { Icon(NovaIcons.Car, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    Column(Modifier.weight(1f)) {
                        Text("Your last trip · Today 10:14 AM", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("${Sample.destination} · ${Sample.fare}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Get help with this trip", fontSize = 14.sp)
                    }
                    Icon(NovaIcons.Chev, null)
                }
            }
            Text("Browse topics", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            val topics = listOf(
                Triple("Payments & fares", NovaIcons.Card, c.surfaceContainer to c.onSurface),
                Triple("Safety", NovaIcons.Shield, c.errorContainer to c.onErrorContainer),
                Triple("Lost items", NovaIcons.Search, c.tertiaryContainer to c.onTertiaryContainer),
                Triple("Account", NovaIcons.User, c.surfaceContainer to c.onSurface),
                Triple("Accessibility", NovaIcons.Access, NovaPalette.Mint to NovaPalette.OnMint),
                Triple("Offers", NovaIcons.Tag, c.surfaceContainer to c.onSurface),
            ).filter { query.isBlank() || it.first.contains(query, ignoreCase = true) }
            topics.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, icon, colors) ->
                        Surface(
                            onClick = { if (label == "Lost items") nav.go(LostItem) else nav.go(SupportChat) },
                            shape = RoundedCornerShape(24.dp), color = colors.first, contentColor = colors.second,
                            modifier = Modifier.weight(1f).height(96.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(icon, null)
                                Text(label, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(80.dp))
        }
        Surface(
            onClick = { nav.go(SupportChat) },
            shape = RoundedCornerShape(20.dp), color = c.primary, contentColor = Color.White, shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp).height(64.dp),
        ) {
            Row(Modifier.padding(start = 18.dp, end = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(NovaIcons.Msg, null); Text("Chat with us", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private data class SupportMessage(val me: Boolean, val text: String)

@Composable
fun SupportChatScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val messages = remember {
        mutableStateListOf(
            SupportMessage(false, "Hi ${Sample.riderName}, I’m Mere from Nova Support. What can I help with on your trip to ${Sample.destination}?"),
            SupportMessage(true, "I left my umbrella in the back seat"),
            SupportMessage(false, "Sorry about that! I’ve messaged ${Sample.driverName}. Would you like him to drop it off? A $15 return fee goes to him."),
        )
    }
    var options by remember { mutableStateOf(listOf("Yes, drop it off", "I’ll collect it")) }
    var draft by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    fun send(t: String) { val s = t.trim(); if (s.isNotEmpty()) { messages.add(SupportMessage(true, s)); draft = ""; options = emptyList() } }
    LaunchedEffect(messages.size) { list.animateScrollToItem(messages.size + 1) }
    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        Row(
            Modifier.fillMaxWidth().background(c.surfaceContainerLow).statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = { nav.back() }) { Icon(NovaIcons.Back, "Back") }
            CookieAvatar("N", c.primary, Color.White, size = 44.dp, shape = NovaShape.Burst)
            Column {
                Text("Nova Support", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Mere is online", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NovaTheme.colors.success)
            }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Row(
                    Modifier.padding(bottom = 8.dp).fillMaxWidth().border(1.dp, c.outlineVariant, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconTile(NovaIcons.Car, c.primaryContainer, c.onPrimaryContainer, size = 36.dp, radius = 12.dp, iconSize = 18.dp)
                    Column {
                        Text("Comfort · Today 10:14 AM", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Ponsonby Rd → ${Sample.destination} · ${Sample.fare}", fontSize = 13.sp, color = c.onSurfaceVariant)
                    }
                }
            }
            items(messages) { m ->
                Box(Modifier.fillMaxWidth(), contentAlignment = if (m.me) Alignment.CenterEnd else Alignment.CenterStart) {
                    Text(
                        m.text,
                        Modifier.widthIn(max = 280.dp)
                            .clip(if (m.me) RoundedCornerShape(22.dp, 6.dp, 22.dp, 22.dp) else RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp))
                            .background(if (m.me) c.primary else c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 12.dp),
                        fontSize = 15.sp, lineHeight = 21.sp, color = if (m.me) Color.White else c.onSurface,
                    )
                }
            }
            item {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { o ->
                        Surface(onClick = { send(o) }, shape = CircleShape, color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = Modifier.height(44.dp)) {
                            Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(o, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.surfaceContainerLow).navigationBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = {}) { Icon(NovaIcons.Image, "Attach photo", tint = c.onSurfaceVariant) }
            Box(Modifier.weight(1f).height(56.dp).clip(CircleShape).background(c.surfaceContainerHighest).padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) Text("Message", fontSize = 16.sp, color = c.onSurfaceVariant)
                BasicTextField(
                    draft, { draft = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface), cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(draft) }),
                )
            }
            FilledIconButton(onClick = { send(draft) }, shapes = IconButtonDefaults.shapes(RoundedCornerShape(18.dp), RoundedCornerShape(28.dp)), modifier = Modifier.size(56.dp)) {
                Icon(NovaIcons.Send, "Send")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LostItemScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var description by rememberSaveable { mutableStateOf("Black umbrella with a wooden handle, back seat on the left") }
    var kind by rememberSaveable { mutableStateOf("Umbrella") }
    SubPage("Lost item", onBack = { nav.back() }, bottom = {
        NovaButton("Send to ${Sample.driverName}", onClick = { nav.replaceTop(SupportChat) }, enabled = description.isNotBlank())
    }) {
        Text("What did you leave behind?", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surfaceContainer).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CookieAvatar(Sample.driverInitials, c.primaryContainer, c.onPrimaryContainer, size = 44.dp)
            Column {
                Text("${Sample.driverName} · ${Sample.carShort} ${Sample.plate}", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("Today 10:14 AM · ${Sample.destination}", fontSize = 13.sp, color = c.onSurfaceVariant)
            }
        }
        com.nova.designsystem.components.NovaTextField(description, { description = it }, "Describe the item", singleLine = false, minLines = 3)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Umbrella", "Phone", "Wallet", "Bag").forEach { k ->
                val on = k == kind
                FilterChip(
                    selected = on, onClick = { kind = k },
                    label = { Text(k, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) },
                    leadingIcon = if (on) { { Icon(NovaIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                    shape = RoundedCornerShape(10.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.secondaryContainer, selectedLabelColor = c.onSecondaryContainer, selectedLeadingIconColor = c.onSecondaryContainer),
                )
            }
        }
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.tertiaryContainer).padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(NovaIcons.Smartphone, null, tint = c.onTertiaryContainer)
            Text(
                buildAnnotatedString { append("Lost your phone? We'll call this backup number instead: "); withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("+64 27 555 0142") } },
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = c.onTertiaryContainer,
            )
        }
    }
}
