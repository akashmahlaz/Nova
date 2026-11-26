package com.nova.driver.data.auth

import android.content.Context
import android.telephony.TelephonyManager
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import java.text.Collator
import java.util.Locale

fun isoToFlagEmoji(iso: String): String {
    if (iso.length != 2) return "🌐"
    val upper = iso.uppercase(Locale.ROOT)
    val first = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

data class Country(val iso: String, val name: String, val dial: String) {
    val flag: String get() = isoToFlagEmoji(iso)
}

/**
 * Every region and calling code comes from Google's libphonenumber metadata, so nothing is
 * hand-maintained. Names come from the platform's locale data in the device language.
 */
object PhoneRegions {
    private val util: PhoneNumberUtil = PhoneNumberUtil.getInstance()

    val all: List<Country> by lazy {
        val collator = Collator.getInstance()
        util.supportedRegions
            .mapNotNull { iso -> find(iso) }
            .sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    fun find(iso: String): Country? {
        val region = iso.uppercase(Locale.ROOT)
        if (!util.supportedRegions.contains(region)) return null
        val code = util.getCountryCodeForRegion(region)
        if (code == 0) return null
        return Country(region, displayName(region), code.toString())
    }

    fun toE164(iso: String, typed: String): String? {
        return try {
            val number = util.parse(typed, iso.uppercase(Locale.ROOT))
            if (util.isValidNumber(number)) util.format(number, PhoneNumberFormat.E164) else null
        } catch (_: NumberParseException) {
            null
        }
    }

    fun exampleNumber(iso: String): String? {
        val region = iso.uppercase(Locale.ROOT)
        val example = util.getExampleNumberForType(region, PhoneNumberUtil.PhoneNumberType.MOBILE)
            ?: util.getExampleNumber(region)
            ?: return null
        return util.format(example, PhoneNumberFormat.NATIONAL)
    }

    fun defaultRegion(context: Context): Country {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOfNotNull(
            tm?.simCountryIso,
            tm?.networkCountryIso,
            Locale.getDefault().country,
        )
        return candidates.firstNotNullOfOrNull { find(it) }
            ?: find("IN")
            ?: find("US")
            ?: all.first()
    }

    private fun displayName(iso: String): String =
        Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.getDefault()).ifBlank { iso }
}
