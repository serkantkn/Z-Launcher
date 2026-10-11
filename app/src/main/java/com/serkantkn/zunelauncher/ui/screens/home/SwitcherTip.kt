package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The balloon that teaches the switcher, shown once: the first time a hub is left running.
 *
 * The switcher has no button of its own — it is Home pressed twice — so nothing on screen would
 * ever tell anyone it is there. The balloon stands just above the navigation bar with its point
 * on the Home key, and two rings go out from the key one after the other, the way the two
 * presses go. A tap anywhere, or the wait running out, puts it away.
 */
@Composable
fun SwitcherTip(onDismiss: () -> Unit) {
    val accent = LocalZuneColors.current.accentColor
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val appear = remember { Animatable(0f) }
    val firstRing = remember { Animatable(0f) }
    val secondRing = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        appear.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(TIP_MILLIS)
        appear.animateTo(0f, tween(220))
        onDismiss()
    }
    // Two presses: a ring for each, a beat apart, again and again while the balloon is up.
    LaunchedEffect(Unit) {
        delay(400)
        while (true) {
            firstRing.snapTo(0f)
            secondRing.snapTo(0f)
            launch { firstRing.animateTo(1f, tween(700, easing = LinearEasing)) }
            delay(260)
            secondRing.animateTo(1f, tween(700, easing = LinearEasing))
            delay(900)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    ) {
        // The rings, on the Home key itself.
        Canvas(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (navBottom / 2 - 28.dp).coerceAtLeast(0.dp))
                .size(56.dp)
                .graphicsLayer { alpha = appear.value }
        ) {
            listOf(firstRing.value, secondRing.value).forEach { t ->
                if (t > 0f && t < 1f) {
                    drawCircle(
                        color = accent.copy(alpha = 1f - t),
                        radius = size.minDimension / 2f * (0.35f + 0.65f * t),
                        center = center,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = navBottom + 6.dp)
                .widthIn(max = 380.dp)
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 24.dp.toPx()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_running_hubs),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 22.sp),
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.switcher_tip_text),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    color = Color.White
                )
                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = stringResource(R.string.switcher_tip_ok),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        color = Color.White,
                        modifier = Modifier
                            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }
            }
            // The point, aimed down at the Home key.
            Canvas(modifier = Modifier.size(width = 22.dp, height = 12.dp)) {
                val point = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(point, accent)
            }
        }
    }
}

/** How long the balloon stays if nobody touches it. */
private const val TIP_MILLIS = 9_000L
