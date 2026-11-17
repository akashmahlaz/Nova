package com.nova.rider.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json

/** Details collected during sign-up, kept in memory until the account is created. */
data class SignupDraft(
    val phone: String = "",
    val phoneProof: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val emailProof: String = "",
)

/**
 * Session and sign-up state for the rider app. Tokens are persisted in private SharedPreferences.
 * TODO(security): move tokens to an encrypted store (Android Keystore-backed DataStore) before release.
 */
object AuthStore {
    private const val PREFS = "nova_auth"
    private const val KEY_SESSION = "session"

    private var prefs: SharedPreferences? = null

    /** Last session saved on this device, or null when signed out. */
    var session: Session? by mutableStateOf(null)
        private set

    /** Region for the phone prefix. Auto-detected on first open; null until the rider picks one if nothing was detected. */
    var country: Country? by mutableStateOf(null)
        private set

    var draft: SignupDraft = SignupDraft()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        session = store.getString(KEY_SESSION, null)?.let { runCatching { Json.decodeFromString(Session.serializer(), it) }.getOrNull() }
        country = detectCountry(context)
    }

    fun selectCountry(value: Country) {
        country = value
    }

    fun saveSession(value: Session) {
        session = value
        prefs?.edit()?.putString(KEY_SESSION, Json.encodeToString(Session.serializer(), value))?.apply()
    }

    fun clearSession() {
        session = null
        prefs?.edit()?.remove(KEY_SESSION)?.apply()
    }
}
