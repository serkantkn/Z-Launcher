package com.serkantkn.zunelauncher.ui.screens.home

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.AppShortcuts
import com.serkantkn.zunelauncher.util.toImageBitmap

/**
 * What a held app tile offers: the app's own shortcuts, and editing Start — which is what a
 * held tile used to do straight away, and still does for an app with no shortcuts to show.
 */
@Composable
fun AppShortcutsDialog(
    appLabel: String,
    shortcuts: List<ShortcutInfo>,
    onPick: (ShortcutInfo) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = appLabel,
        dismissButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim { onDismiss() } },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            shortcuts.forEach { shortcut ->
                val icon = remember(shortcut.id) {
                    runCatching { AppShortcuts.icon(context, shortcut)?.toImageBitmap() }.getOrNull()
                }
                ShortcutRow(
                    label = (shortcut.longLabel ?: shortcut.shortLabel)?.toString().orEmpty(),
                    onClick = { dismissWithAnim { onPick(shortcut) } }
                ) {
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
            }
            ShortcutRow(
                label = stringResource(R.string.start_edit_start),
                onClick = { dismissWithAnim { onEdit() } }
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(zuneColors.accentColor),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
