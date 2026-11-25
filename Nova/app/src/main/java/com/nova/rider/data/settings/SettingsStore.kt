package com.nova.rider.data.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.rider.data.auth.AppNotification
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.RiderSettings

/**
 * Rider preferences and the notification inbox.
 *
 * Ride preferences (wheelchair access, quiet ride, location only during trips) live on the
 * server, because dispatch and drivers act on them.
 */
object SettingsStore {
    var ride: RiderSettings by mutableStateOf(RiderSettings())
        private set
    var rideLoaded: Boolean by mutableStateOf(false)
        private set

    var notifications: List<AppNotification> by mutableStateOf(emptyList())
        private set
    var unread: Int by mutableIntStateOf(0)
        private set

    suspend fun loadRide() {
        runCatching { NovaApi.riderSettings() }.onSuccess { ride = it; rideLoaded = true }
    }

    /**
     * Optimistic save: the switch moves at once and rolls back if the server refuses, so toggles
     * feel instant on slow mobile data. Returns false when the save failed.
     */
    suspend fun updateRide(next: RiderSettings): Boolean {
        val previous = ride
        ride = next
        return runCatching { NovaApi.saveRiderSettings(next) }
            .onSuccess { ride = it }
            .onFailure { ride = previous }
            .isSuccess
    }

    suspend fun loadNotifications(): Boolean =
        runCatching { NovaApi.notifications() }
            .onSuccess { (list, count) -> notifications = list; unread = count }
            .isSuccess

    suspend fun markAllRead() {
        if (unread == 0) return
        val before = notifications
        val now = java.time.Instant.now().toString()
        notifications = notifications.map { if (it.readAt == null) it.copy(readAt = now) else it }
        unread = 0
        runCatching { NovaApi.markAllNotificationsRead() }.onFailure { notifications = before; unread = before.count { it.readAt == null } }
    }

    /** Signed-out state. */
    fun clearAccountData() {
        ride = RiderSettings()
        rideLoaded = false
        notifications = emptyList()
        unread = 0
    }
}
