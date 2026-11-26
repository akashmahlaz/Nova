package com.nova.driver.data

import android.content.Context

/** Small on-device choices: which maps app navigates, and whether new requests chime. */
object NavPrefs {
    private const val FILE = "nova_driver_prefs"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun app(context: Context): String = prefs(context).getString("nav_app", "google") ?: "google"
    fun setApp(context: Context, value: String) = prefs(context).edit().putString("nav_app", value).apply()
    fun chime(context: Context): Boolean = prefs(context).getBoolean("chime", true)
    fun setChime(context: Context, value: Boolean) = prefs(context).edit().putBoolean("chime", value).apply()
}
