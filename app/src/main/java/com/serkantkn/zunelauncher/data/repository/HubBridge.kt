package com.serkantkn.zunelauncher.data.repository

import com.serkantkn.zunelauncher.data.model.HubType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Open this hub", asked for from somewhere that has no way to navigate.
 *
 * The Social hub uses it to hand a notification over to the launcher's own Messaging or Phone hub
 * instead of to the app that sent it. The launcher screen picks the request up on its next
 * composition, the same way [NotesBridge] and [SettingsBridge] work.
 */
object HubBridge {

    private val _pendingHub = MutableStateFlow<HubType?>(null)
    val pendingHub: StateFlow<HubType?> = _pendingHub.asStateFlow()

    fun open(hub: HubType) {
        _pendingHub.value = hub
    }

    /** Returns and clears the pending request. */
    fun consume(): HubType? {
        val current = _pendingHub.value
        _pendingHub.value = null
        return current
    }
}
