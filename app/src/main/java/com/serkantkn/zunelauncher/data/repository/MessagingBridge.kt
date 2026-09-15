package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-hub hand-off for the Messaging Hub, in the same style as [PeopleBridge].
 *
 * Three things arrive at the hub from outside it: a conversation pinned to Start, a message
 * another app asked us to write, and the news that the store has changed because something came
 * in or went out. The hub picks all three up on its next composition.
 */
object MessagingBridge {

    /** A conversation to open: a pinned tile, or a notification being tapped. */
    private val _pendingThreadId = MutableStateFlow<Long?>(null)
    val pendingThreadId: StateFlow<Long?> = _pendingThreadId.asStateFlow()

    /** A message another app asked us to write — an `sms:` link, or text shared to us. */
    data class ComposeRequest(val address: String, val body: String)

    private val _pendingCompose = MutableStateFlow<ComposeRequest?>(null)
    val pendingCompose: StateFlow<ComposeRequest?> = _pendingCompose.asStateFlow()

    /**
     * Ticks whenever the message store changes under us. The hub watches the number rather than
     * the store, which keeps the reload in one place instead of a content observer per screen.
     */
    private val _changes = MutableStateFlow(0L)
    val changes: StateFlow<Long> = _changes.asStateFlow()

    fun openThread(threadId: Long) {
        _pendingThreadId.value = threadId
    }

    fun consumeThread(): Long? {
        val current = _pendingThreadId.value
        _pendingThreadId.value = null
        return current
    }

    fun compose(address: String, body: String = "") {
        _pendingCompose.value = ComposeRequest(address, body)
    }

    fun consumeCompose(): ComposeRequest? {
        val current = _pendingCompose.value
        _pendingCompose.value = null
        return current
    }

    /**
     * The conversation the user is looking at, if any.
     *
     * A notification for a message that is already on screen is noise, so the notifier asks here
     * before putting one up.
     */
    @Volatile
    var visibleThreadId: Long? = null

    fun messagesChanged() {
        _changes.value += 1
    }
}
