package com.serkantkn.zeta.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets every alarm on reboot. Re-arms all enabled alarms (Clock Hub alarms and
 * Notes Hub reminders), the launcher's own event reminders and a running timer once the device
 * has booted — the walk itself is [ClockRearm], shared with the time-change receiver and the cold
 * start — and books the mail sync again.
 *
 * The same walk runs when the user allows exact alarms: what was booked without the exact minute
 * meanwhile is booked again with it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val exactAllowed = action == android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            !exactAllowed
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClockRearm.rearmAll(context, action)
                if (!exactAllowed) context.applicationContext.appContainer.emailSyncScheduler.reschedule()
            } catch (e: Exception) {
                ZuneLog.e("BootReceiver", "onReceive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
