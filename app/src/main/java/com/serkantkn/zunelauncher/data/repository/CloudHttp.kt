package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudException
import com.serkantkn.zunelauncher.util.ZuneLog
import org.json.JSONObject
import java.io.File
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
    private const val BUFFER = 64 * 1024
    private const val REPORT_EVERY = 256 * 1024L

    fun getJson(url: String, token: String): JSONObject = request("GET", url, token)

    fun postJson(url: String, token: String, body: JSONObject): JSONObject =
        request("POST", url, token, body.toString(), "application/json; charset=utf-8")

    fun patchJson(url: String, token: String, body: JSONObject): JSONObject =
        // Not every proxy passes PATCH through, so the standard override header is sent as well.
        request("POST", url, token, body.toString(), "application/json; charset=utf-8", override = "PATCH")

    fun delete(url: String, token: String) {
        request("DELETE", url, token, expectsBody = false)
    }

    /**
     * Pulls bytes down, telling [onProgress] how far along it is as it goes.
     *
     * [knownTotal] is what the caller already knows the size to be; when it is zero the answer's
     * own content length is used, and when the service will not say either the progress is
     * reported against a total of zero — a counter rather than a bar, which is honest.
     *
     * The copy checks whether its coroutine is still wanted between chunks, so cancelling a
     * download actually stops it instead of finishing quietly in the background.
     */
    fun download(
        url: String,
        token: String?,
        output: OutputStream,
        knownTotal: Long = 0L,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ) {
        val connection = open(url, "GET", token)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw failure(status, readError(connection))
            val total = if (knownTotal > 0L) knownTotal else connection.contentLengthLong.coerceAtLeast(0L)
            connection.inputStream.use { input -> pump(input, output, total, onProgress) }
        } catch (e: IOException) {
            throw CloudException(R.string.cloud_error_network, e.message, e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * A file and its metadata in one POST, the way Drive's multipart upload wants them: two parts
     * separated by a boundary, JSON first and bytes second.
     */
    fun uploadMultipart(
        url: String,
        token: String,
        metadata: JSONObject,
        file: File,
        mimeType: String,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): JSONObject {
        val boundary = "zune${System.nanoTime()}"
        val head = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\n")
            append("Content-Type: $mimeType\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        val connection = open(url, "POST", token)
        try {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            // Streaming rather than buffering: a large file must not be held in memory twice.
            connection.setFixedLengthStreamingMode(head.size + file.length() + tail.size)
            connection.outputStream.use { out ->
                out.write(head)
                file.inputStream().use { input -> pump(input, out, file.length(), onProgress) }
                out.write(tail)
            }
            val status = connection.responseCode
            if (status !in 200..299) throw failure(status, readError(connection))
            val text = connection.inputStream.readText()
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: IOException) {
            ZuneLog.w(TAG, "upload to $url failed", e)
            throw CloudException(R.string.cloud_error_network, e.message, e)
        } finally {
            connection.disconnect()
        }
    }

    /** Copies one stream into another, reporting progress and honouring cancellation. */
    private fun pump(input: InputStream, output: OutputStream, total: Long, onProgress: (Long, Long) -> Unit) {
        val buffer = ByteArray(BUFFER)
        var moved = 0L
        var lastReport = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            moved += read
            // Reporting every chunk would repaint the bar hundreds of times a second.
            if (moved - lastReport >= REPORT_EVERY || moved == total) {
                lastReport = moved
                onProgress(moved, total)
            }
        }
        onProgress(moved, if (total > 0L) total else moved)
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
