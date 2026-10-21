package com.serkantkn.zeta.data.repository

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
        fromNews = false
        _pendingUrl.value = url
    }

    /**
     * Opens a story carried on from the news hub. Its tab goes back to the hub on Back, past the
     * Google page a Google News link passes through on the way.
     */
    fun openFromNews(url: String) {
        fromNews = true
        _pendingUrl.value = url
    }

    /** Whether the address now pending came from the news hub; read when it is consumed. */
    @Volatile
    private var fromNews: Boolean = false

    /** Returns and clears the pending address. */
    fun consume(): String? {
        val current = _pendingUrl.value
        _pendingUrl.value = null
        return current
    }

    /** Returns and clears whether the address just consumed came from the news hub. */
    fun consumeFromNews(): Boolean {
        val news = fromNews
        fromNews = false
        return news
    }
}
