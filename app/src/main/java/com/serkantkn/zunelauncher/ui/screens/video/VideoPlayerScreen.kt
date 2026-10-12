package com.serkantkn.zunelauncher.ui.screens.video

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.screens.messaging.RingButton
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay

/**
 * What the player draws: the picture, and at a tap the controls over it. In the small window
 * there is only the picture; the system draws that window's buttons.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayerScreen(
    player: ExoPlayer,
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    errorText: String?,
    isInPip: Boolean,
    resumedFromMs: Long,
    canOpenElsewhere: Boolean,
    canPip: Boolean,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onRotate: () -> Unit,
    onPip: () -> Unit,
    onControlsShown: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenElsewhere: () -> Unit
) {
    val accent = LocalZuneColors.current.accentColor
    var controlsVisible by remember { mutableStateOf(true) }
    /** Bumped by every touch, so the controls stay while they are being used. */
    var touchedAt by remember { mutableLongStateOf(0L) }
    /** -1 or +1 while the jump just made is shown at that side; 0 otherwise. */
    var jumpSide by remember { mutableIntStateOf(0) }
    var jumpCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(controlsVisible, isPlaying, touchedAt) {
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_LINGER_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, isInPip) { onControlsShown(controlsVisible && !isInPip) }
    LaunchedEffect(jumpCount) {
        if (jumpCount > 0) {
            delay(600L)
            jumpSide = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isInPip) return@Box

        // Taps anywhere: one shows or hides the controls, two at a side jump ten seconds.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                            touchedAt = System.currentTimeMillis()
                        },
                        onDoubleTap = { offset ->
                            val third = size.width / 3f
                            val side = when {
                                offset.x < third -> -1
                                offset.x > third * 2 -> 1
                                else -> 0
                            }
                            if (side == 0) {
                                onTogglePlay()
                            } else {
                                onSeekBy(side * JUMP_MS)
                                jumpSide = side
                                jumpCount++
                            }
                            touchedAt = System.currentTimeMillis()
                        }
                    )
                }
        )

        // The ten seconds just jumped, at the side it went.
        if (jumpSide != 0) {
            Text(
                text = stringResource(if (jumpSide < 0) R.string.video_back10 else R.string.video_forward10),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 22.sp),
                color = Color.White,
                modifier = Modifier
                    .align(if (jumpSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (isBuffering && errorText == null) {
            CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }

        AnimatedVisibility(
            visible = controlsVisible && errorText == null,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(260)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))) {
                // ── The name, and what the screen itself can do ──
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 24.dp, end = 16.dp, top = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (canPip) {
                            Spacer(modifier = Modifier.width(12.dp))
                            RingButton(Icons.Default.PictureInPictureAlt, stringResource(R.string.video_pip), enabled = true, onClick = onPip)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        RingButton(Icons.Default.ScreenRotation, stringResource(R.string.video_rotate), enabled = true) {
                            touchedAt = System.currentTimeMillis()
                            onRotate()
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        RingButton(Icons.Default.Close, stringResource(R.string.common_close_cap), enabled = true, onClick = onClose)
                    }
                    if (resumedFromMs > 0L) {
                        Text(
                            text = stringResource(R.string.video_resumed_from, formatTime(resumedFromMs)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // ── Back ten, play or pause, on ten ──
                Row(
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    RingButton(Icons.Default.Replay10, stringResource(R.string.video_back10), enabled = true, size = 56.dp) {
                        touchedAt = System.currentTimeMillis()
                        onSeekBy(-JUMP_MS)
                    }
                    RingButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = stringResource(if (isPlaying) R.string.video_pause else R.string.video_play),
                        enabled = true,
                        size = 80.dp
                    ) {
                        touchedAt = System.currentTimeMillis()
                        onTogglePlay()
                    }
                    RingButton(Icons.Default.Forward10, stringResource(R.string.video_forward10), enabled = true, size = 56.dp) {
                        touchedAt = System.currentTimeMillis()
                        onSeekBy(JUMP_MS)
                    }
                }

                // ── Where in the film ──
                SeekLine(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accent = accent,
                    onSeekTo = onSeekTo,
                    onTouched = { touchedAt = System.currentTimeMillis() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                )
            }
        }

        if (errorText != null) {
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            ) {
                Text(
                    text = errorText,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
                    color = Color.White
                )
                if (canOpenElsewhere) {
                    Text(
                        text = stringResource(R.string.video_open_elsewhere),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = accent,
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .clickable(onClick = onOpenElsewhere)
                            .padding(vertical = 4.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.common_close),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable(onClick = onClose)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * The film's length as a thin line, the part already watched in the accent, with a narrow bar
 * at the place - tapped or dragged to go somewhere else. The time follows the finger while it
 * drags, and the film moves there when it lets go.
 */
@Composable
private fun SeekLine(
    positionMs: Long,
    durationMs: Long,
    accent: Color,
    onSeekTo: (Long) -> Unit,
    onTouched: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = dragFraction?.let { (it * durationMs).toLong() } ?: positionMs

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        onTouched()
                        if (durationMs > 0) onSeekTo((offset.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onTouched()
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            dragFraction?.let { if (durationMs > 0) onSeekTo((it * durationMs).toLong()) }
                            dragFraction = null
                        },
                        onDragCancel = { dragFraction = null }
                    ) { change, _ ->
                        onTouched()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            val line = 3.dp.toPx()
            val y = size.height / 2
            drawRect(Color.White.copy(alpha = 0.3f), topLeft = Offset(0f, y - line / 2), size = Size(size.width, line))
            drawRect(accent, topLeft = Offset(0f, y - line / 2), size = Size(size.width * fraction, line))
            val thumbWidth = 6.dp.toPx()
            val thumbHeight = 20.dp.toPx()
            drawRect(
                Color.White,
                topLeft = Offset((size.width * fraction - thumbWidth / 2).coerceIn(0f, size.width - thumbWidth), y - thumbHeight / 2),
                size = Size(thumbWidth, thumbHeight)
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(formatTime(shownMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                if (durationMs > 0) formatTime(durationMs) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

/** 4:05, or 1:02:03 for anything an hour long or more. */
internal fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private const val JUMP_MS = 10_000L
private const val CONTROLS_LINGER_MS = 3_000L
