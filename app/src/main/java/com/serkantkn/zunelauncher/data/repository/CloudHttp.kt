package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.util.ZuneLog
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * The little bit of HTTP the cloud services need: JSON in, JSON out, plus a raw download. Written
 * on HttpURLConnection so the launcher keeps its "no extra dependency" rule.
 *
 * Every call is blocking and belongs on Dispatchers.IO.
 */
internal object CloudHttp {

    private const val TAG = "CloudHttp"
    private const val TIMEOUT_MS = 30_000

    fun getJson(url: String, token: String): JSONObject = request("GET", url, token)

    fun postJson(url: String, token: String, body: JSONObject): JSONObject =
        request("POST", url, token, body.toString(), "application/json; charset=utf-8")

    fun patchJson(url: String, token: String, body: JSONObject): JSONObject =
        // Not every proxy passes PATCH through, so the standard override header is sent as well.
        request("POST", url, token, body.toString(), "application/json; charset=utf-8", override = "PATCH")

    fun delete(url: String, token: String) {
        request("DELETE", url, token, expectsBody = false)
    }

    /** Form-encoded POST without a bearer token, for the OAuth token endpoints. */
    fun postForm(url: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        return request("POST", url, token = null, body = body, contentType = "application/x-www-form-urlencoded")
    }

    /** Streams a document into [output]; [token] is optional because some links are pre-signed. */
    fun download(url: String, token: String?, output: OutputStream) {
        val connection = open(url, "GET", token)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw failure(status, readError(connection))
            connection.inputStream.use { input -> input.copyTo(output) }
        } catch (e: IOException) {
            throw CloudException(R.string.cloud_error_network, e.message, e)
        } finally {
            connection.disconnect()
        }
    }

    fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun request(
        method: String,
        url: String,
        token: String?,
        body: String? = null,
        contentType: String? = null,
        override: String? = null,
        expectsBody: Boolean = true
    ): JSONObject {
        val connection = open(url, method, token)
        try {
            if (override != null) connection.setRequestProperty("X-HTTP-Method-Override", override)
            if (body != null) {
                connection.doOutput = true
                contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw failure(status, readError(connection))
            if (!expectsBody) return JSONObject()
            val text = connection.inputStream.readText()
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: IOException) {
            ZuneLog.w(TAG, "$method $url failed", e)
            throw CloudException(R.string.cloud_error_network, e.message, e)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, method: String, token: String?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
        }

    /**
     * 401 means the token is stale and the account has to sign in again. A 403 needs a closer
     * look: Google answers with it both when the Drive API was never switched on for the OAuth
     * project and when the granted scope is too small, and those need different fixes.
     */
    private fun failure(status: Int, detail: String?): CloudException {
        val body = detail.orEmpty()
        ZuneLog.w(TAG, "http $status: ${body.take(300)}")
        return when {
            status == HttpURLConnection.HTTP_UNAUTHORIZED ->
                CloudException(R.string.cloud_error_signin_again, detail)
            status == HttpURLConnection.HTTP_FORBIDDEN && body.containsAny(
                "accessNotConfigured", "SERVICE_DISABLED", "has not been used in project"
            ) -> CloudException(R.string.cloud_error_google_api_off, detail)
            status == HttpURLConnection.HTTP_FORBIDDEN && body.containsAny(
                "insufficientPermissions", "insufficient_scope", "ACCESS_TOKEN_SCOPE_INSUFFICIENT"
            ) -> CloudException(R.string.cloud_error_signin_again, detail)
            else -> CloudException(R.string.cloud_error_service, detail)
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { contains(it, ignoreCase = true) }

    private fun readError(connection: HttpURLConnection): String? =
        runCatching { connection.errorStream?.readText() }.getOrNull()

    private fun InputStream.readText(): String = bufferedReader().use { it.readText() }
}
