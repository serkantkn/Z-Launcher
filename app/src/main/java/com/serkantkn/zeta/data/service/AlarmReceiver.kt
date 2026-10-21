package com.serkantkn.zeta.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.serkantkn.zeta.data.model.Alarm
import com.serkantkn.zeta.di.appContainer
import com.serkantkn.zeta.ui.screens.clock.AlarmActivity
import com.serkantkn.zeta.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fires when an Alarm's scheduled time is reached.
 *
 * - Starts the sound itself, here, before anything else: a receiver may not be allowed to put a
 *   screen up (Android refuses background starts), but it is always allowed to make a noise. Then
 *   it posts the full-screen notification and lets the system bring [AlarmActivity] up.
 * - Holds a short wake lock for the whole hand-off (released by timeout, not immediately,
 *   so the device does not fall back asleep before the screen is up).
 * - Repeating alarms are re-booked for their next weekday at the moment they fire, whatever is
 *   pressed afterwards; one-shot alarms are disabled so the Clock Hub list stays truthful. Note
 *   reminders (alarms carrying a noteId) are removed after firing.
 * - A snooze ringing back is not a fresh firing: it only clears the "snoozed until" note the list
 *   is showing — and, for a one-shot alarm, switches it off, because its own firing already came.
 *
 * It also takes the snooze and the dismiss themselves, because the phone can be asleep with only
 * the notification on screen and no activity alive to press a button in.
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
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        val isSnoozeRinging = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ZuneLauncher::AlarmWakeLock"
        )
        // Released automatically after the timeout; AlarmActivity keeps the screen on itself.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)

        // Two alarms a minute apart: the newer one takes the speaker, the older one is settled as
        // though it had been dismissed, and the log says so.
        val older = AlarmRinger.activeId
        if (older != null && older != alarmId) {
            ZuneLog.w(TAG, "alarm $alarmId rings over $older; the older one is dismissed")
            if (older == AlarmRinger.TIMER_ID) TimerNotifier.clear(context) else settleDismissed(context, older)
        }

        val app = context.applicationContext
        AlarmRinger.start(
            context = app,
            id = alarmId,
            ringtoneUri = intent.getStringExtra(EXTRA_RINGTONE),
            vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, true),
            gradualVolume = intent.getBooleanExtra(EXTRA_GRADUAL, true),
            autoSilenceMillis = intent.getIntExtra(EXTRA_AUTO_SILENCE, Alarm.DEFAULT_AUTO_SILENCE_MINUTES) * 60_000L,
            onAutoSilence = {
                // Nobody came: settle it the way a dismiss would, so the list stops saying "snoozed".
                app.sendBroadcast(
                    Intent(app, AlarmReceiver::class.java).apply {
                        action = ACTION_DISMISS
                        putExtra(EXTRA_ALARM_ID, alarmId)
                    }
                )
            }
        )
        AlarmNotifier.showAlarm(app, intent)

        // Asking directly as well costs nothing: where the start is allowed the screen is up a
        // beat sooner, and where it is refused the full-screen notification does the same job.
        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            intent.extras?.let { putExtras(it) }
        }
        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the alarm screen would not open directly; the notification will", e)
        }

        // Re-arm or retire the alarm record off the main thread.
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val container = app.appContainer
                val store = container.alarmDataStore
                val scheduler = container.alarmScheduler
                val alarms = store.alarmsFlow.first()
                val fired = alarms.firstOrNull { it.id == alarmId } ?: return@launch

                if (isSnoozeRinging) {
                    // The snooze note is stale. A repeating alarm's next day is already booked;
                    // a one-shot one has now rung for good and goes quiet.
                    val settled = if (fired.isRepeating) {
                        fired.copy(snoozedUntilMillis = null)
                    } else {
                        fired.copy(snoozedUntilMillis = null, isEnabled = false, bookedForMillis = null)
                    }
                    store.saveAlarms(alarms.map { if (it.id == alarmId) settled else it })
                    return@launch
                }

                when {
                    fired.noteId != null || noteId != null -> {
                        // Note reminders are one-shot: drop the record.
                        store.saveAlarms(alarms.filter { it.id != alarmId })
                    }
                    fired.isRepeating -> {
                        // Repeating: book the next matching weekday now, whatever happens next.
                        val cleared = fired.copy(snoozedUntilMillis = null)
                        val booked = scheduler.schedule(cleared)
                        store.saveAlarms(
                            alarms.map { if (it.id == alarmId) cleared.copy(bookedForMillis = booked) else it }
                        )
                    }
                    else -> {
                        store.saveAlarms(
                            alarms.map {
                                if (it.id == alarmId) {
                                    it.copy(isEnabled = false, snoozedUntilMillis = null, bookedForMillis = null)
                                } else {
                                    it
                                }
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
        AlarmRinger.stop(alarmId)
        AlarmNotifier.clearRinging(context)
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
        AlarmRinger.stop(alarmId)
        AlarmNotifier.clearRinging(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                settleDismissedNow(context, alarmId)
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the alarm would not settle after being dismissed", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Clears a dismissed alarm's snooze note on the record, without touching the ringer. */
    private fun settleDismissed(context: Context, alarmId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { settleDismissedNow(context, alarmId) }
                .onFailure { ZuneLog.w(TAG, "the handed-over alarm would not settle", it) }
        }
    }

    private suspend fun settleDismissedNow(context: Context, alarmId: String) {
        val container = context.applicationContext.appContainer
        val store = container.alarmDataStore
        val alarms = store.alarmsFlow.first()
        val alarm = alarms.firstOrNull { it.id == alarmId } ?: return
        if (alarm.snoozedUntilMillis != null) {
            container.alarmScheduler.cancelSnooze(alarm)
            store.saveAlarms(alarms.map { if (it.id == alarmId) it.copy(snoozedUntilMillis = null) else it })
        }
    }

    companion object {
        const val ACTION_FIRE = "com.serkantkn.zeta.ALARM_FIRE"
        const val ACTION_SNOOZE_FIRE = "com.serkantkn.zeta.ALARM_SNOOZE_FIRE"
        const val ACTION_SNOOZE = "com.serkantkn.zeta.ALARM_SNOOZE"
        const val ACTION_DISMISS = "com.serkantkn.zeta.ALARM_DISMISS"
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
