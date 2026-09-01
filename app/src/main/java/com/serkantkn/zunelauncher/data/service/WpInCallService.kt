package com.serkantkn.zunelauncher.data.service

import android.telecom.Call
import android.telecom.InCallService

/**
 * Telecom InCallService required by Android OS for default Phone/Dialer app eligibility.
 * Forwards system call events to CallManager.
 */
class WpInCallService : InCallService() {

    override fun onCallAdded(call: Call?) {
        super.onCallAdded(call)
        if (call == null) return

        val number = call.details?.handle?.schemeSpecificPart ?: ""
        val state = call.state

        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(c: Call?, newState: Int) {
                super.onStateChanged(c, newState)
                when (newState) {
                    Call.STATE_ACTIVE -> CallManager.answerCall()
                    Call.STATE_DISCONNECTED -> CallManager.endCall()
                }
            }
        })

        if (state == Call.STATE_RINGING) {
            CallManager.startIncomingCall(name = "", number = number)
        } else if (state == Call.STATE_DIALING || state == Call.STATE_CONNECTING) {
            CallManager.startOutgoingCall(name = "", number = number)
        } else if (state == Call.STATE_ACTIVE) {
            CallManager.answerCall()
        }
    }

    override fun onCallRemoved(call: Call?) {
        super.onCallRemoved(call)
        CallManager.endCall()
    }
}
