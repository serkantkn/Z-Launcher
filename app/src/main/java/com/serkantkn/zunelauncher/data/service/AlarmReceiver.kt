package com.serkantkn.zunelauncher.data.service

import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.screens.clock.AlarmActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when an Alarm's scheduled time is reached.
 *
 * - Holds a short wake lock for the whole hand-off (released by timeout, not immediately,
 *   so the device does not fall back asleep before AlarmActivity is up).
 * - Repeating alarms are re-scheduled for their next weekday; one-shot alarms are disabled
 *   so the Clock Hub list stays truthful. Note reminders (alarms carrying a noteId) are
 *   removed after firing.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZuneLauncher::AlarmWakeLock"
        )
        // Released automatically after the timeout; AlarmActivity keeps the screen on itself.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, alarmLabel)
            if (noteId != null) putExtra(EXTRA_NOTE_ID, noteId)
        }
        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            ZuneLog.e("AlarmReceiver", "onReceive failed", e)
        }

        // Re-arm or retire the alarm record off the main thread.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.applicationContext.appContainer
                val store = container.alarmDataStore
                val scheduler = container.alarmScheduler
                val alarms = store.alarmsFlow.first()
                val fired = alarms.firstOrNull { it.id == alarmId }
                if (fired != null) {
                    when {
                        fired.noteId != null -> {
                            // Note reminders are one-shot: drop the record.
                            store.saveAlarms(alarms.filter { it.id != alarmId })
                        }
                        fired.daysOfWeek.isNotEmpty() -> {
                            // Repeating: schedule the next matching weekday.
                            scheduler.schedule(fired)
                        }
                        else -> {
                            store.saveAlarms(alarms.map { if (it.id == alarmId) it.copy(isEnabled = false) else it })
                        }
                    }
                }
            } catch (e: Exception) {
                ZuneLog.e("AlarmReceiver", "onReceive failed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_ALARM_LABEL = "ALARM_LABEL"
        const val EXTRA_NOTE_ID = "NOTE_ID"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
    }
}
