package com.serkantkn.zunelauncher.data.service

import android.Manifest
import android.app.Notification
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.util.PhoneAccounts
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The launcher's end of Telecom.
 *
 * Android binds this — with its own user interface rather than the system dialer's — only while
 * the launcher holds the default phone app role. Everything the call screen does goes through the
 * [Call] objects kept here: answering, turning down, hanging up, holding, tones and the audio
 * route are all the real thing.
 */
class WpInCallService : InCallService(), CallManager.CallHost {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val calls = mutableListOf<Call>()

    override fun onCreate() {
        super.onCreate()
        CallManager.attach(this)
    }

    override fun onDestroy() {
        CallManager.detach(this)
        onCallGone()
        scope.cancel()
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        calls += call
        CallManager.onCallAdded(call)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        calls -= call
        CallManager.onCallRemoved(call)
        // A second call can still be up; show whichever one is left.
        calls.lastOrNull()?.let { CallManager.onCallAdded(it) }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        CallManager.onAudioStateChanged(audioState)
    }

    // ── What the call screen needs of the service ────────────────────────────────────────────

    // Telecom's own setMuted is final, so the call screen reaches it through this.
    override fun setCallMuted(muted: Boolean) {
        setMuted(muted)
    }

    override fun setAudioRoute(route: CallAudioRoute) {
        val telecomRoute = when (route) {
            CallAudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallAudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallAudioRoute.HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            CallAudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
        }
        super.setAudioRoute(telecomRoute)
    }

    /**
     * Who is calling. Telecom hands over a number; the name and the face come from the phone's own
     * contacts, looked up off the main thread because a call screen cannot wait on a query.
     */
    override fun lookUpCaller(number: String, onFound: (String?, String?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        scope.launch {
            val found = withContext(Dispatchers.IO) { queryContact(number) }
            onFound(found?.first, found?.second)
        }
    }

    private fun queryContact(number: String): Pair<String?, String?>? = try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_URI
            ),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1) else null
        }
    } catch (e: Exception) {
        ZuneLog.w(TAG, "could not look up $number", e)
        null
    }

    /** Which SIM a call is on, named the way the phone's own settings name it. */
    override fun labelOfLine(handle: android.telecom.PhoneAccountHandle?): String? =
        PhoneAccounts.labelOf(this, handle)

    /**
     * A call has to be reachable when the launcher is not what is on screen, and when the screen
     * is off altogether. The notification is that path: it rings through as a full screen intent
     * and stays for the length of the call.
     *
     * Android will not take a CallStyle notification from an app that is merely running: a ringing
     * call is allowed through on the strength of its full screen intent, and a call in progress
     * only as a foreground service. So a call in progress makes this service one.
     */
    override fun onCallVisible(status: CallStatus, name: String, number: String) {
        val notification = CallNotifier.build(this, status, name.ifBlank { number }) ?: return
        if (status == CallStatus.INCOMING) {
            CallNotifier.post(this, notification)
        } else {
            goForeground(notification)
        }
    }

    override fun onCallGone() {
        if (inForeground) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            inForeground = false
        }
        CallNotifier.clear(this)
    }

    private var inForeground = false

    /**
     * Being the phone app is what makes this allowed while nothing of ours is on screen. If the
     * system refuses anyway, the call is still perfectly usable — only the way back to it from
     * another app is lost — so the refusal is noted rather than thrown.
     */
    private fun goForeground(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this,
                CallNotifier.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
            )
            inForeground = true
        } catch (e: Exception) {
            ZuneLog.w(TAG, "not allowed to keep the call in front", e)
            CallNotifier.post(this, notification)
        }
    }

    private companion object {
        const val TAG = "WpInCallService"
    }
}
