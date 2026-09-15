package com.serkantkn.zunelauncher.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.serkantkn.zunelauncher.data.model.CameraFacing
import com.serkantkn.zunelauncher.data.model.CameraMode
import com.serkantkn.zunelauncher.data.model.CameraSettings
import com.serkantkn.zunelauncher.data.model.FlashMode
import com.serkantkn.zunelauncher.data.model.TimerOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.cameraDataStore: DataStore<Preferences> by preferencesDataStore(name = "camera")

/**
 * How the camera was left last time. A camera that opens in whatever mode it was closed in is the
 * difference between a hub and a chore.
 */
class CameraDataStore(private val context: Context) {

    private val MODE = stringPreferencesKey("mode")
    private val FACING = stringPreferencesKey("facing")
    private val FLASH = stringPreferencesKey("flash")
    private val TIMER = stringPreferencesKey("timer")
    private val GRID = booleanPreferencesKey("grid_lines")
    private val MIRROR_FRONT = booleanPreferencesKey("mirror_front")

    val settings: Flow<CameraSettings> = context.cameraDataStore.data.map { prefs ->
        CameraSettings(
            mode = prefs[MODE].toEnum(CameraMode.PHOTO) { CameraMode.valueOf(it) },
            facing = prefs[FACING].toEnum(CameraFacing.BACK) { CameraFacing.valueOf(it) },
            flash = prefs[FLASH].toEnum(FlashMode.AUTO) { FlashMode.valueOf(it) },
            timer = prefs[TIMER].toEnum(TimerOption.OFF) { TimerOption.valueOf(it) },
            gridLines = prefs[GRID] ?: false,
            mirrorFrontCamera = prefs[MIRROR_FRONT] ?: true
        )
    }

    suspend fun setMode(mode: CameraMode) = put(MODE, mode.name)

    suspend fun setFacing(facing: CameraFacing) = put(FACING, facing.name)

    suspend fun setFlash(flash: FlashMode) = put(FLASH, flash.name)

    suspend fun setTimer(timer: TimerOption) = put(TIMER, timer.name)

    suspend fun setGridLines(enabled: Boolean) {
        context.cameraDataStore.edit { it[GRID] = enabled }
    }

    suspend fun setMirrorFrontCamera(enabled: Boolean) {
        context.cameraDataStore.edit { it[MIRROR_FRONT] = enabled }
    }

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        context.cameraDataStore.edit { it[key] = value }
    }
}

/** A stored name that no longer means anything falls back rather than bringing the camera down. */
private inline fun <T> String?.toEnum(fallback: T, parse: (String) -> T): T = try {
    if (this == null) fallback else parse(this)
} catch (_: Exception) {
    fallback
}
