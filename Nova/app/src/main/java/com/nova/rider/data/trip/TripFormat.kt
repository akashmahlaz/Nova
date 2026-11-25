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
    /**
     * Minor units + ISO 4217 code -> the rider's local style: 2840 + "NZD" -> "$28.40" on an
     * en-NZ phone, "NZ$28.40" elsewhere; 24500 + "INR" -> "₹245.00"; 2840 + "JPY" -> "¥2,840".
     * Symbol, separators and decimals all come from the platform's CLDR/ISO data, so no currency
     * is special-cased. A code the phone doesn't know is shown as "XYZ 28.40", never with the
     * phone's own currency symbol.
     */
    fun money(cents: Long, currency: String, locale: Locale = Locale.getDefault()): String {
        val known = runCatching { Currency.getInstance(currency) }.getOrNull()
        val digits = known?.defaultFractionDigits?.takeIf { it >= 0 } ?: 2
        val amount = java.math.BigDecimal.valueOf(cents, digits)
        if (known == null) {
            val plain = NumberFormat.getNumberInstance(locale).apply {
                minimumFractionDigits = digits
                maximumFractionDigits = digits
            }
            return "$currency ${plain.format(amount)}"
        }
        val format = NumberFormat.getCurrencyInstance(locale).apply {
            this.currency = known
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return format.format(amount)
    }

    /**
     * Road distance in the rider's units: miles where roads are signed in miles (US, UK, Liberia,
     * Myanmar, from the locale's measurement system), kilometres everywhere else.
     * 850 m -> "850 m" / "0.5 mi"; 9800 m -> "9.8 km" / "6.1 mi"; 24300 m -> "24 km" / "15 mi".
     */
    fun distance(meters: Long, locale: Locale = Locale.getDefault()): String {
        if (usesMiles(locale)) {
            val miles = meters / 1609.344
            return if (miles < 10) String.format(locale, "%.1f mi", miles) else "${miles.roundToLong()} mi"
        }
        return when {
            meters < 1_000 -> "${(meters / 10.0).roundToLong() * 10} m"
            meters < 10_000 -> String.format(locale, "%.1f km", meters / 1000.0)
            else -> "${(meters / 1000.0).roundToLong()} km"
        }
    }

    private fun usesMiles(locale: Locale): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val system = android.icu.util.LocaleData.getMeasurementSystem(android.icu.util.ULocale.forLocale(locale))
            // ICU's "UK" system is metric for most things but miles for roads.
            return system == android.icu.util.LocaleData.MeasurementSystem.US || system == android.icu.util.LocaleData.MeasurementSystem.UK
        }
        return locale.country in setOf("US", "GB", "LR", "MM")
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
