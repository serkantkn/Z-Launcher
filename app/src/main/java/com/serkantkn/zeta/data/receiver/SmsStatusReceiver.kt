package com.serkantkn.zeta.data.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.serkantkn.zeta.data.repository.MessagingBridge
import com.serkantkn.zeta.data.service.MessageNotifier
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * What the network says about a message after it has left.
 *
 * A send finishes in two stages the radio reports separately, sometimes minutes apart: whether the
 * message left the phone, and — if a delivery report was asked for — whether it reached the other
 * one. Both come back here against the row the message was filed under, so the tick in the
 * conversation is the network's own answer rather than a guess made at the moment of sending.
 */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val row = intent?.data ?: return

        when (intent.action) {
            ACTION_SENT -> {
                val ok = resultCode == Activity.RESULT_OK
                if (ok) {
                    // A part that succeeds must not undo a part that already failed.
                    if (currentType(ctx, row) == Telephony.Sms.MESSAGE_TYPE_FAILED) return
                    update(ctx, row, Telephony.Sms.TYPE to Telephony.Sms.MESSAGE_TYPE_SENT)
                } else {
                    ZuneLog.w(TAG, "the message did not leave the phone (code $resultCode)")
                    update(ctx, row, Telephony.Sms.TYPE to Telephony.Sms.MESSAGE_TYPE_FAILED)
                }
            }

            ACTION_DELIVERED -> {
                val arrived = resultCode == Activity.RESULT_OK
                // A long message reports once per part; it arrived when the first part did, and
                // that is the one time to say so.
                val firstReport = arrived && currentStatus(ctx, row) != Telephony.Sms.STATUS_COMPLETE
                update(ctx, row, Telephony.Sms.STATUS to if (arrived) Telephony.Sms.STATUS_COMPLETE else Telephony.Sms.STATUS_FAILED)
                if (firstReport) {
                    val pending = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            if (ctx.appContainer.settingsDataStore.smsDeliveredNotice.first()) {
                                val (address, threadId) = addressOf(ctx, row)
                                MessageNotifier.showDelivered(ctx, address, threadId)
                            }
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
        MessagingBridge.messagesChanged()
    }

    private fun currentStatus(context: Context, row: android.net.Uri): Int = try {
        context.contentResolver.query(row, arrayOf(Telephony.Sms.STATUS), null, null, null)?.use {
            if (it.moveToFirst()) it.getInt(0) else -1
        } ?: -1
    } catch (e: Exception) {
        -1
    }

    private fun addressOf(context: Context, row: android.net.Uri): Pair<String, Long> = try {
        context.contentResolver.query(row, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.THREAD_ID), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0).orEmpty() to it.getLong(1) else "" to 0L
        } ?: ("" to 0L)
    } catch (e: Exception) {
        "" to 0L
    }

    private fun currentType(context: Context, row: android.net.Uri): Int = try {
        context.contentResolver.query(row, arrayOf(Telephony.Sms.TYPE), null, null, null)?.use {
            if (it.moveToFirst()) it.getInt(0) else -1
        } ?: -1
    } catch (e: Exception) {
        -1
    }

    private fun update(context: Context, row: android.net.Uri, field: Pair<String, Int>) {
        try {
            context.contentResolver.update(
                row,
                ContentValues().apply { put(field.first, field.second) },
                null,
                null
            )
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the message's state could not be written", e)
        }
    }

    companion object {
        private const val TAG = "SmsStatusReceiver"
        const val ACTION_SENT = "com.serkantkn.zeta.SMS_SENT"
        const val ACTION_DELIVERED = "com.serkantkn.zeta.SMS_DELIVERED"
    }
}
