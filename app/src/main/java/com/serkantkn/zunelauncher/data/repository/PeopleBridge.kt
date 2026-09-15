package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-hub hand-off for the People Hub, in the same style as [NotesBridge].
 *
 * A person pinned to Start puts their contact id here and opens the PEOPLE hub; the hub picks it
 * up on its next composition and opens straight onto them.
 */
object PeopleBridge {

    private val _pendingContactId = MutableStateFlow<String?>(null)
    val pendingContactId: StateFlow<String?> = _pendingContactId.asStateFlow()

    fun open(contactId: String) {
        _pendingContactId.value = contactId
    }

    /** Returns and clears the pending person. */
    fun consume(): String? {
        val current = _pendingContactId.value
        _pendingContactId.value = null
        return current
    }
}
