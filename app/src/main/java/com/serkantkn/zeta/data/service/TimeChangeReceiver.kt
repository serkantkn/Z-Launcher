package com.serkantkn.zeta.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The clock under the alarms moved, or the launcher itself was replaced.
 *
 * An alarm is booked as a wall-clock moment. Change the zone and seven o'clock is a different
 * moment; set the clock back an hour and a booked alarm rings an hour early, or already went by.
 * An update of the app throws every booking away outright. In each case the only right thing is
 * to work the moments out again from the records, which is what [ClockRearm] does.
 *
 * Manifest:
 * ```
 * <receiver android:name=".data.service.TimeChangeReceiver" android:exported="false">
 *     <intent-filter>
 *         <action android:name="android.intent.action.TIMEZONE_CHANGED" />
 *         <action android:name="android.intent.action.TIME_SET" />
 *         <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
 *     </intent-filter>
 * </receiver>
 * ```
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClockRearm.rearmAll(context, action)
            } catch (e: Exception) {
                ZuneLog.e("TimeChangeReceiver", "onReceive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
