package com.serkantkn.zunelauncher.data.model

import java.io.File

/**
 * A place files live: the phone's own storage, or a card pushed into the side of it.
 *
 * The hub used to know about exactly one — `Environment.getExternalStorageDirectory()` — so a
 * phone with an SD card in it showed nothing of what was on the card.
 */
data class StorageVolumeInfo(
    val root: File,
    val label: String,
    val isPrimary: Boolean,
    val isRemovable: Boolean
) {
    /** Bytes in use, worked out from the volume itself rather than remembered. */
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)

    val totalBytes: Long get() = runCatching { root.totalSpace }.getOrDefault(0L)

    val freeBytes: Long get() = runCatching { root.usableSpace }.getOrDefault(0L)

    /** How full it is, 0..1, or null when the volume will not say. */
    val fraction: Float?
        get() {
            val total = totalBytes
            return if (total > 0L) (usedBytes.toFloat() / total).coerceIn(0f, 1f) else null
        }
}
