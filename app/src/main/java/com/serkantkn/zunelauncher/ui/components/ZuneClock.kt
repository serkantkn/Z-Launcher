package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ZuneClock(
    notification: SocialMessageModel? = null,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf(getCurrentTime()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            delay(1000L)
        }
    }

    val isNotificationVisible = notification != null
    val squeezeProgress by animateFloatAsState(
        targetValue = if (isNotificationVisible) 1f else 0f,
        animationSpec = tween(600, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "clock_squeeze"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Clock with Squeeze Effect
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    // Squeeze the layout width by up to 75% based on progress
                    val squashedWidth = (placeable.width * (1f - squeezeProgress * 0.75f)).toInt()
                    layout(squashedWidth, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }
                .graphicsLayer {
                    // Visually scale down the text by the exact same amount to fit the squashed bounds
                    scaleX = 1f - (squeezeProgress * 0.75f)
                    transformOrigin = TransformOrigin(0f, 0.5f) // Anchor to the left
                }
        ) {
            Row {
                currentTime.forEachIndexed { index, char ->
                    AnimatedContent(
                        targetState = char,
                        transitionSpec = {
                            (slideInVertically(
                                initialOffsetY = { fullHeight -> fullHeight / 2 },
                                animationSpec = tween(800)
                            ) + fadeIn(tween(800))).togetherWith(
                                slideOutVertically(
                                    targetOffsetY = { fullHeight -> -fullHeight / 2 },
                                    animationSpec = tween(800)
                                ) + fadeOut(tween(800))
                            )
                        },
                        label = "digit_transition_$index"
                    ) { targetChar ->
                        Text(
                            text = targetChar.toString(),
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Thin,
                                fontSize = ZuneDimens.ClockFontSize
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        // Notification Area Container
        val alphaProgress by animateFloatAsState(
            targetValue = if (isNotificationVisible) 1f else 0f,
            animationSpec = tween(400),
            label = "notification_alpha"
        )

        // Notification — content inside ZuneGlassSurface, sized by fillMaxWidth
        // (can't use matchParentSize here because height comes from glass surface's own content)
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = ZuneDimens.SpacingLg)
                .alpha(alphaProgress)
        ) {
            val zuneColors = LocalZuneColors.current
            val glassTint = if (zuneColors.isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.20f)
            val fallbackTint = if (zuneColors.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
            val strokeColor = if (zuneColors.isDark) Color.White.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.55f)

            ZuneGlassSurface(
                tintColor = glassTint,
                fallbackColor = fallbackTint,
                borderColor = strokeColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZuneDimens.SpacingMd, vertical = ZuneDimens.SpacingSm),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = notification?.title ?: "",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = notification?.text ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun getCurrentTime(): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date())
}
