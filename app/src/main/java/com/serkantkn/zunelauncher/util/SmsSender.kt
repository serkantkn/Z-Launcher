package com.serkantkn.zunelauncher.util

import android.Manifest
import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.data.receiver.SmsStatusReceiver
import com.serkantkn.zunelauncher.data.repository.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A SIM a message can leave from.
 *
 * Text messages are addressed by subscription rather than by the Telecom account a call uses, so
 * this is its own small list rather than a reuse of the dialler's.
 */
data class SimCard(val subscriptionId: Int, val label: String, val number: String?)

/** How a send ended, in terms the hub can put on screen. */
enum class SendOutcome {
    /** Handed to the radio. Whether it arrives is the network's business, reported later. */
    HANDED_OVER,

    /** There is no permission to send. */
    NO_PERMISSION,

    /** There is nothing to send it to. */
    BAD_NUMBER,

    /** Nothing to send. */
    EMPTY,

    /** The radio would not take it. */
    FAILED
}

/**
 * Sending a text message, all the way through.
 *
 * A message is more than the radio call: a long message has to be split, the outgoing copy has to
 * be written into the phone's own history so the conversation shows it, and the network's later
 * answer — went out, arrived, failed — has to find its way back to that same copy. The pieces that
 * happen later are handled by [SmsStatusReceiver], which is why the message is filed before it is
 * sent rather than after.
 */
object SmsSender {

    private const val TAG = "SmsSender"

    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Sends [text] to [address], filing it under [threadId] when the launcher owns messaging.
     *
     * [subscriptionId] picks the SIM; -1 leaves the choice to the phone.
     */
    suspend fun send(
        context: Context,
        address: String,
        text: String,
        threadId: Long = 0L,
        subscriptionId: Int = -1
    ): SendOutcome = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext SendOutcome.EMPTY
        if (!canSend(context)) return@withContext SendOutcome.NO_PERMISSION
        val dialable = PhoneNumbers.toDialable(address)
        if (dialable.isBlank()) return@withContext SendOutcome.BAD_NUMBER

        val manager = smsManager(context, subscriptionId)
            ?: return@withContext SendOutcome.FAILED

        // The thread has to exist before the copy can be filed under it.
        val thread = threadId.takeIf { it > 0L }
            ?: SmsRepository.getOrCreateThreadId(context, setOf(dialable))
            ?: 0L

        val filed = fileAsOutgoing(context, dialable, text, thread, subscriptionId)

        return@withContext try {
            val parts = manager.divideMessage(text)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(
                    dialable,
                    null,
                    parts,
                    ArrayList(parts.indices.map { sentIntent(context, filed, it) }),
                    ArrayList(parts.indices.map { deliveredIntent(context, filed, it) })
                )
            } else {
                manager.sendTextMessage(
                    dialable,
                    null,
                    text,
                    sentIntent(context, filed, 0),
                    deliveredIntent(context, filed, 0)
                )
            }
            // A message just sent is no longer a draft.
            SmsRepository.saveDraft(context, thread, dialable, "")
            SendOutcome.HANDED_OVER
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the message would not go out", e)
            markFailed(context, filed)
            SendOutcome.FAILED
        }
    }

    /**
     * Writes the outgoing copy into the phone's history and hands back its row.
     *
     * Android lets only the app that owns messaging write here. When that is not us the message is
     * still sent — it simply will not appear in a history we are not allowed to touch, and there is
     * no row for the delivery report to come back to either.
     */
    private fun fileAsOutgoing(
        context: Context,
        address: String,
        text: String,
        threadId: Long,
        subscriptionId: Int
    ): Uri? {
        if (!SmsRepository.isDefaultSmsApp(context)) return null
        return try {
            context.contentResolver.insert(
                Telephony.Sms.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, address)
                    put(Telephony.Sms.BODY, text)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
                    if (threadId > 0L) put(Telephony.Sms.THREAD_ID, threadId)
                    if (subscriptionId >= 0) put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            )
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the message went out but could not be filed", e)
            null
        }
    }

    private fun markFailed(context: Context, row: Uri?) {
        val uri = row ?: return
        try {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_FAILED) },
                null,
                null
            )
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the failure could not be recorded", e)
        }
    }

    /**
     * A message the phone filed but never sent — the failed ones a conversation offers to retry.
     * Deleting the old row first keeps a retried message from appearing twice.
     */
    suspend fun resend(context: Context, messageId: Long, address: String, text: String, threadId: Long): SendOutcome =
        withContext(Dispatchers.IO) {
            if (SmsRepository.isDefaultSmsApp(context) && messageId > 0L) {
                runCatching {
                    context.contentResolver.delete(
                        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId),
                        null,
                        null
                    )
                }
            }
            send(context, address, text, threadId)
        }

    private fun sentIntent(context: Context, row: Uri?, part: Int): PendingIntent? =
        statusIntent(context, row, part, SmsStatusReceiver.ACTION_SENT)

    private fun deliveredIntent(context: Context, row: Uri?, part: Int): PendingIntent? =
        statusIntent(context, row, part, SmsStatusReceiver.ACTION_DELIVERED)

    private fun statusIntent(context: Context, row: Uri?, part: Int, action: String): PendingIntent? {
        val uri = row ?: return null
        val intent = Intent(context, SmsStatusReceiver::class.java).apply {
            this.action = action
            data = uri
        }
        // Every part needs its own pending intent, or the radio reports on one and loses the rest.
        val requestCode = (uri.toString() + action + part).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * The SIMs in the phone. Empty when it will not say — no permission, or a phone that keeps
     * the list to itself — and the hub then asks nothing and lets the phone choose.
     */
    fun sims(context: Context): List<SimCard> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }
        return try {
            val manager = context.getSystemService(SubscriptionManager::class.java)
                ?: return emptyList()
            manager.activeSubscriptionInfoList.orEmpty().mapIndexed { index, info ->
                SimCard(
                    subscriptionId = info.subscriptionId,
                    label = info.displayName?.toString()?.takeIf { it.isNotBlank() }
                        ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                        ?: "SIM ${index + 1}",
                    number = info.number?.takeIf { it.isNotBlank() }
                )
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the phone would not list its SIMs", e)
            emptyList()
        }
    }

    /** The radio for a given SIM, or the phone's own choice when no SIM was named. */
    fun smsManager(context: Context, subscriptionId: Int): SmsManager? {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return null

        if (subscriptionId < 0) return base
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                base.createForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "no radio for subscription $subscriptionId", e)
            base
        }
    }
}
