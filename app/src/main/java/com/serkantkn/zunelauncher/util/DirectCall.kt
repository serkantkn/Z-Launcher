package com.serkantkn.zunelauncher.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.flow.first

/**
 * The "direct call" switch in Settings, finally wired to something.
 *
 * On, a tap on a number rings it straight away; off, or without the CALL_PHONE permission, the
 * dialler opens with the number filled in and the person presses the green button themselves.
 * The phone and people hubs ask here for the intent to fire rather than building their own, so
 * the switch means the same thing on every screen that places a call.
 */
object DirectCall {

    /** Whether the launcher may place a call without the dialler in between. */
    fun canCallDirectly(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    /**
     * The intent for [number]: ACTION_CALL when the switch is on and the permission is held,
     * ACTION_DIAL otherwise. Reads the setting, so call it off the main thread or from a
     * coroutine; [intentFor] takes the setting as an argument for callers that already have it.
     */
    suspend fun intent(context: Context, number: String): Intent {
        val enabled = runCatching { context.appContainer.settingsDataStore.directCallEnabled.first() }.getOrDefault(false)
        return intentFor(context, number, enabled)
    }

    /** The same, with the switch's value in hand. */
    fun intentFor(context: Context, number: String, directCallEnabled: Boolean): Intent {
        val uri = Uri.parse("tel:" + Uri.encode(number.trim()))
        val action = if (directCallEnabled && canCallDirectly(context)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
