package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-hub hand-off for the Notes Hub, in the same singleton style as SocialRepository.
 *
 * Other hubs (Start tiles, Calendar, People, Social, share intents) put a request here and
 * open the NOTES hub; NotesHubScreen consumes the request on its next composition.
 */
object NotesBridge {

    /** What the Notes Hub should do as soon as it is shown. */
    sealed interface Request {
        /** Open an existing note in the editor. */
        data class Open(val noteId: String) : Request

        /** Start a new note, optionally pre-filled and pre-linked. */
        data class New(
            val title: String = "",
            val content: String = "",
            val asChecklist: Boolean = false,
            val linkedContactId: String? = null,
            val linkedContactName: String? = null
        ) : Request
    }

    private val _pending = MutableStateFlow<Request?>(null)
    val pending: StateFlow<Request?> = _pending.asStateFlow()

    fun open(noteId: String) {
        _pending.value = Request.Open(noteId)
    }

    fun newNote(
        title: String = "",
        content: String = "",
        asChecklist: Boolean = false,
        linkedContactId: String? = null,
        linkedContactName: String? = null
    ) {
        _pending.value = Request.New(title, content, asChecklist, linkedContactId, linkedContactName)
    }

    /** Returns and clears the pending request. */
    fun consume(): Request? {
        val current = _pending.value
        _pending.value = null
        return current
    }
}
