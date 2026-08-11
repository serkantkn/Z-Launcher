package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow

/**
 * Large Metro-style hub title with accent color, text shadow for readability, and guaranteed press animation.
 * Used on the Home Hub to navigate to other hubs.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZuneHubTitle(
    title: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    fontSize: TextUnit = ZuneDimens.HubTitleFontSize,
    verticalPadding: androidx.compose.ui.unit.Dp = ZuneDimens.SpacingXs
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressedState by interactionSource.collectIsPressedAsState()
    var isClickedPressed by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    val isVisualPressed = isPressedState || isClickedPressed

    val alpha by animateFloatAsState(
        targetValue = if (isVisualPressed) 0.35f else 0.85f,
        animationSpec = tween(durationMillis = 100),
        label = "hub_title_alpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isVisualPressed) 0.88f else 1f,
        animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing),
        label = "hub_title_scale"
    )

    val zuneColors = LocalZuneColors.current

    Text(
        text = title,
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = fontSize,
            shadow = Shadow(
                color = if (zuneColors.isDark) Color.Black.copy(alpha = 0.55f)
                        else Color.White.copy(alpha = 0.85f),
                offset = Offset(2f, 3f),
                blurRadius = 6f
            )
        ),
        color = accentColor,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(0f, 0.5f) // Shrink towards left edge (Metro style)
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    coroutineScope.launch {
                        isClickedPressed = true
                        delay(120)
                        isClickedPressed = false
                        onClick()
                    }
                },
                onLongClick = onLongClick
            )
            .padding(vertical = verticalPadding)
    )
}
