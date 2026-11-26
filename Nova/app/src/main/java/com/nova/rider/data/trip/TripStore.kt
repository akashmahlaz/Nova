package com.nova.rider.data.trip

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nova.rider.data.auth.ApiException
import com.nova.rider.data.auth.GeoPlace
import com.nova.rider.data.auth.GeoPoint
import com.nova.rider.data.auth.NovaApi
import com.nova.rider.data.auth.Passenger
import com.nova.rider.data.auth.Ride
import com.nova.rider.data.auth.RideQuote
import com.nova.rider.data.auth.SavedPlace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * The ride being planned: where the rider is, pickup, stops, drop-off and the locked upfront quote.
 *
 * Booking screens are separate Navigation 3 destinations without arguments, so they share this
 * Compose-observable store (same pattern as AuthStore/ProfileStore). It is in-memory on purpose:
 * a quote expires in minutes, so there is nothing worth persisting across process death.
 */
object TripStore {
    /** Most stops a rider can add between pickup and drop-off (the server accepts up to 3). */
    const val MAX_STOPS = 3

    /** Last device fix. Null until location permission is granted and a fix arrives. */
    var currentLocation: GeoPoint? by mutableStateOf(null)
        private set

    /** Accuracy radius of [currentLocation] in metres (68% confidence), if known. */
    var currentAccuracyMeters: Float? by mutableStateOf(null)
        private set

    var pickup: GeoPlace? by mutableStateOf(null)
        private set

    /** True while the pickup follows the device (the default); false once the rider picks another place. */
    var pickupIsCurrentLocation: Boolean by mutableStateOf(true)
        private set

    /** Intermediate stops in order. A null entry is a stop row the rider added but hasn't filled yet. */
    var stops: List<GeoPlace?> by mutableStateOf(emptyList())
        private set

    var dropoff: GeoPlace? by mutableStateOf(null)
        private set

    var quote: RideQuote? by mutableStateOf(null)
        private set

    var quoting: Boolean by mutableStateOf(false)
        private set

    /** Stable server error code from the last quote attempt, mapped to copy by the screen. */
    var quoteError: String? by mutableStateOf(null)
        private set

    /** Wall-clock end of the current quote's price lock (ms). */
    private var quoteValidUntil = 0L

    /**
     * Drops a quote whose server lock has (nearly) run out, so screens re-quote instead of
     * showing an old price. Call before showing or booking a price.
     */
    fun dropExpiredQuote() {
        if (quote != null && System.currentTimeMillis() >= quoteValidUntil) invalidateQuote()
    }

    /**
     * Back to a fresh plan (Home is the start of every trip): destination, stops, price, rider and
     * ride type are cleared, and the pickup follows the phone again. Kept: the rider's location,
     * nearby places and a ride that is still in progress.
     */
    fun startNewPlan() {
        if (ride?.isActive == true) return
        stops = emptyList()
        dropoff = null
        if (!pickupIsCurrentLocation) {
            pickup = null
            pickupIsCurrentLocation = true
        }
        invalidateQuote()
        productId = null
        passenger = null
        bookingMode = BookingMode.Now
        bookError = null
        ride = null
    }

    /** Closest landmark places to the rider, nearest first (from Places Nearby Search). */
    var nearby: List<GeoPlace> by mutableStateOf(emptyList())
        private set
    private var nearbyCenter: GeoPoint? = null

    /**
     * Loads nearby places once per position. Nearby Search is a Pro-SKU call, so it is only
     * repeated when the rider has moved more than 150 m since the last load.
     */
    suspend fun loadNearby() {
        val here = currentLocation ?: return
        val last = nearbyCenter
        if (last != null && metersBetween(last, here) < 150f && nearby.isNotEmpty()) return
        runCatching { NovaApi.nearby(here) }.onSuccess {
            nearby = it
            nearbyCenter = here
        }
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Device location (the system toggle) must be on, or no app gets a fix. Returns the Play
     * services "Turn on location" dialog to launch when it is off, or null when it's already on.
     * This is the in-app one-tap prompt ride-hailing apps use instead of sending riders to Settings.
     */
    suspend fun locationSettingsResolution(context: Context): IntentSenderRequest? {
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000).build())
            .setAlwaysShow(true)
            .build()
        return try {
            LocationServices.getSettingsClient(context).checkLocationSettings(request).await()
            null
        } catch (e: ResolvableApiException) {
            IntentSenderRequest.Builder(e.resolution).build()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Two-step fix from the Fused Location Provider, the pattern Google recommends for "where am I":
     *  1. `lastLocation`: instant and free (no GPS spin-up). Centres the map immediately.
     *  2. `getCurrentLocation(HIGH_ACCURACY, maxUpdateAge = 0)`: a fresh GPS/Wi-Fi fix, the most
     *     accurate one available. Replaces step 1 when it arrives (usually 1–5 s outdoors).
     * [onFix] fires for each improvement so the camera can follow. While the pickup follows the
     * device, the best fix is reverse geocoded into the pickup address, once per meaningful move.
     */
    @SuppressLint("MissingPermission") // Checked by hasLocationPermission.
    suspend fun locate(context: Context, onFix: suspend (GeoPoint) -> Unit = {}): GeoPoint? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

        runCatching { client.lastLocation.await() }.getOrNull()?.let { last ->
            if (currentLocation == null) {
                currentLocation = GeoPoint(last.latitude, last.longitude)
                currentAccuracyMeters = if (last.hasAccuracy()) last.accuracy else null
                onFix(currentLocation!!)
            }
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0) // never a cached fix: we want the most accurate position now
            .setDurationMillis(15_000)
            .build()
        // A first GPS fix indoors can be 50-100 m off. Take up to 3 fresh fixes and keep the best,
        // stopping as soon as one is within 25 m (good enough to stand at the right door).
        for (attempt in 1..3) {
            val cancel = CancellationTokenSource()
            val fresh = runCatching { client.getCurrentLocation(request, cancel.token).await(cancel) }.getOrNull()
                ?: continue
            val accuracy = if (fresh.hasAccuracy()) fresh.accuracy else null
            val best = currentAccuracyMeters
            if (best == null || accuracy == null || accuracy <= best || currentLocation == null) {
                currentLocation = GeoPoint(fresh.latitude, fresh.longitude)
                currentAccuracyMeters = accuracy
                onFix(currentLocation!!)
            }
            if ((currentAccuracyMeters ?: Float.MAX_VALUE) <= 25f) break
        }

        val point = currentLocation ?: return null
        if (pickupIsCurrentLocation) {
            val previous = pickup
            // Skip the paid reverse geocode when we already resolved (almost) this spot.
            if (previous == null || metersBetween(previous.point, point) > 25f) {
                pickup = runCatching { NovaApi.reverseGeocode(point) }.getOrNull()
                    ?: GeoPlace(name = "Current location", address = "", lat = point.lat, lng = point.lng)
                invalidateQuote()
            }
        }
        return point
    }

    /** Back to "pickup = where I am" (the rider chose "Use current location"). */
    suspend fun useCurrentLocationAsPickup(context: Context) {
        pickupIsCurrentLocation = true
        pickup = null
        invalidateQuote()
        locate(context)
    }

    fun choosePickup(place: GeoPlace) {
        pickup = place
        pickupIsCurrentLocation = false
        invalidateQuote()
    }

    fun chooseDropoff(place: GeoPlace) {
        dropoff = place
        invalidateQuote()
    }

    /** Saved places only carry coordinates when they were saved from search. */
    fun chooseDropoff(saved: SavedPlace): Boolean {
        val place = saved.toGeoPlace() ?: return false
        chooseDropoff(place)
        return true
    }

    /** Adds an empty stop row (filled by search). Returns its index, or null at the limit. */
    fun addStop(): Int? {
        if (stops.size >= MAX_STOPS) return null
        stops = stops + null
        return stops.lastIndex
    }

    fun chooseStop(index: Int, place: GeoPlace) {
        if (index !in stops.indices) return
        stops = stops.toMutableList().also { it[index] = place }
        invalidateQuote()
    }

    fun removeStop(index: Int) {
        if (index !in stops.indices) return
        val hadPlace = stops[index] != null
        stops = stops.toMutableList().also { it.removeAt(index) }
        if (hadPlace) invalidateQuote()
    }

    /** True once pickup and drop-off are set and every added stop is filled. */
    val routeComplete: Boolean
        get() = pickup != null && dropoff != null && stops.all { it != null }

    /** Fetches upfront prices for pickup -> stops -> drop-off. */
    suspend fun requestQuote() {
        val from = pickup ?: return
        val to = dropoff ?: return
        val via = stops.filterNotNull()
        quoting = true
        quoteError = null
        try {
            quote = NovaApi.quote(from.point, to.point, via.map { it.point })
            // Keep 30 s of margin under the server's lock so a price is never shown after it expired.
            quoteValidUntil = System.currentTimeMillis() + (quote!!.expiresIn - 30).coerceAtLeast(0) * 1000
        } catch (e: ApiException) {
            quote = null
            quoteError = e.code
        } finally {
            quoting = false
        }
    }

    private fun invalidateQuote() {
        quote = null
        quoteError = null
    }

    // ---------- booking ----------

    /** Home's Now / Schedule / For someone choice, in that order (matches the segmented control). */
    enum class BookingMode { Now, Schedule, ForSomeone }

    var bookingMode: BookingMode by mutableStateOf(BookingMode.Now)

    /** Ride type chosen on Ride options (a FareOption.productId). Null = the first option. */
    var productId: String? by mutableStateOf(null)

    /** "gpay", "card" or "credit" (server values). */
    var paymentMethod: String by mutableStateOf("gpay")

    /** Set when booking for someone else; null = the rider is riding. Change it with [rideFor]. */
    var passenger: Passenger? by mutableStateOf(null)
        private set

    /**
     * Who is riding (null = me). For someone else, a pickup that only followed this phone's
     * location is cleared, because they are usually somewhere else: the planner then asks
     * "Where should we pick up <name>?" ("Use current location" stays one tap away).
     * Back to me with no pickup chosen: the pickup follows this phone again.
     */
    fun rideFor(who: Passenger?) {
        passenger = who
        bookingMode = if (who == null) BookingMode.Now else BookingMode.ForSomeone
        if (who != null && pickupIsCurrentLocation) {
            pickup = null
            pickupIsCurrentLocation = false
            invalidateQuote()
        } else if (who == null && pickup == null) {
            pickupIsCurrentLocation = true
        }
    }

    /** The booked ride being followed (Finding driver onwards). */
    var ride: Ride? by mutableStateOf(null)
        private set

    var booking: Boolean by mutableStateOf(false)
        private set

    var bookError: String? by mutableStateOf(null)
        private set

    /**
     * Books the selected ride against the locked quote, now or at [scheduledAt] (ISO-8601 UTC).
     * A quote that expired while the rider decided is refreshed once and the booking retried, so
     * the rider isn't sent back for an error they can't act on. Returns the ride, or null on error.
     */
    suspend fun book(scheduledAt: String? = null): Ride? {
        val from = pickup ?: return null
        val to = dropoff ?: return null
        if (booking) return null
        booking = true
        bookError = null
        try {
            repeat(2) { attempt ->
                if (quote == null) requestQuote()
                val q = quote ?: run { bookError = quoteError ?: "quote_expired"; return null }
                val product = q.options.firstOrNull { it.productId == productId } ?: q.options.firstOrNull()
                    ?: run { bookError = "no_products"; return null }
                try {
                    val booked = NovaApi.bookRide(
                        quoteId = q.quoteId,
                        productId = product.productId,
                        paymentMethod = paymentMethod,
                        pickup = from,
                        dropoff = to,
                        stopNames = stops.filterNotNull().map { it.name },
                        scheduledAt = scheduledAt,
                        passenger = passenger,
                    )
                    // The quote is spent once booked; the next trip needs a fresh one, and starts
                    // as a normal "Now" ride for the rider (Uber resets "for someone" the same way).
                    invalidateQuote()
                    if (scheduledAt == null) {
                        bookingMode = BookingMode.Now
                        passenger = null
                    }
                    if (booked.isActive) ride = booked
                    return booked
                } catch (e: ApiException) {
                    if (e.code == "quote_expired" && attempt == 0) {
                        invalidateQuote()
                    } else {
                        bookError = e.code
                        return null
                    }
                }
            }
            return null
        } finally {
            booking = false
        }
    }

    /** Re-reads the followed ride (polled by Finding driver until dispatch pushes updates). */
    suspend fun refreshRide(): Ride? {
        val current = ride ?: return null
        return runCatching { NovaApi.ride(current.id) }.getOrNull()?.also { ride = it }
    }

    /**
     * Waits until the followed ride's status differs from [from] (a socket push refreshed it) or
     * the poll interval passes: [pollMs] while the live socket is open, 3 s while it's not.
     */
    suspend fun awaitRideChange(from: String?, pollMs: Long = 15_000) {
        val wait = if (LiveRide.connected) pollMs else 3_000
        kotlinx.coroutines.withTimeoutOrNull(wait) {
            androidx.compose.runtime.snapshotFlow { ride?.status }.first { it != from }
        }
    }

    /** Resumes a ride in progress after the app restarts. */
    suspend fun loadActiveRide(): Ride? =
        runCatching { NovaApi.activeRide() }.getOrNull()?.also { ride = it }

    suspend fun cancelRide(reason: String? = null): Boolean {
        val current = ride ?: return true
        return try {
            ride = NovaApi.cancelRide(current.id, reason)
            true
        } catch (e: ApiException) {
            bookError = e.code
            false
        }
    }

    /** Stop following a ride that ended (cancelled, no drivers, completed). */
    fun forgetRide() {
        ride = null
    }

    fun clear() {
        pickup = null
        pickupIsCurrentLocation = true
        stops = emptyList()
        dropoff = null
        invalidateQuote()
        productId = null
        passenger = null
        bookingMode = BookingMode.Now
        ride = null
        bookError = null
    }
}

fun SavedPlace.toGeoPlace(): GeoPlace? {
    val lat = lat ?: return null
    val lng = lng ?: return null
    return GeoPlace(name = label, address = address, lat = lat, lng = lng)
}

/** Great-circle distance in metres (Android's WGS84 implementation). */
fun metersBetween(a: GeoPoint, b: GeoPoint): Float {
    val out = FloatArray(1)
    android.location.Location.distanceBetween(a.lat, a.lng, b.lat, b.lng, out)
    return out[0]
}
