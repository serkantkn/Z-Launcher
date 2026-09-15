package com.serkantkn.zunelauncher.data.service

import android.telecom.Call
import android.telecom.CallAudioState
import com.serkantkn.zunelauncher.util.ZuneLog
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

/** Where a call's sound is going. */
enum class CallAudioRoute { EARPIECE, SPEAKER, BLUETOOTH, HEADSET }

/**
 * The call the phone is on, as the launcher's own call screen sees it.
 *
 * Everything here is the real thing: the state, the duration and the buttons all belong to a
 * Telecom [Call] handed over by [WpInCallService]. Nothing is simulated — if there is no call,
 * these flows say IDLE, and the screen that watches them stays away.
 *
 * It is a singleton because the call screen has to be reachable from wherever the launcher happens
 * to be, and because Telecom only ever binds one in-call service.
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

    /** Where the sound is going, and whether this call can be put on hold at all. */
    private val _audioRoute = MutableStateFlow(CallAudioRoute.EARPIECE)
    val audioRoute: StateFlow<CallAudioRoute> = _audioRoute.asStateFlow()

    private val _canHold = MutableStateFlow(false)
    val canHold: StateFlow<Boolean> = _canHold.asStateFlow()

    /** Which SIM the call is on, on a phone where that is a question. Null when it is not. */
    private val _simLabel = MutableStateFlow<String?>(null)
    val simLabel: StateFlow<String?> = _simLabel.asStateFlow()

    /** The service that owns the call, while one is bound. */
    private var host: CallHost? = null

    /** The call on show. There can be others; this is the one the screen is about. */
    private var current: Call? = null

    /** The SIM the call on show is using, so a reply goes back out on the same line. */
    val currentLine: android.telecom.PhoneAccountHandle?
        get() = current?.details?.accountHandle

    private var durationTimerJob: Job? = null
    private var endedResetJob: Job? = null

    /** What [WpInCallService] has to be able to do for the screen's buttons to mean anything. */
    interface CallHost {
        fun setCallMuted(muted: Boolean)
        fun setAudioRoute(route: CallAudioRoute)
        fun lookUpCaller(number: String, onFound: (name: String?, photoUri: String?) -> Unit)
        fun labelOfLine(handle: android.telecom.PhoneAccountHandle?): String?
        fun onCallVisible(status: CallStatus, name: String, number: String)
        fun onCallGone()
    }

    // ══════════════════════════════════════════════════════════
    // FROM THE SERVICE
    // ══════════════════════════════════════════════════════════

    fun attach(host: CallHost) {
        this.host = host
    }

    fun detach(host: CallHost) {
        if (this.host === host) this.host = null
    }

    /** A call has arrived or been placed. */
    fun onCallAdded(call: Call) {
        endedResetJob?.cancel()
        current?.unregisterCallback(callback)
        current = call
        call.registerCallback(callback)

        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        _phoneNumber.value = number
        _contactName.value = call.details?.callerDisplayName?.takeIf { it.isNotBlank() } ?: number
        _photoUri.value = null
        _isKeypadOpen.value = false
        _isOnHold.value = false
        _simLabel.value = host?.labelOfLine(call.details?.accountHandle)

        if (number.isNotBlank()) {
            host?.lookUpCaller(number) { name, photo ->
                if (current !== call) return@lookUpCaller
                if (!name.isNullOrBlank()) _contactName.value = name
                _photoUri.value = photo
                announce()
            }
        }
        applyState(call)
    }

    /** Telecom has taken the call away: it is over, however it ended. */
    fun onCallRemoved(call: Call) {
        if (current !== call) return
        call.unregisterCallback(callback)
        current = null
        finishOnScreen()
    }

    fun onAudioStateChanged(state: CallAudioState) {
        _isMuted.value = state.isMuted
        _audioRoute.value = when (state.route) {
            CallAudioState.ROUTE_SPEAKER -> CallAudioRoute.SPEAKER
            CallAudioState.ROUTE_BLUETOOTH -> CallAudioRoute.BLUETOOTH
            CallAudioState.ROUTE_WIRED_HEADSET -> CallAudioRoute.HEADSET
            else -> CallAudioRoute.EARPIECE
        }
        _isSpeakerOn.value = _audioRoute.value == CallAudioRoute.SPEAKER
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            if (current === call) applyState(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            if (current === call) applyState(call)
        }
    }

    /** Telecom's state is the only source of truth for what the screen shows. */
    private fun applyState(call: Call) {
        val state = call.state
        _canHold.value = call.details?.can(Call.Details.CAPABILITY_HOLD) == true
        _isOnHold.value = state == Call.STATE_HOLDING

        val status = statusOf(state)
        if (status == null) {
            finishOnScreen()
            return
        }
        _callStatus.value = status

        if (status == CallStatus.ACTIVE || state == Call.STATE_HOLDING) {
            startDurationTimer(call.details?.connectTimeMillis ?: 0L)
        }
        announce()
    }

    /**
     * Whether the launcher's own call screen is what the user is looking at.
     *
     * The notification exists to carry a call to where the call screen is not; while the screen is
     * right there, a notification on top of it would only be in the way.
     */
    fun setScreenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) host?.onCallGone() else announce()
    }

    private var screenVisible = false

    private fun announce() {
        val status = _callStatus.value
        if (status == CallStatus.IDLE || status == CallStatus.ENDED) return
        if (screenVisible) {
            host?.onCallGone()
            return
        }
        host?.onCallVisible(status, _contactName.value, _phoneNumber.value)
    }

    private fun finishOnScreen() {
        durationTimerJob?.cancel()
        host?.onCallGone()
        if (_callStatus.value == CallStatus.IDLE) return
        _callStatus.value = CallStatus.ENDED
        endedResetJob?.cancel()
        // The screen holds on "call ended" for a moment before it goes, as Windows Phone did.
        endedResetJob = scope.launch {
            delay(ENDED_LINGER_MILLIS)
            if (_callStatus.value == CallStatus.ENDED) resetState()
        }
    }

    // ══════════════════════════════════════════════════════════
    // FROM THE SCREEN
    // ══════════════════════════════════════════════════════════

    fun answerCall() {
        val call = current ?: return
        if (call.state == Call.STATE_RINGING) call.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Turns down a ringing call; a call already up is simply ended. */
    fun declineCall() {
        val call = current ?: return
        if (call.state == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    fun endCall() {
        current?.disconnect()
    }

    /**
     * Turns a ringing call down and texts the caller instead.
     *
     * The message is sent from here rather than from the screen because the screen goes away the
     * moment the call is turned down, and a message half-way out of a cancelled coroutine is a
     * message nobody receives. The reply leaves on the same SIM the call arrived on.
     */
    fun declineWithMessage(
        context: android.content.Context,
        text: String,
        onResult: (sent: Boolean) -> Unit = {}
    ) {
        val number = _phoneNumber.value
        val line = currentLine
        val appContext = context.applicationContext
        declineCall()
        scope.launch {
            onResult(com.serkantkn.zunelauncher.util.QuickReply.send(appContext, number, text, line))
        }
    }

    fun toggleMute() {
        host?.setCallMuted(!_isMuted.value) ?: run { _isMuted.value = !_isMuted.value }
    }

    /** Sends the call's sound somewhere else, if the phone has that somewhere. */
    fun setAudioRoute(route: CallAudioRoute) {
        host?.setAudioRoute(route)
    }

    fun toggleSpeaker() {
        val next = if (_audioRoute.value == CallAudioRoute.SPEAKER) {
            CallAudioRoute.EARPIECE
        } else {
            CallAudioRoute.SPEAKER
        }
        host?.setAudioRoute(next)
    }

    fun toggleHold() {
        val call = current ?: return
        when (call.state) {
            Call.STATE_HOLDING -> call.unhold()
            Call.STATE_ACTIVE -> call.hold()
            else -> Unit
        }
    }

    fun toggleKeypad() {
        _isKeypadOpen.value = !_isKeypadOpen.value
    }

    fun setKeypadOpen(open: Boolean) {
        _isKeypadOpen.value = open
    }

    /** A digit pressed on the in-call keypad goes down the line as a tone. */
    fun playDtmf(digit: Char) {
        val call = current ?: return
        try {
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        } catch (e: Exception) {
            ZuneLog.w(TAG, "tone $digit went nowhere", e)
        }
    }

    /**
     * Puts the screen up for a call that is being placed, before Telecom has told us about it.
     * Without this the screen would appear a beat late, after the network had already started
     * ringing. Telecom takes over the moment the call exists.
     */
    fun expectOutgoingCall(
        name: String,
        number: String,
        photo: String? = null,
        simLabel: String? = null
    ) {
        if (_callStatus.value != CallStatus.IDLE && _callStatus.value != CallStatus.ENDED) return
        endedResetJob?.cancel()
        resetState()
        _contactName.value = name.ifBlank { number }
        _phoneNumber.value = number
        _photoUri.value = photo
        _simLabel.value = simLabel
        _callStatus.value = CallStatus.OUTGOING
    }

    /** Gives up on a call that never turned into one — the dial failed, or was refused. */
    fun abandonExpectedCall() {
        if (current == null && _callStatus.value == CallStatus.OUTGOING) finishOnScreen()
    }

    private fun startDurationTimer(connectTimeMillis: Long) {
        if (durationTimerJob?.isActive == true) return
        durationTimerJob = scope.launch {
            while (true) {
                _callDurationSeconds.value = if (connectTimeMillis > 0L) {
                    ((System.currentTimeMillis() - connectTimeMillis) / 1000).toInt().coerceAtLeast(0)
                } else {
                    _callDurationSeconds.value + 1
                }
                delay(1000L)
            }
        }
    }

    private fun resetState() {
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
        _canHold.value = false
        _simLabel.value = null
        _audioRoute.value = CallAudioRoute.EARPIECE
    }

    fun formatDuration(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private const val TAG = "CallManager"
    private const val ENDED_LINGER_MILLIS = 1600L

    /** Telecom's state as the screen understands it; null once there is nothing to show. */
    fun statusOf(state: Int): CallStatus? = when (state) {
        Call.STATE_RINGING -> CallStatus.INCOMING
        Call.STATE_CONNECTING, Call.STATE_DIALING, Call.STATE_PULLING_CALL -> CallStatus.OUTGOING
        Call.STATE_ACTIVE, Call.STATE_HOLDING -> CallStatus.ACTIVE
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> null
        else -> null
    }
}
