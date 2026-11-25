package com.nova.rider.feature.account

import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.nova.designsystem.components.SectionLabel
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.components.SwitchRow
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.designsystem.theme.NovaTheme
import com.nova.rider.data.Sample
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.PasskeyManager
import com.nova.rider.data.auth.SavedPlace
import com.nova.rider.data.auth.authErrorMessage
import com.nova.rider.data.profile.ProfileStore
import com.nova.rider.data.settings.SettingsStore
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
import com.nova.rider.navigation.Route
import com.nova.rider.navigation.Safety
import com.nova.rider.navigation.SavedPlaces
import com.nova.rider.navigation.Settings
import com.nova.rider.navigation.SosActive
import com.nova.rider.navigation.Surge
import com.nova.rider.navigation.Welcome
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    var dark by rememberSaveable { mutableStateOf(false) }
    var passkeyBusy by remember { mutableStateOf(false) }
    var passkeyMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val passkeyManager = remember { PasskeyManager(context) }
    val user = AuthStore.session?.user
    val riderName = listOfNotNull(
        user?.firstName?.ifBlank { null },
        user?.lastName?.ifBlank { null },
    ).joinToString(" ").ifBlank { "Nova rider" }
    // Whichever contact the rider signed up with (phone, email or both).
    val riderContact = user?.phone ?: user?.email ?: ""
    val riderInitials = listOfNotNull(
        user?.firstName?.firstOrNull()?.uppercaseChar(),
        user?.lastName?.firstOrNull()?.uppercaseChar(),
    ).joinToString("").ifBlank { "?" }
    // Rider ratings arrive with drivers (they rate riders after a trip); until then the badge says
    // how long they've been with Nova instead of showing a made-up rating.
    val memberSince = user?.createdAt?.let { iso ->
        runCatching {
            java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
        }.getOrNull()
    }

    LaunchedEffect(Unit) { SettingsStore.loadNotifications() }

    TabPage("", RiderTab.Account, nav::tab) {
        Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CookieAvatar(riderInitials, c.tertiaryContainer, c.onTertiaryContainer, size = 80.dp, shape = NovaShape.Sunny)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Long names wrap to a second line, then end with an ellipsis; the edit button keeps its place.
                Text(riderName, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 32.sp), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                if (riderContact.isNotBlank()) Text(riderContact, fontSize = 14.sp, color = c.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                memberSince?.let {
                    Box(Modifier.height(28.dp).clip(CircleShape).background(c.secondaryContainer).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        Text("Rider since $it", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = c.onSecondaryContainer)
                    }
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
            NovaListItem(
                0, 4, "Notifications", leading = { Icon(NovaIcons.Bell, null) },
                trailing = if (SettingsStore.unread > 0) { { androidx.compose.material3.Badge { Text("${SettingsStore.unread}") } } } else null,
                onClick = { nav.go(Notifications) },
            )
            NovaListItem(1, 4, "Settings", leading = { Icon(NovaIcons.Sliders, null) }, trailing = { Icon(NovaIcons.Chev, null) }, onClick = { nav.go(Settings) })
            SwitchRow(2, 4, NovaIcons.Moon, "Dark theme", null, dark) { dark = it }
            NovaListItem(3, 4, "Help & support", leading = { Icon(NovaIcons.Help, null) }, onClick = { nav.go(Help) })
        }
        // Passkey registration — lets the rider sign in with fingerprint / screen lock next time.
        Surface(
            onClick = {
                if (!passkeyBusy) {
                    scope.launch {
                        passkeyBusy = true
                        passkeyMessage = null
                        try {
                            val options = NovaApi.passkeyRegisterOptions()
                            val credential = passkeyManager.createPasskey(options)
                            NovaApi.passkeyRegisterVerify(credential)
                            passkeyMessage = "Passkey created. You can sign in with it next time."
                        } catch (e: Throwable) {
                            passkeyMessage = authErrorMessage(e)
                        } finally {
                            passkeyBusy = false
                        }
                    }
                }
            },
            enabled = !passkeyBusy,
            shape = RoundedCornerShape(20.dp),
            color = c.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ShapeBadge(NovaShape.Cookie, 40.dp, c.primaryContainer) {
                    if (passkeyBusy) {
                        androidx.compose.material3.LoadingIndicator(Modifier.size(20.dp), color = c.onPrimaryContainer)
                    } else {
                        Icon(NovaIcons.Finger, null, tint = c.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text("Create a passkey", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (passkeyBusy) "Setting up…" else "Use fingerprint or screen lock to sign in",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.onSurfaceVariant,
                    )
                }
                Icon(NovaIcons.Chev, null, modifier = Modifier.size(20.dp))
            }
        }
        passkeyMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = c.primary, modifier = Modifier.padding(horizontal = 4.dp))
        }
        TextButton(
            onClick = { scope.launch { signOut(); nav.reset(Welcome) } },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
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
    // On medium/expanded windows the page becomes a centred column of readable width (Material
    // adaptive guidance); on phones the max width is wider than the screen, so nothing changes.
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).imePadding()) {
        NovaTopBar(onBack = onBack, title = title, actions = actions)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .then(if (bottom == null) Modifier.navigationBarsPadding() else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = com.nova.rider.feature.common.ReadableContentWidth).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
            )
        }
        if (bottom != null) Column(Modifier.fillMaxWidth().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.widthIn(max = com.nova.rider.feature.common.ReadableContentWidth).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), content = bottom,
            )
        }
    }
}

@Composable
fun EditProfileScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val user = AuthStore.session?.user
    var firstName by rememberSaveable(user?.firstName) { mutableStateOf(user?.firstName.orEmpty()) }
    var lastName by rememberSaveable(user?.lastName) { mutableStateOf(user?.lastName.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { ProfileStore.refreshIfNeeded() }

    val riderInitials = listOfNotNull(
        firstName.trim().firstOrNull()?.uppercaseChar(),
        lastName.trim().firstOrNull()?.uppercaseChar(),
    ).joinToString("").ifBlank { "?" }
    val primaryContact = ProfileStore.contacts.firstOrNull()
    val contactSummary = primaryContact?.let { "${it.name} · ${it.phone}" } ?: "Add a trusted contact"

    SubPage(
        "Edit profile",
        onBack = { nav.back() },
        bottom = {
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
            NovaButton(
                if (busy) "Saving…" else "Save changes",
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val updated = NovaApi.updateProfile(firstName.trim(), lastName.trim())
                            AuthStore.updateUser(updated)
                            nav.back()
                        } catch (e: Throwable) {
                            error = authErrorMessage(e)
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = firstName.isNotBlank() && !busy,
            )
        },
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp).size(120.dp)) {
            // Initials avatar. The "change photo" button returns with photo storage (profile photos
            // need an image bucket and moderation); a button that does nothing is worse than none.
            CookieAvatar(riderInitials, c.tertiaryContainer, c.onTertiaryContainer, size = 120.dp, shape = NovaShape.Sunny)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NovaTextField(
                firstName,
                { firstName = it },
                "First name",
                Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            NovaTextField(
                lastName,
                { lastName = it },
                "Last name",
                Modifier.weight(1f),
                placeholder = "Optional",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        }
        var verifyChannel by remember { mutableStateOf<String?>(null) }
        ReadOnlyField("Email", user?.email ?: "Not set", onClick = if (user?.emailVerifiedAt == null) ({ verifyChannel = "email" }) else null) {
            if (user?.emailVerifiedAt != null) {
                VerifiedPill()
            } else {
                TextButton(onClick = { verifyChannel = "email" }) {
                    Text(if (user?.email.isNullOrBlank()) "Add" else "Verify", fontWeight = FontWeight.Bold)
                }
            }
        }
        ReadOnlyField("Mobile", user?.phone ?: "Not set", onClick = if (user?.phoneVerifiedAt == null) ({ verifyChannel = "phone" }) else null) {
            if (user?.phoneVerifiedAt != null) {
                VerifiedPill()
            } else {
                TextButton(onClick = { verifyChannel = "phone" }) {
                    Text(if (user?.phone.isNullOrBlank()) "Add" else "Verify", fontWeight = FontWeight.Bold)
                }
            }
        }
        ReadOnlyField("Emergency contact", contactSummary, onClick = { nav.go(Safety) }) { Icon(NovaIcons.Chev, null) }

        verifyChannel?.let { channel ->
            VerifyContactSheet(
                channel = channel,
                initialValue = if (channel == "email") user?.email.orEmpty() else user?.phone.orEmpty(),
                onDismiss = { verifyChannel = null },
                onVerified = { updated ->
                    AuthStore.updateUser(updated)
                    verifyChannel = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerifyContactSheet(
    channel: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onVerified: (com.nova.rider.data.auth.User) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val isEmail = channel == "email"
    var destination by remember { mutableStateOf(initialValue) }
    var code by remember { mutableStateOf("") }
    var codeSent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(if (isEmail) "Verify email" else "Verify mobile number", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            NovaTextField(
                value = destination,
                onValueChange = { destination = it },
                label = if (isEmail) "Email address" else "Mobile (+country code)",
                placeholder = if (isEmail) "you@example.com" else exampleInternationalMobile(),
                keyboardOptions = KeyboardOptions(keyboardType = if (isEmail) KeyboardType.Email else KeyboardType.Phone),
            )
            if (codeSent) {
                NovaTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = "6-digit code",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
            NovaButton(
                when {
                    busy -> "Please wait…"
                    codeSent -> "Verify & save"
                    else -> "Send code"
                },
                enabled = !busy && (if (codeSent) code.length == 6 else if (isEmail) destination.contains('@') else destination.trim().startsWith("+")),
                onClick = {
                    val dest = if (isEmail) destination.trim().lowercase() else destination.trim()
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            if (!codeSent) {
                                NovaApi.sendOtp(channel, dest, "signup")
                                codeSent = true
                            } else {
                                val res = NovaApi.verifyOtp(channel, dest, "signup", code)
                                val proof = (res as com.nova.rider.data.auth.VerifyResult.Proof).token
                                val updated = NovaApi.verifyContact(proof)
                                onVerified(updated)
                            }
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
private fun VerifiedPill() {
    Row(Modifier.height(28.dp).clip(CircleShape).background(NovaPalette.Mint).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(NovaIcons.Check, null, Modifier.size(14.dp), tint = NovaPalette.OnMint)
        Text("Verified", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = NovaPalette.OnMint)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPlacesScreen(nav: Navigator, setupKind: String? = null) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    // The place being edited (null id = new place). Opening from Home's "Home"/"Work" card jumps
    // straight here for that slot.
    var editor by remember { mutableStateOf<PlaceDraft?>(null) }
    var confirmDelete by remember { mutableStateOf<com.nova.rider.data.auth.SavedPlace?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ProfileStore.refresh()
        setupKind?.let { kind ->
            val existing = ProfileStore.places.firstOrNull { it.kind == kind && kind != "custom" }
            editor = existing?.let(PlaceDraft::from) ?: PlaceDraft.new(kind)
        }
    }

    val places = ProfileStore.places

    SubPage("Saved places", onBack = { nav.back() }) {
        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
        // Home and Work always have a slot, so it's obvious where to set them.
        val slots = listOf("home", "work").map { kind -> kind to places.firstOrNull { it.kind == kind } }
        val customs = places.filter { it.kind == "custom" }
        SegmentedColumn {
            val total = slots.size + customs.size
            slots.forEachIndexed { i, (kind, place) ->
                val (icon, bg, fg) = placeStyle(kind, c)
                NovaListItem(
                    i, total,
                    if (kind == "home") "Home" else "Work",
                    supporting = place?.address ?: "Add ${if (kind == "home") "home" else "work"} address",
                    leading = { IconTile(icon, bg, fg) },
                    trailing = { PlaceActions(place, onEdit = { editor = place?.let(PlaceDraft::from) ?: PlaceDraft.new(kind) }, onDelete = { confirmDelete = place }) },
                    onClick = { editor = place?.let(PlaceDraft::from) ?: PlaceDraft.new(kind) },
                )
            }
            customs.forEachIndexed { j, place ->
                val (icon, bg, fg) = placeStyle(place.kind, c)
                NovaListItem(
                    slots.size + j, total, place.label,
                    supporting = place.address,
                    leading = { IconTile(icon, bg, fg) },
                    trailing = { PlaceActions(place, onEdit = { editor = PlaceDraft.from(place) }, onDelete = { confirmDelete = place }) },
                    onClick = { editor = PlaceDraft.from(place) },
                )
            }
        }
        Surface(onClick = { editor = PlaceDraft.new("custom") }, shape = RoundedCornerShape(16.dp), color = Color.Transparent, modifier = Modifier.fillMaxWidth().height(56.dp).dashedBorder(c.outline)) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(NovaIcons.Plus, null, Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("Add a place", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    editor?.let { draft ->
        AddPlaceSheet(
            draft = draft,
            onDismiss = { editor = null; if (setupKind != null) nav.back() },
            onSave = { label, place ->
                if (draft.id != null) {
                    ProfileStore.updatePlace(draft.id, label, place.address, place.lat, place.lng)
                } else {
                    ProfileStore.savePlace(draft.kind, label, place.address, place.lat, place.lng)
                }
                editor = null
                // Came from a Home/Work card: back to Home with the slot filled.
                if (setupKind != null) nav.back()
            },
        )
    }

    // Removing is destructive, so confirm with the place named (Material 3 dialog guidance).
    confirmDelete?.let { place ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${place.label}?") },
            text = { Text(place.address) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        runCatching { ProfileStore.deletePlace(place.id) }.onFailure { error = authErrorMessage(it) }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

/** Edit + remove for a saved row; nothing for an empty Home/Work slot. */
@Composable
private fun PlaceActions(place: com.nova.rider.data.auth.SavedPlace?, onEdit: () -> Unit, onDelete: () -> Unit) {
    if (place == null) {
        Icon(NovaIcons.Plus, null, Modifier.size(20.dp))
        return
    }
    Row {
        IconButton(onClick = onEdit) { Icon(NovaIcons.Edit, "Edit ${place.label}", Modifier.size(20.dp)) }
        IconButton(onClick = onDelete) { Icon(NovaIcons.Close, "Remove ${place.label}", Modifier.size(20.dp)) }
    }
}

/** What the editor sheet works on. [id] null = a new place. */
private data class PlaceDraft(
    val id: String?,
    val kind: String,
    val label: String,
    val place: com.nova.rider.data.auth.GeoPlace?,
) {
    companion object {
        fun new(kind: String) = PlaceDraft(null, kind, when (kind) { "home" -> "Home"; "work" -> "Work"; else -> "" }, null)
        fun from(p: com.nova.rider.data.auth.SavedPlace) = PlaceDraft(
            p.id, p.kind, p.label,
            if (p.lat != null && p.lng != null) com.nova.rider.data.auth.GeoPlace(name = p.label, address = p.address, lat = p.lat, lng = p.lng)
            else com.nova.rider.data.auth.GeoPlace(name = p.label, address = p.address, lat = Double.NaN, lng = Double.NaN),
        )
    }
}

private fun placeStyle(kind: String, c: androidx.compose.material3.ColorScheme): Triple<ImageVector, Color, Color> = when (kind) {
    "home" -> Triple(NovaIcons.Home, c.primaryContainer, c.onPrimaryContainer)
    "work" -> Triple(NovaIcons.Work, c.tertiaryContainer, c.onTertiaryContainer)
    else -> Triple(NovaIcons.Pin, c.secondaryContainer, c.onSecondaryContainer)
}

/**
 * Add / edit a saved place. The address comes from Places search (debounced Autocomplete with
 * a session token), so every saved place has coordinates and can be booked in one tap. Before
 * typing it offers the rider's current location and the nearest landmarks (Nearby Search).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPlaceSheet(
    draft: PlaceDraft,
    onDismiss: () -> Unit,
    onSave: suspend (label: String, place: com.nova.rider.data.auth.GeoPlace) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var label by remember { mutableStateOf(draft.label) }
    // Places saved before coordinates were stored have NaN coords: make the rider re-pick them.
    var chosen by remember { mutableStateOf(draft.place?.takeIf { !it.lat.isNaN() }) }
    var query by remember { mutableStateOf(if (draft.place != null && draft.place.lat.isNaN()) draft.place.address else "") }
    var searching by remember { mutableStateOf(chosen == null) }
    var results by remember { mutableStateOf<List<com.nova.rider.data.auth.PlaceSuggestion>>(emptyList()) }
    var sessionToken by remember { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Nearest suggestions need a fix; both calls are no-ops when already fresh.
        com.nova.rider.data.trip.TripStore.locate(context)
        com.nova.rider.data.trip.TripStore.loadNearby()
    }
    // Debounced Places Autocomplete (300 ms, >= 2 chars, newer keystroke cancels the older one).
    LaunchedEffect(query, searching) {
        val q = query.trim()
        if (!searching || q.length < 2) { results = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        runCatching { NovaApi.autocomplete(q, sessionToken, com.nova.rider.data.trip.TripStore.currentLocation) }
            .onSuccess { results = it; error = null }
            .onFailure { error = com.nova.rider.data.trip.tripErrorMessage(it) }
    }
    fun choose(place: com.nova.rider.data.auth.GeoPlace) {
        chosen = place
        searching = false
        query = ""
        results = emptyList()
        if (draft.kind == "custom" && label.isBlank()) label = place.name
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                when {
                    draft.id != null -> "Edit ${draft.label}"
                    draft.kind == "home" -> "Set home"
                    draft.kind == "work" -> "Set work"
                    else -> "Save a place"
                },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            if (draft.kind == "custom") {
                NovaTextField(
                    label, { label = it }, "Name",
                    placeholder = "e.g. Gym, Mum's place, Airport",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }

            val picked = chosen
            if (picked != null && !searching) {
                // Chosen address, with a clear way to change it.
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.primaryContainer).padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(NovaIcons.Pin, null, tint = c.onPrimaryContainer)
                    Column(Modifier.weight(1f)) {
                        Text(picked.name, style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(picked.address, style = MaterialTheme.typography.bodySmall, color = c.onPrimaryContainer, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    androidx.compose.material3.TextButton(onClick = { searching = true }) { Text("Change") }
                }
            } else {
                NovaTextField(
                    query, { query = it }, "Address",
                    placeholder = "Search street, building or place",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (query.trim().length < 2) {
                        val here = com.nova.rider.data.trip.TripStore.currentLocation
                        val nearby = com.nova.rider.data.trip.TripStore.nearby
                        if (here != null) {
                            SegmentedColumn {
                                NovaListItem(
                                    0, 1, "Use current location",
                                    supporting = com.nova.rider.data.trip.TripStore.currentAccuracyMeters?.let { "Accurate to about ${it.toInt()} m" } ?: "Your device's location",
                                    leading = { IconTile(NovaIcons.Pin, c.primaryContainer, c.onPrimaryContainer) },
                                    onClick = {
                                        scope.launch {
                                            runCatching { NovaApi.reverseGeocode(here) }
                                                .onSuccess(::choose)
                                                .onFailure { error = com.nova.rider.data.trip.tripErrorMessage(it) }
                                        }
                                    },
                                )
                            }
                        }
                        if (nearby.isNotEmpty()) {
                            SectionLabel("Nearby")
                            SegmentedColumn {
                                nearby.forEachIndexed { i, p ->
                                    NovaListItem(
                                        i, nearby.size, p.name, supporting = p.address,
                                        leading = { IconTile(NovaIcons.Pin, c.surfaceContainerHigh, c.onSurface) },
                                        trailing = {
                                            here?.let { Text(com.nova.rider.data.trip.TripFormat.distance(com.nova.rider.data.trip.metersBetween(it, p.point).toLong()), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) }
                                        },
                                        onClick = { choose(p) },
                                    )
                                }
                            }
                        }
                    } else {
                        SegmentedColumn {
                            results.forEachIndexed { i, s ->
                                NovaListItem(
                                    i, results.size, s.primaryText, supporting = s.secondaryText,
                                    leading = { IconTile(NovaIcons.Pin, c.surfaceContainerHigh, c.onSurface) },
                                    trailing = {
                                        s.distanceMeters?.let { Text(com.nova.rider.data.trip.TripFormat.distance(it), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant) }
                                    },
                                    onClick = {
                                        scope.launch {
                                            runCatching { NovaApi.placeDetails(s.placeId, sessionToken) }
                                                .onSuccess { choose(it.copy(name = s.primaryText)); sessionToken = java.util.UUID.randomUUID().toString() }
                                                .onFailure { error = com.nova.rider.data.trip.tripErrorMessage(it) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }
            NovaButton(
                if (busy) "Saving…" else "Save",
                enabled = label.isNotBlank() && chosen != null && !searching && !busy,
                onClick = {
                    val place = chosen ?: return@NovaButton
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            onSave(label.trim(), place)
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

/** Icon and colour for each server event; unknown events get a neutral bell. */
@Composable
private fun noticeStyle(n: com.nova.rider.data.auth.AppNotification): Triple<ImageVector, Color, Color> {
    val c = MaterialTheme.colorScheme
    return when (n.event) {
        "ride_scheduled" -> Triple(NovaIcons.Calendar, c.tertiaryContainer, c.onTertiaryContainer)
        "ride_cancelled" -> Triple(NovaIcons.Close, c.surfaceContainerHigh, c.onSurface)
        "no_drivers" -> Triple(NovaIcons.Car, c.secondaryContainer, c.onSecondaryContainer)
        "booked_for_you" -> Triple(NovaIcons.People, c.primaryContainer, c.onPrimaryContainer)
        "receipt" -> Triple(NovaIcons.ReceiptFilled, c.primaryContainer, c.onPrimaryContainer)
        else -> if (n.kind == "offer") Triple(NovaIcons.Tag, NovaPalette.Mint, NovaPalette.OnMint)
        else Triple(NovaIcons.Bell, c.surfaceContainerHigh, c.onSurface)
    }
}

@Composable
fun NotificationsScreen(nav: Navigator) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(SettingsStore.notifications.isEmpty()) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        failed = !SettingsStore.loadNotifications()
        loading = false
    }
    val zone = remember { java.time.ZoneId.systemDefault() }
    val today = remember { java.time.LocalDate.now(zone) }
    val kinds = listOf(null, "trip", "offer")
    val shown = SettingsStore.notifications.filter { kinds[filter] == null || it.kind == kinds[filter] }
    fun localTime(iso: String) = runCatching { java.time.Instant.parse(iso).atZone(zone) }.getOrNull()
    SubPage("Notifications", onBack = { nav.back() }, actions = {
        TextButton(onClick = { scope.launch { SettingsStore.markAllRead() } }, enabled = SettingsStore.unread > 0) {
            Text("Mark all read", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Trips", "Offers").forEachIndexed { i, label ->
                val on = i == filter
                FilterChip(
                    selected = on, onClick = { filter = i },
                    label = { Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) },
                    leadingIcon = if (on) { { Icon(NovaIcons.Check, null, Modifier.size(FilterChipDefaults.IconSize)) } } else null,
                    shape = if (on) CircleShape else RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.primary, selectedLabelColor = c.onPrimary, selectedLeadingIconColor = c.onPrimary),
                    modifier = Modifier.height(40.dp),
                )
            }
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.LoadingIndicator() }
            failed && shown.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Couldn't load notifications.", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                com.nova.designsystem.components.NovaOutlinedButton("Try again", onClick = {
                    scope.launch { loading = true; failed = !SettingsStore.loadNotifications(); loading = false }
                })
            }
            shown.isEmpty() -> Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShapeBadge(NovaShape.Cookie, 96.dp, c.primaryContainer) { Icon(NovaIcons.Bell, null, tint = c.onPrimaryContainer, modifier = Modifier.size(40.dp)) }
                Text("You're all caught up", style = MaterialTheme.typography.titleLarge)
                Text("Ride updates and offers will show up here.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
            }
            else -> listOf(true to "Today", false to "Earlier").forEach { (isToday, label) ->
                val group = shown.filter { (localTime(it.createdAt)?.toLocalDate() == today) == isToday }
                if (group.isNotEmpty()) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                    SegmentedColumn {
                        group.forEachIndexed { i, n ->
                            val (icon, bg, fg) = noticeStyle(n)
                            val t = localTime(n.createdAt)
                            val time = when {
                                t == null -> ""
                                isToday -> t.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                                t.toLocalDate() == today.minusDays(1) -> "Yesterday"
                                else -> t.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM"))
                            }
                            NovaListItem(
                                i, group.size, n.title, supporting = listOf(n.body, time).filter { it.isNotBlank() }.joinToString("\n"),
                                leading = { IconTile(icon, bg, fg, size = 44.dp, radius = 16.dp) },
                                trailing = if (n.readAt == null) { { Box(Modifier.size(10.dp).clip(CircleShape).background(c.primary).semantics { contentDescription = "Unread" }) } } else null,
                                // Trip notifications open Activity, where the ride is listed.
                                // A ride booked for me opens its live tracking; my own rides open Activity.
                                onClick = when {
                                    n.shareToken != null -> ({ nav.go(com.nova.rider.navigation.TrackRide(n.shareToken)) })
                                    n.rideId != null -> ({ nav.tab(RiderTab.Activity) })
                                    else -> null
                                },
                            )
                        }
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
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val ride = SettingsStore.ride
    LaunchedEffect(Unit) { SettingsStore.loadRide() }

    fun saveRide(next: com.nova.rider.data.auth.RiderSettings) {
        scope.launch { if (!SettingsStore.updateRide(next)) message = "Couldn't save that. Check your connection and try again." }
    }

    // "Download my data": the rider picks where to save (Storage Access Framework, no storage
    // permission needed), then we fetch the export and write it there.
    val saveExport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            exporting = true
            message = try {
                val text = NovaApi.exportMyData()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                "Your data was saved."
            } catch (e: Throwable) {
                authErrorMessage(e)
            } finally {
                exporting = false
            }
        }
    }

    SubPage("Settings", onBack = { nav.back() }) {
        Group("Preferences")
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Moon, "Dark theme", "Follow system when off", dark) { dark = it }
            // English is the only language shipped; the row says so instead of opening an empty picker.
            NovaListItem(1, 2, "Language", supporting = "English", leading = { Icon(NovaIcons.Globe, null, tint = c.onSurfaceVariant) })
        }
        Group("Ride needs")
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Access, "Wheelchair-accessible vehicles", "Only match me with WAV cars", ride.wheelchairAccess) {
                saveRide(ride.copy(wheelchairAccess = it))
            }
            SwitchRow(1, 2, NovaIcons.Volume, "Quiet ride", "Let drivers know you prefer silence", ride.quietRide) {
                saveRide(ride.copy(quietRide = it))
            }
        }
        Group("Privacy")
        SegmentedColumn {
            SwitchRow(0, 2, NovaIcons.Pin, "Location only during trips", "Stops background location after drop-off", ride.tripLocationOnly) {
                saveRide(ride.copy(tripLocationOnly = it))
            }
            NovaListItem(
                1, 2, "Download my data",
                supporting = if (exporting) "Preparing your file…" else "A copy of everything Nova holds about you",
                leading = { Icon(NovaIcons.Download, null, tint = c.onSurfaceVariant) },
                trailing = { if (exporting) androidx.compose.material3.LoadingIndicator(Modifier.size(24.dp)) else Icon(NovaIcons.Chev, null) },
                onClick = { if (!exporting) saveExport.launch("nova-my-data-${java.time.LocalDate.now()}.json") },
            )
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.primary, modifier = Modifier.padding(horizontal = 4.dp)) }
        // Design-preview shortcuts to edge-case screens: debug builds only, never shipped to riders.
        if (com.nova.rider.BuildConfig.DEBUG) {
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
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.nova.designsystem.components.NovaOutlinedButton(
                "Log out", onClick = { scope.launch { signOut(); nav.reset(Welcome) } },
                modifier = Modifier.weight(1f), height = 52.dp, leadingIcon = NovaIcons.Logout,
            )
            com.nova.designsystem.components.NovaTonalButton(
                "Delete account", onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), height = 52.dp, leadingIcon = NovaIcons.Trash,
                containerColor = c.errorContainer, contentColor = c.onErrorContainer,
            )
        }
    }
    if (confirmDelete) {
        BasicAlertDialog(onDismissRequest = { if (!deleting) confirmDelete = false }) {
            Surface(shape = RoundedCornerShape(28.dp), color = c.surfaceContainerHigh) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ShapeBadge(NovaShape.Cookie, 56.dp, c.errorContainer) { Icon(NovaIcons.Trash, null, tint = c.onErrorContainer) }
                    Text("Delete your account?", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "You'll be signed out on every device and scheduled rides will be cancelled. Your trips, saved places and contacts are removed after 30 days. Sign back in before then to keep your account.",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                    )
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { confirmDelete = false }, enabled = !deleting) { Text("Keep account") }
                        TextButton(
                            enabled = !deleting,
                            onClick = {
                                scope.launch {
                                    deleting = true
                                    try {
                                        NovaApi.requestAccountDeletion()
                                        // Every session is already revoked server side; just forget this one.
                                        AuthStore.clearSession()
                                        nav.reset(Welcome)
                                    } catch (e: Throwable) {
                                        confirmDelete = false
                                        message = if ((e as? com.nova.rider.data.auth.ApiException)?.code == "ride_in_progress")
                                            "Finish or cancel your current ride before deleting your account."
                                        else authErrorMessage(e)
                                    } finally {
                                        deleting = false
                                    }
                                }
                            },
                        ) { Text(if (deleting) "Deleting…" else "Delete", color = c.error) }
                    }
                }
            }
        }
    }
}

/** Revokes this device's refresh token on the server, then forgets the session locally. */
suspend fun signOut() {
    AuthStore.session?.refreshToken?.takeIf { it.isNotBlank() }?.let { token -> runCatching { NovaApi.logout(token) } }
    AuthStore.clearSession()
}

@Composable
private fun Group(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

/** "+64 21 234 5678" style example for the phone's own country (SIM, network or locale). */
@Composable
internal fun exampleInternationalMobile(): String {
    val context = LocalContext.current
    return remember(context) {
        com.nova.rider.data.auth.detectCountry(context)?.let { com.nova.rider.data.auth.PhoneRegions.exampleMobile(it.iso, international = true) }
            ?: "+ country code and number"
    }
}
