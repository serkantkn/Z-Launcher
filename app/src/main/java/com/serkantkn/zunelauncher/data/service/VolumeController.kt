package com.serkantkn.zunelauncher.data.service

import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.localizedString
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VolumeState(
    /**
     * The level of the stream the keys are driving: music as a rule, the call's voice while one
     * is up. The bar shows this one number either way, which is why the stream is not a second
     * field the bar has to know about.
     */
    val mediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val ringerVolume: Int = 0,
    val maxRingerVolume: Int = 7,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL, // NORMAL, VIBRATE, SILENT
    val isVisible: Boolean = false,
    val eventId: Long = 0L,
    /** True while the keys are turning a call up and down rather than the music. */
    val isInCall: Boolean = false
)

object VolumeController {
    private val _volumeState = MutableStateFlow(VolumeState())
    val volumeState: StateFlow<VolumeState> = _volumeState.asStateFlow()

    /**
     * The stream the volume keys belong to right now.
     *
     * During a call the keys mean the earpiece, as they do on every phone; at every other time
     * they mean the music. While the phone is ringing they mean "quiet", which only the system
     * knows how to do, so that case is answered with null and the key is left alone.
     */
    private fun activeStream(): Int? = when (CallManager.callStatus.value) {
        CallStatus.INCOMING -> null
        CallStatus.ACTIVE, CallStatus.OUTGOING -> AudioManager.STREAM_VOICE_CALL
        CallStatus.IDLE, CallStatus.ENDED -> AudioManager.STREAM_MUSIC
    }

    fun updateStateFromSystem(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val stream = activeStream() ?: AudioManager.STREAM_MUSIC
        val mediaVol = am.getStreamVolume(stream)
        val maxMediaVol = am.getStreamMaxVolume(stream)
        val ringerVol = am.getStreamVolume(AudioManager.STREAM_RING)
        val maxRingerVol = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        val mode = am.ringerMode

        _volumeState.value = _volumeState.value.copy(
            mediaVolume = mediaVol,
            maxMediaVolume = maxMediaVol,
            ringerVolume = ringerVol,
            maxRingerVolume = maxRingerVol,
            ringerMode = mode,
            isInCall = stream == AudioManager.STREAM_VOICE_CALL
        )
    }

    /** Returns false when the key was not for us — a ringing phone — and should go on to the system. */
    fun handleVolumeKey(context: Context, isUp: Boolean): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val stream = activeStream() ?: return false
        val direction = if (isUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER

        try {
            am.adjustStreamVolume(stream, direction, 0)
        } catch (e: Exception) {
            ZuneLog.e("VolumeController", "handleVolumeKey failed", e)
        }

        updateStateFromSystem(context)
        _volumeState.value = _volumeState.value.copy(
            isVisible = true,
            eventId = System.currentTimeMillis()
        )
        return true
    }

    fun setMediaVolume(context: Context, volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val stream = activeStream() ?: AudioManager.STREAM_MUSIC
        val clamped = volume.coerceIn(0, _volumeState.value.maxMediaVolume)
        try {
            am.setStreamVolume(stream, clamped, 0)
        } catch (e: Exception) {
            ZuneLog.e("VolumeController", "setMediaVolume failed", e)
        }
        updateStateFromSystem(context)
        _volumeState.value = _volumeState.value.copy(
            mediaVolume = clamped,
            eventId = System.currentTimeMillis()
        )
    }

    fun setRingerVolume(context: Context, volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val clamped = volume.coerceIn(0, _volumeState.value.maxRingerVolume)
        try {
            am.setStreamVolume(AudioManager.STREAM_RING, clamped, 0)
        } catch (e: Exception) {
            ZuneLog.e("VolumeController", "setRingerVolume failed", e)
        }
        updateStateFromSystem(context)
        _volumeState.value = _volumeState.value.copy(
            ringerVolume = clamped,
            eventId = System.currentTimeMillis()
        )
    }

    /**
     * Whether the phone will let the bar go all the way to silent.
     *
     * Silent is a do-not-disturb change in Android's eyes, and only an app the user has let into
     * the notification policy may make one; asking without it throws and the bar does nothing.
     */
    fun canSilence(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true

    /** The system page where the user lets the launcher into the notification policy. */
    fun openPolicyAccessSettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { ZuneLog.w("VolumeController", "the policy access page would not open", it) }
    }

    /** Ring → vibrate → silent → ring; without policy access, ring ↔ vibrate and a word about why. */
    fun toggleRingerMode(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val mayGoSilent = canSilence(context)
        try {
            val newMode = when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE ->
                    if (mayGoSilent) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            if (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE && !mayGoSilent) {
                // Said once per press rather than silently skipping a mode the user expected.
                Toast.makeText(
                    context,
                    context.localizedString(R.string.volume_silent_needs_access),
                    Toast.LENGTH_LONG
                ).show()
            }
            am.ringerMode = newMode
        } catch (e: Exception) {
            ZuneLog.e("VolumeController", "toggleRingerMode failed", e)
        }
        updateStateFromSystem(context)
        _volumeState.value = _volumeState.value.copy(eventId = System.currentTimeMillis())
    }

    fun hide() {
        _volumeState.value = _volumeState.value.copy(isVisible = false)
    }
}
