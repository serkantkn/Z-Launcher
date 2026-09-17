package com.serkantkn.zunelauncher.data.repository

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the pictures hub should do the moment it opens.
 *
 * An album pinned to Start has to land inside that album, not at the top of the hub — the same
 * problem every other pinned tile has, solved the same way: whoever taps the tile leaves a note
 * here, and the hub reads it once and clears it.
 *
 * The camera leaves the same kind of note: the shot it has just taken, so that tapping the corner
 * thumbnail lands on that picture in this launcher's own viewer rather than in whatever else on
 * the phone answers to a JPEG.
 */
object PicturesBridge {

    /** The album to open, by its media-store bucket, or null. */
    private val _pendingAlbum = MutableStateFlow<Long?>(null)
    val pendingAlbum: StateFlow<Long?> = _pendingAlbum.asStateFlow()

    fun openAlbum(bucketId: Long) {
        _pendingAlbum.value = bucketId
    }

    fun consume() {
        _pendingAlbum.value = null
    }

    /** The picture to open, or null. */
    private val _pendingPhoto = MutableStateFlow<Uri?>(null)
    val pendingPhoto: StateFlow<Uri?> = _pendingPhoto.asStateFlow()

    fun openPhoto(uri: Uri) {
        _pendingPhoto.value = uri
    }

    fun consumePhoto() {
        _pendingPhoto.value = null
    }
}
