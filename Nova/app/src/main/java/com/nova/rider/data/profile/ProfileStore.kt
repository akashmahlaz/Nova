package com.nova.rider.data.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.rider.data.auth.AuthStore
import com.nova.rider.data.auth.EmergencyContact
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.SavedPlace
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * In-memory cache of the signed-in rider's PostgreSQL-backed saved places and emergency contacts.
 * Cleared automatically on logout via [AuthStore.clearSession].
 */
object ProfileStore {
    var places: List<SavedPlace> by mutableStateOf(emptyList())
        private set

    var contacts: List<EmergencyContact> by mutableStateOf(emptyList())
        private set

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
            placesJob.await()?.let { places = it }
            contactsJob.await()?.let { contacts = it }
            loaded = true
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
        contacts = emptyList()
        loaded = false
    }
}
