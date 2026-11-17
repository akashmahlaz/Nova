package com.nova.rider.data.auth

/** Maps server error codes to the short messages shown under the auth fields. */
fun authErrorMessage(error: Throwable): String {
    val apiEx = error as? ApiException ?: return "Something went wrong. Try again."
    return when (apiEx.code) {
        "invalid_phone" -> "Enter a valid mobile number."
        "invalid_email" -> "Enter a valid email address."
        "invalid_code" -> "That code isn't right. Check it and try again."
        "code_expired" -> "That code has expired. Send a new one."
        "code_not_found" -> "Send a new code to continue."
        "too_many_attempts" -> "Too many wrong codes. Send a new one."
        "already_registered" -> "An account already exists for this. Log in instead."
        "account_not_found" -> "No Nova account for this. Create an account first."
        "too_many_requests" -> {
            val secs = apiEx.retryAfterSeconds
            if (secs != null && secs > 0) "Please wait ${secs}s before requesting a new code."
            else "Too many requests. Please wait a moment and try again."
        }
        "sms_not_configured", "email_not_configured", "sms_failed", "email_failed" -> "We couldn't send the code. Try again shortly."
        "network_error" -> "No connection. Check your internet and try again."
        else -> "Something went wrong. Try again."
    }
}
