package com.serkantkn.zunelauncher.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How far the Start screen's wallpaper should have drifted, in pixels.
 *
 * The Zune list scrolls over the wallpaper and the wallpaper follows it a little more slowly,
 * which is what gives the list depth. The list lives in the home screen and the wallpaper is
 * drawn under the whole launcher, so the two meet here rather than through a dozen parameters.
 */
object StartParallax {
    private val _shiftPx = MutableStateFlow(0f)
    val shiftPx: StateFlow<Float> = _shiftPx.asStateFlow()

    fun set(px: Float) {
        _shiftPx.value = px
    }

    /** How much slower than the list the wallpaper moves. */
    const val FACTOR = 0.06f
}
