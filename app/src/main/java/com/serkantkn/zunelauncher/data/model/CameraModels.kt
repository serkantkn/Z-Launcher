package com.serkantkn.zunelauncher.data.model

import androidx.annotation.StringRes
import com.serkantkn.zunelauncher.R

/**
 * What the camera is set to do. Windows Phone put these on the lens itself rather than in a menu,
 * so they read as one strip across the bottom of the viewfinder.
 */
enum class CameraMode(@StringRes val titleRes: Int) {
    PHOTO(R.string.camera_mode_photo),
    VIDEO(R.string.camera_mode_video),
    NIGHT(R.string.camera_mode_night),
    PANORAMA(R.string.camera_mode_panorama);

    /** Panorama and night both read the preview stream frame by frame. */
    val readsFrames: Boolean get() = this == NIGHT || this == PANORAMA
}

enum class CameraFacing { BACK, FRONT }

enum class FlashMode(@StringRes val labelRes: Int) {
    AUTO(R.string.camera_flash_auto),
    ON(R.string.camera_flash_on),
    OFF(R.string.camera_flash_off)
}

/** The self-timer, in seconds. */
enum class TimerOption(val seconds: Int, @StringRes val labelRes: Int) {
    OFF(0, R.string.camera_timer_off),
    THREE(3, R.string.camera_timer_3),
    TEN(10, R.string.camera_timer_10)
}

/** Everything the camera remembers between visits. */
data class CameraSettings(
    val mode: CameraMode = CameraMode.PHOTO,
    val facing: CameraFacing = CameraFacing.BACK,
    val flash: FlashMode = FlashMode.AUTO,
    val timer: TimerOption = TimerOption.OFF,
    val gridLines: Boolean = false,
    /** A selfie is saved the way it looked in the viewfinder, which is the way people expect it. */
    val mirrorFrontCamera: Boolean = true
)

/** What just happened, so the viewfinder can say so. */
sealed interface CameraEvent {
    data class Saved(val uri: String, val isVideo: Boolean) : CameraEvent
    data class Failed(@StringRes val messageRes: Int) : CameraEvent
}
