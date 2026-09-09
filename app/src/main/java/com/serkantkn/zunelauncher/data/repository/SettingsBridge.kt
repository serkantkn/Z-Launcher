package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-component requests to open the settings hub on a given tab, e.g. the "all settings" link
 * of the keyboard's quick panel. Mirrors [NotesBridge]: the launcher screen picks the request up
 * as soon as it is composed.
 */
object SettingsBridge {

    private val _pendingTab = MutableStateFlow<String?>(null)
    val pendingTab: StateFlow<String?> = _pendingTab.asStateFlow()

    /** [tab] is the name of a SettingsTab entry, e.g. "KEYBOARD". */
    fun open(tab: String) {
        _pendingTab.value = tab
    }

    /** Returns and clears the pending request. */
    fun consume(): String? {
        val current = _pendingTab.value
        _pendingTab.value = null
        return current
    }
}
