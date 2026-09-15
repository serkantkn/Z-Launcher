package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import java.util.Locale

/**
 * A number the way Windows Phone let you set one: a column you drag, with the value in large light
 * type and nothing around it.
 *
 * It wraps, because a minute picker that stops dead at 59 makes you drag the whole way back to get
 * to 0. Dragging moves it a step per notch with a tick of haptic feedback, and the arrows above and
 * below are there for the single step a drag is clumsy for.
 */
@Composable
fun WpNumberColumn(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    digits: Int = 2
) {
    val zuneColors = LocalZuneColors.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val notchPx = with(density) { NOTCH.toPx() }
    val dragged = remember { mutableFloatStateOf(0f) }

    val span = range.last - range.first + 1
    fun step(by: Int) {
        val next = ((value - range.first + by) % span + span) % span + range.first
        onValueChange(next)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textMuted
        )
        Arrow(text = "▲", onClick = { step(1) })
        Box(
            modifier = Modifier
                .padding(vertical = 2.dp)
                .pointerInput(value, range) {
                    detectVerticalDragGestures(
                        onDragEnd = { dragged.floatValue = 0f },
                        onDragCancel = { dragged.floatValue = 0f }
                    ) { change, amount ->
                        change.consume()
                        dragged.floatValue += amount
                        while (dragged.floatValue <= -notchPx) {
                            dragged.floatValue += notchPx
                            step(1)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        while (dragged.floatValue >= notchPx) {
                            dragged.floatValue -= notchPx
                            step(-1)
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format(Locale.ROOT, "%0${digits}d", value),
                style = metroDigitStyle(fontSize = 56.sp, weight = FontWeight.Light, letterSpacing = (-2).sp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Arrow(text = "▼", onClick = { step(-1) })
    }
}

/** Hours, minutes and seconds side by side, separated the way a clock separates them. */
@Composable
fun WpDurationPicker(
    hours: Int,
    minutes: Int,
    seconds: Int?,
    onHours: (Int) -> Unit,
    onMinutes: (Int) -> Unit,
    onSeconds: (Int) -> Unit,
    hourLabel: String,
    minuteLabel: String,
    secondLabel: String,
    modifier: Modifier = Modifier,
    hourRange: IntRange = 0..23
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        WpNumberColumn(
            value = hours,
            range = hourRange,
            onValueChange = onHours,
            label = hourLabel
        )
        Separator()
        WpNumberColumn(
            value = minutes,
            range = 0..59,
            onValueChange = onMinutes,
            label = minuteLabel
        )
        if (seconds != null) {
            Separator()
            WpNumberColumn(
                value = seconds,
                range = 0..59,
                onValueChange = onSeconds,
                label = secondLabel
            )
        }
    }
}

@Composable
private fun Separator() {
    val zuneColors = LocalZuneColors.current
    Text(
        text = ":",
        style = metroDigitStyle(fontSize = 44.sp, weight = FontWeight.Thin, letterSpacing = 0.sp),
        color = zuneColors.textDim,
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}

@Composable
private fun Arrow(text: String, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.accentColor
        )
    }
}

/** How far a finger travels for one step. */
private val NOTCH = 26.dp
