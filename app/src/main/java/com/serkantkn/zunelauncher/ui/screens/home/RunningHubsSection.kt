package com.serkantkn.zunelauncher.ui.screens.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.components.getHubIcon
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * The hubs left running, under everything else on Start.
 *
 * Only the launcher's own hubs appear here. An app you started from the app list is Android's
 * business and lives in Android's recents; putting it in this strip as well would be claiming
 * something about it that the launcher does not control.
 *
 * Tapping a tile goes back into the hub exactly where it stood. The cross closes it for good —
 * the same thing the Back key does from inside.
 */
@Composable
fun RunningHubsSection(
    hubs: List<HubType>,
    cornerStyle: TileCornerStyle,
    previews: Map<HubType, ImageBitmap>,
    onOpen: (HubType) -> Unit,
    onStop: (HubType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (hubs.isEmpty()) return
    var previewHub by remember { mutableStateOf<HubType?>(null) }
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 22.dp, height = 2.dp)
                    .background(zuneColors.accentColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_running_hubs),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                ),
                color = ink.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            hubs.forEach { hub ->
                RunningHubTile(
                    hub = hub,
                    cornerStyle = cornerStyle,
                    ink = ink,
                    onOpen = { onOpen(hub) },
                    onPreview = { previewHub = hub },
                    onStop = { onStop(hub) }
                )
            }
        }
    }

    previewHub?.let { hub ->
        HubPreviewDialog(
            hub = hub,
            preview = previews[hub],
            onOpen = { previewHub = null; onOpen(hub) },
            onStop = { previewHub = null; onStop(hub) },
            onDismiss = { previewHub = null }
        )
    }
}

/**
 * What the hub looked like when it was left, held up on a dark field.
 *
 * The picture is the one taken as the hub turned away; a hub whose picture was lost — after the
 * launcher's process was restarted, say — falls back to its glyph rather than an empty frame.
 */
@Composable
private fun HubPreviewDialog(
    hub: HubType,
    preview: ImageBitmap?,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val name = stringResource(hub.titleRes)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.wrapContentSize()
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 34.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                // The frame hugs the picture: an aspect ratio taken from the bitmap itself, so
                // the border is around the hub and not around the empty space beside it.
                val frameHeight = screenHeight * 0.66f
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = stringResource(R.string.home_running_hub_preview),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(frameHeight)
                            .aspectRatio(preview.width.toFloat() / preview.height.toFloat())
                            .border(1.dp, zuneColors.accentColor)
                            .clickable(onClick = onOpen)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .height(frameHeight)
                            .aspectRatio(0.46f)
                            .background(zuneColors.accentColor)
                            .border(1.dp, zuneColors.accentColor)
                            .clickable(onClick = onOpen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getHubIcon(hub),
                            contentDescription = name,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Text(
                        text = stringResource(R.string.home_running_hub_open),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier
                            .clickable(onClick = onOpen)
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                    Text(
                        text = stringResource(R.string.home_running_hub_close),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier
                            .clickable(onClick = onStop)
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunningHubTile(
    hub: HubType,
    cornerStyle: TileCornerStyle,
    ink: Color,
    onOpen: () -> Unit,
    onPreview: () -> Unit,
    onStop: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val shape = when (cornerStyle) {
        TileCornerStyle.SHARP -> RectangleShape
        TileCornerStyle.ROUNDED -> RoundedCornerShape(8.dp)
    }
    val name = stringResource(hub.titleRes)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(TILE)
    ) {
        Box(modifier = Modifier.size(TILE)) {
            Box(
                modifier = Modifier
                    .size(TILE)
                    .clip(shape)
                    .background(zuneColors.accentColor)
                    .combinedClickable(onClick = onOpen, onLongClick = onPreview),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getHubIcon(hub),
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            // The cross sits half off the tile, the way the Start board's own remove badge does.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 0.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .clickable(onClick = onStop),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.home_running_hub_close),
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = ink.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val TILE = 56.dp
