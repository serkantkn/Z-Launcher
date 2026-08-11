package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ZuneDate(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var currentDate by remember { mutableStateOf(getCurrentDate()) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.5f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "date_alpha"
    )

    LaunchedEffect(Unit) {
        while (true) {
            currentDate = getCurrentDate()
            delay(60_000L)
        }
    }

    Text(
        text = currentDate,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Light,
            shadow = androidx.compose.ui.graphics.Shadow(
                color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                blurRadius = 4f
            )
        ),
        color = LocalZuneColors.current.textMuted,
        modifier = modifier
            .alpha(alpha)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    )
}

private fun getCurrentDate(): String {
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(Date()).lowercase()
}
