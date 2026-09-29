package com.serkantkn.zunelauncher.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.components.TileIconImage
import com.serkantkn.zunelauncher.ui.components.getHubIcon
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.screens.settings.SelectionDot
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.TileIconFace
import androidx.compose.material3.Icon

/**
 * What a hub's tile or title opens when it is tapped: the hub itself, or any app on the phone in
 * its place — for whoever would rather the "internet" tile opened Chrome.
 *
 * A page like the icon picker, since it lists every app there is. Each app is drawn the way its
 * own tile would draw it, so the list reads as part of the board.
 */
@Composable
fun HubTargetPicker(
    hub: HubType,
    apps: List<AppInfo>,
    current: String?,
    face: (AppInfo) -> TileIconFace,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    BackHandler(onBack = onDismiss)

    val sorted = remember(apps) { apps.sortedBy { it.label.lowercase() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(PICKER_Z)
            .background(if (zuneColors.isDark) Color.Black else Color.White)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = {})
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 18.dp, bottom = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.hub_target_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 34.sp),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(hub.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal,
                    bottom = 24.dp
                ),
                modifier = Modifier.fillMaxSize()
            ) {
                item(key = "hub") {
                    TargetRow(
                        icon = {
                            Icon(
                                imageVector = getHubIcon(hub),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        title = stringResource(R.string.hub_target_hub),
                        subtitle = stringResource(R.string.hub_target_hub_sub),
                        selected = current == null,
                        onClick = { onPick(null) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(sorted, key = { it.packageName }) { app ->
                    TargetRow(
                        icon = {
                            TileIconImage(face = face(app), size = 22.dp, ink = Color.White, contentDescription = null)
                        },
                        title = app.label,
                        subtitle = if (current == app.packageName) stringResource(R.string.hub_target_app_sub) else "",
                        selected = current == app.packageName,
                        onClick = { onPick(app.packageName) }
                    )
                }
            }
        }
    }
}

/** One choice: a small accent tile with the icon on it, the name, and the selection dot. */
@Composable
private fun TargetRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(zuneColors.accentColor.copy(alpha = if (selected) 1f else 0.6f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 18.sp),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        SelectionDot(selected = selected)
    }
}

/** Over the customise page, which is where this is usually opened from. */
private const val PICKER_Z = 50f
