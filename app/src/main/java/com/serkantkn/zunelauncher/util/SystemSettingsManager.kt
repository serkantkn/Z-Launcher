package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SystemSettingsManager(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _brightness = MutableStateFlow(0.5f)
    val brightness: StateFlow<Float> = _brightness

    private val _mediaVolume = MutableStateFlow(0.5f)
    val mediaVolume: StateFlow<Float> = _mediaVolume

    private val _ringVolume = MutableStateFlow(0.5f)
    val ringVolume: StateFlow<Float> = _ringVolume

    init {
        updateBrightness()
        updateVolumes()
    }

    fun updateBrightness() {
        try {
            val max = 255f
            val current = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            _brightness.value = current / max
        } catch (e: Exception) {
            ZuneLog.e("SystemSettingsManager", "updateBrightness failed", e)
        }
    }

    fun setBrightness(value: Float) {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        }
        
        try {
            val newBrightness = (value * 255).toInt().coerceIn(1, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
            _brightness.value = value
        } catch (e: Exception) {
            ZuneLog.e("SystemSettingsManager", "setBrightness failed", e)
        }
    }

    fun updateVolumes() {
        try {
            val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            val currentMedia = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
            _mediaVolume.value = if (maxMedia > 0) currentMedia / maxMedia else 0f

            val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).toFloat()
            val currentRing = audioManager.getStreamVolume(AudioManager.STREAM_RING).toFloat()
            _ringVolume.value = if (maxRing > 0) currentRing / maxRing else 0f
        } catch (e: Exception) {
            ZuneLog.e("SystemSettingsManager", "updateVolumes failed", e)
        }
    }

    fun setMediaVolume(value: Float) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            val newVol = (value * max).toInt().coerceIn(0, max.toInt())
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
            _mediaVolume.value = value
        } catch (e: Exception) {
            ZuneLog.e("SystemSettingsManager", "setMediaVolume failed", e)
        }
    }

    fun setRingVolume(value: Float) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).toFloat()
            val newVol = (value * max).toInt().coerceIn(0, max.toInt())
            audioManager.setStreamVolume(AudioManager.STREAM_RING, newVol, 0)
            _ringVolume.value = value
        } catch (e: Exception) {
            ZuneLog.e("SystemSettingsManager", "setRingVolume failed", e)
        }
    }
}
