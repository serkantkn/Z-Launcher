package com.serkantkn.zunelauncher.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.components.getHubIcon
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * The hubs left running, laid along the bottom of the screen for going straight from one to
 * another.
 *
 * Windows Phone put its task switcher behind a held Back key, which is not a key any more: on
 * gesture navigation there is no Back button to hold, and Home reaches the launcher as an intent
 * with no press length attached to it. Two quick presses of Home is the one trigger that works in
 * both navigation modes, so that is what opens this.
 *
 * Each hub shows the picture taken as it was left, the same one the Start strip shows when a tile
 * is held. It slides up from the navigation bar and goes back down the same way.
 */
@Composable
fun HubSwitcher(
    hubs: List<HubType>,
    previews: Map<HubType, ImageBitmap>,
    onPick: (HubType) -> Unit,
    onStop: (HubType) -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { progress.animateTo(1f, tween(240, easing = FastOutSlowInEasing)) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }
    LaunchedEffect(hubs) { if (hubs.isEmpty()) closing = true }
    BackHandler(enabled = !closing) { closing = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { closing = true }
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    translationY = size.height * (1f - p)
                }
                .background(if (zuneColors.isDark) Color(0xFF0B0B0B) else Color(0xFFF4F4F4))
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            ) {
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
                    color = if (zuneColors.isDark) {
                        Color.White.copy(alpha = 0.7f)
                    } else {
                        Color.Black.copy(alpha = 0.7f)
                    }
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp)
            ) {
                items(items = hubs, key = { it.name }) { hub ->
                    SwitcherCard(
                        hub = hub,
                        preview = previews[hub],
                        onPick = { closing = true; onPick(hub) },
                        onStop = { onStop(hub) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitcherCard(
    hub: HubType,
    preview: ImageBitmap?,
    onPick: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val name = stringResource(hub.titleRes)
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.width(CARD_WIDTH)) {
        Box(modifier = Modifier.size(width = CARD_WIDTH, height = CARD_HEIGHT)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(1.dp, zuneColors.accentColor)
                    .clickable(onClick = onPick),
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
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
                            imageVector = getHubIcon(hub),
                            contentDescription = name,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
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
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = ink.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private val CARD_WIDTH = 104.dp
private val CARD_HEIGHT = 168.dp
