package com.serkantkn.zunelauncher.util

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

/**
 * One of the phone's lines: a SIM card, as Telecom describes it.
 *
 * [key] is what gets written down when the user picks a line to keep, because a
 * [PhoneAccountHandle] itself cannot be stored.
 */
data class SimLine(
    val key: String,
    val handle: PhoneAccountHandle,
    val label: String,
    val number: String?
)

/**
 * Which SIM a call goes out on.
 *
 * On a phone with one SIM none of this is ever seen: [lines] returns a single line, the hub asks
 * nothing and the call goes where it was always going to go. It only comes into view on a phone
 * with two, where the question "from which number?" is a real one.
 */
object PhoneAccounts {

    private const val TAG = "PhoneAccounts"

    /** The SIM cards that can place a call, in the order Telecom lists them. */
    fun lines(context: Context): List<SimLine> {
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return emptyList()
        if (!canRead(context)) return emptyList()
        return try {
            telecom.callCapablePhoneAccounts
                .mapNotNull { handle -> telecom.getPhoneAccount(handle)?.let { handle to it } }
                // A voice-over-internet account is not a SIM, and asking about it would only
                // put a chooser in front of people who have one phone line.
                .filter { (_, account) -> account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION) }
                .mapIndexed { index, (handle, account) ->
                    SimLine(
                        key = keyOf(handle),
                        handle = handle,
                        label = account.label?.toString()?.takeIf { it.isNotBlank() }
                            ?: account.shortDescription?.toString()?.takeIf { it.isNotBlank() }
                            ?: "SIM ${index + 1}",
                        number = account.address?.schemeSpecificPart?.takeIf { it.isNotBlank() }
                    )
                }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "Telecom would not list the phone's lines", e)
            emptyList()
        }
    }

    /** Whether there is a choice to be made at all. */
    fun hasChoice(context: Context): Boolean = lines(context).size > 1

    /** The line that was written down, or null if it has since been taken out of the phone. */
    fun lineFor(context: Context, key: String?): SimLine? {
        if (key.isNullOrBlank()) return null
        return lines(context).firstOrNull { it.key == key }
    }

    /** What to call the line a call is on, for the call screen's corner. */
    fun labelOf(context: Context, handle: PhoneAccountHandle?): String? {
        if (handle == null) return null
        val all = lines(context)
        // On a single-SIM phone the line is never in question, so naming it is only clutter.
        if (all.size < 2) return null
        return all.firstOrNull { it.handle == handle }?.label
    }

    /** A handle written as one string, so a choice can be remembered between runs. */
    fun keyOf(handle: PhoneAccountHandle): String =
        encodeLineKey(handle.componentName.flattenToString(), handle.id)

    /** The handle a [keyOf] string came from, without asking Telecom. */
    fun handleOf(key: String): PhoneAccountHandle? {
        val (component, id) = decodeLineKey(key) ?: return null
        return PhoneAccountHandle(ComponentName.unflattenFromString(component) ?: return null, id)
    }

    private fun canRead(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED || PhoneCaller.isDefaultDialer(context)

    private const val SEPARATOR = "|"
}

/**
 * The two halves of a line key, kept apart from Telecom so the splitting can be reasoned about.
 *
 * A flattened component name never contains the separator but an account id may, so the key
 * splits at the first one, not the last.
 */
internal fun encodeLineKey(component: String, id: String): String = "$component|$id"

internal fun decodeLineKey(key: String): Pair<String, String>? {
    val at = key.indexOf('|')
    if (at <= 0) return null
    return key.substring(0, at) to key.substring(at + 1)
}
