package com.serkantkn.zunelauncher.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

/**
 * The voicemail box.
 *
 * A launcher cannot read anybody's messages — visual voicemail belongs to the carrier — but it can
 * do the one thing every phone has always done with it: know the number, know how many messages
 * are waiting, and ring it when the 1 key is held down.
 */
object Voicemail {

    private const val TAG = "Voicemail"

    /** The number the carrier answers voicemail on, or null if the phone will not say. */
    fun number(context: Context): String? = readFrom(context) { it.voiceMailNumber }
        ?.takeIf { it.isNotBlank() }

    /** What the carrier calls its voicemail, for when it has a name rather than a number. */
    fun label(context: Context): String? = readFrom(context) { it.voiceMailAlphaTag }
        ?.takeIf { it.isNotBlank() }

    /**
     * How many voicemail messages are waiting.
     *
     * Android keeps no public count, so this counts the unheard voicemails the carrier has filed
     * in the call log. Carriers without visual voicemail file none, and nought here honestly means
     * "nothing waiting, as far as this phone has been told" rather than "no messages".
     */
    fun waitingCount(context: Context): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return 0
        }
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.IS_READ} = 0",
                arrayOf(CallLog.Calls.VOICEMAIL_TYPE.toString()),
                null
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not count what is waiting", e)
            0
        }
    }

    /** Whether there is anything to ring at all. */
    fun exists(context: Context): Boolean = number(context) != null

    /**
     * Rings the voicemail box. It is a call like any other, so it goes out the same way and shows
     * on the same screen.
     */
    fun call(context: Context): Boolean {
        val number = number(context) ?: return false
        val name = label(context) ?: context.getString(com.serkantkn.zunelauncher.R.string.phone_voicemail)
        return PhoneCaller.call(context, number, name) != CallOutcome.FAILED
    }

    /**
     * Telephony will not answer any of this without permission, and throws rather than saying no,
     * so every read goes through here. Being the phone app is its own kind of permission, so that
     * is tried too and a refusal is caught below rather than guessed at.
     */
    private fun <T> readFrom(context: Context, read: (TelephonyManager) -> T): T? {
        val permitted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!permitted && !PhoneCaller.isDefaultDialer(context)) return null
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return null
        return try {
            read(telephony)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the phone would not talk about voicemail", e)
            null
        }
    }
}
