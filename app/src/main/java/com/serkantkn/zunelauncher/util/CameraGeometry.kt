package com.serkantkn.zunelauncher.util

import android.view.Surface

/**
 * The arithmetic behind the viewfinder's gestures and the way a shot is stored.
 *
 * None of it needs a camera to work out, so none of it lives in the engine: a pinch is a number
 * against a range, and which way up a picture goes is a reading against four quarters.
 */
object CameraGeometry {

    /** Below this, a pinch is a hand resting on the glass rather than an intention. */
    const val PINCH_DEAD_ZONE = 0.01f

    /**
     * Where a pinch leaves the zoom.
     *
     * The factor multiplies rather than adds, so the same spread of the fingers moves the same
     * proportion at every zoom — pinching from 1× to 2× should feel like pinching from 4× to 8×.
     */
    fun zoomAfterPinch(current: Float, factor: Float, min: Float, max: Float): Float {
        if (max <= min) return min
        if (!factor.isFinite() || factor <= 0f) return current.coerceIn(min, max)
        return (current * factor).coerceIn(min, max)
    }

    /** True when the lens can do anything at all with a pinch. */
    fun canZoom(min: Float, max: Float): Boolean = max > min + 0.01f

    /**
     * Which way up the phone is being held, as a [Surface] rotation.
     *
     * This is asked of the sensor rather than of the display, because the two part company the
     * moment auto-rotate is off: the screen stays portrait while the phone is held sideways, and a
     * picture taken then would otherwise be stored on its side.
     */
    fun surfaceRotationFor(degrees: Int): Int {
        if (degrees < 0) return Surface.ROTATION_0    // the sensor cannot tell (face up, flat)
        val turned = ((degrees % 360) + 360) % 360
        return when {
            turned >= 315 || turned < 45 -> Surface.ROTATION_0
            turned < 135 -> Surface.ROTATION_270
            turned < 225 -> Surface.ROTATION_180
            else -> Surface.ROTATION_90
        }
    }
}
