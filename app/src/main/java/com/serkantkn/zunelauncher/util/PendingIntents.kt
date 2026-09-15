package com.serkantkn.zunelauncher.util

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * Firing somebody else's pending intent and actually getting a screen.
 *
 * A notification's own intent belongs to the app that posted it, so sending it means that app
 * starts an activity — and from Android 14 the system blocks an app starting an activity it was
 * not itself in front for. The launcher *is* in front, which is exactly the case the platform
 * allows: it just has to say so, by handing the send an options bundle that passes its own right
 * to start something along with the intent.
 *
 * Without this the send succeeds, nothing appears, and the only sign is a line in the log reading
 * "Background activity launch blocked".
 */
fun PendingIntent.sendAllowingBackgroundStart(context: Context): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val options = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            .toBundle()
        send(context, 0, null, null, null, null, options)
    } else {
        send()
    }
    true
} catch (e: Exception) {
    ZuneLog.w(TAG, "the app's own intent would not open", e)
    false
}

private const val TAG = "PendingIntents"
