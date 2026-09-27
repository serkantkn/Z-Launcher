package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "The Home key was pressed", from the activity to the launcher screen.
 *
 * The launcher is the home app, so a press arrives as a fresh MAIN/HOME intent on the activity
 * that is already running. It carries nothing to distinguish one press from the next, so this
 * counts them: the screen reacts to the count changing and therefore reacts to every press,
 * including the second one in a row.
 */
object HomeKeyBridge {

    private val _presses = MutableStateFlow(0)

    /** Increments once per Home key press. Zero means the key has not been pressed yet. */
    val presses: StateFlow<Int> = _presses.asStateFlow()

    fun press() {
        _presses.value++
    }
}
