package com.serkantkn.zunelauncher.util

import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import com.serkantkn.zunelauncher.data.service.VolumeController

/**
 * What the phone's own display and sound settings are, for the "display+sound" page, and the
 * means to change them.
 *
 * Everything under Settings.System is changed only once the user has let the launcher modify
 * system settings; volumes and the ringer need nothing, except that going all the way to silent
 * is a do-not-disturb change and needs the launcher let into the notification policy.
 */
object DisplaySound {

    /** One stream's volume, in the stream's own steps. */
    data class Level(val current: Int, val max: Int, val min: Int = 0)

    data class State(
        val canWrite: Boolean = false,
        val autoBrightness: Boolean = false,
        val brightness: Float = 0.5f,
        val screenTimeoutMs: Int = 30_000,
        val autoRotate: Boolean = true,
        val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
        val maySilence: Boolean = false,
        val vibrateWhenRinging: Boolean = false,
        val media: Level = Level(0, 1),
        val ring: Level = Level(0, 1),
        val notification: Level = Level(0, 1),
        val alarm: Level = Level(0, 1),
        val ringtone: String? = null,
        val notificationSound: String? = null,
        val alarmSound: String? = null,
        val touchSounds: Boolean = true,
        val dialTones: Boolean = true,
        val touchVibration: Boolean = true
    )

    /** The choices for the screen timeout, as Android's own settings offer them. */
    val TIMEOUTS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000, 1_800_000)

    /** The timeouts to offer: the usual ones, and the phone's own if it is something else. */
    fun timeoutChoices(current: Int): List<Int> =
        if (current in TIMEOUTS || current <= 0) TIMEOUTS else (TIMEOUTS + current).sorted()

    /** Reads it all. Ringtone titles query the media store, so call this off the main thread. */
    fun read(context: Context): State {
        val resolver = context.contentResolver
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        fun system(name: String, default: Int) = runCatching { Settings.System.getInt(resolver, name, default) }.getOrDefault(default)
        fun level(stream: Int) = runCatching {
            Level(
                current = am.getStreamVolume(stream),
                max = am.getStreamMaxVolume(stream).coerceAtLeast(1),
                min = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) am.getStreamMinVolume(stream) else 0
            )
        }.getOrDefault(Level(0, 1))
        return State(
            canWrite = Settings.System.canWrite(context),
            autoBrightness = QuickActions.isAutoBrightness(context),
            brightness = QuickActions.brightness(context),
            screenTimeoutMs = system(Settings.System.SCREEN_OFF_TIMEOUT, 30_000),
            autoRotate = system(Settings.System.ACCELEROMETER_ROTATION, 1) == 1,
            ringerMode = runCatching { am.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL),
            maySilence = VolumeController.canSilence(context),
            vibrateWhenRinging = system(Settings.System.VIBRATE_WHEN_RINGING, 0) == 1,
            media = level(AudioManager.STREAM_MUSIC),
            ring = level(AudioManager.STREAM_RING),
            notification = level(AudioManager.STREAM_NOTIFICATION),
            alarm = level(AudioManager.STREAM_ALARM),
            ringtone = toneTitle(context, RingtoneManager.TYPE_RINGTONE),
            notificationSound = toneTitle(context, RingtoneManager.TYPE_NOTIFICATION),
            alarmSound = toneTitle(context, RingtoneManager.TYPE_ALARM),
            touchSounds = system(Settings.System.SOUND_EFFECTS_ENABLED, 1) == 1,
            dialTones = system(Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1,
            touchVibration = system(Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 1
        )
    }

    /** The tone's name, "" for none, null when it cannot be read. */
    private fun toneTitle(context: Context, type: Int): String? = runCatching {
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, type) ?: return ""
        RingtoneManager.getRingtone(context, uri)?.getTitle(context)
    }.getOrNull()

    fun toneUri(context: Context, type: Int): Uri? =
        runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, type) }.getOrNull()

    /** Sets a Settings.System value; false when the phone would not have it. */
    private fun put(context: Context, name: String, value: Int): Boolean = runCatching {
        Settings.System.canWrite(context) && Settings.System.putInt(context.contentResolver, name, value)
    }.onFailure { ZuneLog.w(TAG, "$name could not be set", it) }.getOrDefault(false)

    fun setScreenTimeout(context: Context, ms: Int) = put(context, Settings.System.SCREEN_OFF_TIMEOUT, ms)
    fun setAutoRotate(context: Context, on: Boolean) = put(context, Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
    fun setVibrateWhenRinging(context: Context, on: Boolean) = put(context, Settings.System.VIBRATE_WHEN_RINGING, if (on) 1 else 0)
    fun setTouchVibration(context: Context, on: Boolean) = put(context, Settings.System.HAPTIC_FEEDBACK_ENABLED, if (on) 1 else 0)
    fun setDialTones(context: Context, on: Boolean) = put(context, Settings.System.DTMF_TONE_WHEN_DIALING, if (on) 1 else 0)

    fun setTouchSounds(context: Context, on: Boolean): Boolean {
        val done = put(context, Settings.System.SOUND_EFFECTS_ENABLED, if (on) 1 else 0)
        // The click sounds are loaded once; Android's own switch loads or drops them with it.
        if (done) runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (on) am.loadSoundEffects() else am.unloadSoundEffects()
        }
        return done
    }

    fun setVolume(context: Context, stream: Int, value: Int) {
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(stream, value.coerceIn(0, am.getStreamMaxVolume(stream)), 0)
        }.onFailure { ZuneLog.w(TAG, "the volume of stream $stream could not be set", it) }
    }

    /**
     * Rings, vibrates or is silent. Silent without the notification policy opens the page that
     * grants it instead, and answers false.
     */
    fun setRingerMode(context: Context, mode: Int): Boolean {
        if (mode == AudioManager.RINGER_MODE_SILENT && !VolumeController.canSilence(context)) {
            VolumeController.openPolicyAccessSettings(context)
            return false
        }
        return runCatching {
            (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).ringerMode = mode
            true
        }.onFailure { ZuneLog.w(TAG, "the ringer could not be set", it) }.getOrDefault(false)
    }

    fun setTone(context: Context, type: Int, uri: Uri?): Boolean = runCatching {
        if (!Settings.System.canWrite(context)) return false
        RingtoneManager.setActualDefaultRingtoneUri(context, type, uri)
        true
    }.onFailure { ZuneLog.w(TAG, "the tone could not be set", it) }.getOrDefault(false)

    private const val TAG = "DisplaySound"
}
