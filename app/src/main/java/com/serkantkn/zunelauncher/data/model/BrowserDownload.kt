package com.serkantkn.zunelauncher.data.model

/**
 * Represents a file download task in the Internet Hub.
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
)
