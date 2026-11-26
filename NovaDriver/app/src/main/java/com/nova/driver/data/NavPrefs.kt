package com.nova.driver.data

import android.content.Context

/** Small on-device choices: maps app, request chime, chat read-aloud and voice guidance. */
object NavPrefs {
    private const val FILE = "nova_driver_prefs"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun app(context: Context): String = prefs(context).getString("nav_app", "google") ?: "google"
    fun setApp(context: Context, value: String) = prefs(context).edit().putString("nav_app", value).apply()
    fun chime(context: Context): Boolean = prefs(context).getBoolean("chime", true)
    fun setChime(context: Context, value: Boolean) = prefs(context).edit().putBoolean("chime", value).apply()
    /** Chat: read the rider's new messages aloud while driving. */
    fun readAloud(context: Context): Boolean = prefs(context).getBoolean("read_aloud", true)
    fun setReadAloud(context: Context, value: Boolean) = prefs(context).edit().putBoolean("read_aloud", value).apply()
    /** Spoken turn-by-turn instructions on the pickup and trip maps. */
    fun voice(context: Context): Boolean = prefs(context).getBoolean("voice_guidance", true)
    fun setVoice(context: Context, value: Boolean) = prefs(context).edit().putBoolean("voice_guidance", value).apply()
}
