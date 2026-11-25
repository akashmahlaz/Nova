package com.nova.driver.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nova.driver.data.api.Session
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** What the sign-up / log-in screens collected, kept in memory until the code is verified. */
data class SignInDraft(
    /** E.164, e.g. +64221234567. */
    val phone: String = "",
    /** "signup" creates the Nova account after the code; "login" signs into an existing one. */
    val purpose: String = "login",
    val firstName: String = "",
    val lastName: String = "",
    val destinationHint: String = "",
    val resendIn: Int = 30,
)

/**
 * The signed-in session. Tokens are encrypted with an Android Keystore AES-256-GCM key before
 * they touch disk, and `nova_driver_auth.xml` is excluded from backups and device transfer
 * (the key can't leave this phone, so a copied file would be useless anyway).
 */
object AuthStore {
    private const val PREFS = "nova_driver_auth"
    private const val KEY_SESSION = "session"
    private const val KEYSTORE_ALIAS = "nova_driver_auth_aes_key"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val ENC_PREFIX = "v1:"
    private val json = Json { ignoreUnknownKeys = true }

    private var prefs: SharedPreferences? = null

    var session: Session? by mutableStateOf(null)
        private set

    var draft: SignInDraft = SignInDraft()

    fun init(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = store
        session = store.getString(KEY_SESSION, null)?.let(::decrypt)?.let {
            runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull()
        }
    }

    fun saveSession(value: Session) {
        session = value
        val encrypted = encrypt(json.encodeToString(Session.serializer(), value)) ?: return
        prefs?.edit()?.putString(KEY_SESSION, encrypted)?.apply()
    }

    fun clearSession() {
        session = null
        draft = SignInDraft()
        prefs?.edit()?.remove(KEY_SESSION)?.apply()
        com.nova.driver.data.DriverStore.clear()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(AES_GCM).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        "$ENC_PREFIX$iv:" + Base64.encodeToString(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }.getOrNull()

    private fun decrypt(encoded: String): String? = runCatching {
        if (!encoded.startsWith(ENC_PREFIX)) return null
        val (iv, body) = encoded.removePrefix(ENC_PREFIX).split(':', limit = 2).takeIf { it.size == 2 } ?: return null
        val cipher = Cipher.getInstance(AES_GCM).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        }
        String(cipher.doFinal(Base64.decode(body, Base64.NO_WRAP)), Charsets.UTF_8)
    }.getOrNull()
}
