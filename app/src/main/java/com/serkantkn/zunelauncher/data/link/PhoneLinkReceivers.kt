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

/** A notification's title that is only a number - the phone app knew no name for it. */
private fun looksLikeNumber(text: String): Boolean =
    text.count(Char::isDigit) >= 5 && text.all { it.isDigit() || it in "+-() ." }

/** A notification's title, with a bare number turned into its contact's name where there is one. */
internal suspend fun namedTitle(context: Context, title: String): String =
    if (looksLikeNumber(title)) nameFor(context, title) else title

private suspend fun nameFor(context: Context, number: String): String =
    if (number.isBlank()) context.localizedString(R.string.phone_private_number)
    else runCatching { SmsRepository.contactNameFor(context, number) }.getOrDefault(number)

/**
 * Calls, from the phone's own state - whichever app answers them. A ringing phone tells the TV
 * who is calling; answered, the notice goes from the TV; left to ring out, it says missed.
 *
 * Who is calling comes from the number when the launcher may read it (it is the phone app, and
 * Android gave it the call log with that role), otherwise from the phone app's own incoming-call
 * notification, read by the notification listener ([PhoneLinkAppMessages]). Google Play allows
 * the call log and SMS permissions only to the default phone and SMS apps, so they are never
 * asked for here.
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
                    TelephonyManager.EXTRA_STATE_RINGING -> PhoneLinkCalls.ringing(app, number)
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> PhoneLinkCalls.answered(app)
                    TelephonyManager.EXTRA_STATE_IDLE -> PhoneLinkCalls.ended(app)
                }
            } catch (e: Exception) {
                ZuneLog.w("PhoneLinkCall", "the call could not be told", e)
            } finally {
                pending.finish()
            }
        }
    }
}

/** The one call ringing now, put together from the phone's state and the phone app's notification. */
internal object PhoneLinkCalls {
    private var ringing = false
    private var announced = false
    private var name: String? = null
    private var number: String = ""
    /** A name from an incoming-call notification that came before the phone said it was ringing. */
    private var earlyName: String? = null
    private var earlyAt = 0L

    suspend fun ringing(context: Context, incoming: String?) {
        synchronized(this) {
            if (ringing && (incoming == null || incoming == number)) {
                // The second, numbered broadcast of the same ring.
                if (incoming == null || announced) return
            }
            ringing = true
            announced = false
            number = incoming.orEmpty()
            name = earlyName?.takeIf { System.currentTimeMillis() - earlyAt < EARLY_MILLIS }
            earlyName = null
        }
        if (incoming != null) {
            announce(context, nameFor(context, incoming))
            return
        }
        // No number: the phone app's notification, or the numbered broadcast, usually follows.
        delay(WAIT_MILLIS)
        val known = synchronized(this) { if (!ringing || announced) return else name }
        announce(context, known?.let { namedTitle(context, it) } ?: context.localizedString(R.string.phone_private_number))
    }

    /** The phone app's incoming-call notification, with the caller's name on it. */
    fun fromNotification(context: Context, caller: String) {
        val now = synchronized(this) {
            if (!ringing) {
                earlyName = caller
                earlyAt = System.currentTimeMillis()
                return
            }
            if (announced) return
            name = caller
            true
        }
        // (A bare number is named when the notice goes.)
        if (now) PhoneLink.scope.launch { announce(context, namedTitle(context, caller)) }
    }

    suspend fun answered(context: Context) {
        val was = synchronized(this) { ringing.also { ringing = false } }
        if (was) PhoneLink.announce(context, LinkNotice(LinkNotice.Kind.CALL_ENDED, ""))
    }

    suspend fun ended(context: Context) {
        val missed = synchronized(this) {
            if (!ringing) return
            ringing = false
            (name ?: number.ifBlank { null }) to number
        }
        val who = missed.first?.let { namedTitle(context, it) } ?: context.localizedString(R.string.phone_private_number)
        PhoneLink.announce(context, LinkNotice(LinkNotice.Kind.MISSED_CALL, who, missed.second))
    }

    private suspend fun announce(context: Context, who: String) {
        val num = synchronized(this) {
            if (announced) return
            announced = true
            name = who
            number
        }
        PhoneLink.announce(context, LinkNotice(LinkNotice.Kind.CALL, who, num))
    }

    private const val WAIT_MILLIS = 900L
    private const val EARLY_MILLIS = 5_000L
}

/**
 * Text messages, as they arrive, while the launcher is the SMS app (only then does Android - and
 * Google Play - let it receive them). Otherwise the SMS app's own notification brings them, through
 * [PhoneLinkAppMessages]. Only the sender goes to the TV unless the user let the text go too.
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
 * What the phone's other apps announce, read from their notifications by the launcher's
 * notification listener while it has access: messages (WhatsApp, Telegram, ...), and - when the
 * launcher is not the SMS or phone app itself - the SMS app's messages and the phone app's
 * incoming call.
 */
internal object PhoneLinkAppMessages {
    private val recent = LinkedHashMap<String, Long>()

    fun onPosted(context: Context, sbn: android.service.notification.StatusBarNotification) {
        val app = context.applicationContext
        val settings = sending(app) ?: return
        if (sbn.packageName == app.packageName) return
        val n = sbn.notification
        if (n.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0) return
        val title = n.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: return
        if (n.category == android.app.Notification.CATEGORY_CALL) {
            // The phone app's incoming call; its ongoing-call notification is not a ring, and
            // PhoneLinkCalls tells the two apart by the phone's own state.
            if (settings.calls) PhoneLinkCalls.fromNotification(app, title)
            return
        }
        if (n.category != android.app.Notification.CATEGORY_MESSAGE) return
        val smsApp = sbn.packageName == Telephony.Sms.getDefaultSmsPackage(app)
        // While the launcher is the SMS app, its own receiver has the message already.
        if (smsApp && androidx.core.content.ContextCompat.checkSelfPermission(app, android.Manifest.permission.RECEIVE_SMS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        if (if (smsApp) !settings.sms else !settings.apps) return
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
        val appName = if (smsApp) null else runCatching {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        PhoneLink.scope.launch {
            runCatching {
                PhoneLink.announce(
                    app,
                    LinkNotice(
                        if (smsApp) LinkNotice.Kind.SMS else LinkNotice.Kind.APP_MESSAGE,
                        if (smsApp) namedTitle(app, title) else title,
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
