package com.serkantkn.zunelauncher.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.components.getHubIcon
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlin.math.roundToInt

/** A running hub and where its tile sits, in root coordinates, so a preview can grow out of it. */
data class RunningHubTarget(val hub: HubType, val tile: Rect)

/**
 * The hubs left running, under everything else on Start.
 *
 * Only the launcher's own hubs appear here. An app you started from the app list is Android's
 * business and lives in Android's recents; putting it in this strip as well would be claiming
 * something about it that the launcher does not control.
 *
 * Tapping a tile goes back into the hub exactly where it stood. The cross closes it for good —
 * the same thing the Back key does from inside. Holding one asks [onPreview] for the balloon.
 */
@Composable
fun RunningHubsSection(
    hubs: List<HubType>,
    cornerStyle: TileCornerStyle,
    onOpen: (HubType) -> Unit,
    onStop: (HubType) -> Unit,
    onPreview: (RunningHubTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    if (hubs.isEmpty()) return
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
                    onPreview = { bounds -> onPreview(RunningHubTarget(hub, bounds)) },
                    onStop = { onStop(hub) }
                )
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
    onPreview: (Rect) -> Unit,
    onStop: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val shape = when (cornerStyle) {
        TileCornerStyle.SHARP -> RectangleShape
        TileCornerStyle.ROUNDED -> RoundedCornerShape(8.dp)
    }
    val name = stringResource(hub.titleRes)
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(TILE)
    ) {
        Box(modifier = Modifier.size(TILE)) {
            Box(
                modifier = Modifier
                    .size(TILE)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .clip(shape)
                    .background(zuneColors.accentColor)
                    .combinedClickable(
                        onClick = onOpen,
                        onLongClick = { if (bounds != Rect.Zero) onPreview(bounds) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getHubIcon(hub),
                    contentDescription = name,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
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

/**
 * The held tile, blown up into a small picture of the hub without the Start screen going away.
 *
 * Metro has no vocabulary for a balloon — it moves whole panes, it does not pop things out of
 * them — so the shapes stay Metro even where the gesture cannot: no rounded card, no drop shadow,
 * a single hairline of the accent colour around the picture, and the two actions are the flat
 * lettered blocks the launcher uses everywhere else. What is borrowed is only the growing: the
 * picture comes out of the tile that was held and, when dismissed, goes back into it.
 *
 * [hostOrigin] is where the box hosting this sits in root coordinates; the tile's bounds arrive in
 * root coordinates too, and the difference is what puts the balloon over the right tile.
 */
@Composable
fun HubPreviewBalloon(
    target: RunningHubTarget,
    preview: ImageBitmap?,
    hostOrigin: Offset,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(target.hub) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(170, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }
    BackHandler(enabled = !closing) { closing = true }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val hostW = constraints.maxWidth.toFloat()
        val hostH = constraints.maxHeight.toFloat()
        val tile = target.tile.translate(-hostOrigin.x, -hostOrigin.y)

        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 12.dp.toPx() }
        val actionsW = with(density) { ACTION_WIDTH.toPx() }

        // A small picture, not a full-screen one: a glance at the hub while Start stays put.
        val aspect = preview?.let { it.width.toFloat() / it.height.toFloat() } ?: 0.46f
        var balloonH = hostH * 0.40f
        var balloonW = balloonH * aspect
        val widest = hostW - 2 * margin - gap - actionsW
        if (balloonW > widest) {
            balloonW = widest
            balloonH = balloonW / aspect
        }

        var left = (tile.center.x - balloonW / 2f)
            .coerceIn(margin, (hostW - balloonW - margin).coerceAtLeast(margin))
        // Whichever side the tile leaves room on; the tile is usually near the left edge, so the
        // actions usually end up on the right.
        val roomRight = hostW - (left + balloonW) - gap - margin
        val actionsOnRight = roomRight >= actionsW
        if (!actionsOnRight) {
            val needed = margin + actionsW + gap
            if (left < needed) left = needed.coerceAtMost(hostW - balloonW - margin)
        }

        var top = tile.top - gap - balloonH
        if (top < margin) top = tile.bottom + gap
        if (top + balloonH > hostH - margin) top = ((hostH - balloonH) / 2f).coerceAtLeast(margin)

        val originX = ((tile.center.x - left) / balloonW).coerceIn(-2f, 3f)
        val originY = ((tile.center.y - top) / balloonH).coerceIn(-2f, 3f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = SCRIM))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = { closing = true }
                )
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    width = with(density) { balloonW.toDp() },
                    height = with(density) { balloonH.toDp() }
                )
                .graphicsLayer {
                    val p = progress.value
                    scaleX = 0.25f + 0.75f * p
                    scaleY = 0.25f + 0.75f * p
                    alpha = p
                    transformOrigin = TransformOrigin(originX, originY)
                }
                .border(1.dp, zuneColors.accentColor)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = stringResource(R.string.home_running_hub_preview),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(zuneColors.accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getHubIcon(target.hub),
                        contentDescription = stringResource(target.hub.titleRes),
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        val actionsX = if (actionsOnRight) left + balloonW + gap else left - gap - actionsW
        val actionsH = with(density) { (ACTION_HEIGHT * 2 + 8.dp).toPx() }
        val actionsY = (top + balloonH / 2f - actionsH / 2f).coerceIn(margin, hostH - actionsH - margin)

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .offset { IntOffset(actionsX.roundToInt(), actionsY.roundToInt()) }
                .width(ACTION_WIDTH)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    scaleX = 0.7f + 0.3f * p
                    scaleY = 0.7f + 0.3f * p
                    transformOrigin = TransformOrigin(if (actionsOnRight) 0f else 1f, 0.5f)
                }
        ) {
            ActionBlock(
                label = stringResource(R.string.home_running_hub_open),
                fill = zuneColors.accentColor,
                onClick = onOpen
            )
            ActionBlock(
                label = stringResource(R.string.home_running_hub_close),
                fill = Color.Black.copy(alpha = 0.72f),
                outlined = true,
                onClick = onStop
            )
        }
    }
}

@Composable
private fun ActionBlock(
    label: String,
    fill: Color,
    onClick: () -> Unit,
    outlined: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ACTION_HEIGHT)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = if (outlined) 0.7f else 0f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Light),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

private val TILE = 56.dp
private val ACTION_WIDTH = 92.dp
private val ACTION_HEIGHT = 42.dp

/** Dark enough to push Start back, light enough that the tiles are still plainly there. */
private const val SCRIM = 0.64f
