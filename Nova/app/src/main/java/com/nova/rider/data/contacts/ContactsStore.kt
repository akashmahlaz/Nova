package com.nova.rider.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.PhoneRegions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * One phone contact, reduced to what "Who's riding?" needs. [phone] is E.164.
 * [onNova]: true / false once checked against Nova; null while not checked (or the check failed),
 * so the screen never claims "No app" when it simply doesn't know.
 */
data class PhoneContact(val name: String, val phone: String, val onNova: Boolean?)

/**
 * The rider's phone contacts, marked with whether each one has a Nova account.
 *
 * Contacts are read on the phone (READ_CONTACTS, asked only after an in-app explanation) and
 * never uploaded: each number is normalised to E.164 in the rider's country and only its
 * SHA-256 goes to the server, which answers with the hashes that are Nova riders. Names stay on
 * the phone. Results are kept for the app session and refreshed at most every 10 minutes.
 */
object ContactsStore {
    private const val MATCH_BATCH = 1000
    private const val REFRESH_MS = 10 * 60 * 1000L

    var contacts: List<PhoneContact> by mutableStateOf(emptyList())
        private set
    var loading: Boolean by mutableStateOf(false)
        private set
    /** The last "who's on Nova" check failed (offline, server down); contacts show without badges. */
    var matchFailed: Boolean by mutableStateOf(false)
        private set
    /** True once contacts have been read at least once (even if the phone book is empty). */
    var loaded: Boolean by mutableStateOf(false)
        private set
    private var loadedAt = 0L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Reads the phone book and checks it against Nova. [region] formats local numbers (e.g. "NZ"). */
    suspend fun load(context: Context, region: String, force: Boolean = false) {
        if (!hasPermission(context) || loading) return
        if (!force && loaded && System.currentTimeMillis() - loadedAt < REFRESH_MS) return
        loading = true
        try {
            val read = withContext(Dispatchers.IO) { readPhoneBook(context, region) }
            // Show the phone book at once (keeping badges we already know); fresh badges follow.
            val known = contacts.associate { it.phone to it.onNova }
            contacts = read.map { (name, phone) -> PhoneContact(name, phone, known[phone]) }
            loaded = true
            val onNova = runCatching { matchOnNova(read.map { it.second }) }
                .onFailure { android.util.Log.w("ContactsStore", "contact match failed", it) }
                .getOrNull()
            matchFailed = onNova == null
            if (onNova != null) {
                contacts = read.map { (name, phone) -> PhoneContact(name, phone, phone in onNova) }
                loadedAt = System.currentTimeMillis()
            }
        } finally {
            loading = false
        }
    }

    fun clear() {
        matchFailed = false
        contacts = emptyList()
        loaded = false
        loadedAt = 0L
    }

    /** (name, E.164) for every contact number that is a valid number, one entry per number. */
    private fun readPhoneBook(context: Context, region: String): List<Pair<String, String>> {
        val seen = LinkedHashMap<String, String>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)?.trim().orEmpty()
                val raw = cursor.getString(1)?.trim().orEmpty()
                val e164 = PhoneRegions.toE164(region, raw) ?: continue
                if (e164 !in seen) seen[e164] = name.ifBlank { e164 }
            }
        }
        return seen.map { (phone, name) -> name to phone }
    }

    /** E.164 numbers that belong to Nova riders, matched by SHA-256 in batches. */
    private suspend fun matchOnNova(phones: List<String>): Set<String> {
        if (phones.isEmpty()) return emptySet()
        val byHash = phones.associateBy(::sha256Hex)
        val matched = mutableSetOf<String>()
        byHash.keys.chunked(MATCH_BATCH).forEach { batch ->
            NovaApi.matchContacts(batch).forEach { hash -> byHash[hash]?.let(matched::add) }
        }
        return matched
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
