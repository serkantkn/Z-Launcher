package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the music hub should do the moment it opens.
 *
 * A record pinned to Start has to start playing, not merely open the hub and leave somebody to
 * find it again. The tile leaves a note here and the hub reads it once.
 */
object MusicBridge {

    /** The album to play, by its media-store id, or null. */
    private val _pendingAlbum = MutableStateFlow<Long?>(null)
    val pendingAlbum: StateFlow<Long?> = _pendingAlbum.asStateFlow()

    fun playAlbum(albumId: Long) {
        _pendingAlbum.value = albumId
    }

    fun consume() {
        _pendingAlbum.value = null
    }
}
