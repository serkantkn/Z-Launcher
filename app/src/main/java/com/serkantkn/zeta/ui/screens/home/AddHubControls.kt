package com.serkantkn.zeta.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.HubId
import com.serkantkn.zeta.ui.components.ZuneDialogButton
import com.serkantkn.zeta.ui.components.ZuneFlipDialog
import com.serkantkn.zeta.ui.components.hubGlyph
import com.serkantkn.zeta.ui.components.hubTitle
import com.serkantkn.zeta.ui.theme.LocalZuneColors

/**
 * The way back for a hub taken off the home screen.
 *
 * It only exists while the board is being edited, and only while something is actually missing:
 * a button that is always there, offering nothing, is worse than no button.
 */
@Composable
fun AddHubButton(
    missing: List<HubId>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (missing.isEmpty()) return
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .padding(top = 16.dp)
            .border(1.dp, ink.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = zuneColors.accentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = stringResource(R.string.home_add_hub),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 14.sp
            ),
            color = ink.copy(alpha = 0.85f)
        )
    }
}

/** The hubs that are off the home screen, offered back one tap at a time. */
@Composable
fun AddHubDialog(
    missing: List<HubId>,
    onPick: (HubId) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.home_add_hub_title),
        dismissButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim { onDismiss() } },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            missing.forEach { hub ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { dismissWithAnim { onPick(hub) } }
                        .padding(vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(zuneColors.accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        if (hub is com.serkantkn.zeta.data.model.ExternalHub) {
                            com.serkantkn.zeta.ui.components.TileIconImage(
                                face = com.serkantkn.zeta.ui.components.externalHubFace(
                                    androidx.compose.ui.platform.LocalContext.current, hub
                                ),
                                size = 20.dp,
                                ink = Color.White,
                                contentDescription = null
                            )
                        } else {
                            Icon(
                                imageVector = hubGlyph(hub),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = hubTitle(hub),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Light
                            ),
                            color = Color.White
                        )
                        // Another app's hub says whose it is: adding it is trusting that app.
                        if (hub is com.serkantkn.zeta.data.model.ExternalHub) {
                            val from = com.serkantkn.zeta.data.hubs.HubDirectory.find(hub)?.appLabel ?: hub.packageName
                            Text(
                                text = stringResource(R.string.external_hub_from, from),
                                style = MaterialTheme.typography.bodySmall,
                                color = zuneColors.textMuted
                            )
                        }
                    }
                }
            }
            if (missing.any { it is com.serkantkn.zeta.data.model.ExternalHub }) {
                Text(
                    text = stringResource(R.string.external_hub_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
