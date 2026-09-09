package com.serkantkn.zunelauncher.data.repository

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.serkantkn.zunelauncher.BuildConfig
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.data.model.CloudService
import com.serkantkn.zunelauncher.util.SecretStore
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * Signing in to the cloud services, and keeping the tokens afterwards.
 *
 * Google goes through the Play services authorization API — the same door the Gmail hub already
 * uses — so the user picks a Google account, grants the Drive scope once and the launcher gets a
 * fresh access token whenever it needs one. Google Drive's own app does not have to be installed.
 *
 * Microsoft is a plain OAuth 2.0 authorization-code flow with PKCE: the sign-in page opens in the
 * browser, comes back to the launcher through the redirect scheme, and the refresh token is kept
 * in the encrypted [SecretStore]. No client secret is used or needed for either service.
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

    // --- Microsoft ------------------------------------------------------------------------------

    /** True once a client id has been configured for the Microsoft sign-in. */
    fun isMicrosoftConfigured(): Boolean = BuildConfig.MICROSOFT_CLIENT_ID.isNotBlank()

    /**
     * The sign-in page to open in the browser. A fresh PKCE verifier is stored alongside it, so
     * the code that comes back through the redirect can be exchanged without a client secret.
     */
    fun microsoftAuthorizationUrl(): String {
        if (!isMicrosoftConfigured()) throw CloudException(R.string.cloud_error_microsoft_config)
        val verifier = randomVerifier()
        secretStore.put(PKCE_KEY, verifier)
        return Uri.parse("$MICROSOFT_AUTHORITY/authorize").buildUpon()
            .appendQueryParameter("client_id", BuildConfig.MICROSOFT_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.MICROSOFT_REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", MICROSOFT_SCOPES)
            .appendQueryParameter("code_challenge", challengeOf(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("prompt", "select_account")
            .build()
            .toString()
    }

    /** Exchanges the authorization code for tokens and returns the account that signed in. */
    suspend fun completeMicrosoftSignIn(code: String): CloudAccount = withContext(Dispatchers.IO) {
        val verifier = secretStore.get(PKCE_KEY)
            ?: throw CloudException(R.string.cloud_error_signin_again)
        val response = CloudHttp.postForm(
            "$MICROSOFT_AUTHORITY/token",
            mapOf(
                "client_id" to BuildConfig.MICROSOFT_CLIENT_ID,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to BuildConfig.MICROSOFT_REDIRECT_URI,
                "code_verifier" to verifier,
                "scope" to MICROSOFT_SCOPES
            )
        )
        secretStore.remove(PKCE_KEY)

        val accessToken = response.optString("access_token").ifBlank {
            throw CloudException(R.string.cloud_error_service)
        }
        val profile = CloudHttp.getJson("$GRAPH/me", accessToken)
        val email = profile.optString("mail").ifBlank { profile.optString("userPrincipalName") }
        val account = CloudAccount(
            id = "${CloudService.ONEDRIVE.id}:${email.ifBlank { "me" }}",
            service = CloudService.ONEDRIVE,
            displayName = profile.optString("displayName").ifBlank { email },
            email = email
        )
        storeTokens(account.id, response.optString("refresh_token"), accessToken, response.optLong("expires_in", 0L))
        account
    }

    /** A valid access token for [account], refreshed through the stored refresh token if needed. */
    suspend fun microsoftAccessToken(account: CloudAccount): String = withContext(Dispatchers.IO) {
        cachedToken(account.id)?.let { return@withContext it }
        val refreshToken = secretStore.get(refreshKey(account.id))
            ?: throw CloudException(R.string.cloud_error_signin_again)
        val response = CloudHttp.postForm(
            "$MICROSOFT_AUTHORITY/token",
            mapOf(
                "client_id" to BuildConfig.MICROSOFT_CLIENT_ID,
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "scope" to MICROSOFT_SCOPES
            )
        )
        val accessToken = response.optString("access_token").ifBlank {
            throw CloudException(R.string.cloud_error_signin_again)
        }
        storeTokens(
            account.id,
            response.optString("refresh_token").ifBlank { refreshToken },
            accessToken,
            response.optLong("expires_in", 0L)
        )
        accessToken
    }

    /** Drops everything stored for an account that is being signed out. */
    fun forget(accountId: String) {
        secretStore.remove(refreshKey(accountId))
        secretStore.remove(accessKey(accountId))
        accessTokens.remove(accountId)
    }

    private fun storeTokens(accountId: String, refreshToken: String?, accessToken: String, expiresIn: Long) {
        if (!refreshToken.isNullOrBlank()) secretStore.put(refreshKey(accountId), refreshToken)
        val validUntil = System.currentTimeMillis() + (expiresIn.coerceAtLeast(60L) - TOKEN_MARGIN_SECONDS) * 1000L
        accessTokens[accountId] = CachedToken(accessToken, validUntil)
    }

    private fun cachedToken(accountId: String): String? =
        accessTokens[accountId]?.takeIf { it.validUntil > System.currentTimeMillis() }?.token

    private fun refreshKey(accountId: String) = "cloud_refresh_$accountId"

    private fun accessKey(accountId: String) = "cloud_access_$accountId"

    /** RFC 7636 code verifier: 64 random bytes, base64url without padding. */
    private fun randomVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun challengeOf(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private data class CachedToken(val token: String, val validUntil: Long)

    companion object {
        private const val TAG = "CloudAuth"
        private const val AUTH_TIMEOUT_SECONDS = 40L
        private const val TOKEN_MARGIN_SECONDS = 60L
        private const val PKCE_KEY = "cloud_ms_pkce_verifier"

        const val MICROSOFT_AUTHORITY = "https://login.microsoftonline.com/common/oauth2/v2.0"
        const val MICROSOFT_SCOPES = "Files.ReadWrite offline_access User.Read"
        const val GRAPH = "https://graph.microsoft.com/v1.0"
        private const val DRIVE_ABOUT = "https://www.googleapis.com/drive/v3/about"

        /** Full Drive access: the hub browses and manages files the user already has. */
        val DRIVE_SCOPE: Scope = Scope("https://www.googleapis.com/auth/drive")

        private val accessTokens = HashMap<String, CachedToken>()
    }
}
