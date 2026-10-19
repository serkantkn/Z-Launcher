package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlin.math.roundToInt

/**
 * Windows Phone's slider: a thin line, filled in the accent up to the value, with a narrow
 * upright block for a thumb. A tap jumps there; a drag follows the finger. With [steps] the
 * value lands only on that many stops between the ends, as Material's does.
 *
 * It also takes the focus, and the left and right arrows move it a stop (or a twentieth of the
 * way): on a television that is the only way to reach it, and a keyboard is glad of it too.
 */
@Composable
fun WpSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {},
    accent: Color = LocalZuneColors.current.accentColor,
    track: Color = LocalZuneColors.current.let { (if (it.isDark) Color.White else Color.Black).copy(alpha = 0.2f) }
) {
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val range by rememberUpdatedState(valueRange)
    val stops by rememberUpdatedState(steps)
    var width by remember { mutableIntStateOf(1) }
    val thumbPx = with(LocalDensity.current) { THUMB_WIDTH.toPx() }
    val fraction = sliderFraction(value, valueRange)

    fun valueAt(x: Float) = sliderValueAt(x, width.toFloat(), thumbPx, range, stops)

    Box(
        modifier = modifier
            .height(32.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange, steps)
                setProgress { target ->
                    change(snapToSteps(target.coerceIn(range), range, stops))
                    finished()
                    true
                }
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> -1
                    Key.DirectionRight -> 1
                    else -> return@onKeyEvent false
                }
                change(sliderStep(value, range, stops, direction))
                finished()
                true
            }
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    change(valueAt(offset.x))
                    finished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { finished() },
                    onDragCancel = { finished() }
                ) { pointer, _ ->
                    pointer.consume()
                    change(valueAt(pointer.position.x))
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // The line, and the accent up to the middle of the thumb.
        val travel = (width - thumbPx).coerceAtLeast(0f)
        val thumbLeft = travel * fraction
        Box(modifier = Modifier.fillMaxWidth().height(TRACK_HEIGHT).background(track))
        Box(
            modifier = Modifier
                .width(with(LocalDensity.current) { (thumbLeft + thumbPx / 2f).toDp() })
                .height(TRACK_HEIGHT)
                .background(accent)
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbLeft.roundToInt(), 0) }
                .size(width = THUMB_WIDTH, height = THUMB_HEIGHT)
                .background(accent)
        )
    }
}

/** Where [value] sits between the ends of [range], 0-1. */
internal fun sliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    return if (span <= 0f) 0f else ((value - range.start) / span).coerceIn(0f, 1f)
}

/**
 * The value under a finger at [x] on a slider [width] across: the thumb's middle follows the
 * finger, so the ends are reached with the thumb still whole on the line.
 */
internal fun sliderValueAt(
    x: Float,
    width: Float,
    thumb: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int
): Float {
    val travel = width - thumb
    val fraction = if (travel <= 0f) 0f else ((x - thumb / 2f) / travel).coerceIn(0f, 1f)
    val raw = range.start + fraction * (range.endInclusive - range.start)
    return snapToSteps(raw, range, steps)
}

/**
 * One press of an arrow from [value]: to the next of the [steps] stops that way, or with none a
 * twentieth of the range; never past the ends.
 */
internal fun sliderStep(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, direction: Int): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return range.start
    val stride = if (steps > 0) span / (steps + 1) else span / KEY_STEPS
    return snapToSteps((value + direction * stride).coerceIn(range), range, steps)
}

/** [value] on the nearest of the [steps] stops between the ends; as it is with none. */
internal fun snapToSteps(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int): Float {
    if (steps <= 0) return value
    val span = range.endInclusive - range.start
    if (span <= 0f) return range.start
    val segments = steps + 1
    val index = (((value - range.start) / span) * segments).roundToInt().coerceIn(0, segments)
    return range.start + span * index / segments
}

private const val KEY_STEPS = 20f
private val TRACK_HEIGHT = 4.dp
private val THUMB_WIDTH = 10.dp
private val THUMB_HEIGHT = 24.dp
