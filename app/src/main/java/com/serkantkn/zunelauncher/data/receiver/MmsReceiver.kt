package com.serkantkn.zunelauncher.data.receiver

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.serkantkn.zunelauncher.data.repository.MessagingBridge
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import com.serkantkn.zunelauncher.data.service.MessageNotifier
import com.serkantkn.zunelauncher.util.BlockedNumbers
import com.serkantkn.zunelauncher.util.MmsNotificationParser
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A picture message, as far as the launcher takes it.
 *
 * Android hands `WAP_PUSH_DELIVER` to the messaging app alone, and what it hands over is not the
 * picture but a note that one is waiting: who sent it, how big it is, where to fetch it. The
 * launcher does not fetch pictures. What it must not do is lose the note — so the note is filed
 * into the phone's own message store, the way every messaging app files it, where the
 * conversation shows "a picture came" and any app that does fetch pictures can still find it.
 * The sender is told about by a notification like any other message.
 *
 * This receiver, and RECEIVE_MMS / RECEIVE_WAP_PUSH with it, stay for a second reason: Android
 * will not offer the messaging role at all to an app that does not declare them.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        if (intent?.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != MMS_CONTENT_TYPE) return

        val push = MmsNotificationParser.parse(intent.getByteArrayExtra("data"))
        if (push == null) {
            ZuneLog.w(TAG, "a picture message push could not be read")
            return
        }
        val subscriptionId = intent.getIntExtra("subscription", -1)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (push.from.isNotBlank() && BlockedNumbers.isBlocked(ctx, push.from)) {
                    ZuneLog.d(TAG, "a picture message from a blocked number was not filed")
                    return@launch
                }
                val threadId = file(ctx, push, subscriptionId)
                MessagingBridge.messagesChanged()
                runCatching { MessageNotifier.showIncomingMms(ctx, push.from, threadId) }
                    .onFailure { ZuneLog.e(TAG, "the picture message was filed but could not be shown", it) }
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the picture message could not be filed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Writes the notification into the inbox and its sender into the address table. */
    private fun file(
        context: Context,
        push: com.serkantkn.zunelauncher.util.MmsNotification,
        subscriptionId: Int
    ): Long {
        val threadId = if (push.from.isBlank()) 0L else try {
            Telephony.Threads.getOrCreateThreadId(context, push.from)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "no conversation for the sender", e)
            0L
        }

        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.MESSAGE_TYPE, MESSAGE_TYPE_NOTIFICATION_IND)
            put(Telephony.Mms.MMS_VERSION, MMS_VERSION_1_2)
            put(Telephony.Mms.TRANSACTION_ID, push.transactionId)
            put(Telephony.Mms.CONTENT_LOCATION, push.contentLocation)
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.MESSAGE_SIZE, push.messageSize)
            put(Telephony.Mms.MESSAGE_CLASS, push.messageClass)
            if (push.expirySeconds > 0L) put(Telephony.Mms.EXPIRY, push.expirySeconds)
            if (push.subject.isNotBlank()) {
                put(Telephony.Mms.SUBJECT, push.subject)
                put(Telephony.Mms.SUBJECT_CHARSET, CHARSET_UTF8)
            }
            if (threadId > 0L) put(Telephony.Mms.THREAD_ID, threadId)
            if (subscriptionId >= 0) put(Telephony.Mms.SUBSCRIPTION_ID, subscriptionId)
        }
        val row = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return threadId
        val mmsId = ContentUris.parseId(row)

        if (push.from.isNotBlank()) {
            runCatching {
                context.contentResolver.insert(
                    Uri.parse("content://mms/$mmsId/addr"),
                    ContentValues().apply {
                        put(Telephony.Mms.Addr.MSG_ID, mmsId)
                        put(Telephony.Mms.Addr.ADDRESS, push.from)
                        put(Telephony.Mms.Addr.TYPE, ADDR_TYPE_FROM)
                        put(Telephony.Mms.Addr.CHARSET, CHARSET_UTF8)
                    }
                )
            }.onFailure { ZuneLog.w(TAG, "the sender could not be filed with the picture message", it) }
        }
        SmsRepository.clearContactCache()
        return threadId
    }

    private companion object {
        const val TAG = "MmsReceiver"
        const val MMS_CONTENT_TYPE = "application/vnd.wap.mms-message"
        /** PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND */
        const val MESSAGE_TYPE_NOTIFICATION_IND = 130
        /** PduHeaders.CURRENT_MMS_VERSION, 1.2 */
        const val MMS_VERSION_1_2 = 0x12
        /** PduHeaders.FROM */
        const val ADDR_TYPE_FROM = 137
        const val CHARSET_UTF8 = 106
    }
}
