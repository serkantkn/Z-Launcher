package com.serkantkn.zunelauncher.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telecom.PhoneAccountHandle
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turning down a call with a message instead of silence.
 *
 * The message is a real text message: it goes out through the phone's own radio, on the SIM the
 * call came in on, and it is written into the phone's message history the same way the messages
 * app would write it. What it is not is a notice on screen saying a message was sent.
 */
object QuickReply {

    private const val TAG = "QuickReply"

    /** Whether a message can be sent at all, so the call screen can leave the button out. */
    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Sends [text] to [number] over [account]'s SIM, and returns whether the radio took it.
     *
     * True here means handed to the network, not read by anybody: a text message has no way of
     * telling us it arrived.
     */
    suspend fun send(
        context: Context,
        number: String,
        text: String,
        account: PhoneAccountHandle? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!canSend(context) || number.isBlank() || text.isBlank()) return@withContext false
        val dialable = PhoneNumbers.toDialable(number)
        if (dialable.isBlank()) return@withContext false

        try {
            val manager = smsManager(context, account) ?: return@withContext false
            val parts = manager.divideMessage(text)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(dialable, null, parts, null, null)
            } else {
                manager.sendTextMessage(dialable, null, text, null, null)
            }
            recordAsSent(context, dialable, text)
            true
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the message would not go out", e)
            false
        }
    }

    /** The radio for the SIM the call is on, so a reply leaves from the number that was rung. */
    private fun smsManager(context: Context, account: PhoneAccountHandle?): SmsManager? {
        val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return null

        val subscription = subscriptionOf(context, account) ?: return base
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                base.createForSubscriptionId(subscription)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscription)
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "no radio for subscription $subscription", e)
            base
        }
    }

    private fun subscriptionOf(context: Context, account: PhoneAccountHandle?): Int? {
        if (account == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null
        return try {
            telephony.getSubscriptionId(account).takeIf { it >= 0 }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Android writes sent messages into the phone's history for the app that owns messaging, and
     * for nobody else. If that is this launcher, the writing is ours to do — otherwise the message
     * is still sent, it simply will not show in a history we are not allowed to touch.
     */
    private fun recordAsSent(context: Context, number: String, text: String) {
        if (Telephony.Sms.getDefaultSmsPackage(context) != context.packageName) return
        try {
            context.contentResolver.insert(
                Telephony.Sms.Sent.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, number)
                    put(Telephony.Sms.BODY, text)
                    put(Telephony.Sms.READ, 1)
                }
            )
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the message went out but could not be filed", e)
        }
    }
}
