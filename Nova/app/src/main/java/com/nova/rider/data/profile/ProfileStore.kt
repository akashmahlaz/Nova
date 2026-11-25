package com.nova.rider.data.profile

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.EmergencyContact
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.SavedPlace
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The signed-in rider's saved places and emergency contacts (stored in Postgres on the server).
 *
 * Saved places are also kept on the phone (stale-while-revalidate, as ride-hailing apps do), so
 * Home and Work show the moment the app opens; the server copy then replaces them. The local copy
 * lives in app-private storage, is excluded from backups, and is wiped on logout via
 * [AuthStore.clearSession].
 */
object ProfileStore {
    private const val PREFS = "nova_profile"
    private const val KEY_PLACES = "places"
    private val json = Json { ignoreUnknownKeys = true }
    private var prefs: SharedPreferences? = null

    /** Loads the on-device copy of saved places. Call once at startup, after AuthStore.init. */
    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        if (AuthStore.session == null) return
        store.getString(KEY_PLACES, null)?.let { cached ->
            runCatching { json.decodeFromString(ListSerializer(SavedPlace.serializer()), cached) }
                .onSuccess { placesState.value = it }
        }
    }

    private val placesState = mutableStateOf<List<SavedPlace>>(emptyList())

    /** Every change is mirrored to the on-device copy. */
    var places: List<SavedPlace>
        get() = placesState.value
        private set(value) {
            placesState.value = value
            prefs?.edit()?.putString(KEY_PLACES, json.encodeToString(ListSerializer(SavedPlace.serializer()), value))?.apply()
        }

    var contacts: List<EmergencyContact> by mutableStateOf(emptyList())
        private set

    /** True once places have been fetched from the server in this app session. */
    var loaded: Boolean by mutableStateOf(false)
        private set

    fun homePlace(): SavedPlace? = places.firstOrNull { it.kind == "home" }
    fun workPlace(): SavedPlace? = places.firstOrNull { it.kind == "work" }
    fun customPlaces(): List<SavedPlace> = places.filter { it.kind == "custom" }

    suspend fun refreshIfNeeded() {
        if (!loaded && AuthStore.session != null) {
            refresh()
        }
    }

    suspend fun refresh() {
        if (AuthStore.session == null) return
        coroutineScope {
            val placesJob = async { runCatching { NovaApi.listPlaces() }.getOrNull() }
            val contactsJob = async { runCatching { NovaApi.listEmergencyContacts() }.getOrNull() }
            // Only a successful fetch counts as loaded. A failed one (server down, token being
            // renewed, no signal) keeps the cached places and is retried next time Home opens.
            placesJob.await()?.let { places = it; loaded = true }
            contactsJob.await()?.let { contacts = it }
        }
    }

    suspend fun savePlace(kind: String, label: String, address: String, lat: Double? = null, lng: Double? = null): SavedPlace {
        val saved = NovaApi.savePlace(kind, label, address, lat, lng)
        places = if (kind == "custom") {
            listOf(saved) + places
        } else {
            val filtered = places.filterNot { it.kind == kind }
            (filtered + saved).sortedBy { when (it.kind) { "home" -> 0; "work" -> 1; else -> 2 } }
        }
        return saved
    }

    suspend fun updatePlace(id: String, label: String, address: String, lat: Double?, lng: Double?): SavedPlace {
        val updated = NovaApi.updatePlace(id, label, address, lat, lng)
        places = places.map { if (it.id == id) updated else it }
        return updated
    }

    suspend fun deletePlace(id: String) {
        NovaApi.deletePlace(id)
        places = places.filterNot { it.id == id }
    }

    suspend fun addContact(name: String, phone: String, relationship: String = ""): EmergencyContact {
        val created = NovaApi.addEmergencyContact(name, phone, relationship)
        contacts = contacts + created
        return created
    }

    suspend fun deleteContact(id: String) {
        NovaApi.deleteEmergencyContact(id)
        contacts = contacts.filterNot { it.id == id }
    }

    fun clear() {
        places = emptyList()
        prefs?.edit()?.remove(KEY_PLACES)?.apply()
        contacts = emptyList()
        loaded = false
    }
}
