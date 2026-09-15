package com.serkantkn.zunelauncher.data.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The noise an alarm makes, owned in one place.
 *
 * It does not belong to the screen that shows the alarm: the phone can be woken by a notification
 * with no screen of ours in front, and a snooze pressed there has to be able to stop the sound.
 * So the player lives here, and whoever wants quiet calls [stop].
 *
 * Every step of starting it is allowed to fail. A phone with no alarm sound, a ringtone on a
 * memory card that is no longer in the phone, an audio focus refusal — none of those are reasons
 * for the alarm to crash at the one moment it was supposed to work. It falls back, and in the
 * worst case it still vibrates.
 */
object AlarmRinger {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var fadeJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob())

    /** Whether something is making a noise right now. */
    @Volatile
    var isRinging: Boolean = false
        private set

    fun start(
        context: Context,
        ringtoneUri: String?,
        vibrate: Boolean,
        gradualVolume: Boolean
    ) {
        stop()
        isRinging = true
        startSound(context, ringtoneUri, gradualVolume)
        if (vibrate) startVibration(context)
    }

    private fun startSound(context: Context, ringtoneUri: String?, gradualVolume: Boolean) {
        val candidates = listOfNotNull(
            ringtoneUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() },
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        )

        for (uri in candidates) {
            val started = runCatching {
                val media = MediaPlayer()
                media.setDataSource(context, uri)
                media.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                media.isLooping = true
                if (gradualVolume) media.setVolume(START_VOLUME, START_VOLUME)
                media.prepare()
                media.start()
                player = media
                media
            }.getOrElse { error ->
                ZuneLog.w(TAG, "the alarm sound at $uri would not play", error)
                null
            }
            if (started != null) {
                if (gradualVolume) fadeUp(started)
                return
            }
        }
        // Nothing in the phone would play. The vibration is the alarm now.
        ZuneLog.e(TAG, "no alarm sound on this phone would play", null)
    }

    /** Climbs from a whisper to full over half a minute, the way a phone alarm should wake you. */
    private fun fadeUp(media: MediaPlayer) {
        fadeJob?.cancel()
        fadeJob = scope.launch {
            var volume = START_VOLUME
            while (volume < 1f && isRinging) {
                delay(FADE_STEP_MS)
                volume = (volume + FADE_STEP).coerceAtMost(1f)
                runCatching { media.setVolume(volume, volume) }
            }
        }
    }

    private fun startVibration(context: Context) {
        val device = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
        }.getOrNull() ?: return
        vibrator = device
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                device.vibrate(VibrationEffect.createWaveform(PATTERN, AMPLITUDES, 0))
            } else {
                @Suppress("DEPRECATION")
                device.vibrate(PATTERN, 0)
            }
        }
    }

    fun stop() {
        isRinging = false
        fadeJob?.cancel()
        fadeJob = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    private const val TAG = "AlarmRinger"
    private const val START_VOLUME = 0.12f
    private const val FADE_STEP = 0.05f
    private const val FADE_STEP_MS = 1_500L
    private val PATTERN = longArrayOf(0, 500, 500)
    private val AMPLITUDES = intArrayOf(0, 255, 0)
}
