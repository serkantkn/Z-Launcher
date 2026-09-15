package com.serkantkn.zunelauncher.data.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.data.service.MessageNotifier
import com.serkantkn.zunelauncher.util.BlockedNumbers
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * An arriving text message, when the launcher is the phone's messaging app.
 *
 * This is not a formality. Android hands `SMS_DELIVER` to exactly one app and writes nothing
 * itself: whatever that app does not file is gone. So the message is written into the phone's own
 * history here — the same history every other messaging app reads — and only then shown.
 *
 * The parts of a long message arrive together in one broadcast and are joined back into the one
 * message they were written as.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        if (intent?.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val parts = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the arriving message could not be read", e)
            return
        }
        if (parts.isNullOrEmpty()) return

        val first = parts.first()
        val sender = first.displayOriginatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        if (body.isEmpty()) return

        // A number the user blocked is a number they asked not to hear from.
        if (BlockedNumbers.isBlocked(ctx, sender)) {
            ZuneLog.d(TAG, "a message from a blocked number was not filed")
            return
        }

        val subscriptionId = intent.getIntExtra("subscription", -1)
        val threadId = fileInInbox(ctx, sender, body, first.timestampMillis, subscriptionId)

        MessagingBridge.messagesChanged()
        // The message is already filed; a notification that will not go up must not undo that.
        runCatching { MessageNotifier.showIncoming(ctx, sender, body, threadId) }
            .onFailure { ZuneLog.e(TAG, "the message was filed but could not be shown", it) }
    }

    /** Writes the message where every messaging app looks, and returns its conversation. */
    private fun fileInInbox(
        context: Context,
        sender: String,
        body: String,
        sentAt: Long,
        subscriptionId: Int
    ): Long {
        val threadId = try {
            Telephony.Threads.getOrCreateThreadId(context, sender)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "no conversation for the sender", e)
            0L
        }

        try {
            context.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, sender)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.DATE_SENT, sentAt)
                    put(Telephony.Sms.READ, 0)
                    put(Telephony.Sms.SEEN, 0)
                    if (threadId > 0L) put(Telephony.Sms.THREAD_ID, threadId)
                    if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            )
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the message arrived but could not be filed", e)
        }

        SmsRepository.clearContactCache()
        return threadId
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
