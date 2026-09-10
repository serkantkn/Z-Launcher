package com.serkantkn.zunelauncher.data.repository

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.CloudService
import com.serkantkn.zunelauncher.util.SecretStore
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Signing in to the cloud services, and keeping the tokens afterwards.
 *
 * Google goes through the Play services authorization API — the same door the Gmail hub already
 * uses — so the user picks a Google account, grants the Drive scope once and the launcher gets a
 * fresh access token whenever it needs one. Google Drive's own app does not have to be installed.
 *
 * No client secret is used or needed.
 */
class CloudAuthRepository(
    private val context: Context,
    private val secretStore: SecretStore
) {

    // --- Google ---------------------------------------------------------------------------------

    /** Starts (or silently repeats) the Drive authorization; the result may need UI. */
    fun googleAuthorizeTask(email: String? = null): Task<AuthorizationResult> {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(DRIVE_SCOPE))
            .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
            .build()
        return Identity.getAuthorizationClient(context.applicationContext).authorize(request)
    }

    fun googleResultFromIntent(data: Intent): AuthorizationResult =
        Identity.getAuthorizationClient(context.applicationContext).getAuthorizationResultFromIntent(data)

    /**
     * Which account signed in.
     *
     * The authorization result only carries a Google account object when the profile scopes were
     * part of the request, and the Drive scope alone is not enough — so the identity is read from
     * Drive itself ("about"), which every granted token can do. The result's own copy is used as a
     * fallback for the display name.
     */
    suspend fun googleAccountOf(result: AuthorizationResult): CloudAccount = withContext(Dispatchers.IO) {
        val token = result.accessToken ?: googleAnyAccessToken()
        val about = try {
            CloudHttp.getJson("$DRIVE_ABOUT?fields=user(displayName,emailAddress)", token)
        } catch (e: CloudException) {
            ZuneLog.e(TAG, "drive about failed while identifying the account", e)
            throw e
        }
        val user = about.optJSONObject("user")
        val email = user?.optString("emailAddress").orEmpty()
        if (email.isBlank()) throw CloudException(R.string.cloud_error_google_identity)
        val name = user?.optString("displayName").orEmpty().ifBlank { signInName(result) ?: email }
        ZuneLog.d(TAG, "google account signed in: $email")
        CloudAccount(
            id = "${CloudService.GOOGLE_DRIVE.id}:$email",
            service = CloudService.GOOGLE_DRIVE,
            displayName = name,
            email = email
        )
    }

    @Suppress("DEPRECATION")
    private fun signInName(result: AuthorizationResult): String? =
        runCatching { result.toGoogleSignInAccount()?.displayName }.getOrNull()?.takeIf { it.isNotBlank() }

    /** A token for whichever Google account already granted the scope, without naming one. */
    private fun googleAnyAccessToken(): String {
        val result = try {
            Tasks.await(googleAuthorizeTask(), AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw mapGoogleError(e.cause ?: e)
        } catch (e: Exception) {
            throw mapGoogleError(e)
        }
        return result.accessToken ?: throw CloudException(R.string.cloud_error_signin_again)
    }

    /** A fresh Drive access token for [email]; Play services refreshes it silently. */
    suspend fun googleAccessToken(email: String): String = withContext(Dispatchers.IO) {
        val result = try {
            Tasks.await(googleAuthorizeTask(email), AUTH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw mapGoogleError(e.cause ?: e)
        } catch (e: Exception) {
            throw mapGoogleError(e)
        }
        if (result.hasResolution()) throw CloudException(R.string.cloud_error_signin_again)
        result.accessToken ?: throw CloudException(R.string.cloud_error_signin_again)
    }

    fun mapGoogleError(e: Throwable): CloudException {
        ZuneLog.e(TAG, "google authorization failed", e)
        return when ((e as? ApiException)?.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR, CommonStatusCodes.INVALID_ACCOUNT ->
                CloudException(R.string.cloud_error_google_config, e.message, e)
            CommonStatusCodes.CANCELED, CommonStatusCodes.SIGN_IN_REQUIRED ->
                CloudException(R.string.cloud_error_cancelled, e.message, e)
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT ->
                CloudException(R.string.cloud_error_network, e.message, e)
            else -> CloudException(R.string.cloud_error_service, e.message, e)
        }
    }

    /**
     * Drops everything stored for an account that is being signed out. Play services holds the
     * Google token itself, so this only clears entries an older build may have left behind.
     */
    fun forget(accountId: String) {
        secretStore.remove("cloud_refresh_$accountId")
        secretStore.remove("cloud_access_$accountId")
    }

    companion object {
        private const val TAG = "CloudAuth"
        private const val AUTH_TIMEOUT_SECONDS = 40L
        private const val DRIVE_ABOUT = "https://www.googleapis.com/drive/v3/about"

        /** Full Drive access: the hub browses and manages files the user already has. */
        val DRIVE_SCOPE: Scope = Scope("https://www.googleapis.com/auth/drive")
    }
}
