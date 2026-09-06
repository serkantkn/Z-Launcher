package com.serkantkn.zunelauncher.data.model

import org.json.JSONObject

/**
 * Represents a file download task in the Internet Hub.
 * JSON keys match the field names of the previously stored records so
 * existing downloads keep loading; nullable fields may be absent or JSON null.
 */
data class BrowserDownload(
    val id: Long,
    val fileName: String,
    val url: String,
    val mimeType: String?,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: Int = android.app.DownloadManager.STATUS_RUNNING,
    val localUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("fileName", fileName)
        .put("url", url)
        .put("mimeType", mimeType ?: JSONObject.NULL)
        .put("totalBytes", totalBytes)
        .put("downloadedBytes", downloadedBytes)
        .put("status", status)
        .put("localUri", localUri ?: JSONObject.NULL)
        .put("timestamp", timestamp)

    companion object {
        /** Throws JSONException when "id" is missing. */
        fun fromJson(obj: JSONObject): BrowserDownload = BrowserDownload(
            id = obj.getLong("id"),
            fileName = obj.optString("fileName", ""),
            url = obj.optString("url", ""),
            mimeType = obj.optStringOrNull("mimeType"),
            totalBytes = obj.optLong("totalBytes", 0L),
            downloadedBytes = obj.optLong("downloadedBytes", 0L),
            status = obj.optInt("status", android.app.DownloadManager.STATUS_RUNNING),
            localUri = obj.optStringOrNull("localUri"),
            timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        )
    }
}
