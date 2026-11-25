package com.nova.rider.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Backend base URL, from `nova.api.base.url` in local.properties (see app/build.gradle.kts). */
private const val API_BASE_URL = com.nova.rider.BuildConfig.API_BASE_URL

@Serializable
data class User(
    val id: String,
    @SerialName("phone_e164") val phone: String? = null,
    val email: String? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String = "",
)

@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: User,
)

data class SendOtpResult(val destinationHint: String, val resendIn: Int)

/** Sign-up verifies return a proof; login verifies return a session. */
sealed interface VerifyResult {
    data class Proof(val token: String) : VerifyResult
    data class SignedIn(val session: Session) : VerifyResult
}

/** Stable error code from the server (see `server/src/error.rs`) plus the retry hint when rate limited. */
class ApiException(val code: String, val retryAfterSeconds: Int? = null) : Exception(code)

/** Thin HTTP client for the Nova auth API. Blocking IO runs on [Dispatchers.IO]. */
object NovaApi {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendOtp(channel: String, destination: String, purpose: String): SendOtpResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("channel", channel)
            put("destination", destination)
            put("purpose", purpose)
        }
        val res = request("POST", "/v1/auth/otp/send", body)
        SendOtpResult(
            destinationHint = res.str("destination_hint"),
            resendIn = res.getValue("resend_in").jsonPrimitive.content.toInt(),
        )
    }

    suspend fun verifyOtp(channel: String, destination: String, purpose: String, code: String): VerifyResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("channel", channel)
            put("destination", destination)
            put("purpose", purpose)
            put("code", code)
        }
        val res = request("POST", "/v1/auth/otp/verify", body)
        if (res.containsKey("otp_proof")) {
            VerifyResult.Proof(res.str("otp_proof"))
        } else {
            VerifyResult.SignedIn(res.toSession())
        }
    }

    suspend fun signup(firstName: String, lastName: String, phoneProof: String, emailProof: String): Session = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("first_name", firstName)
            put("last_name", lastName)
            put("phone_proof", phoneProof)
            put("email_proof", emailProof)
        }
        request("POST", "/v1/auth/signup", body).toSession()
    }

    suspend fun refresh(refreshToken: String): Session = withContext(Dispatchers.IO) {
        request("POST", "/v1/auth/refresh", buildJsonObject { put("refresh_token", refreshToken) }).toSession()
    }

    suspend fun logout(refreshToken: String) = withContext(Dispatchers.IO) {
        request("POST", "/v1/auth/logout", buildJsonObject { put("refresh_token", refreshToken) })
        Unit
    }

    // ---------- passkeys ----------

    /** Authenticated: returns the WebAuthn creation challenge as a JSON string for Credential Manager. */
    suspend fun passkeyRegisterOptions(): String = withContext(Dispatchers.IO) {
        val res = requestAuth("POST", "/v1/auth/passkey/register/options", null)
        res.toString()
    }

    /** Authenticated: sends the credential produced by `createPasskey()` back to the server. */
    suspend fun passkeyRegisterVerify(credential: String) = withContext(Dispatchers.IO) {
        requestAuth(
            "POST",
            "/v1/auth/passkey/register/verify",
            buildJsonObject { put("credential", credential) },
        )
        Unit
    }

    /** Returns the assertion challenge plus the opaque state token the client must echo back. */
    suspend fun passkeyLoginOptions(): Pair<String, String> = withContext(Dispatchers.IO) {
        val res = request("POST", "/v1/auth/passkey/login/options", null)
        val options = res.getValue("options").toString()
        val token = res.str("state_token")
        options to token
    }

    /** Sends the assertion produced by `getPasskey()` back to the server; returns a session. */
    suspend fun passkeyLoginVerify(credential: String, stateToken: String): Session = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("credential", credential)
            put("state_token", stateToken)
        }
        request("POST", "/v1/auth/passkey/login/verify", body).toSession()
    }

    private fun JsonObject.toSession(): Session = json.decodeFromJsonElement(Session.serializer(), this)

    /**
     * Authenticated request with automatic one-shot token refresh on 401.
     * Synchronized around refresh so concurrent expired requests don't race and revoke rotated tokens.
     */
    private fun requestAuth(method: String, path: String, body: JsonObject?): JsonObject {
        val current = AuthStore.session
        return try {
            executeRequest(method, path, body, bearerToken = current?.accessToken)
        } catch (e: ApiException) {
            if ((e.code != "unauthorized" && e.code != "http_401") || current == null) throw e
            val refreshed = refreshSessionLocked(current)
            executeRequest(method, path, body, bearerToken = refreshed.accessToken)
        }
    }

    private fun refreshSessionLocked(expiredSession: Session): Session = synchronized(this) {
        val latest = AuthStore.session
        if (latest != null && latest.accessToken != expiredSession.accessToken) {
            return latest
        }
        return try {
            val next = executeRequest(
                "POST",
                "/v1/auth/refresh",
                buildJsonObject { put("refresh_token", expiredSession.refreshToken) },
                bearerToken = null,
            ).toSession()
            AuthStore.saveSession(next)
            next
        } catch (e: ApiException) {
            if (e.code == "http_401" || e.code == "unauthorized") {
                AuthStore.clearSession()
            }
            throw e
        }
    }

    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    private fun request(method: String, path: String, body: JsonObject?): JsonObject =
        executeRequest(method, path, body, bearerToken = null)

    private fun executeRequest(method: String, path: String, body: JsonObject?, bearerToken: String?): JsonObject {
        val fullUrl = API_BASE_URL + path
        android.util.Log.d("NovaApi", "--> $method $fullUrl body=$body")
        val conn = (URL(fullUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (bearerToken != null) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            }
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            android.util.Log.d("NovaApi", "<-- $code $fullUrl response: $text")
            val parsed = if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text).jsonObject
            if (code !in 200..299) {
                val error = parsed["error"]?.jsonPrimitive?.contentOrNull ?: "http_$code"
                throw ApiException(error, conn.getHeaderField("Retry-After")?.toIntOrNull())
            }
            return parsed
        } catch (e: IOException) {
            android.util.Log.e("NovaApi", "Network error connecting to $fullUrl", e)
            throw ApiException("network_error")
        } finally {
            conn.disconnect()
        }
    }
}
