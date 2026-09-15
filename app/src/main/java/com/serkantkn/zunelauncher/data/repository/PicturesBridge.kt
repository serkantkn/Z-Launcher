package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the pictures hub should do the moment it opens.
 *
 * An album pinned to Start has to land inside that album, not at the top of the hub — the same
 * problem every other pinned tile has, solved the same way: whoever taps the tile leaves a note
 * here, and the hub reads it once and clears it.
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
}
