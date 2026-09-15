package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The Clock hub's four pages, in the order the pivot runs. */
enum class ClockPage { WORLD, ALARMS, STOPWATCH, TIMER }

/**
 * "Open the Clock hub on this page", asked for from outside the hub.
 *
 * A timer notification and the status bar's own alarm icon both land in the launcher, and neither
 * of them wants the world clock. Same shape as [HubBridge], which brings the hub itself forward;
 * this only says which page to stop on once it is there.
 */
object ClockBridge {

    private val _pendingPage = MutableStateFlow<ClockPage?>(null)
    val pendingPage: StateFlow<ClockPage?> = _pendingPage.asStateFlow()

    fun openPage(page: ClockPage) {
        _pendingPage.value = page
    }

    /** Returns and clears the pending request. */
    fun consume(): ClockPage? {
        val current = _pendingPage.value
        _pendingPage.value = null
        return current
    }
}
