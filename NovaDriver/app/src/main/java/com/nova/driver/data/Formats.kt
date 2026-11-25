package com.nova.driver.data

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Money from minor units in any ISO 4217 currency (digits come from the currency, not a guess). */
fun formatMoney(minor: Long, currency: String): String {
    val cur = runCatching { Currency.getInstance(currency) }.getOrNull() ?: return "$currency ${"%.2f".format(minor / 100.0)}"
    val digits = cur.defaultFractionDigits.coerceAtLeast(0)
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        this.currency = cur
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }
    var divisor = 1.0
    repeat(digits) { divisor *= 10 }
    return format.format(minor / divisor)
}

fun formatKm(meters: Number): String {
    val km = meters.toDouble() / 1000
    return if (km < 10) "%.1f km".format(km) else "%.0f km".format(km)
}

fun formatMinutes(seconds: Long): String = "${(seconds + 59) / 60} min"

/** Phone numbers in the driver's own country (SIM first, then network, then locale). */
object Phones {
    private val util = PhoneNumberUtil.getInstance()

    fun region(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .map { it.uppercase() }
            .firstOrNull { it.length == 2 && util.getCountryCodeForRegion(it) != 0 } ?: "NZ"
    }

    /** "+64" for NZ. */
    fun prefix(region: String): String = "+${util.getCountryCodeForRegion(region)}"

    /** E.164 for what the driver typed, or null when it isn't a valid number in [region]. */
    fun toE164(region: String, input: String): String? = runCatching {
        val number = util.parse(input, region)
        if (util.isValidNumber(number)) util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164) else null
    }.getOrNull()
}
