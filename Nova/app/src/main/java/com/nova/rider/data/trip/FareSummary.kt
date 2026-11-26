package com.nova.rider.data.trip

import com.nova.rider.data.auth.Ride

/** One receipt line. [struck] marks the upfront price that was replaced by the actual fare. */
data class FareLine(val label: String, val amount: String, val struck: Boolean = false)

/**
 * Receipt lines for a ride, from the server's settled amounts (minor units in the ride's
 * currency, never a hard-coded one). Mirrors how Uber explains a final charge:
 *  - upfront price held: one trip line, plus a wait time line when the driver waited;
 *  - repriced ("actual"): the upfront price struck through, then the actual time-and-distance fare;
 *  - cancelled late or no-show: the fee alone.
 */
object FareSummary {
    private fun Ride.money(cents: Long) = TripFormat.money(cents, currency)

    val Ride.isNoShow: Boolean get() = cancelReason == "rider_no_show"

    /** What the rider was charged in the end. */
    fun total(ride: Ride): Long = if (ride.status == "cancelled") ride.cancelFeeCents else ride.fareCents

    fun lines(ride: Ride): List<FareLine> = with(ride) {
        if (status == "cancelled") {
            return if (cancelFeeCents > 0) listOf(FareLine(if (isNoShow) "No-show fee" else "Cancellation fee", money(cancelFeeCents))) else emptyList()
        }
        if (status != "completed") return emptyList()
        val trip = (fareCents - waitFeeCents).coerceAtLeast(0)
        buildList {
            if (fareBasis == "actual") {
                upfrontFareCents?.let { add(FareLine("Upfront price", money(it), struck = true)) }
                add(FareLine("Trip fare · actual time and distance", money(trip)))
            } else {
                add(FareLine("Trip fare", money(trip)))
            }
            if (waitFeeCents > 0) add(FareLine("Wait time", money(waitFeeCents)))
        }
    }

    /** One plain sentence when the charge differs from the agreed price, else null. */
    fun explanation(ride: Ride): String? = with(ride) {
        when {
            status == "cancelled" && cancelFeeCents > 0 ->
                if (isNoShow) "Your driver waited at the pickup and couldn't find you, so a no-show fee was charged."
                else "You cancelled after the free cancellation time while your driver was on the way, so a cancellation fee was charged."
            status == "cancelled" -> "This ride was cancelled. You weren't charged."
            fareBasis == "actual" -> when (fareChangeReason) {
                "ended_away" -> "Your trip ended away from the destination you booked, so the price was adjusted to the actual time and distance."
                "longer_trip" -> "Your trip took much longer than expected, so the price was adjusted to the actual time and distance."
                else -> "Your price was adjusted to the actual time and distance of your trip."
            } + if (waitFeeCents > 0) " A wait time fee was added for the time your driver waited." else ""
            waitFeeCents > 0 -> "Your driver waited more than the free minutes at the pickup, so a wait time fee was added."
            else -> null
        }
    }

    /** Seconds of free cancellation left now; null when the window isn't known (no driver yet). */
    fun freeCancelSecondsLeft(ride: Ride, now: Long = System.currentTimeMillis()): Long? {
        val until = ride.cancelFreeUntil ?: return null
        val end = runCatching { java.time.Instant.parse(until).toEpochMilli() }.getOrNull() ?: return null
        return ((end - now) / 1_000).coerceAtLeast(0)
    }

    /**
     * Whether cancelling right now costs money, Uber's rule: free before a driver accepts, free in
     * the grace window after they accept, then a fee while the driver is on the way or waiting.
     */
    fun cancelFeeNow(ride: Ride, now: Long = System.currentTimeMillis()): Long {
        if (ride.status !in setOf("accepted", "arriving")) return 0
        val left = freeCancelSecondsLeft(ride, now) ?: return 0
        return if (left > 0) 0 else ride.cancelFeePreviewCents ?: 0
    }
}
