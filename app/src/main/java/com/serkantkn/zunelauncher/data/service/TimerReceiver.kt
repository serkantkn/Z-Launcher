package com.serkantkn.zunelauncher.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.serkantkn.zunelauncher.data.datastore.ClockDataStore
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.ui.screens.clock.AlarmActivity
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything that can happen to a timer while nobody is looking at it.
 *
 * It ends the way an alarm ends — the same screen, the same ringer — because a timer that finishes
 * quietly behind whatever you were doing is the thing that was wrong with it before. The only
 * difference is what the big button says: an alarm offers to come back in ten minutes, a timer
 * offers another minute.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DONE -> ring(context)
            ACTION_PAUSE -> pause(context)
            ACTION_CANCEL -> cancel(context)
            ACTION_STOP -> stop(context)
            ACTION_ADD_MINUTE -> addMinute(context)
        }
    }

    /** The countdown reached zero: wake the phone and put the screen up. */
    private fun ring(context: Context) {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZuneLauncher::TimerWakeLock")
            .acquire(WAKE_LOCK_TIMEOUT_MS)

        TimerNotifier.clear(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = context.applicationContext.appContainer.clockDataStore
                val state = store.timer.first()
                store.saveTimer(
                    state.copy(isRunning = false, endsAtMillis = 0L, remainingMillis = 0L)
                )
                val screen = Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AlarmActivity.EXTRA_TIMER_MODE, true)
                    putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, state.label)
                }
                runCatching { context.startActivity(screen) }
                    .onFailure { ZuneLog.e(TAG, "the timer screen would not open", it) }
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the finished timer would not settle", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Freezes the countdown where it is, keeping what is left of it. */
    private fun pause(context: Context) = edit(context) { store, state ->
        val remaining = state.remainingAt(System.currentTimeMillis())
        context.applicationContext.appContainer.timerScheduler.cancel()
        TimerNotifier.clear(context)
        store.saveTimer(state.copy(isRunning = false, endsAtMillis = 0L, remainingMillis = remaining))
    }

    /** Drops the countdown and puts the dial back to what it was set to. */
    private fun cancel(context: Context) = edit(context) { store, state ->
        context.applicationContext.appContainer.timerScheduler.cancel()
        TimerNotifier.clear(context)
        store.saveTimer(
            state.copy(isRunning = false, endsAtMillis = 0L, remainingMillis = state.totalMillis)
        )
    }

    /** Silences a timer that is ringing. */
    private fun stop(context: Context) {
        AlarmRinger.stop()
        TimerNotifier.clear(context)
    }

    /** Another minute, from the moment the button was pressed. */
    private fun addMinute(context: Context) {
        AlarmRinger.stop()
        edit(context) { store, state ->
            val endsAt = System.currentTimeMillis() + ONE_MINUTE_MS
            context.applicationContext.appContainer.timerScheduler.schedule(endsAt)
            store.saveTimer(
                state.copy(isRunning = true, endsAtMillis = endsAt, remainingMillis = ONE_MINUTE_MS)
            )
            TimerNotifier.showRunning(context, endsAt, state.label)
        }
    }

    private fun edit(
        context: Context,
        block: suspend (ClockDataStore, ClockDataStore.TimerState) -> Unit
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = context.applicationContext.appContainer.clockDataStore
                block(store, store.timer.first())
            } catch (e: Exception) {
                ZuneLog.e(TAG, "the timer would not change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.serkantkn.zunelauncher.TIMER_DONE"
        const val ACTION_PAUSE = "com.serkantkn.zunelauncher.TIMER_PAUSE"
        const val ACTION_CANCEL = "com.serkantkn.zunelauncher.TIMER_CANCEL"
        const val ACTION_STOP = "com.serkantkn.zunelauncher.TIMER_STOP"
        const val ACTION_ADD_MINUTE = "com.serkantkn.zunelauncher.TIMER_ADD_MINUTE"
        const val ONE_MINUTE_MS = 60_000L
        private const val TAG = "TimerReceiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 60_000L
    }
}
