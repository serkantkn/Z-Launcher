package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-hub hand-off for the Internet Hub, in the same style as [NotesBridge].
 *
 * A website pinned to Start puts its address here and opens the INTERNET hub; the hub picks it up
 * on its next composition and loads it in a tab.
 */
object BrowserBridge {

    private val _pendingUrl = MutableStateFlow<String?>(null)
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    fun open(url: String) {
        readingRequested = false
        _pendingUrl.value = url
    }

    /**
     * Opens [url] and puts it in reading view once it has loaded - a story carried on from the
     * news hub. Links that pass through a redirect (Google News's do) are read where they land.
     */
    fun openForReading(url: String) {
        readingRequested = true
        _pendingUrl.value = url
    }

    /** Whether the address now pending asked for the reading view; read when it is consumed. */
    @Volatile
    var readingRequested: Boolean = false
        private set

    /** Returns and clears the pending address. */
    fun consume(): String? {
        val current = _pendingUrl.value
        _pendingUrl.value = null
        return current
    }

    /** Returns and clears whether the address just consumed is to be read in reading view. */
    fun consumeReading(): Boolean {
        val reading = readingRequested
        readingRequested = false
        return reading
    }
}
