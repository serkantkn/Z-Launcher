package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlin.math.abs

/**
 * Vertical alphabet index for fast scrolling in the apps list.
 * Height covers 75% of screen height from the bottom.
 * When touched, letters bulge outward to the left in a smooth fluid wave around the touch Y position.
 */
@Composable
fun ZuneAlphabetIndex(
    availableLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val letters = ('A'..'Z').toList() + '#'
    var totalHeightPx by remember { mutableFloatStateOf(0f) }
    var touchYPx by remember { mutableFloatStateOf(-1f) }
    var isTouching by remember { mutableStateOf(false) }
    var lastSelectedLetter by remember { mutableStateOf<Char?>(null) }

    val maxBulgeOffsetPx = with(density) { 36.dp.toPx() }
    val waveRadiusPx = with(density) { 110.dp.toPx() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .width(36.dp)
            .onGloballyPositioned { totalHeightPx = it.size.height.toFloat() }
            .pointerInput(availableLetters) {
                detectVerticalDragGestures(
                    onDragStart = { change ->
                        if (totalHeightPx <= 0f) return@detectVerticalDragGestures
                        isTouching = true
                        touchYPx = change.y
                        val letterIndex = ((change.y / totalHeightPx) * letters.size)
                            .toInt()
                            .coerceIn(0, letters.lastIndex)
                        val letter = letters[letterIndex]
                        if (letter != '#' && letter in availableLetters && letter != lastSelectedLetter) {
                            lastSelectedLetter = letter
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letter)
                        }
                    },
                    onDragEnd = {
                        isTouching = false
                        touchYPx = -1f
                        lastSelectedLetter = null
                    },
                    onDragCancel = {
                        isTouching = false
                        touchYPx = -1f
                        lastSelectedLetter = null
                    },
                    onVerticalDrag = { change, _ ->
                        if (totalHeightPx <= 0f) return@detectVerticalDragGestures
                        isTouching = true
                        touchYPx = change.position.y
                        val letterIndex = ((change.position.y / totalHeightPx) * letters.size)
                            .toInt()
                            .coerceIn(0, letters.lastIndex)
                        val letter = letters[letterIndex]
                        if (letter != '#' && letter in availableLetters && letter != lastSelectedLetter) {
                            lastSelectedLetter = letter
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLetterSelected(letter)
                        }
                    }
                )
            }
    ) {
        val itemHeightPx = if (letters.isNotEmpty() && totalHeightPx > 0f) totalHeightPx / letters.size else 0f

        letters.forEachIndexed { index, letter ->
            val isAvailable = letter == '#' || letter in availableLetters
            val isCurrentSelected = letter == lastSelectedLetter

            val itemCenterYPx = (index + 0.5f) * itemHeightPx
            val distFromTouchPx = if (isTouching && touchYPx >= 0f) abs(touchYPx - itemCenterYPx) else Float.MAX_VALUE

            val factor = if (distFromTouchPx < waveRadiusPx) {
                (1f - (distFromTouchPx / waveRadiusPx)).coerceIn(0f, 1f)
            } else 0f

            // Smooth cosine bulge shape
            val bulgeFactor = if (factor > 0f) kotlin.math.cos((1f - factor) * (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f) else 0f

            val targetOffsetX = if (isTouching) -maxBulgeOffsetPx * bulgeFactor else 0f
            val targetScale = if (isTouching) 1f + (0.55f * bulgeFactor) else 1f

            val animatedOffsetX by animateFloatAsState(
                targetValue = targetOffsetX,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "letter_offset_$letter"
            )

            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "letter_scale_$letter"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = animatedOffsetX
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isCurrentSelected) 16.sp else ZuneDimens.AlphabetLetterSize,
                        fontWeight = when {
                            isCurrentSelected -> FontWeight.ExtraBold
                            isAvailable -> FontWeight.Medium
                            else -> FontWeight.Thin
                        }
                    ),
                    color = when {
                        isCurrentSelected -> zuneColors.accentColor
                        isAvailable -> MaterialTheme.colorScheme.onBackground
                        else -> zuneColors.textDim.copy(alpha = 0.25f)
                    },
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
