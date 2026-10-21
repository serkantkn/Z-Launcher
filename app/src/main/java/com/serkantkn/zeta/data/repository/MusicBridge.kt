package com.serkantkn.zeta.data.repository

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

    /**
     * A track another app asked the launcher to play, as the phone's music player: one from the
     * music library by its id, or a file of its own (an attachment, a download) by address.
     */
    sealed interface Track {
        data class FromLibrary(val mediaId: Long) : Track
        data class FromFile(val uri: android.net.Uri, val title: String, val artist: String) : Track
    }

    private val _pendingTrack = MutableStateFlow<Track?>(null)
    val pendingTrack: StateFlow<Track?> = _pendingTrack.asStateFlow()

    fun playTrack(track: Track) {
        _pendingTrack.value = track
    }

    fun consumeTrack() {
        _pendingTrack.value = null
    }

    /**
     * The Zune list keeps "videos" as a word of its own under "music", as the Zune HD did; it
     * opens the same hub, at its videos page.
     */
    private val _showVideos = MutableStateFlow(false)
    val showVideos: StateFlow<Boolean> = _showVideos.asStateFlow()

    fun openVideos() {
        _showVideos.value = true
    }

    fun consumeVideos() {
        _showVideos.value = false
    }

    /** Vista's "play all" under music: everything, shuffled, as the hub opens. */
    private val _shuffleAll = MutableStateFlow(false)
    val pendingShuffleAll: StateFlow<Boolean> = _shuffleAll.asStateFlow()

    fun shuffleAll() {
        _shuffleAll.value = true
    }

    fun consumeShuffleAll() {
        _shuffleAll.value = false
    }
}
