package com.serkantkn.zunelauncher.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.data.service.CallManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How a call was placed, so the caller can say something useful when it was not. */
enum class CallOutcome {
    /** Placed through Telecom: the launcher's own call screen is showing it. */
    PLACED,

    /** A real call, but the phone's own dialler is showing it — the launcher is not the default. */
    HANDED_OVER,

    /** Nothing was dialled. */
    FAILED
}

/**
 * Placing a call.
 *
 * There are two ways out of here and both of them ring a real telephone. While the launcher holds
 * the default phone app role the call goes through Telecom and comes back to the launcher's own
 * in-call service, so the Windows Phone call screen is what the user sees. While it does not, the
 * call is handed to whatever dialler the phone does trust — still a real call, just not our screen.
 * What never happens is a pretend call.
 */
object PhoneCaller {

    private const val TAG = "PhoneCaller"
    private val scope = CoroutineScope(Dispatchers.Main)

    /** Whether the launcher is the phone's dialler, which is what its call screen depends on. */
    fun isDefaultDialer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
            return roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
                roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        }
        val telecom = context.getSystemService(TelecomManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return telecom.defaultDialerPackage == context.packageName
    }

    /** The system's own "make this your phone app" prompt, or null where there is no such role. */
    fun defaultDialerRequest(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }

    fun canPlaceCalls(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Rings [number]. [name] and [photo] are only what the call screen should show while the
     * network is still connecting; Telecom replaces them with what it knows as soon as it can.
     * [account] is the SIM to call from, or null to let the phone choose as it always would.
     */
    fun call(
        context: Context,
        number: String,
        name: String = "",
        photo: String? = null,
        account: PhoneAccountHandle? = null
    ): CallOutcome {
        val dialable = PhoneNumbers.toDialable(number)
        if (dialable.isBlank()) return CallOutcome.FAILED
        val uri = Uri.fromParts("tel", dialable, null)

        if (isDefaultDialer(context) && canPlaceCalls(context)) {
            val telecom = context.getSystemService(TelecomManager::class.java)
            if (telecom != null) {
                return try {
                    // The screen goes up first so there is no gap between the press and the call.
                    CallManager.expectOutgoingCall(
                        name,
                        number,
                        photo,
                        PhoneAccounts.labelOf(context, account)
                    )
                    telecom.placeCall(uri, extrasFor(account))
                    watchForTheCall()
                    CallOutcome.PLACED
                } catch (e: Exception) {
                    ZuneLog.e(TAG, "Telecom would not place the call", e)
                    CallManager.abandonExpectedCall()
                    handOver(context, uri)
                }
            }
        }
        return handOver(context, uri)
    }

    /** Telecom is told which SIM to use the same way the system dialler tells it. */
    private fun extrasFor(account: PhoneAccountHandle?): Bundle? {
        if (account == null) return null
        return Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, account)
        }
    }

    /** Opens the phone's own dialler on the number, dialling it outright where that is allowed. */
    private fun handOver(context: Context, uri: Uri): CallOutcome {
        val action = if (canPlaceCalls(context)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return try {
            context.startActivity(
                Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            CallOutcome.HANDED_OVER
        } catch (e: Exception) {
            ZuneLog.e(TAG, "nothing on this phone would dial", e)
            CallOutcome.FAILED
        }
    }

    /**
     * A call that never arrives leaves the screen sitting on "calling…" forever. Telecom is given
     * a few seconds to produce one, and if it does not the screen is let go of.
     */
    private fun watchForTheCall() {
        scope.launch {
            delay(PLACE_TIMEOUT_MILLIS)
            CallManager.abandonExpectedCall()
        }
    }

    private const val PLACE_TIMEOUT_MILLIS = 6000L
}
