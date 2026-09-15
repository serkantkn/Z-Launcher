package com.serkantkn.zunelauncher.data.model

import android.net.Uri

/**
 * One thing in the gallery: a photo, or a video.
 *
 * The hub used to know only about photos, which meant a video shot with the launcher's own camera
 * — written into the video store by [com.serkantkn.zunelauncher.ui.screens.camera.CameraEngine] —
 * was invisible in the launcher's own gallery. The camera roll holds both now, the way it did on
 * Windows Phone, and [isVideo] is what tells them apart everywhere downstream.
 *
 * The name is kept for the sake of everything already calling it that; what changed is what it can
 * hold.
 */
data class MediaImage(
    val id: Long,
    val uri: Uri,
    val dateAdded: Long,
    val bucketId: Long,
    val bucketName: String,
    val displayName: String,
    val relativePath: String = "",
    /** Whether this is a video rather than a still. */
    val isVideo: Boolean = false,
    /** How long the video runs, in milliseconds; zero for a photo. */
    val durationMillis: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val mimeType: String = ""
) {
    /**
     * A key that survives a rescan.
     *
     * Media ids are handed out by the store and a photo can be given a new one when the library is
     * rebuilt, which would silently empty the favourites. The name and the moment it arrived do
     * not change, so they are what a favourite is remembered by.
     */
    val stableKey: String get() = "${if (isVideo) "v" else "p"}:$displayName:$dateAdded"

    /** Shape of the picture, for a grid that does not want to crop everything into squares. */
    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
}

data class MediaAlbum(
    val bucketId: Long,
    val bucketName: String,
    val coverUri: Uri,
    val photoCount: Int,
    /** How many of them are videos, for the line under the album's name. */
    val videoCount: Int = 0
)
