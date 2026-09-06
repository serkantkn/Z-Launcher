package com.serkantkn.zunelauncher.data.service

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
import com.serkantkn.zunelauncher.data.repository.EmailException
import com.serkantkn.zunelauncher.util.ZuneLog
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

/**
 * "Google ile oturum aç" for Gmail. Uses the Google Play services authorization API: the user
 * picks a Google account once and grants the mail scope; afterwards [accessToken] returns a
 * fresh OAuth 2.0 access token silently (Play services refreshes it), which JavaMail sends as
 * the XOAUTH2 password. No client secret or password is ever stored by the app.
 *
 * Requires an OAuth client of type "Android" in Google Cloud Console for this package name and
 * signing certificate; without it Play services answers DEVELOPER_ERROR (code 10).
 */
object GoogleMailAuth {

    private const val TAG = "GoogleMailAuth"
    val MAIL_SCOPE: Scope = Scope("https://mail.google.com/")

    /** Starts (or silently repeats) the authorization; the result may need UI ([AuthorizationResult.hasResolution]). */
    fun authorizeTask(context: Context, email: String? = null): Task<AuthorizationResult> {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(MAIL_SCOPE))
            .apply { if (!email.isNullOrBlank()) setAccount(Account(email, "com.google")) }
            .build()
        return Identity.getAuthorizationClient(context.applicationContext).authorize(request)
    }

    fun resultFromIntent(context: Context, data: Intent): AuthorizationResult =
        Identity.getAuthorizationClient(context.applicationContext).getAuthorizationResultFromIntent(data)

    /** E-mail address of the account that granted access, when Play services reports it. */
    @Suppress("DEPRECATION")
    fun emailOf(result: AuthorizationResult): String? = runCatching { result.toGoogleSignInAccount()?.email }.getOrNull()

    @Suppress("DEPRECATION")
    fun displayNameOf(result: AuthorizationResult): String? = runCatching { result.toGoogleSignInAccount()?.displayName }.getOrNull()

    /**
     * Blocking token fetch for the mail connections (call on Dispatchers.IO). Throws an
     * [EmailException] when the grant is missing (user must sign in again) or the OAuth client
     * is not configured.
     */
    fun accessToken(context: Context, email: String): String {
        val result = try {
            Tasks.await(authorizeTask(context, email), 40, TimeUnit.SECONDS)
        } catch (e: ExecutionException) {
            throw mapError(e.cause ?: e)
        } catch (e: Exception) {
            throw mapError(e)
        }
        if (result.hasResolution()) throw EmailException(R.string.email_error_google_reauth)
        return result.accessToken ?: throw EmailException(R.string.email_error_google_reauth)
    }

    fun mapError(e: Throwable): EmailException {
        ZuneLog.e(TAG, "authorization failed", e)
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            CommonStatusCodes.DEVELOPER_ERROR, CommonStatusCodes.INVALID_ACCOUNT -> EmailException(R.string.email_error_google_config, e.message, e)
            CommonStatusCodes.CANCELED, CommonStatusCodes.SIGN_IN_REQUIRED -> EmailException(R.string.email_error_google_cancelled, e.message, e)
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> EmailException(R.string.email_error_connection, e.message, e)
            else -> EmailException(R.string.email_error_google_failed, e.message, e)
        }
    }
}
