package com.serkantkn.zunelauncher.ui.components

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * Large Metro-style hub title with accent color and press animation.
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
    val isPressed by interactionSource.collectIsPressedAsState()

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.5f else 0.85f,
        animationSpec = tween(durationMillis = ZuneDimens.AnimDurationShort),
        label = "hub_title_alpha"
    )

    Text(
        text = title,
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = fontSize
        ),
        color = accentColor,
        modifier = modifier
            .alpha(alpha)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = verticalPadding)
    )
}
