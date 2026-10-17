package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.Pro

/**
 * A setting that is Pro's. In Pro it is simply there; in the free build it is shown, faded, with
 * a line saying so, and a touch anywhere on it says it again instead of changing anything.
 */
@Composable
fun ProLocked(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (Pro.unlocked) {
        Box(modifier = modifier) { content() }
        return
    }
    val context = LocalContext.current
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        ProNote()
        Box {
            Box(modifier = Modifier.alpha(LOCKED_ALPHA)) { content() }
            // Over the whole of it, so no part of the setting can be changed underneath.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        Pro.tell(context)
                    }
            )
        }
    }
}

/** "Z Launcher Pro", with a padlock: what a locked setting carries above it. */
@Composable
fun ProNote(modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.padding(start = 4.dp, bottom = 6.dp)) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = zuneColors.accentColor,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.pro_in_pro),
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.accentColor
        )
    }
}

private const val LOCKED_ALPHA = 0.4f
