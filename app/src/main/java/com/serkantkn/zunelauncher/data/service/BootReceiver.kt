package com.serkantkn.zunelauncher.data.service

import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * AlarmManager forgets every alarm on reboot. Re-arms all enabled alarms (Clock Hub alarms
 * and Notes Hub reminders) once the device has booted.
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
                val container = context.applicationContext.appContainer
                val alarms = container.alarmDataStore.alarmsFlow.first()
                val now = System.currentTimeMillis()
                val scheduler = container.alarmScheduler
                alarms.filter { it.isEnabled }.forEach { alarm ->
                    // Skip note reminders whose time already passed while the phone was off.
                    if (alarm.noteId != null && alarm.exactTimeMillis != null && alarm.exactTimeMillis < now) return@forEach
                    scheduler.schedule(alarm)
                }
                container.emailSyncScheduler.reschedule()
            } catch (e: Exception) {
                ZuneLog.e("BootReceiver", "onReceive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
