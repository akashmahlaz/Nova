package com.nova.rider.data.auth

import android.content.Context
import android.content.MutableContextWrapper
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential

/**
 * Official Android Credential Manager wrapper for passkeys.
 *
 * Uses a [MutableContextWrapper] around the foreground Activity [Context] per Android's official
 * Credential Manager guide (`developer.android.com/identity/passkeys/sign-in-with-passkeys`) so
 * the system biometric / passkey sheet launches on the Activity stack without leaking it.
 */
class PasskeyManager(context: Context) {
    private val activityContext = MutableContextWrapper(context)
    private val manager = CredentialManager.create(activityContext)

    /**
     * Creates a new passkey for the signed-in user. [optionsJson] is the unwrapped `publicKey`
     * `PublicKeyCredentialCreationOptions` JSON from the server.
     */
    suspend fun createPasskey(optionsJson: String): String {
        val request = CreatePublicKeyCredentialRequest(requestJson = optionsJson)
        val result = manager.createCredential(
            context = activityContext,
            request = request,
        )
        val response = result as CreatePublicKeyCredentialResponse
        return response.registrationResponseJson
    }

    /**
     * Signs in with an existing passkey. [optionsJson] is the unwrapped `publicKey`
     * `PublicKeyCredentialRequestOptions` JSON from the server.
     */
    suspend fun getPasskey(optionsJson: String): String {
        val option = GetPublicKeyCredentialOption(
            requestJson = optionsJson,
            clientDataHash = null,
        )
        val request = GetCredentialRequest(
            credentialOptions = listOf(option),
        )
        val result = manager.getCredential(
            context = activityContext,
            request = request,
        )
        val credential = result.credential as PublicKeyCredential
        return credential.authenticationResponseJson
    }
}
