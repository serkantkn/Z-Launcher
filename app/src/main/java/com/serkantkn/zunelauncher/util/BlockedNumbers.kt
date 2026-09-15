package com.serkantkn.zunelauncher.util

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.Telephony

/** A number the phone has been told to turn away, as the system's own list holds it. */
data class BlockedNumber(val id: Long, val number: String)

/**
 * The phone's blocked-number list.
 *
 * This is the system's list, not a list of the launcher's own: what goes in here is what Android
 * turns away before it ever rings, and it stays blocked if the launcher is uninstalled tomorrow.
 * Android only lets the phone app and the messages app touch it, which is why everything here
 * checks [canBlock] first and why the hub hides the button when it is false.
 */
object BlockedNumbers {

    private const val TAG = "BlockedNumbers"

    /** Whether this phone, this user and this app are all allowed to change the list. */
    fun canBlock(context: Context): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(context) && isAllowedToEdit(context)
    } catch (e: Exception) {
        ZuneLog.w(TAG, "cannot tell whether numbers can be blocked", e)
        false
    }

    /** Only the default phone app and the default messages app may edit the system's list. */
    private fun isAllowedToEdit(context: Context): Boolean =
        PhoneCaller.isDefaultDialer(context) ||
            Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

    fun isBlocked(context: Context, number: String): Boolean {
        if (number.isBlank() || !canBlock(context)) return false
        return try {
            BlockedNumberContract.isBlocked(context, number)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not ask whether $number is blocked", e)
            false
        }
    }

    /** Adds a number to the list. Blocking one already on it changes nothing. */
    fun block(context: Context, number: String): Boolean {
        val dialable = PhoneNumbers.toDialable(number)
        if (dialable.isBlank() || !canBlock(context)) return false
        if (isBlocked(context, dialable)) return true
        return try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, dialable)
            }
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                values
            ) != null
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not block $number", e)
            false
        }
    }

    fun unblock(context: Context, number: String): Boolean {
        if (number.isBlank() || !canBlock(context)) return false
        return try {
            BlockedNumberContract.unblock(context, number) > 0
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not unblock $number", e)
            false
        }
    }

    /** Everything on the list, newest first, for the page that shows it. */
    fun all(context: Context): List<BlockedNumber> {
        if (!canBlock(context)) return emptyList()
        return try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
                ),
                null,
                null,
                "${BlockedNumberContract.BlockedNumbers.COLUMN_ID} DESC"
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(1) ?: continue
                        add(BlockedNumber(cursor.getLong(0), number))
                    }
                }
            }.orEmpty()
        } catch (e: Exception) {
            ZuneLog.w(TAG, "could not read the blocked list", e)
            emptyList()
        }
    }
}
