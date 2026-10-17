package com.serkantkn.zunelauncher.data.model

/** A file being written somewhere else - into the cloud, say - while it is happening. */
data class FileTransfer(
    val name: String,
    val isUpload: Boolean,
    val transferredBytes: Long,
    val totalBytes: Long
) {
    /** Null while nobody has said how big the thing is. */
    val fraction: Float? get() =
        if (totalBytes > 0L) (transferredBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
}
