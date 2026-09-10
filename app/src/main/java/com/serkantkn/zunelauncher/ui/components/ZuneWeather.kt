package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * The weather line under the clock on the Zune start screen: temperature, sky and where. It draws
 * nothing at all until the weather hub has a forecast cached — a made-up temperature would be
 * worse than an empty line.
 */
@Composable
fun ZuneWeather(
    temperature: String?,
    conditionLabel: String?,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    if (temperature == null || icon == null) return
    val zuneColors = LocalZuneColors.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = conditionLabel,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(end = 8.dp).size(20.dp)
        )
        Column {
            Text(
                text = temperature,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!conditionLabel.isNullOrBlank()) {
                Text(
                    text = conditionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
