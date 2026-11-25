package com.nova.rider.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
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
    val role: String = "rider",
    @SerialName("phone_verified_at") val phoneVerifiedAt: String? = null,
    @SerialName("email_verified_at") val emailVerifiedAt: String? = null,
)

@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: User,
)

@Serializable
data class SavedPlace(
    val id: String,
    val kind: String,
    val label: String,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null,
)

@Serializable
data class EmergencyContact(
    val id: String,
    val name: String,
    @SerialName("phone_e164") val phone: String,
    val relationship: String = "",
)

@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

/** One row in place search. Distance is straight-line from the rider, in metres. */
@Serializable
data class PlaceSuggestion(
    @SerialName("place_id") val placeId: String,
    @SerialName("primary_text") val primaryText: String,
    @SerialName("secondary_text") val secondaryText: String = "",
    @SerialName("distance_meters") val distanceMeters: Long? = null,
)

/** A resolved place with coordinates (from Place Details or reverse geocoding). */
@Serializable
data class GeoPlace(
    @SerialName("place_id") val placeId: String = "",
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
}

@Serializable
data class FareOption(
    @SerialName("product_id") val productId: String,
    val name: String,
    val seats: Int,
    val description: String = "",
    /** Price in the quote currency's minor units (cents). */
    @SerialName("fare_cents") val fareCents: Long,
)

@Serializable
data class RideQuote(
    @SerialName("quote_id") val quoteId: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("service_area") val serviceArea: String,
    val currency: String,
    @SerialName("distance_meters") val distanceMeters: Long,
    @SerialName("duration_seconds") val durationSeconds: Long,
    /** Google encoded polyline for the route line. */
    val polyline: String,
    @SerialName("surge_multiplier") val surgeMultiplier: Double = 1.0,
    val options: List<FareOption>,
)

@Serializable
data class RidePlace(val name: String, val address: String = "", val lat: Double, val lng: Double) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
}

@Serializable
data class RideStop(val name: String, val lat: Double, val lng: Double) {
    val point: GeoPoint get() = GeoPoint(lat, lng)
}

@Serializable
data class Passenger(val name: String, val phone: String)

/**
 * A booked ride as stored by the server. [status] is one of: scheduled, requested, accepted,
 * arriving, in_progress, completed, cancelled, no_drivers. Times are ISO-8601 UTC strings.
 */
@Serializable
data class Ride(
    val id: String,
    val status: String,
    @SerialName("product_id") val productId: String,
    @SerialName("product_name") val productName: String,
    val currency: String,
    @SerialName("fare_cents") val fareCents: Long,
    @SerialName("distance_meters") val distanceMeters: Long,
    @SerialName("duration_seconds") val durationSeconds: Long,
    val polyline: String = "",
    val pickup: RidePlace,
    val dropoff: RidePlace,
    val stops: List<RideStop> = emptyList(),
    @SerialName("payment_method") val paymentMethod: String,
    val passenger: Passenger? = null,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("requested_at") val requestedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("created_at") val createdAt: String,
) {
    /** Waiting for or riding with a driver (one at a time per rider). */
    val isActive: Boolean get() = status in setOf("requested", "accepted", "arriving", "in_progress")
}

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

    suspend fun signup(
        firstName: String,
        lastName: String,
        phoneProof: String? = null,
        emailProof: String? = null,
        phone: String? = null,
        email: String? = null,
    ): Session = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("first_name", firstName)
            put("last_name", lastName)
            phoneProof?.takeIf { it.isNotBlank() }?.let { put("phone_proof", it) }
            emailProof?.takeIf { it.isNotBlank() }?.let { put("email_proof", it) }
            phone?.takeIf { it.isNotBlank() }?.let { put("phone", it) }
            email?.takeIf { it.isNotBlank() }?.let { put("email", it) }
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
        (res["publicKey"]?.jsonObject ?: res).toString()
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
        val raw = res.getValue("options").jsonObject
        val options = (raw["publicKey"]?.jsonObject ?: raw).toString()
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

    // ---------- profile, saved places & emergency contacts ----------

    suspend fun updateProfile(firstName: String, lastName: String): User = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("first_name", firstName)
            put("last_name", lastName)
        }
        val res = requestAuth("PATCH", "/v1/me", body)
        json.decodeFromJsonElement(User.serializer(), res)
    }

    suspend fun verifyContact(otpProof: String): User = withContext(Dispatchers.IO) {
        val res = requestAuth("POST", "/v1/me/verify-contact", buildJsonObject { put("otp_proof", otpProof) })
        json.decodeFromJsonElement(User.serializer(), res)
    }

    suspend fun listPlaces(): List<SavedPlace> = withContext(Dispatchers.IO) {
        val res = requestAuthElement("GET", "/v1/places", null)
        json.decodeFromJsonElement(ListSerializer(SavedPlace.serializer()), res)
    }

    suspend fun savePlace(kind: String, label: String, address: String, lat: Double? = null, lng: Double? = null): SavedPlace = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("kind", kind)
            put("label", label)
            put("address", address)
            lat?.let { put("lat", it) }
            lng?.let { put("lng", it) }
        }
        val res = requestAuth("POST", "/v1/places", body)
        json.decodeFromJsonElement(SavedPlace.serializer(), res)
    }

    suspend fun updatePlace(id: String, label: String, address: String, lat: Double?, lng: Double?): SavedPlace = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("label", label)
            put("address", address)
            lat?.let { put("lat", it) }
            lng?.let { put("lng", it) }
        }
        json.decodeFromJsonElement(SavedPlace.serializer(), requestAuth("PATCH", "/v1/places/$id", body))
    }

    suspend fun deletePlace(id: String) = withContext(Dispatchers.IO) {
        requestAuthElement("DELETE", "/v1/places/$id", null)
        Unit
    }

    suspend fun listEmergencyContacts(): List<EmergencyContact> = withContext(Dispatchers.IO) {
        val res = requestAuthElement("GET", "/v1/emergency-contacts", null)
        json.decodeFromJsonElement(ListSerializer(EmergencyContact.serializer()), res)
    }

    suspend fun addEmergencyContact(name: String, phone: String, relationship: String = ""): EmergencyContact = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("name", name)
            put("phone", phone)
            put("relationship", relationship)
        }
        val res = requestAuth("POST", "/v1/emergency-contacts", body)
        json.decodeFromJsonElement(EmergencyContact.serializer(), res)
    }

    suspend fun deleteEmergencyContact(id: String) = withContext(Dispatchers.IO) {
        requestAuthElement("DELETE", "/v1/emergency-contacts/$id", null)
        Unit
    }

    // ---------- geo & pricing (server proxies Google; the Places/Routes key never ships in the app) ----------

    /** One call per debounced keystroke. Reuse [sessionToken] for the whole search, then drop it. */
    suspend fun autocomplete(input: String, sessionToken: String, near: GeoPoint?): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("input", input)
            put("session_token", sessionToken)
            near?.let { put("near", buildJsonObject { put("lat", it.lat); put("lng", it.lng) }) }
        }
        val res = requestAuth("POST", "/v1/geo/autocomplete", body)
        json.decodeFromJsonElement(ListSerializer(PlaceSuggestion.serializer()), res.getValue("suggestions"))
    }

    /** Resolves a suggestion to coordinates. Passing the same [sessionToken] closes Google's billing session. */
    suspend fun placeDetails(placeId: String, sessionToken: String?): GeoPlace = withContext(Dispatchers.IO) {
        val query = sessionToken?.let { "?session_token=${encode(it)}" }.orEmpty()
        json.decodeFromJsonElement(GeoPlace.serializer(), requestAuth("GET", "/v1/geo/places/${encode(placeId)}$query", null))
    }

    /** Street address under a map pin. */
    suspend fun reverseGeocode(at: GeoPoint): GeoPlace = withContext(Dispatchers.IO) {
        json.decodeFromJsonElement(GeoPlace.serializer(), requestAuth("GET", "/v1/geo/reverse?lat=${at.lat}&lng=${at.lng}", null))
    }

    /** Closest landmark places around [at], nearest first (zero-typing suggestions). */
    suspend fun nearby(at: GeoPoint): List<GeoPlace> = withContext(Dispatchers.IO) {
        val res = requestAuth("GET", "/v1/geo/nearby?lat=${at.lat}&lng=${at.lng}", null)
        json.decodeFromJsonElement(ListSerializer(GeoPlace.serializer()), res.getValue("places"))
    }

    /** Upfront prices for every ride type, locked server side for [RideQuote.expiresIn] seconds. */
    suspend fun quote(pickup: GeoPoint, dropoff: GeoPoint, stops: List<GeoPoint> = emptyList()): RideQuote = withContext(Dispatchers.IO) {
        fun GeoPoint.json() = buildJsonObject { put("lat", lat); put("lng", lng) }
        val body = buildJsonObject {
            put("pickup", pickup.json())
            put("dropoff", dropoff.json())
            put("stops", kotlinx.serialization.json.JsonArray(stops.map { it.json() }))
        }
        json.decodeFromJsonElement(RideQuote.serializer(), requestAuth("POST", "/v1/rides/quote", body))
    }

    // ---------- rides ----------

    /**
     * Books [productId] from a locked quote. The server takes price and route from the quote, so
     * only display names and choices are sent. [scheduledAt] (ISO-8601 UTC) books for later.
     */
    suspend fun bookRide(
        quoteId: String,
        productId: String,
        paymentMethod: String,
        pickup: GeoPlace,
        dropoff: GeoPlace,
        stopNames: List<String>,
        scheduledAt: String? = null,
        passenger: Passenger? = null,
    ): Ride = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("quote_id", quoteId)
            put("product_id", productId)
            put("payment_method", paymentMethod)
            put("pickup_name", pickup.name)
            put("pickup_address", pickup.address)
            put("dropoff_name", dropoff.name)
            put("dropoff_address", dropoff.address)
            put("stop_names", kotlinx.serialization.json.JsonArray(stopNames.map { kotlinx.serialization.json.JsonPrimitive(it) }))
            scheduledAt?.let { put("scheduled_at", it) }
            passenger?.let { put("passenger", buildJsonObject { put("name", it.name); put("phone", it.phone) }) }
        }
        json.decodeFromJsonElement(Ride.serializer(), requestAuth("POST", "/v1/rides", body))
    }

    suspend fun ride(id: String): Ride = withContext(Dispatchers.IO) {
        json.decodeFromJsonElement(Ride.serializer(), requestAuth("GET", "/v1/rides/${encode(id)}", null))
    }

    /** The ride in progress, if any (to resume after the app restarts). */
    suspend fun activeRide(): Ride? = withContext(Dispatchers.IO) {
        val res = requestAuth("GET", "/v1/rides/active", null)
        res["ride"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.let { json.decodeFromJsonElement(Ride.serializer(), it) }
    }

    /** Newest first. Pass the last ride's createdAt as [before] for the next page. */
    suspend fun rides(before: String? = null, limit: Int = 20): List<Ride> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("?limit=").append(limit)
            before?.let { append("&before=").append(encode(it)) }
        }
        val res = requestAuth("GET", "/v1/rides$query", null)
        json.decodeFromJsonElement(ListSerializer(Ride.serializer()), res.getValue("rides"))
    }

    suspend fun cancelRide(id: String, reason: String? = null): Ride = withContext(Dispatchers.IO) {
        val body = buildJsonObject { reason?.let { put("reason", it) } }
        json.decodeFromJsonElement(Ride.serializer(), requestAuth("POST", "/v1/rides/${encode(id)}/cancel", body))
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8)

    private fun JsonObject.toSession(): Session = json.decodeFromJsonElement(Session.serializer(), this)

    private fun requestAuth(method: String, path: String, body: JsonObject?): JsonObject =
        requestAuthElement(method, path, body).jsonObject

    /**
     * Authenticated request with automatic one-shot token refresh on 401.
     * Synchronized around refresh so concurrent expired requests don't race and revoke rotated tokens.
     */
    private fun requestAuthElement(method: String, path: String, body: JsonObject?): JsonElement {
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
            ).jsonObject.toSession()
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
        executeRequest(method, path, body, bearerToken = null).jsonObject

    private fun executeRequest(method: String, path: String, body: JsonObject?, bearerToken: String?): JsonElement {
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
            val parsed = if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
            if (code !in 200..299) {
                val error = runCatching { parsed.jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "http_$code"
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
