package com.nova.driver.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nova.driver.data.api.API_BASE_URL
import com.nova.driver.data.api.ApiException
import com.nova.driver.data.api.DriverApi
import com.nova.driver.data.api.DriverMe
import com.nova.driver.data.api.DriverRide
import com.nova.driver.data.api.DriverState
import com.nova.driver.data.api.Offer
import com.nova.driver.data.api.ProductRule
import com.nova.driver.data.auth.AuthStore
import com.nova.driver.navigation.Arrived
import com.nova.driver.navigation.OnTrip
import com.nova.driver.navigation.Route
import com.nova.driver.navigation.ToPickup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** The car as typed on the Vehicle screen; sent with the application on "Submit for review". */
data class VehicleDraft(
    val make: String,
    val model: String,
    val color: String,
    val plate: String,
    val year: Int,
    val seats: Int,
    val electric: Boolean,
    val wheelchair: Boolean,
)

/**
 * Everything the driver app knows about the account and the current shift.
 *
 * While the driver is online or on a trip it keeps three things running:
 *  * location: a GPS fix every ~5 s goes to the server (heartbeat + rider's map),
 *  * the live socket (`/v1/ws`): offers and ride changes arrive the moment they happen,
 *  * a slow `GET /v1/driver/state` poll, so a dropped socket can never hide an offer for long.
 */
object DriverStore {
    private const val HEARTBEAT_MS = 15_000L
    private const val POLL_MS = 10_000L
    private val activeStatuses = setOf("accepted", "arriving", "in_progress")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = MainScope()
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()

    var me: DriverMe? by mutableStateOf(null)
        private set
    var state: DriverState by mutableStateOf(DriverState())
        private set
    var rules: List<ProductRule> by mutableStateOf(emptyList())
        private set
    /** The trip just finished, for the Complete screen. */
    var lastCompleted: DriverRide? by mutableStateOf(null)
        private set
    /** Name of the rider who cancelled the trip this driver was on; RiderCancelled clears it. */
    var cancelledRider: String? by mutableStateOf(null)

    /** Fares and trips since local midnight / since Monday (null until loaded). */
    var today: com.nova.driver.data.api.Earnings? by mutableStateOf(null)
        private set
    var week: com.nova.driver.data.api.Earnings? by mutableStateOf(null)
        private set
    var unread: Int by mutableStateOf(0)
        private set

    var vehicleDraft: VehicleDraft? = null
    var licenceNumber: String = ""

    private lateinit var appContext: Context
    private var liveJob: Job? = null
    private var socket: WebSocket? = null
    private var lastFix: Location? = null
    private var lastSentAt = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun clear() {
        stopLive()
        me = null
        state = DriverState()
        lastCompleted = null
        cancelledRider = null
        vehicleDraft = null
        licenceNumber = ""
        today = null
        week = null
        unread = 0
    }

    // ---------- account ----------

    suspend fun refreshMe(): DriverMe = DriverApi.me().also { me = it }

    suspend fun loadRules() {
        if (rules.isEmpty()) runCatching { DriverApi.productRules() }.onSuccess { rules = it }
    }

    /** Creates the driver profile, saves the car, then sends it all for review. */
    suspend fun submitApplication() {
        val current = me ?: refreshMe()
        if (current.driver == null || current.driver.status == "pending") {
            val draft = AuthStore.draft
            me = DriverApi.apply(
                licenceNumber = licenceNumber.trim(),
                licenceExpiry = null,
                city = draft.city,
                countryCode = draft.countryCode,
                hasPassengerLicence = draft.hasPassengerLicence,
            )
        }
        vehicleDraft?.let { v ->
            me = DriverApi.setVehicle(v.make, v.model, v.color, v.plate, v.year, v.seats, v.electric, v.wheelchair)
        }
        me = DriverApi.submit()
    }

    /** Photo or PDF for one document type, straight to S3, then the checklist is re-read. */
    suspend fun uploadDocument(category: String, uri: android.net.Uri) {
        val file = runCatching { DocumentImages.prepare(appContext, uri) }.getOrElse { throw ApiException("invalid_content_type") }
        DriverApi.uploadDocument(category, file.bytes, file.contentType, file.fileName)
        refreshMe()
    }

    suspend fun savePayout(name: String, number: String, tax: String, gst: Boolean, schedule: String) {
        me = DriverApi.setPayout(name, number, tax, gst, schedule)
    }

    suspend fun setProduct(productId: String, enabled: Boolean) {
        me = DriverApi.setProduct(productId, enabled)
    }

    /** Today's and this week's earnings, in the phone's time zone (weeks start Monday). */
    suspend fun refreshEarnings() {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val midnight = now.toLocalDate().atStartOfDay(zone)
        val monday = now.toLocalDate().with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone)
        val end = now.plusMinutes(1).toInstant().toString()
        runCatching { DriverApi.earnings(midnight.toInstant().toString(), end) }.onSuccess { today = it }
        runCatching { DriverApi.earnings(monday.toInstant().toString(), end) }.onSuccess { week = it }
    }

    suspend fun refreshUnread() {
        runCatching { DriverApi.notifications() }.onSuccess { unread = it.second }
    }

    fun markRead() { unread = 0 }

    /** Where a signed-in driver belongs: onboarding, review, a trip in progress, or home. */
    suspend fun entryRoute(): Route? {
        val account = try { refreshMe() } catch (e: ApiException) {
            // One account, one role: a rider/admin account can't use the driver app.
            if (e.code == "not_driver_account") { AuthStore.clearSession(); throw e }
            return null
        } catch (e: Exception) { return null }
        val driver = account.driver
        if (driver == null || account.vehicle == null) return com.nova.driver.navigation.Vehicle
        if (driver.status != "approved") return com.nova.driver.navigation.Pending
        runCatching { refreshState() }
        return rideRoute(state.ride) ?: com.nova.driver.navigation.Home
    }

    fun rideRoute(ride: DriverRide?): Route? = when (ride?.status) {
        "accepted" -> ToPickup
        "arriving" -> Arrived
        "in_progress" -> OnTrip
        else -> null
    }

    // ---------- shift ----------

    suspend fun refreshState() = applyState(DriverApi.state(), detectRiderCancel = true)

    private fun applyState(next: DriverState, detectRiderCancel: Boolean = false) {
        val before = state.ride
        if (detectRiderCancel && before != null && before.status in activeStatuses && next.ride?.id != before.id) {
            // Our ride left without us ending it: the rider cancelled.
            cancelledRider = before.riderName
        }
        state = next
        if (next.online || next.ride != null) startLive() else stopLive()
    }

    suspend fun goOnline() {
        val fix = currentLocation() ?: throw ApiException("invalid_location")
        applyState(DriverApi.goOnline(fix.latitude, fix.longitude))
    }

    suspend fun goOffline() = applyState(DriverApi.goOffline())

    suspend fun accept(): DriverRide {
        val offer = state.offer ?: throw ApiException("offer_expired")
        return try {
            DriverApi.accept(offer.offerId).also { ride ->
                state = state.copy(offer = null, ride = ride)
                startLive()
            }
        } catch (e: ApiException) {
            dropOffer(offer.offerId)
            throw e
        }
    }

    suspend fun decline() {
        val offer = state.offer ?: return
        dropOffer(offer.offerId)
        runCatching { applyState(DriverApi.decline(offer.offerId)) }
    }

    /** The offer timed out on screen (the server closes it at the same moment). */
    fun dropOffer(offerId: String) {
        if (state.offer?.offerId == offerId) state = state.copy(offer = null)
    }

    suspend fun arrived() = rideStep { DriverApi.arrived(it) }

    suspend fun start(pin: String) = rideStep { DriverApi.start(it, pin) }

    suspend fun complete() {
        val ride = state.ride ?: return
        val done = DriverApi.complete(ride.id)
        lastCompleted = done
        state = state.copy(ride = null)
        refreshEarnings()
    }

    suspend fun cancelRide(reason: String) {
        val ride = state.ride ?: return
        applyState(DriverApi.cancel(ride.id, reason))
    }

    /** The rider never came (allowed after the free wait at the pickup). */
    suspend fun noShow() {
        val ride = state.ride ?: return
        applyState(DriverApi.cancel(ride.id, "no_show"))
    }

    private suspend fun rideStep(call: suspend (String) -> DriverRide): DriverRide {
        val ride = state.ride ?: throw ApiException("invalid_ride_state")
        return try {
            call(ride.id).also { state = state.copy(ride = it) }
        } catch (e: ApiException) {
            if (e.code == "invalid_ride_state" || e.code == "ride_cancelled") runCatching { refreshState() }
            throw e
        }
    }

    // ---------- live: location, socket, poll ----------

    private fun startLive() {
        if (liveJob?.isActive == true || AuthStore.session == null) return
        liveJob = scope.launch {
            launch { locationUpdates() }
            launch { heartbeat() }
            launch { socketLoop() }
            launch {
                while (isActive) {
                    delay(POLL_MS)
                    runCatching { refreshState() }
                }
            }
        }
    }

    private fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        socket?.close(1000, null)
        socket = null
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): Location? {
        if (!hasLocationPermission()) return null
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val fresh = runCatching {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token).await()
        }.getOrNull()
        return (fresh ?: runCatching { client.lastLocation.await() }.getOrNull())?.also { lastFix = it }
    }

    @SuppressLint("MissingPermission")
    private suspend fun locationUpdates() {
        if (!hasLocationPermission()) return
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        callbackFlow {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).setMinUpdateIntervalMillis(3_000).build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { trySend(it) }
                }
            }
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            awaitClose { client.removeLocationUpdates(callback) }
        }.collect { fix ->
            lastFix = fix
            if (System.currentTimeMillis() - lastSentAt >= 4_000) send(fix)
        }
    }

    /** Keeps the server heartbeat alive even when the car is parked and GPS is quiet. */
    private suspend fun heartbeat() {
        while (currentCoroutineContext().isActive) {
            delay(HEARTBEAT_MS)
            val fix = lastFix ?: currentLocation()
            if (fix != null && System.currentTimeMillis() - lastSentAt >= HEARTBEAT_MS - 1_000) send(fix)
        }
    }

    private suspend fun send(fix: Location) {
        lastSentAt = System.currentTimeMillis()
        val stillOnline = runCatching { DriverApi.location(fix.latitude, fix.longitude) }.getOrNull() ?: return
        // The server dropped us (missed heartbeats, suspended): show the truth.
        if (!stillOnline && state.online) runCatching { refreshState() }
    }

    private suspend fun socketLoop() {
        val url = API_BASE_URL.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/v1/ws"
        var backoff = 2_000L
        while (currentCoroutineContext().isActive) {
            val token = AuthStore.session?.accessToken ?: return
            val closed = CompletableDeferred<Int>()
            val opened = CompletableDeferred<Unit>()
            val ws = http.newWebSocket(
                Request.Builder().url(url).header("Authorization", "Bearer $token").build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.complete(Unit)
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        scope.launch { onEvent(text) }
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(code)
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        closed.complete(response?.code ?: 0)
                    }
                },
            )
            socket = ws
            val code = try {
                closed.await()
            } finally {
                ws.cancel()
            }
            if (opened.isCompleted) backoff = 2_000L
            // An expired access token: refresh it before reconnecting.
            if (code == 401) withContext(Dispatchers.IO) { DriverApi.freshAccessToken() }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000L)
        }
    }

    private suspend fun onEvent(text: String) {
        val event = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (event["type"]?.jsonPrimitive?.content) {
            "offer" -> event["offer"]?.let { raw ->
                runCatching { json.decodeFromJsonElement(Offer.serializer(), raw) }.onSuccess { state = state.copy(offer = it) }
            }
            "offer_closed" -> event["offer_id"]?.jsonPrimitive?.content?.let(::dropOffer)
            // Approved, paused, or a document sent back: re-read the account.
            "driver_status" -> { runCatching { refreshMe() }; refreshUnread() }
            // hello (just connected) and ride changes: re-read the whole state, it's one cheap call.
            else -> runCatching { refreshState() }
        }
    }
}
