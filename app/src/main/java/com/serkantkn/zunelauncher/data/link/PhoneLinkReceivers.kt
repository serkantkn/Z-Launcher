package com.serkantkn.zunelauncher.data.link

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.TelephonyManager
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Whether this phone has anything to tell, and is allowed to. */
private fun sending(context: Context): LinkStore.SendSettings? {
    if (com.serkantkn.zunelauncher.ui.theme.isTelevision(context)) return null
    val store = LinkStore.attach(context)
    if (store.peers.value.isEmpty()) return null
    return store.send.value.takeIf { it.enabled }
}

private suspend fun nameFor(context: Context, number: String): String =
    if (number.isBlank()) context.localizedString(R.string.phone_private_number)
    else runCatching { SmsRepository.contactNameFor(context, number) }.getOrDefault(number)

/**
 * Calls, from the phone's own state - whichever app answers them. A ringing phone tells the TV
 * who is calling; answered, the notice goes from the TV; left to ring out, it says missed.
 *
 * Android sends the ringing state twice when the app may read the call log - once without the
 * number and once with it - so a ring without one waits a moment for the one that has it.
 */
class PhoneLinkCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val app = context.applicationContext
        val settings = sending(app) ?: return
        if (!settings.calls) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val pending = goAsync()
        PhoneLink.scope.launch {
            try {
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING -> ringing(app, number)
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> if (ringingNumber != null) {
                        ringingNumber = null
                        PhoneLink.announce(app, LinkNotice(LinkNotice.Kind.CALL_ENDED, ""))
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> ringingNumber?.let { missed ->
                        ringingNumber = null
                        PhoneLink.announce(app, LinkNotice(LinkNotice.Kind.MISSED_CALL, nameFor(app, missed), missed))
                    }
                }
            } catch (e: Exception) {
                ZuneLog.w(TAG, "the call could not be told", e)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun ringing(context: Context, number: String?) {
        if (number == null) {
            // The numbered broadcast usually follows at once; if it does not come, the call is
            // told without a number.
            val waitingSince = System.currentTimeMillis()
            lastRingAt = waitingSince
            delay(NUMBER_WAIT_MILLIS)
            if (ringingNumber != null || lastRingAt != waitingSince) return
            ringingNumber = ""
            PhoneLink.announce(context, LinkNotice(LinkNotice.Kind.CALL, nameFor(context, "")))
            return
        }
        if (ringingNumber == number) return
        ringingNumber = number
        lastRingAt = System.currentTimeMillis()
        PhoneLink.announce(context, LinkNotice(LinkNotice.Kind.CALL, nameFor(context, number), number))
    }

    companion object {
        private const val TAG = "PhoneLinkCall"
        private const val NUMBER_WAIT_MILLIS = 600L

        /** The number ringing now, "" for one without a number, null when nothing rings. */
        @Volatile
        private var ringingNumber: String? = null

        @Volatile
        private var lastRingAt = 0L
    }
}

/**
 * Text messages, as they arrive - whichever app files them. Only the sender goes to the TV unless
 * the user let the text go too.
 */
class PhoneLinkSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val app = context.applicationContext
        val settings = sending(app) ?: return
        if (!settings.sms) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()
        if (parts.isNullOrEmpty()) return
        val sender = parts.first().displayOriginatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val pending = goAsync()
        PhoneLink.scope.launch {
            try {
                PhoneLink.announce(
                    app,
                    LinkNotice(
                        LinkNotice.Kind.SMS,
                        nameFor(app, sender),
                        sender,
                        text = body.takeIf { settings.text && it.isNotBlank() }?.take(MAX_TEXT)
                    )
                )
            } catch (e: Exception) {
                ZuneLog.w("PhoneLinkSms", "the message could not be told", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Messages from other apps (WhatsApp, Telegram, ...), read from their notifications by the
 * launcher's notification listener while it has access. The phone's own SMS app is left out: its
 * messages come through [PhoneLinkSmsReceiver] already.
 */
internal object PhoneLinkAppMessages {
    private val recent = LinkedHashMap<String, Long>()

    fun onPosted(context: Context, sbn: android.service.notification.StatusBarNotification) {
        val app = context.applicationContext
        val settings = sending(app) ?: return
        if (!settings.apps) return
        val n = sbn.notification
        if (n.category != android.app.Notification.CATEGORY_MESSAGE) return
        if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        if (sbn.packageName == app.packageName) return
        if (sbn.packageName == Telephony.Sms.getDefaultSmsPackage(app)) return
        val title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: return
        val text = n.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty()
        // A notification updated in place - another message in the same chat, a "sending" tick -
        // is told once a few seconds at most.
        val now = System.currentTimeMillis()
        synchronized(recent) {
            val key = sbn.packageName + "|" + title
            if (now - (recent[key] ?: 0L) < SAME_CHAT_MILLIS) return
            recent[key] = now
            while (recent.size > 32) recent.remove(recent.keys.first())
        }
        val appName = runCatching {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        PhoneLink.scope.launch {
            runCatching {
                PhoneLink.announce(
                    app,
                    LinkNotice(
                        LinkNotice.Kind.APP_MESSAGE,
                        title,
                        text = text.takeIf { settings.text && it.isNotBlank() }?.take(MAX_TEXT),
                        app = appName
                    )
                )
            }.onFailure { ZuneLog.w("PhoneLinkApps", "the message could not be told", it) }
        }
    }

    private const val SAME_CHAT_MILLIS = 5_000L
}

private const val MAX_TEXT = 300
