package com.nova.rider.feature.booking

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaListItem
import com.nova.designsystem.components.NovaShape
import com.nova.designsystem.components.NovaTextField
import com.nova.designsystem.components.NovaTonalButton
import com.nova.designsystem.components.NovaTopBar
import com.nova.designsystem.components.SectionLabel
import com.nova.designsystem.components.ShapeBadge
import com.nova.designsystem.icons.NovaIcons
import com.nova.designsystem.theme.NovaPalette
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.Passenger
import com.nova.rider.data.auth.PhoneRegions
import com.nova.rider.data.auth.detectCountry
import com.nova.rider.data.contacts.ContactsStore
import com.nova.rider.data.trip.TripStore
import com.nova.rider.feature.common.ReadableContentWidth
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Search
import kotlinx.coroutines.launch

/**
 * "Who's riding?" (Uber's "Request for someone else", Bolt's "Order for a friend").
 *
 * Contacts-first: the rider's phone book is the list, and each contact shows whether they are on
 * Nova. Friends on Nova get the booking in their app (inbox now, live updates over WebSocket
 * later); friends without the app are still bookable and the driver calls them. One tap on a
 * person continues to the route planner, which asks where to pick that person up.
 *
 * Contacts are read only after the rider taps "Allow contacts" on a card that says exactly what
 * happens (Google Play's prominent-disclosure rule for contacts). "Enter a number" works without
 * the permission.
 *
 * [then] = "search": came from Home, continue to the planner. "back": return to where we came from.
 */
@Composable
fun ChooseRiderScreen(nav: Navigator, then: String) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val region = remember { homeRegion(context) }
    val current = TripStore.passenger
    var recent by remember { mutableStateOf<List<Passenger>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var manualEntry by rememberSaveable { mutableStateOf(false) }
    var granted by remember { mutableStateOf(ContactsStore.hasPermission(context)) }
    var askedAndDenied by rememberSaveable { mutableStateOf(false) }
    var dismissedCard by rememberSaveable { mutableStateOf(false) }

    fun choose(passenger: Passenger?) {
        TripStore.rideFor(passenger)
        if (then == "search") nav.replaceTop(Search) else nav.back()
    }

    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedAndDenied = !ok
        if (ok) scope.launch { ContactsStore.load(context, region, force = true) }
    }
    // Coming back from system Settings after turning contacts on.
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && !granted && ContactsStore.hasPermission(context)) {
                granted = true
                scope.launch { ContactsStore.load(context, region, force = true) }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        recent = runCatching { NovaApi.recentPassengers() }.getOrDefault(emptyList())
        if (granted) ContactsStore.load(context, region)
    }

    val contacts = ContactsStore.contacts
    val needle = query.trim()
    val digits = needle.filter(Char::isDigit)
    fun matches(name: String, phone: String) =
        needle.isEmpty() || name.contains(needle, ignoreCase = true) || (digits.length >= 3 && phone.contains(digits))
    val shownRecent = recent.filter { matches(it.name, it.phone) }
    val recentPhones = recent.map { it.phone }.toSet()
    val onNova = contacts.filter { it.onNova == true && it.phone !in recentPhones && matches(it.name, it.phone) }
    val others = contacts.filter { it.onNova != true && it.phone !in recentPhones && matches(it.name, it.phone) }
    val onNovaOf = contacts.associate { it.phone to it.onNova }

    Column(Modifier.fillMaxSize().background(c.surface).imePadding()) {
        NovaTopBar(onBack = { nav.back() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "title") {
                Column(Modifier.widthIn(max = ReadableContentWidth).fillMaxWidth().padding(start = 4.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Who's riding?", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Book for a friend. Friends on Nova get the ride in their app.",
                        style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
                    )
                }
            }
            item(key = "me") {
                MeCard(selected = current == null, modifier = Modifier.widthIn(max = ReadableContentWidth).padding(bottom = 14.dp)) { choose(null) }
            }
            if (!granted && !dismissedCard) {
                item(key = "permission") {
                    ContactsPermissionCard(
                        blocked = askedAndDenied && context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) == false,
                        onAllow = { askContacts.launch(Manifest.permission.READ_CONTACTS) },
                        onOpenSettings = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                        },
                        onNotNow = { dismissedCard = true },
                        modifier = Modifier.widthIn(max = ReadableContentWidth).padding(bottom = 14.dp),
                    )
                }
            }
            if (granted && ContactsStore.matchFailed) {
                item(key = "match-failed") {
                    Row(
                        Modifier.widthIn(max = ReadableContentWidth).fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp))
                            .background(c.surfaceContainerHigh).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Couldn't check who's on Nova.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                        TextButton(onClick = { scope.launch { ContactsStore.load(context, region, force = true) } }) { Text("Try again", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            if (granted && contacts.size > 6 || needle.isNotEmpty()) {
                item(key = "search") {
                    SearchPill(query, { query = it }, Modifier.widthIn(max = ReadableContentWidth).padding(bottom = 10.dp))
                }
            }
            if (shownRecent.isNotEmpty()) {
                item(key = "recent-label") { Label("Recent") }
                itemsIndexed(shownRecent, key = { _, p -> "r" + p.phone }) { i, p ->
                    PersonRow(i, shownRecent.size, p.name, p.phone, onNova = onNovaOf[p.phone], selected = current?.phone == p.phone) { choose(p) }
                }
            }
            if (onNova.isNotEmpty()) {
                item(key = "nova-label") { Label("On Nova", count = onNova.size) }
                itemsIndexed(onNova, key = { _, p -> "n" + p.phone }) { i, p ->
                    PersonRow(i, onNova.size, p.name, p.phone, onNova = true, selected = current?.phone == p.phone) { choose(Passenger(p.name, p.phone)) }
                }
            }
            if (others.isNotEmpty()) {
                item(key = "contacts-label") { Label("Contacts", count = others.size) }
                itemsIndexed(others, key = { _, p -> "c" + p.phone }) { i, p ->
                    PersonRow(i, others.size, p.name, p.phone, onNova = p.onNova, selected = current?.phone == p.phone) { choose(Passenger(p.name, p.phone)) }
                }
            }
            when {
                ContactsStore.loading && contacts.isEmpty() -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                }
                needle.isNotEmpty() && shownRecent.isEmpty() && onNova.isEmpty() && others.isEmpty() -> item(key = "none") {
                    Text(
                        "No contacts match \"$needle\". Use \"Enter a number\" below.",
                        style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = ReadableContentWidth).fillMaxWidth().padding(top = 24.dp, start = 4.dp),
                    )
                }
                granted && ContactsStore.loaded && contacts.isEmpty() -> item(key = "empty") {
                    Text(
                        "No contacts with mobile numbers on this phone.",
                        style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant,
                        modifier = Modifier.widthIn(max = ReadableContentWidth).fillMaxWidth().padding(top = 24.dp, start = 4.dp),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp, top = 4.dp), contentAlignment = Alignment.Center) {
            NovaTonalButton(
                "Enter a number", onClick = { manualEntry = true },
                modifier = Modifier.widthIn(max = ReadableContentWidth).fillMaxWidth(), leadingIcon = NovaIcons.Keypad,
            )
        }
    }
    if (manualEntry) {
        EnterNumberSheet(region, onDismiss = { manualEntry = false }) { manualEntry = false; choose(it) }
    }
}

/** The rider themself: the default, so it's the most prominent choice. */
@Composable
private fun MeCard(selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val user = AuthStore.session?.user
    val name = listOfNotNull(user?.firstName, user?.lastName).joinToString(" ").trim().ifBlank { "Me" }
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = c.primaryContainer, contentColor = c.onPrimaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CookieAvatar(initials(name), c.primary, c.onPrimary, size = 52.dp, shape = NovaShape.Sunny)
            Column(Modifier.weight(1f)) {
                Text("Me", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), fontWeight = FontWeight.ExtraBold)
                Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) {
                ShapeBadge(NovaShape.Cookie, 32.dp, c.primary) { Icon(NovaIcons.Check, "Selected", tint = c.onPrimary, modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

/** Explains, before the system dialog, what reading contacts does (prominent disclosure). */
@Composable
private fun ContactsPermissionCard(blocked: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit, onNotNow: () -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = c.tertiaryContainer, contentColor = c.onTertiaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ShapeBadge(NovaShape.Sunny, 52.dp, c.tertiary) { Icon(NovaIcons.People, null, tint = c.onTertiary, modifier = Modifier.size(24.dp)) }
                Text("Find friends on Nova", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), fontWeight = FontWeight.ExtraBold)
            }
            Text(
                if (blocked) "Contacts access is off for Nova. Turn it on in Settings to book for someone from your contacts."
                else "Nova reads the phone numbers in your contacts to show who's on Nova. Numbers are checked in a scrambled (hashed) form, and names never leave your phone. Nothing is stored.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                NovaButton(
                    if (blocked) "Open Settings" else "Allow contacts",
                    onClick = if (blocked) onOpenSettings else onAllow,
                    modifier = Modifier.weight(1f), height = 48.dp,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = c.tertiary, contentColor = c.onTertiary),
                )
                TextButton(onClick = onNotNow) { Text("Not now", fontWeight = FontWeight.Bold, color = c.onTertiaryContainer) }
            }
        }
    }
}

@Composable
private fun SearchPill(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(c.surfaceContainerHigh).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(NovaIcons.Search, null, tint = c.onSurfaceVariant)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search name or number", style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
            BasicTextField(
                value, onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
                cursorBrush = SolidColor(c.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(36.dp)) { Icon(NovaIcons.Close, "Clear search", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun Label(text: String, count: Int? = null) {
    Row(Modifier.widthIn(max = ReadableContentWidth).fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(text)
        if (count != null) {
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * One person. [onNova]: true shows the "On Nova" badge, false the "No app" badge, null no badge
 * (contacts not checked, e.g. without the contacts permission).
 */
@Composable
private fun PersonRow(index: Int, count: Int, name: String, phone: String, onNova: Boolean?, selected: Boolean, onClick: () -> Unit) {
    val (bg, fg) = avatarColors(name)
    Box(Modifier.widthIn(max = ReadableContentWidth)) {
        NovaListItem(
            index, count, name,
            supporting = formatForDisplay(phone),
            leading = { CookieAvatar(initials(name), bg, fg, size = 44.dp) },
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (onNova) {
                        true -> StatusPill("On Nova", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, NovaIcons.Check)
                        false -> StatusPill("No app", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, null)
                        null -> {}
                    }
                    if (selected) Icon(NovaIcons.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            },
            onClick = onClick,
            modifier = Modifier.semantics(mergeDescendants = true) {},
        )
    }
}

@Composable
private fun StatusPill(text: String, container: Color, content: Color, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Row(
        Modifier.height(26.dp).clip(CircleShape).background(container).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = content)
    }
}

/** A stable tonal colour per person, from the theme's containers, so lists aren't monotone. */
@Composable
private fun avatarColors(name: String): Pair<Color, Color> {
    val c = MaterialTheme.colorScheme
    val options = listOf(
        c.primaryContainer to c.onPrimaryContainer,
        c.tertiaryContainer to c.onTertiaryContainer,
        c.secondaryContainer to c.onSecondaryContainer,
        NovaPalette.Mint to NovaPalette.OnMint,
    )
    return options[Math.floorMod(name.hashCode(), options.size)]
}

/** Someone who isn't in contacts: name + mobile, checked as it's typed. */
@Composable
private fun EnterNumberSheet(region: String, onDismiss: () -> Unit, onChoose: (Passenger) -> Unit) {
    val c = MaterialTheme.colorScheme
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    val e164 = PhoneRegions.toE164(region, phone.trim())
    val country = remember(region) { PhoneRegions.find(region) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp).navigationBarsPadding().imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Enter their details", style = MaterialTheme.typography.headlineSmall)
            NovaTextField(
                name, { name = it }, "Their name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            NovaTextField(
                phone, { phone = it.filter { ch -> ch.isDigit() || ch in "+ -()" }.take(20) }, "Their mobile",
                // An example in the rider's own country's format (libphonenumber), never a fixed one.
                placeholder = PhoneRegions.exampleMobile(region)?.let { "e.g. $it" },
                leading = country?.let { cc -> { Text("${cc.flag} +${cc.dial}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp)) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            )
            if (phone.isNotBlank() && e164 == null) {
                Text("That doesn't look like a mobile number.", style = MaterialTheme.typography.bodySmall, color = c.error)
            }
            NovaButton(
                if (name.isBlank()) "Continue" else "Continue with ${name.trim().substringBefore(' ')}",
                onClick = { onChoose(Passenger(name.trim(), e164!!)) },
                enabled = name.isNotBlank() && e164 != null,
                height = 56.dp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** The rider's own country: from their verified phone, else the SIM/network, else NZ. */
private fun homeRegion(context: Context): String {
    val util = PhoneNumberUtil.getInstance()
    val fromPhone = AuthStore.session?.user?.phone?.let { runCatching { util.getRegionCodeForNumber(util.parse(it, null)) }.getOrNull() }
    return fromPhone?.takeIf { it != "ZZ" } ?: detectCountry(context)?.iso ?: "NZ"
}

/** "+64215550123" -> "+64 21 555 0123". */
private fun formatForDisplay(e164: String): String {
    val util = PhoneNumberUtil.getInstance()
    return runCatching { util.format(util.parse(e164, null), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL) }.getOrDefault(e164)
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").ifBlank { "#" }

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
