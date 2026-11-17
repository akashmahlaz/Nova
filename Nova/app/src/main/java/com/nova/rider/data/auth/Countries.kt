package com.nova.rider.data.auth

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import java.text.Collator
import java.util.Locale

/** A region the rider can pick. [dial] is the country calling code without "+". */
data class Country(val iso: String, val name: String, val dial: String)

/**
 * Every region and calling code comes from Google's libphonenumber metadata, so nothing is
 * hand-maintained. Names come from the platform's locale data, in the device language.
 */
object PhoneRegions {
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    /** All regions that have a calling code, sorted by localised name. */
    val all: List<Country> by lazy {
        val collator = Collator.getInstance()
        util.supportedRegions
            .mapNotNull { iso -> find(iso) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    /** Looks up one region by ISO 3166-1 alpha-2 code, or null if libphonenumber doesn't know it. */
    fun find(iso: String): Country? {
        val region = iso.uppercase(Locale.ROOT)
        if (!util.supportedRegions.contains(region)) return null
        val code = util.getCountryCodeForRegion(region)
        if (code == 0) return null
        return Country(region, displayName(region), code.toString())
    }

    /**
     * Builds the E.164 number ("+6421555123") from what the rider typed in [iso]'s national format.
     * Returns null when the number is not a possible number for that region.
     */
    fun toE164(iso: String, typed: String): String? {
        return try {
            val number = util.parse(typed, iso.uppercase(Locale.ROOT))
            if (util.isPossibleNumber(number)) util.format(number, PhoneNumberFormat.E164) else null
        } catch (_: NumberParseException) {
            null
        }
    }

    private fun displayName(iso: String): String =
        Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.getDefault()).ifBlank { iso }
}

/**
 * Starting region: SIM/network country, then the device region, then null (the rider picks one).
 * Neither lookup needs a runtime permission.
 */
fun detectCountry(context: Context): Country? {
    val telephony = context.getSystemService(TelephonyManager::class.java)
    return listOf(telephony?.networkCountryIso, telephony?.simCountryIso, Locale.getDefault().country)
        .asSequence()
        .mapNotNull { it?.takeIf(String::isNotBlank) }
        .firstNotNullOfOrNull { PhoneRegions.find(it) }
}
