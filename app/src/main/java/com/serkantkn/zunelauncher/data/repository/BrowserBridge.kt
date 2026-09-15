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
        _pendingUrl.value = url
    }

    /** Returns and clears the pending address. */
    fun consume(): String? {
        val current = _pendingUrl.value
        _pendingUrl.value = null
        return current
    }
}
