package com.nova.rider.data.auth

import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential

/**
 * Thin wrapper over the Android Credential Manager for passkeys.
 *
 * The server hands us a JSON options object (from the passkey register/login options endpoints);
 * we pass it to Credential Manager, which shows the system bottom sheet (fingerprint / screen lock
 * / synced passkeys from Google Password Manager). The returned credential JSON is sent back to the
 * server for verification.
 */
class PasskeyManager(context: Context) {
    private val appContext = context.applicationContext
    private val manager = CredentialManager.create(appContext)

    /**
     * Creates a new passkey for the signed-in user. [optionsJson] is the `CreationChallengeResponse`
     * from the server. Returns the `PublicKeyCredential` JSON to send to `/register/verify`.
     */
    suspend fun createPasskey(optionsJson: String): String {
        val request = CreatePublicKeyCredentialRequest(optionsJson)
        val result = manager.createCredential(appContext, request)
        val response = result as CreatePublicKeyCredentialResponse
        return response.registrationResponseJson
    }

    /**
     * Signs in with an existing passkey. [optionsJson] is the `RequestChallengeResponse` from the
     * server. Returns the `PublicKeyCredential` JSON to send to `/login/verify`.
     */
    suspend fun getPasskey(optionsJson: String): String {
        val option = GetPublicKeyCredentialOption(optionsJson)
        val request = GetCredentialRequest(
            credentialOptions = listOf(option),
        )
        val result = manager.getCredential(appContext, request)
        val response = result as GetCredentialResponse
        val credential = response.credential as PublicKeyCredential
        return credential.authenticationResponseJson
    }
}