package com.serkantkn.zeta.data.receiver

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import com.serkantkn.zeta.data.repository.MessagingBridge
import com.serkantkn.zeta.data.repository.SmsRepository
import com.serkantkn.zeta.data.service.MessageNotifier
import com.serkantkn.zeta.data.repository.MmsTransport
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.util.BlockedNumbers
import com.serkantkn.zeta.util.MmsPdu
import kotlinx.coroutines.flow.first
import com.serkantkn.zeta.util.MmsNotificationParser
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * A picture message arriving, or a report about one sent.
 *
 * Android hands `WAP_PUSH_DELIVER` to the messaging app alone, and what it hands over is not the
 * picture but a note that one is waiting: who sent it, how big it is, where to fetch it. The note
 * is filed into the phone's own message store, the way every messaging app files it, and then -
 * while the user wants that - the message is fetched and the note becomes it ([MmsTransport]).
 * The sender is told about by a notification like any other message. A report that a message
 * sent arrived, or was read, comes the same way and is filed against that message.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        if (intent?.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        if (intent.type != MMS_CONTENT_TYPE) return

        val data = intent.getByteArrayExtra("data")
        // Not every push is a message waiting: a report that one sent arrived, or was read,
        // comes the same way.
        val report = MmsPdu.parse(data)
        if (report != null && (report.type == MmsPdu.TYPE_DELIVERY_IND || report.type == MmsPdu.TYPE_READ_ORIG_IND)) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try { MmsTransport.onReport(ctx, report) } finally { pending.finish() }
            }
            return
        }
        val push = MmsNotificationParser.parse(data)
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
                val (threadId, row) = file(ctx, push, subscriptionId)
                MessagingBridge.messagesChanged()
                // Fetched at once while the user wants that; otherwise it waits to be tapped.
                if (row > 0L && ctx.appContainer.settingsDataStore.mmsAutoDownload.first()) {
                    MmsTransport.download(ctx, row, push.contentLocation, subscriptionId)
                }
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
        push: com.serkantkn.zeta.util.MmsNotification,
        subscriptionId: Int
    ): Pair<Long, Long> {
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
        val row = context.contentResolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return threadId to 0L
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
        return threadId to mmsId
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
