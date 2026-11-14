package com.nova.rider.data

/**
 * Sample content used by the UI until the Rust backend is connected. Keep all copy that will
 * come from the API here so screens can later read from a repository instead.
 */
object Sample {
    const val riderName = "Akash"
    const val riderInitials = "AK"
    const val riderPhone = "+64 21 123 4567"
    const val riderRating = "4.9"

    const val driverName = "Ravi"
    const val driverFullName = "Ravi Kumar"
    const val driverInitials = "RK"
    const val driverRating = "4.92"
    const val car = "White Toyota Prius"
    const val carShort = "White Prius"
    const val plate = "NVA412"

    const val pickup = "12 Ponsonby Rd"
    const val pickupFull = "12 Ponsonby Rd, Grey Lynn"
    const val pickupArea = "Grey Lynn, Auckland 1011"
    const val destination = "Sylvia Park"
    const val destinationAddress = "286 Mount Wellington Hwy, Mount Wellington"
    const val tripPin = "4821"

    const val fare = "$28.40"
    const val distance = "9.8 km"
}

data class RideOption(
    val id: String,
    val name: String,
    val seats: Int,
    val meta: String,
    val price: String,
)

val RideChoices = listOf(
    RideOption("ev", "Electric", 4, "2 min away", "$22.90"),
    RideOption("go", "Go", 4, "3 min away", "$24.60"),
    RideOption("comfort", "Comfort", 4, "4 min · top-rated drivers", "$28.40"),
    RideOption("xl", "XL", 6, "7 min away", "$36.90"),
)

data class Place(val name: String, val address: String, val distance: String)

val SearchResults = listOf(
    Place("Sylvia Park", "286 Mount Wellington Hwy, Mount Wellington", "9.8 km"),
    Place("Sylvia Park · Level 1 car park", "Drop-off zone, Gate 3", "9.9 km"),
    Place("Westfield Newmarket", "309 Broadway, Newmarket", "24 km"),
    Place("Westfield Albany", "Don McKinnon Dr, Albany", "21 km"),
    Place("Hoyts Sylvia Park", "Level 2, Sylvia Park", "9.8 km"),
)

data class PastTrip(val place: String, val whenText: String, val fare: String, val paid: Boolean)

val PastTrips = listOf(
    PastTrip("Sylvia Park", "Today, 10:14 AM · Comfort", "$28.40", true),
    PastTrip("Home", "Thu, 7:52 PM · Go", "$21.30", true),
    PastTrip("Commercial Bay", "Wed, 9:05 AM · Electric", "$0.00", false),
    PastTrip("Ponsonby Central", "Mon, 8:40 PM · Go", "$26.10", true),
)
