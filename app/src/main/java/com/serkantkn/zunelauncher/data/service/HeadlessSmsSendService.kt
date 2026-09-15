package com.serkantkn.zunelauncher.data.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.util.SmsSender
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turning down a call with a message, asked for by somebody else's dialler.
 *
 * Android sends this when a call is declined with a quick reply and the launcher is the phone's
 * messaging app. It has no screen: the message is sent and the service is done. The launcher's own
 * call screen does not come through here — it sends its reply directly.
 */
class HeadlessSmsSendService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.data?.schemeSpecificPart?.substringBefore('?').orEmpty()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()

        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE ||
            number.isBlank() ||
            text.isBlank()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch {
            val outcome = SmsSender.send(this@HeadlessSmsSendService, number, text)
            ZuneLog.d(TAG, "the declined call's message: $outcome")
            MessagingBridge.messagesChanged()
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"
    }
}
