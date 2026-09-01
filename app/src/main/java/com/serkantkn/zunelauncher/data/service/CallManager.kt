package com.serkantkn.zunelauncher.data.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class CallStatus {
    IDLE,
    INCOMING,
    OUTGOING,
    ACTIVE,
    ENDED
}

/**
 * Global Call Manager handling Windows Phone In-Call and Ringing state simulation/management.
 */
object CallManager {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _callStatus = MutableStateFlow(CallStatus.IDLE)
    val callStatus: StateFlow<CallStatus> = _callStatus.asStateFlow()

    private val _contactName = MutableStateFlow("")
    val contactName: StateFlow<String> = _contactName.asStateFlow()

    private val _phoneNumber = MutableStateFlow("")
    val phoneNumber: StateFlow<String> = _phoneNumber.asStateFlow()

    private val _photoUri = MutableStateFlow<String?>(null)
    val photoUri: StateFlow<String?> = _photoUri.asStateFlow()

    private val _callDurationSeconds = MutableStateFlow(0)
    val callDurationSeconds: StateFlow<Int> = _callDurationSeconds.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(false)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isOnHold = MutableStateFlow(false)
    val isOnHold: StateFlow<Boolean> = _isOnHold.asStateFlow()

    private val _isKeypadOpen = MutableStateFlow(false)
    val isKeypadOpen: StateFlow<Boolean> = _isKeypadOpen.asStateFlow()

    private var durationTimerJob: Job? = null
    private var autoAnswerJob: Job? = null

    fun startOutgoingCall(name: String, number: String, photo: String? = null) {
        resetState()
        _contactName.value = if (name.isNotBlank()) name else number
        _phoneNumber.value = number
        _photoUri.value = photo
        _callStatus.value = CallStatus.OUTGOING

        // Simulate call connection after 3.5 seconds
        autoAnswerJob?.cancel()
        autoAnswerJob = scope.launch {
            delay(3500L)
            if (_callStatus.value == CallStatus.OUTGOING) {
                answerCall()
            }
        }
    }

    fun startIncomingCall(name: String, number: String, photo: String? = null) {
        resetState()
        _contactName.value = if (name.isNotBlank()) name else number
        _phoneNumber.value = number
        _photoUri.value = photo
        _callStatus.value = CallStatus.INCOMING
    }

    fun answerCall() {
        autoAnswerJob?.cancel()
        _callStatus.value = CallStatus.ACTIVE
        startDurationTimer()
    }

    fun declineCall() {
        endCall()
    }

    fun endCall() {
        autoAnswerJob?.cancel()
        durationTimerJob?.cancel()
        _callStatus.value = CallStatus.ENDED
        scope.launch {
            delay(1600L)
            if (_callStatus.value == CallStatus.ENDED) {
                resetState()
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }

    fun toggleHold() {
        _isOnHold.value = !_isOnHold.value
    }

    fun toggleKeypad() {
        _isKeypadOpen.value = !_isKeypadOpen.value
    }

    fun setKeypadOpen(open: Boolean) {
        _isKeypadOpen.value = open
    }

    private fun startDurationTimer() {
        durationTimerJob?.cancel()
        _callDurationSeconds.value = 0
        durationTimerJob = scope.launch {
            while (_callStatus.value == CallStatus.ACTIVE) {
                delay(1000L)
                if (!_isOnHold.value) {
                    _callDurationSeconds.value += 1
                }
            }
        }
    }

    private fun resetState() {
        autoAnswerJob?.cancel()
        durationTimerJob?.cancel()
        _callStatus.value = CallStatus.IDLE
        _contactName.value = ""
        _phoneNumber.value = ""
        _photoUri.value = null
        _callDurationSeconds.value = 0
        _isMuted.value = false
        _isSpeakerOn.value = false
        _isOnHold.value = false
        _isKeypadOpen.value = false
    }

    fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}
