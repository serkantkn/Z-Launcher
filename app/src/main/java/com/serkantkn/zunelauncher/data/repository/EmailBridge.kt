package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-hub hand-off for the Email Hub, in the same style as [NotesBridge]: new-mail
 * notifications, mailto:/share intents and other hubs put a request here and open the EMAIL
 * hub; EmailHubScreen consumes it on its next composition.
 */
object EmailBridge {

    sealed interface Request {
        /** Open one message (from a notification / toast). */
        data class Open(val accountId: String, val folder: String, val uid: Long) : Request

        /** Show the inbox of an account (or the unified inbox when [accountId] is null). */
        data class Inbox(val accountId: String?) : Request

        /** Start a new message, optionally pre-filled (mailto:, share sheet, People hub). */
        data class Compose(
            val to: String = "",
            val cc: String = "",
            val bcc: String = "",
            val subject: String = "",
            val body: String = "",
            val attachmentUris: List<String> = emptyList()
        ) : Request
    }

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    fun open(accountId: String, folder: String, uid: Long) {
        _pending.value = Request.Open(accountId, folder, uid)
    }

    fun inbox(accountId: String? = null) {
        _pending.value = Request.Inbox(accountId)
    }

    fun compose(
        to: String = "",
        cc: String = "",
        bcc: String = "",
        subject: String = "",
        body: String = "",
        attachmentUris: List<String> = emptyList()
    ) {
        _pending.value = Request.Compose(to, cc, bcc, subject, body, attachmentUris)
    }

    /** Returns and clears the pending request. */
    fun consume(): Request? {
        val current = _pending.value
        _pending.value = null
        return current
    }
}
