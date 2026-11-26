package com.nova.rider.data.trip

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.rider.data.auth.GeoPoint
import com.nova.rider.data.auth.NovaApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** One live position of the assigned driver, as pushed by the server about every 4 s. */
data class DriverFix(
    val rideId: String,
    val status: String,
    val point: GeoPoint,
    /** Degrees clockwise from north, when the phone knows its course. */
    val heading: Float?,
    /** To the pickup before the trip starts, to the drop-off during it. */
    val etaSeconds: Long?,
    /** Road metres left on that same leg, when the server routed it. */
    val remainingMeters: Long?,
    /** Device clock (ms) when the fix arrived, to count the ETA down between fixes. */
    val receivedAt: Long,
)

/**
 * The rider's live channel (`GET /v1/ws`, bearer token in the header). While a ride is active the
 * server pushes `driver_location`, `ride_update` and the driver's `chat_message`; every (re)connect starts with `hello`, after
 * which the ride is re-read over HTTP so nothing missed while offline is lost.
 */
object LiveRide {
    var fix: DriverFix? by mutableStateOf(null)
        private set

    /** True while the socket is open. Trip screens poll HTTP slowly then, quickly otherwise. */
    var connected: Boolean by mutableStateOf(false)
        private set

    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    /** The current fix if it belongs to [rideId]. */
    fun fixFor(rideId: String?): DriverFix? = fix?.takeIf { rideId != null && it.rideId == rideId }

    /**
     * Keeps the socket open until the calling coroutine is cancelled, reconnecting with backoff
     * (1 s doubling to 30 s). An expired token is refreshed once per failed upgrade.
     */
    suspend fun follow() = coroutineScope {
        var backoff = 1_000L
        while (isActive) {
            val token = NovaApi.accessToken() ?: return@coroutineScope
            val inbox = Channel<String>(Channel.UNLIMITED)
            val closed = CompletableDeferred<Int?>()
            val wsUrl = NovaApi.baseUrl.replaceFirst("http", "ws").trimEnd('/') + "/v1/ws"
            val request = Request.Builder().url(wsUrl).header("Authorization", "Bearer $token").build()
            val socket = client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) { connected = true }
                override fun onMessage(webSocket: WebSocket, text: String) { inbox.trySend(text) }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { closed.complete(null) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { closed.complete(response?.code) }
            })
            val reader = launch { for (text in inbox) handle(text) }
            try {
                val failedWith = closed.await()
                if (failedWith == 401) withContext(Dispatchers.IO) { NovaApi.refreshAccessToken() }
                if (connected) backoff = 1_000L
            } finally {
                connected = false
                socket.cancel()
                inbox.close()
                reader.cancel()
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun handle(text: String) {
        val event = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        fun str(key: String) = event[key]?.jsonPrimitive?.contentOrNull
        val current = TripStore.ride
        when (str("type")) {
            "hello" -> TripStore.refreshRide()
            "ride_update" -> if (str("ride_id") == current?.id) TripStore.refreshRide()
            "chat_message" -> {
                val rideId = str("ride_id") ?: return
                val message = NovaApi.ChatMessage(
                    id = str("id") ?: return,
                    from = str("from") ?: "driver",
                    text = str("text") ?: return,
                    sentAt = str("sent_at").orEmpty(),
                )
                RideChat.onPushed(rideId, message)
            }
            "driver_location" -> {
                val rideId = str("ride_id") ?: return
                val lat = event["lat"]?.jsonPrimitive?.doubleOrNull ?: return
                val lng = event["lng"]?.jsonPrimitive?.doubleOrNull ?: return
                val status = str("status").orEmpty()
                fix = DriverFix(
                    rideId = rideId,
                    status = status,
                    point = GeoPoint(lat, lng),
                    heading = event["heading"]?.jsonPrimitive?.doubleOrNull?.toFloat(),
                    etaSeconds = event["eta_seconds"]?.jsonPrimitive?.longOrNull,
                    remainingMeters = event["remaining_meters"]?.jsonPrimitive?.longOrNull,
                    receivedAt = System.currentTimeMillis(),
                )
                // A status the app hasn't seen yet (arrived, started) means the ride moved on.
                if (rideId == current?.id && status.isNotEmpty() && status != current.status) TripStore.refreshRide()
            }
        }
    }
}
