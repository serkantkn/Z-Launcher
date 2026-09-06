package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/** Visual variants of [ZunePermissionRequest]. */
enum class ZunePermissionRequestStyle {
    /**
     * Windows Phone list style: left-aligned title / muted message / accent button, offset from
     * the pivot header by [ZuneDimens.SpacingHuge]. Used by the pictures, people, social and
     * phone hubs.
     */
    Inline,

    /**
     * Centered card style with a large accent [ImageVector] on top and a bold title. Used by the
     * files hub for the "all files access" request.
     */
    Centered
}

/**
 * Shared "permission needed" placeholder shown in place of hub content until the user grants
 * the permission. Strings are passed through unchanged (callers apply `.lowercase()` themselves
 * where the Windows Phone look requires it).
 *
 * @param icon Only rendered in [ZunePermissionRequestStyle.Centered].
 */
@Composable
fun ZunePermissionRequest(
    title: String,
    message: String,
    buttonLabel: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier,
    style: ZunePermissionRequestStyle = ZunePermissionRequestStyle.Inline,
    icon: ImageVector? = null
) {
    val zuneColors = LocalZuneColors.current
    when (style) {
        ZunePermissionRequestStyle.Inline -> {
            Column(modifier = modifier.padding(top = ZuneDimens.SpacingHuge)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted
                )
                Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))
                Button(
                    onClick = onRequest,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = zuneColors.accentColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(text = buttonLabel)
                }
            }
        }

        ZunePermissionRequestStyle.Centered -> {
            Box(
                modifier = modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = zuneColors.accentColor,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textMuted
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onRequest,
                        colors = ButtonDefaults.buttonColors(containerColor = zuneColors.accentColor)
                    ) {
                        Text(text = buttonLabel)
                    }
                }
            }
        }
    }
}

/**
 * Shared hub empty-state line: dim, lowercase body text offset from the pivot header by
 * [ZuneDimens.SpacingLg]. [message] is lowercased here to match the Windows Phone look.
 */
@Composable
fun ZuneEmptyState(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message.lowercase(),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.textDim,
        modifier = modifier.padding(top = ZuneDimens.SpacingLg)
    )
}
