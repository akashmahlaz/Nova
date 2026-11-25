package com.nova.rider.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * Session and sign-up state for the rider app.
 * Session tokens are encrypted at rest with an Android Keystore AES-256-GCM key before storage,
 * and `nova_auth.xml` is excluded from cloud backup and device transfers.
 */
object AuthStore {
    private const val PREFS = "nova_auth"
    private const val KEY_SESSION = "session"
    private const val KEYSTORE_ALIAS = "nova_auth_aes_key"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val ENC_PREFIX = "v1:"

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
        session = loadSession(store)
        country = detectCountry(context)
    }

    fun selectCountry(value: Country) {
        country = value
    }

    fun saveSession(value: Session) {
        session = value
        val raw = Json.encodeToString(Session.serializer(), value)
        val encrypted = encrypt(raw) ?: return
        prefs?.edit()?.putString(KEY_SESSION, encrypted)?.apply()
    }

    fun updateUser(user: User) {
        val current = session ?: return
        saveSession(current.copy(user = user))
    }

    fun clearSession() {
        session = null
        prefs?.edit()?.remove(KEY_SESSION)?.apply()
        com.nova.rider.data.profile.ProfileStore.clear()
        com.nova.rider.data.trip.TripStore.clear()
        com.nova.rider.data.settings.SettingsStore.clearAccountData()
        com.nova.rider.data.contacts.ContactsStore.clear()
    }

    private fun loadSession(store: SharedPreferences): Session? {
        val stored = store.getString(KEY_SESSION, null) ?: return null
        val jsonText = if (stored.startsWith(ENC_PREFIX)) {
            decrypt(stored) ?: return null
        } else {
            // Legacy plaintext session: migrate to encrypted form immediately.
            encrypt(stored)?.let { store.edit().putString(KEY_SESSION, it).apply() }
            stored
        }
        return runCatching { Json.decodeFromString(Session.serializer(), jsonText) }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        "$ENC_PREFIX$iv:$body"
    }.getOrNull()

    private fun decrypt(encoded: String): String? = runCatching {
        val parts = encoded.removePrefix(ENC_PREFIX).split(':', limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrNull()
}
