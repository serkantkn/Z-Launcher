package com.serkantkn.zunelauncher.data.service

import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class VolumeState(
    val mediaVolume: Int = 0,
    val maxMediaVolume: Int = 15,
    val ringerVolume: Int = 0,
    val maxRingerVolume: Int = 7,
    val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL, // NORMAL, VIBRATE, SILENT
    val isVisible: Boolean = false,
    val eventId: Long = 0L
)

object VolumeController {
    private val _volumeState = MutableStateFlow(VolumeState())
    val volumeState: StateFlow<VolumeState> = _volumeState.asStateFlow()

    fun updateStateFromSystem(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val mediaVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxMediaVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val ringerVol = am.getStreamVolume(AudioManager.STREAM_RING)
        val maxRingerVol = am.getStreamMaxVolume(AudioManager.STREAM_RING)
        val mode = am.ringerMode

        _volumeState.value = _volumeState.value.copy(
            mediaVolume = mediaVol,
            maxMediaVolume = maxMediaVol,
            ringerVolume = ringerVol,
            maxRingerVolume = maxRingerVol,
            ringerMode = mode
        )
    }

    fun handleVolumeKey(context: Context, isUp: Boolean): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val direction = if (isUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER

        try {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
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
        val clamped = volume.coerceIn(0, _volumeState.value.maxMediaVolume)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0)
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

    fun toggleRingerMode(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val newMode = when (am.ringerMode) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
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
