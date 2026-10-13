package com.serkantkn.zunelauncher.ui.screens.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ChromeReaderMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay

/**
 * Reading view's button beside the address: there only while the page reads as an article (or
 * is already in reading view, when it is lit in the accent to say so).
 *
 * When a page turns out to be readable, a balloon points at the button once to say so. It goes
 * by itself after a few seconds, or as soon as anything else is touched; touched itself, it opens
 * reading view.
 *
 * @param page the address on show; the balloon is offered once for each
 * @param balloonBelow true where the address bar runs along the top (a tablet), so the balloon
 * hangs under the button; on a phone the bar is at the bottom and the balloon stands above it
 */
@Composable
internal fun ReadingViewButton(
    available: Boolean,
    isReadingView: Boolean,
    page: String,
    balloonBelow: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    var balloonShownFor by rememberSaveable { mutableStateOf<String?>(null) }
    var balloon by remember { mutableStateOf(false) }

    LaunchedEffect(available, isReadingView, page) {
        if (!available || isReadingView) {
            balloon = false
            return@LaunchedEffect
        }
        if (balloonShownFor == page) return@LaunchedEffect
        balloonShownFor = page
        balloon = true
        delay(BALLOON_MILLIS)
        balloon = false
    }

    AnimatedVisibility(
        visible = available || isReadingView,
        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.6f),
        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.6f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(if (isReadingView) zuneColors.accentColor else Color.Transparent)
                .clickable {
                    balloon = false
                    onToggle()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ChromeReaderMode,
                contentDescription = stringResource(
                    if (isReadingView) R.string.browser_reading_view_off else R.string.browser_reading_view
                ),
                tint = if (isReadingView) Color.White else ink,
                modifier = Modifier.size(20.dp)
            )
            if (balloon) {
                MetroBalloon(
                    title = stringResource(R.string.browser_reading_view),
                    message = stringResource(R.string.browser_reading_view_balloon),
                    below = balloonBelow,
                    onDismiss = { balloon = false },
                    onClick = {
                        balloon = false
                        onToggle()
                    }
                )
            }
        }
    }
}

/**
 * A balloon: a block in the accent colour with a point towards what it is about - the button it
 * is drawn from. Not focusable, so the page and the keyboard carry on as they were; a touch
 * anywhere outside it closes it, and [onClick], when there is one, is what touching it does.
 */
@Composable
internal fun MetroBalloon(
    title: String,
    message: String,
    below: Boolean,
    onDismiss: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    val accent = LocalZuneColors.current.accentColor
    val density = LocalDensity.current
    val gap = with(density) { 6.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val pointSize = 10.dp
    // Where along the balloon the point stands, worked out when the balloon is placed: a
    // balloon near the screen's edge is pushed in from it, and the point still finds the button.
    var pointX by remember { mutableIntStateOf(0) }
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }

    val position = remember(below) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val y = if (below) anchorBounds.bottom + gap else anchorBounds.top - popupContentSize.height - gap
                pointX = anchorBounds.center.x - x
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = position,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false, dismissOnClickOutside = true, dismissOnBackPress = true)
    ) {
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(180)) + slideInVertically(tween(220)) { if (below) -it / 4 else it / 4 },
            exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { if (below) -it / 4 else it / 4 }
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                if (below) BalloonPoint(pointX, up = true, color = accent, size = pointSize)
                Column(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .background(accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick ?: onDismiss
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        color = Color.White
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp),
                        color = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (!below) BalloonPoint(pointX, up = false, color = accent, size = pointSize)
            }
        }
    }
}

/** The balloon's point: a small triangle under or over the button's middle. */
@Composable
private fun BalloonPoint(centreX: Int, up: Boolean, color: Color, size: Dp) {
    val density = LocalDensity.current
    val half = with(density) { size.toPx() }
    Canvas(modifier = Modifier.size(width = 1.dp, height = size)) {
        val x = centreX.toFloat()
        val path = Path().apply {
            if (up) {
                moveTo(x - half, this@Canvas.size.height)
                lineTo(x, 0f)
                lineTo(x + half, this@Canvas.size.height)
            } else {
                moveTo(x - half, 0f)
                lineTo(x, this@Canvas.size.height)
                lineTo(x + half, 0f)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** How long the balloon stays when nothing is touched. */
private const val BALLOON_MILLIS = 5_000L
