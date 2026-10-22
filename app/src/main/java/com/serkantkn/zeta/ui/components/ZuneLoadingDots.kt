package com.serkantkn.zeta.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.ui.theme.LocalZuneColors

/**
 * Windows Phone's progress: dots in the accent colour running across the top of the page, slow
 * in the middle and quick at the ends, for as long as something is being fetched.
 */
@Composable
fun ZuneLoadingDots(modifier: Modifier = Modifier) {
    val accent = LocalZuneColors.current.accentColor
    val transition = rememberInfiniteTransition(label = "zune_dots")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "zune_dots_progress"
    )
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(4.dp)) {
        val width = maxWidth
        repeat(5) { index ->
            // Each dot starts a little after the one before it; the cubic makes them bunch up in
            // the middle and race at the edges, as the phone's did.
            val t = (progress * 1.6f - index * 0.08f).coerceIn(0f, 1f)
            val eased = 0.5f + 4f * (t - 0.5f) * (t - 0.5f) * (t - 0.5f)
            if (t > 0f && t < 1f) {
                Box(
                    modifier = Modifier
                        .offset(x = width * eased)
                        .size(4.dp)
                        .background(accent)
                )
            }
        }
    }
}
