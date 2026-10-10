package com.serkantkn.zunelauncher.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets every alarm on reboot. Re-arms all enabled alarms (Clock Hub alarms and
 * Notes Hub reminders), the launcher's own event reminders and a running timer once the device
 * has booted — the walk itself is [ClockRearm], shared with the time-change receiver and the cold
 * start — and books the mail sync again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ClockRearm.rearmAll(context, action)
                context.applicationContext.appContainer.emailSyncScheduler.reschedule()
            } catch (e: Exception) {
                ZuneLog.e("BootReceiver", "onReceive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
