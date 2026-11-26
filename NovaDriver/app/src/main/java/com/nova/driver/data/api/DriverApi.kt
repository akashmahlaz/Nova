package com.nova.driver.data.api

import com.nova.driver.BuildConfig
import com.nova.driver.data.auth.AuthStore
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

/** Debug: this Mac's dev server. Release: production (see app/build.gradle.kts). */
const val API_BASE_URL = BuildConfig.API_BASE_URL

// ---------- models (field names match server/src/drivers.rs) ----------

@Serializable
data class User(
    val id: String,
    @SerialName("phone_e164") val phone: String? = null,
    val email: String? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String = "",
    @SerialName("created_at") val createdAt: String? = null,
) {
    val initials: String
        get() = listOf(firstName, lastName).mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }.joinToString("").ifEmpty { "N" }
}

@Serializable
data class Session(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val user: User,
)

/** status: pending (in review), approved (can drive), suspended. */
@Serializable
data class DriverProfile(
    val status: String,
    @SerialName("licence_number") val licenceNumber: String,
    @SerialName("licence_expiry") val licenceExpiry: String? = null,
    @SerialName("approved_at") val approvedAt: String? = null,
    /** Admin's message when the application was sent back or the account paused. */
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class Vehicle(
    val id: String,
    val make: String,
    val model: String,
    val color: String,
    val plate: String,
    val year: Int,
    val seats: Int,
    @SerialName("is_electric") val isElectric: Boolean = false,
    @SerialName("wheelchair_accessible") val wheelchairAccessible: Boolean = false,
) {
    val label: String get() = "$color $make $model"
}

@Serializable
data class DriverProduct(@SerialName("product_id") val productId: String, val name: String, val enabled: Boolean)

@Serializable
data class DriverMe(
    val user: User,
    val driver: DriverProfile? = null,
    val vehicle: Vehicle? = null,
    val products: List<DriverProduct> = emptyList(),
    val online: Boolean = false,
    val documents: List<DriverDocument> = emptyList(),
    val payout: PayoutSummary? = null,
) {
    /** Every required document is uploaded (in review) or verified. */
    val documentsReady: Boolean get() = documents.isNotEmpty() && documents.all { !it.required || it.status == "uploaded" || it.status == "verified" }
}

/** A required document and the latest upload of it. [status]: null, uploaded, verified, rejected. */
@Serializable
data class DriverDocument(
    val category: String,
    val name: String,
    val description: String = "",
    val required: Boolean = true,
    @SerialName("media_id") val mediaId: String? = null,
    val status: String? = null,
    @SerialName("rejection_reason") val rejectionReason: String? = null,
    @SerialName("uploaded_at") val uploadedAt: String? = null,
)

@Serializable
data class PayoutSummary(
    @SerialName("account_name") val accountName: String,
    @SerialName("account_last4") val accountLast4: String,
    @SerialName("gst_registered") val gstRegistered: Boolean = false,
    @SerialName("payout_schedule") val payoutSchedule: String = "daily",
)

@Serializable
data class DriverTrip(
    val id: String,
    val status: String,
    @SerialName("product_name") val productName: String,
    val currency: String,
    @SerialName("fare_cents") val fareCents: Long,
    @SerialName("distance_meters") val distanceMeters: Long,
    @SerialName("pickup_name") val pickupName: String,
    @SerialName("dropoff_name") val dropoffName: String,
    @SerialName("rider_name") val riderName: String,
    @SerialName("cancel_reason") val cancelReason: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
)

@Serializable
data class EarningPoint(@SerialName("completed_at") val completedAt: String, @SerialName("fare_cents") val fareCents: Long, val currency: String)

@Serializable
data class Earnings(
    val currency: String? = null,
    @SerialName("total_cents") val totalCents: Long = 0,
    val trips: Int = 0,
    @SerialName("online_seconds") val onlineSeconds: Long = 0,
    val rides: List<EarningPoint> = emptyList(),
)

/** Inbox entry (same inbox as the rider app: one Nova account). */
@Serializable
data class AppNotification(
    val id: String,
    val kind: String,
    val event: String,
    val title: String,
    val body: String = "",
    @SerialName("read_at") val readAt: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
private data class NotificationsRes(val notifications: List<AppNotification> = emptyList(), val unread: Int = 0)

@Serializable
private data class PresignRes(@SerialName("media_id") val mediaId: String, @SerialName("upload_url") val uploadUrl: String)

/** What a car needs for one ride type (server table ride_products). */
@Serializable
data class ProductRule(
    val id: String,
    val name: String,
    val seats: Int,
    @SerialName("requires_electric") val requiresElectric: Boolean = false,
    @SerialName("max_vehicle_age_years") val maxVehicleAgeYears: Int? = null,
) {
    fun allows(seats: Int, electric: Boolean, year: Int, thisYear: Int): Boolean =
        seats >= this.seats && (!requiresElectric || electric) && (maxVehicleAgeYears == null || year >= thisYear - maxVehicleAgeYears)
}

@Serializable
data class Place(val name: String, val address: String = "", val lat: Double, val lng: Double)

@Serializable
data class RideStop(val name: String, val lat: Double, val lng: Double)

/** A ride offer waiting for the driver's answer. Money is in [currency] minor units. */
@Serializable
data class Offer(
    @SerialName("offer_id") val offerId: String,
    @SerialName("ride_id") val rideId: String,
    @SerialName("expires_in_ms") val expiresInMs: Long,
    @SerialName("product_name") val productName: String,
    val currency: String,
    @SerialName("fare_cents") val fareCents: Long,
    @SerialName("pickup_distance_meters") val pickupDistanceMeters: Int,
    @SerialName("distance_meters") val distanceMeters: Long,
    @SerialName("duration_seconds") val durationSeconds: Long,
    val polyline: String = "",
    val pickup: Place,
    val dropoff: Place,
    @SerialName("stop_count") val stopCount: Int = 0,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("rider_name") val riderName: String,
)

/** status: accepted (driving to pickup), arriving (at pickup), in_progress, completed, cancelled. */
@Serializable
data class DriverRide(
    val id: String,
    val status: String,
    @SerialName("product_name") val productName: String,
    val currency: String,
    @SerialName("fare_cents") val fareCents: Long,
    @SerialName("distance_meters") val distanceMeters: Long,
    @SerialName("duration_seconds") val durationSeconds: Long,
    val polyline: String = "",
    val pickup: Place,
    val dropoff: Place,
    val stops: List<RideStop> = emptyList(),
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("rider_name") val riderName: String,
    @SerialName("rider_phone") val riderPhone: String? = null,
    @SerialName("booked_by") val bookedBy: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null,
    @SerialName("arrived_at") val arrivedAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
) {
    val riderInitials: String get() = riderName.trim().split(' ').mapNotNull { it.firstOrNull()?.uppercaseChar() }.take(2).joinToString("").ifEmpty { "R" }
}

@Serializable
data class DriverState(val online: Boolean = false, val offer: Offer? = null, val ride: DriverRide? = null)

/** Stable error code from the server (`{"error": "..."}`), plus the retry hint when rate limited. */
class ApiException(val code: String, val retryAfterSeconds: Int? = null) : Exception(code)

data class SendOtpResult(val destinationHint: String, val resendIn: Int)

sealed interface VerifyResult {
    data class Proof(val token: String) : VerifyResult
    data class SignedIn(val session: Session) : VerifyResult
}

/** HTTP client for the Nova API (same server and accounts as the rider app). */
object DriverApi {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    // ---------- sign-in (shared Nova accounts) ----------

    suspend fun sendOtp(phone: String, purpose: String): SendOtpResult = io {
        val res = request("POST", "/v1/auth/otp/send", buildJsonObject {
            put("channel", "phone"); put("destination", phone); put("purpose", purpose)
        }, auth = false).jsonObject
        SendOtpResult(res.str("destination_hint"), res.getValue("resend_in").jsonPrimitive.content.toInt())
    }

    suspend fun verifyOtp(phone: String, purpose: String, code: String): VerifyResult = io {
        val res = request("POST", "/v1/auth/otp/verify", buildJsonObject {
            put("channel", "phone"); put("destination", phone); put("purpose", purpose); put("code", code)
        }, auth = false).jsonObject
        if (res.containsKey("otp_proof")) VerifyResult.Proof(res.str("otp_proof")) else VerifyResult.SignedIn(res.decode(Session.serializer()))
    }

    suspend fun signup(firstName: String, lastName: String, phone: String, phoneProof: String): Session = io {
        request("POST", "/v1/auth/signup", buildJsonObject {
            put("first_name", firstName); put("last_name", lastName); put("phone", phone); put("phone_proof", phoneProof); put("role", "driver")
        }, auth = false).jsonObject.decode(Session.serializer())
    }

    suspend fun logout(refreshToken: String) = io {
        request("POST", "/v1/auth/logout", buildJsonObject { put("refresh_token", refreshToken) }, auth = false)
        Unit
    }

    // ---------- driver account ----------

    suspend fun me(): DriverMe = io { request("GET", "/v1/driver/me").decode(DriverMe.serializer()) }

    suspend fun apply(licenceNumber: String, licenceExpiry: String?): DriverMe = io {
        request("POST", "/v1/driver/apply", buildJsonObject {
            put("licence_number", licenceNumber)
            licenceExpiry?.let { put("licence_expiry", it) }
        }).decode(DriverMe.serializer())
    }

    suspend fun setVehicle(make: String, model: String, color: String, plate: String, year: Int, seats: Int, electric: Boolean, wheelchair: Boolean): DriverMe = io {
        request("PUT", "/v1/driver/vehicle", buildJsonObject {
            put("make", make); put("model", model); put("color", color); put("plate", plate)
            put("year", year); put("seats", seats); put("is_electric", electric); put("wheelchair_accessible", wheelchair)
        }).decode(DriverMe.serializer())
    }

    /** Sends the application for review (server checks car, documents and payout are all in). */
    suspend fun submit(): DriverMe = io { request("POST", "/v1/driver/submit").decode(DriverMe.serializer()) }

    suspend fun setPayout(accountName: String, accountNumber: String, taxNumber: String, gst: Boolean, schedule: String): DriverMe = io {
        request("PUT", "/v1/driver/payout", buildJsonObject {
            put("account_name", accountName); put("account_number", accountNumber); put("tax_number", taxNumber)
            put("gst_registered", gst); put("payout_schedule", schedule)
        }).decode(DriverMe.serializer())
    }

    suspend fun setProduct(productId: String, enabled: Boolean): DriverMe = io {
        request("PUT", "/v1/driver/products", buildJsonObject { put("product_id", productId); put("enabled", enabled) }).decode(DriverMe.serializer())
    }

    suspend fun trips(from: String? = null, to: String? = null, limit: Int = 50): List<DriverTrip> = io {
        val q = listOfNotNull(from?.let { "from=${enc(it)}" }, to?.let { "to=${enc(it)}" }, "limit=$limit").joinToString("&")
        json.decodeFromJsonElement(ListSerializer(DriverTrip.serializer()), request("GET", "/v1/driver/trips?$q"))
    }

    suspend fun earnings(from: String, to: String): Earnings = io {
        request("GET", "/v1/driver/earnings?from=${enc(from)}&to=${enc(to)}").decode(Earnings.serializer())
    }

    suspend fun notifications(): Pair<List<AppNotification>, Int> = io {
        val res = request("GET", "/v1/notifications?limit=50").decode(NotificationsRes.serializer())
        res.notifications to res.unread
    }

    suspend fun markAllRead() = io { request("POST", "/v1/notifications/read-all"); Unit }

    /**
     * Uploads one document photo straight to S3: the server hands out a 5-minute signed PUT URL
     * (the file never passes through our API), then confirms the object landed.
     */
    suspend fun uploadDocument(category: String, bytes: ByteArray, contentType: String, fileName: String) = io {
        val presign = request("POST", "/v1/media/presign-upload", buildJsonObject {
            put("category", category); put("content_type", contentType); put("file_name", fileName)
        }).decode(PresignRes.serializer())
        val conn = (URL(presign.uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", contentType)
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            if (conn.responseCode !in 200..299) throw ApiException("upload_failed")
        } catch (e: IOException) {
            throw ApiException("network_error")
        } finally {
            conn.disconnect()
        }
        request("POST", "/v1/media/${presign.mediaId}/confirm")
        Unit
    }

    private fun enc(v: String) = java.net.URLEncoder.encode(v, Charsets.UTF_8)

    suspend fun productRules(): List<ProductRule> = io {
        json.decodeFromJsonElement(ListSerializer(ProductRule.serializer()), request("GET", "/v1/driver/products", auth = false))
    }

    // ---------- shift ----------

    suspend fun state(): DriverState = io { request("GET", "/v1/driver/state").decode(DriverState.serializer()) }

    suspend fun goOnline(lat: Double, lng: Double): DriverState = io {
        request("POST", "/v1/driver/online", latLng(lat, lng)).decode(DriverState.serializer())
    }

    suspend fun goOffline(): DriverState = io { request("POST", "/v1/driver/offline").decode(DriverState.serializer()) }

    /** Heartbeat + position. Returns whether the server still has this driver online. */
    suspend fun location(lat: Double, lng: Double): Boolean = io {
        request("POST", "/v1/driver/location", latLng(lat, lng)).jsonObject["online"]?.jsonPrimitive?.content == "true"
    }

    suspend fun accept(offerId: String): DriverRide = io { request("POST", "/v1/driver/offers/$offerId/accept").decode(DriverRide.serializer()) }

    suspend fun decline(offerId: String): DriverState = io { request("POST", "/v1/driver/offers/$offerId/decline").decode(DriverState.serializer()) }

    suspend fun arrived(rideId: String): DriverRide = io { request("POST", "/v1/driver/rides/$rideId/arrived").decode(DriverRide.serializer()) }

    suspend fun start(rideId: String, pin: String): DriverRide = io {
        request("POST", "/v1/driver/rides/$rideId/start", buildJsonObject { put("pin", pin) }).decode(DriverRide.serializer())
    }

    suspend fun complete(rideId: String): DriverRide = io { request("POST", "/v1/driver/rides/$rideId/complete").decode(DriverRide.serializer()) }

    suspend fun cancel(rideId: String, reason: String): DriverState = io {
        request("POST", "/v1/driver/rides/$rideId/cancel", buildJsonObject { put("reason", reason) }).decode(DriverState.serializer())
    }

    // ---------- signed S3 media ----------

    @Serializable
    data class PresignUploadRes(
        @SerialName("media_id") val mediaId: String,
        val bucket: String,
        @SerialName("object_key") val objectKey: String,
        @SerialName("upload_url") val uploadUrl: String,
        @SerialName("expires_in") val expiresIn: Long,
    )

    @Serializable
    data class MediaItem(
        val id: String,
        @SerialName("object_key") val objectKey: String,
        val category: String,
        @SerialName("file_name") val fileName: String,
        @SerialName("content_type") val contentType: String,
        @SerialName("size_bytes") val sizeBytes: Long,
        val status: String,
        @SerialName("rejection_reason") val rejectionReason: String? = null,
        @SerialName("view_url") val viewUrl: String,
        @SerialName("created_at") val createdAt: String,
    )

    suspend fun uploadMedia(
        category: String,
        contentType: String,
        fileName: String,
        bytes: ByteArray,
    ): MediaItem = io {
        val presign = request(
            "POST",
            "/v1/media/presign-upload",
            buildJsonObject {
                put("category", category)
                put("content_type", contentType)
                put("file_name", fileName)
            },
        ).decode(PresignUploadRes.serializer())

        val conn = (URL(presign.uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            setFixedLengthStreamingMode(bytes.size)
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            if (conn.responseCode !in 200..299) {
                throw ApiException("s3_upload_failed_${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
        request("POST", "/v1/media/${presign.mediaId}/confirm").decode(MediaItem.serializer())
    }

    suspend fun listMyMedia(): List<MediaItem> = io {
        json.decodeFromJsonElement(ListSerializer(MediaItem.serializer()), request("GET", "/v1/media/mine"))
    }

    // ---------- plumbing ----------

    private fun latLng(lat: Double, lng: Double) = buildJsonObject { put("lat", lat); put("lng", lng) }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun <T> JsonElement.decode(serializer: kotlinx.serialization.KSerializer<T>): T = json.decodeFromJsonElement(serializer, this)

    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

    /** Authenticated calls refresh the access token once on 401 (synchronized, so rotation can't race). */
    private fun request(method: String, path: String, body: JsonObject? = null, auth: Boolean = true): JsonElement {
        if (!auth) return execute(method, path, body, null)
        val current = AuthStore.session ?: throw ApiException("unauthorized")
        return try {
            execute(method, path, body, current.accessToken)
        } catch (e: ApiException) {
            if (e.code != "unauthorized" && e.code != "http_401") throw e
            execute(method, path, body, refreshLocked(current).accessToken)
        }
    }

    /** A fresh access token (used by the live socket too). */
    fun freshAccessToken(): String? {
        val current = AuthStore.session ?: return null
        return runCatching { refreshLocked(current).accessToken }.getOrNull()
    }

    private fun refreshLocked(expired: Session): Session = synchronized(this) {
        val latest = AuthStore.session
        if (latest != null && latest.accessToken != expired.accessToken) return latest
        try {
            val next = execute("POST", "/v1/auth/refresh", buildJsonObject { put("refresh_token", expired.refreshToken) }, null)
                .decode(Session.serializer())
            AuthStore.saveSession(next)
            next
        } catch (e: ApiException) {
            if (e.code == "unauthorized" || e.code == "http_401") AuthStore.clearSession()
            throw e
        }
    }

    private fun execute(method: String, path: String, body: JsonObject?, bearer: String?): JsonElement {
        val conn = (URL(API_BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        try {
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val parsed = if (text.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(text)
            if (code !in 200..299) {
                val error = runCatching { parsed.jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: "http_$code"
                throw ApiException(error, conn.getHeaderField("Retry-After")?.toIntOrNull())
            }
            return parsed
        } catch (e: IOException) {
            throw ApiException("network_error")
        } finally {
            conn.disconnect()
        }
    }
}

/** Short, human messages for server error codes. */
fun errorMessage(error: Throwable): String = when ((error as? ApiException)?.code) {
    "invalid_phone" -> "Enter a valid mobile number."
    "invalid_code" -> "That code isn't right. Check it and try again."
    "code_expired" -> "That code has expired. Send a new one."
    "code_not_found" -> "Send a new code to continue."
    "too_many_attempts" -> "Too many wrong codes. Send a new one."
    "account_not_found" -> "No Nova account for this number yet. Sign up to drive."
    "already_registered" -> "This number already has a Nova account. If you drive with Nova, sign in instead. A rider account can't be used to drive, so use another number."
    "not_driver_account" -> "This number is a Nova rider account. One account can have only one role, so use a different number to drive."
    "too_many_requests" -> (error as ApiException).retryAfterSeconds?.let { "Please wait ${it}s and try again." } ?: "Too many tries. Wait a moment."
    "sms_not_configured", "sms_failed" -> "We couldn't send the code. Try again shortly."
    "phone_required" -> "Add a verified mobile number to drive."
    "invalid_licence" -> "Check your licence number (4–20 letters or digits)."
    "licence_expired" -> "That licence has expired."
    "invalid_vehicle" -> "Fill in the car's make, model and colour."
    "invalid_plate" -> "Check the plate (2–10 letters or digits)."
    "invalid_year" -> "Check the car's year."
    "plate_in_use" -> "That plate is already registered to another driver."
    "not_a_driver" -> "Finish your driver application first."
    "driver_pending" -> "Your application is still in review."
    "driver_suspended" -> "Your account is paused. Contact driver support."
    "vehicle_required" -> "Add your car before going online."
    "no_ride_types" -> "Turn on at least one ride type."
    "invalid_location" -> "We couldn't get your location."
    "offer_expired" -> "That request expired."
    "ride_unavailable" -> "The rider cancelled that request."
    "ride_in_progress" -> "Finish your current trip first."
    "ride_cancelled" -> "The rider cancelled this trip."
    "invalid_ride_state" -> "This trip has moved on. Refreshing…"
    "no_show_too_early" -> "Wait the full free time at the pickup first."
    "documents_missing" -> "Upload every required document first."
    "payout_required" -> "Add your bank details first."
    "invalid_account_name" -> "Enter the name on the bank account."
    "invalid_account_number" -> "Check the account number."
    "invalid_tax_number" -> "Check your tax number."
    "invalid_content_type" -> "Use a photo (JPG or PNG) or a PDF."
    "upload_failed" -> "The upload didn't finish. Try again."
    "s3_unavailable" -> "Document uploads are unavailable right now. Try again shortly."
    "wrong_pin" -> "That PIN doesn't match. Ask the rider to check their app."
    "unauthorized" -> "Please sign in again."
    "network_error" -> "No connection. Check your internet and try again."
    else -> "Something went wrong. Try again."
}
