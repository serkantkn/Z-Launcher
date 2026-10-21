package com.serkantkn.zeta.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Cross-hub hand-off for the Phone hub, in the same style as [PeopleBridge].
 *
 * A `tel:` link tapped in another app, or a DIAL intent from a contacts app, lands on
 * MainActivity with a number and nothing to show it on. The number waits here; the Phone hub
 * picks it up on its next composition and opens the keypad with it typed in — dialled, not rung,
 * because DIAL means "let me look at it first".
 */
object PhoneBridge {

    private val _pendingNumber = MutableStateFlow<String?>(null)
    val pendingNumber: StateFlow<String?> = _pendingNumber.asStateFlow()

    fun dial(number: String) {
        _pendingNumber.value = number
    }

    /** Returns and clears the pending number. */
    fun consume(): String? {
        val current = _pendingNumber.value
        _pendingNumber.value = null
        return current
    }
}
