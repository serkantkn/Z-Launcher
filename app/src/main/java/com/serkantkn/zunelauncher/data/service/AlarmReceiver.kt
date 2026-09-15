package com.serkantkn.zunelauncher.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.screens.clock.AlarmActivity
import com.serkantkn.zunelauncher.util.ZuneLog
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
 * - A snooze ringing back is not a fresh firing: it must not re-arm anything or retire the alarm,
 *   only clear the "snoozed until" note the list is showing.
 *
 * It also takes the snooze itself, because the phone can be asleep with only the notification on
 * screen and no activity alive to press a button in.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> {
                snooze(context, intent.getStringExtra(EXTRA_ALARM_ID) ?: return)
                return
            }
            ACTION_DISMISS -> {
                dismiss(context, intent.getStringExtra(EXTRA_ALARM_ID) ?: return)
                return
            }
        }

        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: return
        val alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        val isSnoozeRinging = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

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
            putExtra(EXTRA_RINGTONE, intent.getStringExtra(EXTRA_RINGTONE))
            putExtra(EXTRA_VIBRATE, intent.getBooleanExtra(EXTRA_VIBRATE, true))
            putExtra(EXTRA_SNOOZE_MINUTES, intent.getIntExtra(EXTRA_SNOOZE_MINUTES, Alarm.DEFAULT_SNOOZE_MINUTES))
            putExtra(EXTRA_GRADUAL, intent.getBooleanExtra(EXTRA_GRADUAL, true))
            putExtra(EXTRA_AUTO_SILENCE, intent.getIntExtra(EXTRA_AUTO_SILENCE, Alarm.DEFAULT_AUTO_SILENCE_MINUTES))
            if (noteId != null) putExtra(EXTRA_NOTE_ID, noteId)
        }
        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the alarm screen would not open", e)
        }

        // Re-arm or retire the alarm record off the main thread.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.applicationContext.appContainer
                val store = container.alarmDataStore
                val scheduler = container.alarmScheduler
                val alarms = store.alarmsFlow.first()
                val fired = alarms.firstOrNull { it.id == alarmId } ?: return@launch

                if (isSnoozeRinging) {
                    // Only the snooze note is stale; when it is due again is unchanged.
                    store.saveAlarms(alarms.map { if (it.id == alarmId) it.copy(snoozedUntilMillis = null) else it })
                    return@launch
                }

                when {
                    fired.noteId != null -> {
                        // Note reminders are one-shot: drop the record.
                        store.saveAlarms(alarms.filter { it.id != alarmId })
                    }
                    fired.daysOfWeek.isNotEmpty() -> {
                        // Repeating: schedule the next matching weekday.
                        val cleared = fired.copy(snoozedUntilMillis = null)
                        store.saveAlarms(alarms.map { if (it.id == alarmId) cleared else it })
                        scheduler.schedule(cleared)
                    }
                    else -> {
                        store.saveAlarms(
                            alarms.map {
                                if (it.id == alarmId) it.copy(isEnabled = false, snoozedUntilMillis = null) else it
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the alarm record would not settle", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Puts a ringing alarm off and writes the new time onto the record, so the list says so. */
    private fun snooze(context: Context, alarmId: String) {
        AlarmRinger.stop()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = context.applicationContext.appContainer
                val store = container.alarmDataStore
                val alarms = store.alarmsFlow.first()
                val alarm = alarms.firstOrNull { it.id == alarmId } ?: return@launch
                val until = System.currentTimeMillis() + alarm.snoozeMinutes * 60_000L
                val snoozed = alarm.copy(snoozedUntilMillis = until, isEnabled = true)
                store.saveAlarms(alarms.map { if (it.id == alarmId) snoozed else it })
                container.alarmScheduler.snooze(snoozed, until)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the alarm would not snooze", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Silences a ringing alarm from outside the screen that is ringing it. */
    private fun dismiss(context: Context, alarmId: String) {
        AlarmRinger.stop()
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = context.applicationContext.appContainer.alarmDataStore
                val alarms = store.alarmsFlow.first()
                store.saveAlarms(alarms.map { if (it.id == alarmId) it.copy(snoozedUntilMillis = null) else it })
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the alarm would not settle after being dismissed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.serkantkn.zunelauncher.ALARM_FIRE"
        const val ACTION_SNOOZE = "com.serkantkn.zunelauncher.ALARM_SNOOZE"
        const val ACTION_DISMISS = "com.serkantkn.zunelauncher.ALARM_DISMISS"
        const val EXTRA_ALARM_ID = "ALARM_ID"
        const val EXTRA_ALARM_LABEL = "ALARM_LABEL"
        const val EXTRA_NOTE_ID = "NOTE_ID"
        const val EXTRA_IS_SNOOZE = "IS_SNOOZE"
        const val EXTRA_RINGTONE = "RINGTONE"
        const val EXTRA_VIBRATE = "VIBRATE"
        const val EXTRA_SNOOZE_MINUTES = "SNOOZE_MINUTES"
        const val EXTRA_GRADUAL = "GRADUAL"
        const val EXTRA_AUTO_SILENCE = "AUTO_SILENCE"
        private const val TAG = "AlarmReceiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
    }
}
