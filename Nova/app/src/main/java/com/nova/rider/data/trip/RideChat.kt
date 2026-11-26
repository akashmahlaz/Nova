package com.nova.rider.data.trip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.rider.data.auth.ApiException
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.NovaApi.ChatMessage

/**
 * In-trip chat with the driver (server `/v1/rides/{id}/messages`). The driver's messages arrive
 * over the live socket as `chat_message`; the rider's own come back from the send call. The
 * conversation belongs to one ride and is dropped when another ride is followed.
 */
object RideChat {
    var rideId: String? by mutableStateOf(null)
        private set
    var messages: List<ChatMessage> by mutableStateOf(emptyList())
        private set
    /** Driver messages that arrived while the chat screen wasn't open (badge on the Message button). */
    var unread: Int by mutableIntStateOf(0)
        private set
    var sending: Boolean by mutableStateOf(false)
        private set

    /** True while the chat screen is on screen, so pushed messages don't count as unread. */
    var open: Boolean = false
        set(value) { field = value; if (value) unread = 0 }

    private fun forRide(id: String) {
        if (rideId != id) { rideId = id; messages = emptyList(); unread = 0 }
    }

    /** Loads the conversation (also marks the driver's messages read on the server). */
    suspend fun load(id: String): Boolean {
        forRide(id)
        return runCatching { NovaApi.rideMessages(id) }.onSuccess { merge(it) }.isSuccess
    }

    /** Sends [text]; returns null on success or the server error code. */
    suspend fun send(id: String, text: String): String? {
        forRide(id)
        sending = true
        return try {
            merge(listOf(NovaApi.sendRideMessage(id, text)))
            null
        } catch (e: ApiException) {
            e.code
        } finally {
            sending = false
        }
    }

    /** A `chat_message` pushed over the socket. */
    fun onPushed(id: String, message: ChatMessage) {
        forRide(id)
        val isNew = messages.none { it.id == message.id }
        merge(listOf(message))
        if (isNew && message.from == "driver" && !open) unread++
    }

    private fun merge(incoming: List<ChatMessage>) {
        messages = (messages + incoming).associateBy { it.id }.values.sortedBy { runCatching { java.time.Instant.parse(it.sentAt) }.getOrDefault(java.time.Instant.EPOCH) }
    }
}
