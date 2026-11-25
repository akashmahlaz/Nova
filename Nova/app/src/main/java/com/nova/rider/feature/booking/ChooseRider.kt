package com.nova.rider.feature.booking

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.nova.designsystem.components.CookieAvatar
import com.nova.designsystem.components.NovaButton
import com.nova.designsystem.components.NovaListItem
import com.nova.designsystem.components.NovaTextField
import com.nova.designsystem.components.SectionLabel
import com.nova.designsystem.components.SegmentedColumn
import com.nova.designsystem.icons.NovaIcons
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.Passenger
import com.nova.rider.data.auth.PhoneRegions
import com.nova.rider.data.auth.detectCountry
import com.nova.rider.data.trip.TripStore
import com.nova.rider.feature.account.SubPage
import com.nova.rider.navigation.Navigator
import com.nova.rider.navigation.Search

/**
 * "Who's riding?" (Uber's "Request for someone else" / Bolt's "Order for a friend").
 *
 * Choices, in the order riders use them: me, someone booked for before (one tap), a phone
 * contact, or someone new typed in. Picking someone other than the rider clears a pickup that was
 * only "where my phone is", so the planner then asks where to pick that person up.
 *
 * [then] = "search": came from Home, continue to the route planner. "back": came from the
 * planner or Ride options, return there.
 */
@Composable
fun ChooseRiderScreen(nav: Navigator, then: String) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    val region = remember { homeRegion(context) }
    val current = TripStore.passenger
    var recent by remember { mutableStateOf<List<Passenger>>(emptyList()) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var pickError by remember { mutableStateOf<String?>(null) }
    val nameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        recent = runCatching { NovaApi.recentPassengers() }.getOrDefault(emptyList())
    }

    fun finish(passenger: Passenger?) {
        TripStore.rideFor(passenger)
        if (then == "search") nav.replaceTop(Search) else nav.back()
    }

    // Typed or picked numbers in any local format ("021 555 0123") become E.164 using the
    // rider's own country; numbers with "+" keep theirs. Checked here, not after tapping Book.
    val e164 = PhoneRegions.toE164(region, phone.trim())
    val canContinue = name.isNotBlank() && e164 != null

    // Android's contact picker: the rider chooses one number, and only that number is shared with
    // the app (temporary read grant), so no READ_CONTACTS permission is needed.
    val pickContact = rememberLauncherForActivityResult(PickPhoneNumber()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val picked = readPickedContact(context, uri)
        if (picked == null) {
            pickError = "Couldn't read that contact. Type their details instead."
            return@rememberLauncherForActivityResult
        }
        pickError = null
        name = picked.first
        phone = picked.second
    }

    SubPage(
        "Who's riding?",
        onBack = { nav.back() },
        bottom = {
            Text(
                "They'll get a text with the pickup and drop-off. The driver will see their name and can call them.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            NovaButton(
                if (name.isBlank()) "Continue" else "Continue with ${name.trim().substringBefore(' ')}",
                onClick = { finish(Passenger(name.trim(), e164!!)) },
                enabled = canContinue,
                height = 56.dp,
            )
        },
    ) {
        SegmentedColumn {
            NovaListItem(
                0, 2, "Me",
                supporting = AuthStore.session?.user?.let { listOf(it.firstName, it.lastName).joinToString(" ").trim() },
                leading = { Icon(NovaIcons.User, null) },
                trailing = { RadioButton(selected = current == null, onClick = null) },
                onClick = { finish(null) },
            )
            NovaListItem(
                1, 2, "Choose a contact",
                supporting = "Pick someone from your phone",
                leading = { Icon(NovaIcons.People, null) },
                trailing = { Icon(NovaIcons.Chev, null) },
                onClick = {
                    try {
                        pickContact.launch(Unit)
                    } catch (_: ActivityNotFoundException) {
                        pickError = "No contacts app found. Type their details instead."
                    }
                },
            )
        }
        pickError?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.error) }

        if (recent.isNotEmpty()) {
            SectionLabel("Recent", Modifier.padding(horizontal = 4.dp))
            SegmentedColumn {
                recent.forEachIndexed { i, p ->
                    NovaListItem(
                        i, recent.size, p.name,
                        supporting = formatForDisplay(p.phone),
                        leading = { CookieAvatar(initials(p.name), c.secondaryContainer, c.onSecondaryContainer, size = 40.dp) },
                        trailing = { RadioButton(selected = current?.phone == p.phone, onClick = null) },
                        onClick = { finish(p) },
                    )
                }
            }
        }

        SectionLabel("Someone new", Modifier.padding(horizontal = 4.dp))
        NovaTextField(
            name, { name = it }, "Their name",
            modifier = Modifier.focusRequester(nameFocus),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        )
        val country = remember(region) { PhoneRegions.find(region) }
        NovaTextField(
            phone, { phone = it.filter { ch -> ch.isDigit() || ch in "+ -()" }.take(20) }, "Their mobile",
            // An example in the rider's own country's format (libphonenumber), never a fixed one.
            placeholder = PhoneRegions.exampleMobile(region)?.let { "e.g. $it" },
            leading = country?.let { cc ->
                { Text("${cc.flag} +${cc.dial}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp)) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        )
        if (phone.isNotBlank() && e164 == null) {
            Text("That doesn't look like a mobile number.", style = MaterialTheme.typography.bodySmall, color = c.error, modifier = Modifier.padding(horizontal = 4.dp))
        }
    }
}

/** The rider's own country: from their verified phone, else the SIM/network, else NZ. */
private fun homeRegion(context: Context): String {
    val util = PhoneNumberUtil.getInstance()
    val fromPhone = AuthStore.session?.user?.phone?.let { runCatching { util.getRegionCodeForNumber(util.parse(it, null)) }.getOrNull() }
    return fromPhone?.takeIf { it != "ZZ" } ?: detectCountry(context)?.iso ?: "NZ"
}

/** "+64215550123" -> "+64 21 555 0123" for the recent list. */
private fun formatForDisplay(e164: String): String {
    val util = PhoneNumberUtil.getInstance()
    return runCatching { util.format(util.parse(e164, null), PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL) }.getOrDefault(e164)
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")

/** Opens the contacts app filtered to phone numbers; returns the chosen number's data URI. */
private class PickPhoneNumber : ActivityResultContract<Unit, Uri?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Name and number of the picked phone row (readable thanks to the picker's one-time grant). */
private fun readPickedContact(context: Context, uri: Uri): Pair<String, String>? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null,
    )?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val name = cursor.getString(0).orEmpty().trim()
        val number = cursor.getString(1).orEmpty().trim()
        if (number.isBlank()) null else name.ifBlank { number } to number
    }
}.getOrNull()
