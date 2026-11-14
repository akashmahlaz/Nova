package com.nova.driver.data

/**
 * Sample content used until the Rust backend is connected. Everything that will come from the
 * API lives here so screens can later read from a repository instead.
 */
object Driver {
    const val firstName = "Ravi"
    const val fullName = "Ravi Kumar"
    const val initials = "RK"
    const val rating = "4.92"
    const val phone = "22 456 7890"
    const val email = "ravi.k@example.co.nz"
    const val car = "White Toyota Prius"
    const val plate = "NVA412"
    const val today = "$286.50"
}

object CurrentTrip {
    const val rider = "Priya"
    const val riderInitials = "PS"
    const val riderRating = "4.85"
    const val riderRides = "212"
    const val pickup = "12 Ponsonby Rd, Grey Lynn"
    const val dropoff = "Sylvia Park, Mount Wellington"
    const val dropoffShort = "Sylvia Park"
    const val fare = "$28.40"
    const val pin = "4821"
}

data class PastTrip(val time: String, val route: String, val detail: String, val earned: String)

val TodayTrips = listOf(
    PastTrip("10:14", "Ponsonby → Sylvia Park", "Comfort · 9.8 km · tip $3", "$25.70"),
    PastTrip("09:20", "Newmarket → Airport", "Go · 21 km", "$61.20"),
    PastTrip("08:41", "Mt Eden → Britomart", "Go · 5.1 km", "$14.90"),
    PastTrip("08:02", "Grey Lynn → Parnell", "Go · 4.2 km · cash", "$12.30"),
    PastTrip("07:30", "Albany → Takapuna", "Comfort · 11 km", "$22.80"),
)

data class EarningsPeriod(
    val label: String,
    val range: String,
    val total: String,
    val trips: String,
    val hours: String,
    val tips: String,
    val days: List<String>,
    val values: List<Int>,
    val highlight: Int,
)

val EarningsPeriods = listOf(
    EarningsPeriod("Day", "Today, Sat 14 Nov", "$312.40", "11", "6.5h", "$18.50", listOf("6a", "8a", "10a", "12p", "2p", "4p", "6p"), listOf(180, 620, 540, 310, 0, 0, 0), 2),
    EarningsPeriod("Week", "9 – 15 Nov", "$948.20", "38", "29h", "$31.00", listOf("M", "T", "W", "T", "F", "S", "S"), listOf(1420, 1780, 960, 2010, 2240, 1030, 0), 5),
    EarningsPeriod("Month", "November so far", "$2,194.70", "91", "72h", "$74.00", listOf("W1", "W2", "W3", "W4", "", "", ""), listOf(12420, 9460, 0, 0, 0, 0, 0), 1),
)
