package com.nova.rider.data.trip

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Display formatting for trip numbers. The server sends raw units (cents + ISO currency, metres,
 * seconds) and the app formats for the rider's locale, so one API serves every market.
 */
object TripFormat {
    /** 2840 + "NZD" -> "$28.40" (en-NZ) or "NZ$28.40" elsewhere. */
    fun money(cents: Long, currency: String, locale: Locale = Locale.getDefault()): String {
        val format = NumberFormat.getCurrencyInstance(locale)
        runCatching { format.currency = Currency.getInstance(currency) }
        // Minor units per currency: 2 for NZD/USD/INR, 0 for JPY.
        val digits = format.currency?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
        format.minimumFractionDigits = digits
        format.maximumFractionDigits = digits
        return format.format(java.math.BigDecimal.valueOf(cents, digits))
    }

    /** 850 -> "850 m", 9800 -> "9.8 km", 24300 -> "24 km". */
    fun distance(meters: Long): String = when {
        meters < 1_000 -> "${(meters / 10.0).roundToLong() * 10} m"
        meters < 10_000 -> String.format(Locale.getDefault(), "%.1f km", meters / 1000.0)
        else -> "${(meters / 1000.0).roundToLong()} km"
    }

    /** 1680 -> "28 min", 4500 -> "1 h 15 min". Never shows "0 min". */
    fun duration(seconds: Long): String {
        val minutes = ((seconds + 59) / 60).coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    }

    /** Arrival clock time for a trip starting now, in the device's 12/24 h style. */
    fun arrival(durationSeconds: Long, now: Long = System.currentTimeMillis()): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(now + durationSeconds * 1000))
}
