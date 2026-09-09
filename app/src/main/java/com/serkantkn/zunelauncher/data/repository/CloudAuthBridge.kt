package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries the browser's answer from the sign-in redirect activity to the Files hub, the same way
 * [NotesBridge] carries cross-hub requests.
 */
object CloudAuthBridge {

    /** Authorization code the browser returned, waiting to be exchanged for tokens. */
    private val _pendingCode = MutableStateFlow<String?>(null)
    val pendingCode: StateFlow<String?> = _pendingCode.asStateFlow()

    /** Description of a sign-in the user cancelled or the service refused. */
    private val _pendingError = MutableStateFlow<String?>(null)
    val pendingError: StateFlow<String?> = _pendingError.asStateFlow()

    fun onCode(code: String) {
        _pendingError.value = null
        _pendingCode.value = code
    }

    fun onError(message: String) {
        _pendingCode.value = null
        _pendingError.value = message
    }

    fun consumeCode(): String? {
        val current = _pendingCode.value
        _pendingCode.value = null
        return current
    }

    fun consumeError(): String? {
        val current = _pendingError.value
        _pendingError.value = null
        return current
    }
}
