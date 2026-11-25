package com.nova.rider.data.trip

import com.nova.rider.data.auth.ApiException

/** Maps geo/quote error codes from the server (see server/src/geo) to rider-facing copy. */
fun tripErrorMessage(code: String?): String = when (code) {
    "outside_service_area" -> "Nova isn't available at this pickup yet."
    "no_route" -> "We couldn't find a driving route between these places."
    "no_products" -> "No ride types are available here right now."
    "maps_unavailable", "maps_failed" -> "Maps are having trouble. Try again in a moment."
    "too_many_requests" -> "You're going a bit fast. Try again in a minute."
    "network_error" -> "No connection. Check your internet and try again."
    "unauthorized" -> "Please sign in again."
    "quote_expired" -> "That price expired. Check the new price and book again."
    "quote_already_booked" -> "This ride is already booked."
    "ride_in_progress" -> "You already have a ride in progress."
    "schedule_too_soon" -> "Pick a time at least 15 minutes from now."
    "schedule_too_far" -> "You can schedule up to 30 days ahead."
    "invalid_phone" -> "That mobile number isn't valid. Check the number and country code."
    "invalid_passenger_name" -> "Enter the rider's name."
    "ride_not_cancellable" -> "This ride can't be cancelled any more."
    else -> "Something went wrong. Try again."
}

fun tripErrorMessage(error: Throwable): String = tripErrorMessage((error as? ApiException)?.code)
