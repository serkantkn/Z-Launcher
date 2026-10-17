package com.serkantkn.zunelauncher.ui.screens.video

import com.serkantkn.zunelauncher.util.Pro
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
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
    hasPrevious: Boolean,
    hasNext: Boolean,
    itemChanges: Int,
    videoAspect: Float,
    options: VideoOptions,
    optionsHandler: VideoOptionsHandler,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStartOver: () -> Unit,
    onRotate: () -> Unit,
    onPip: () -> Unit,
    onControlsShown: (Boolean) -> Unit,
    onClose: () -> Unit,
    onOpenElsewhere: () -> Unit
) {
    val accent = LocalZuneColors.current.accentColor
    val context = androidx.compose.ui.platform.LocalContext.current
    var controlsVisible by remember { mutableStateOf(true) }
    var optionsOpen by remember { mutableStateOf(false) }
    /** Touches do nothing but show the lock, for a film watched by a child or in a pocket. */
    var locked by remember { mutableStateOf(false) }
    var lockShownAt by remember { mutableLongStateOf(0L) }
    /** Bumped by every touch, so the controls stay while they are being used. */
    var touchedAt by remember { mutableLongStateOf(0L) }
    /** -1 or +1 while the jumps just made are shown at that side; 0 otherwise. */
    var jumpSide by remember { mutableIntStateOf(0) }
    var jumpSeconds by remember { mutableIntStateOf(0) }
    var jumpCount by remember { mutableIntStateOf(0) }

    fun jump(side: Int) {
        onSeekBy(side * JUMP_MS)
        jumpSeconds = if (jumpSide == side) jumpSeconds + (JUMP_MS / 1000).toInt() else (JUMP_MS / 1000).toInt()
        jumpSide = side
        jumpCount++
    }

    LaunchedEffect(controlsVisible, isPlaying, touchedAt) {
        if (controlsVisible && isPlaying) {
            delay(CONTROLS_LINGER_MS)
            controlsVisible = false
        }
    }
    LaunchedEffect(controlsVisible, isInPip, locked, optionsOpen) {
        onControlsShown(controlsVisible && !isInPip && !locked && !optionsOpen)
    }
    LaunchedEffect(jumpCount) {
        if (jumpCount > 0) {
            delay(JUMP_WINDOW_MS)
            jumpSide = 0
            jumpSeconds = 0
        }
    }
    LaunchedEffect(lockShownAt) {
        if (lockShownAt > 0L) {
            delay(LOCK_LINGER_MS)
            lockShownAt = 0L
        }
    }
    // Another video of the queue has come on: its name is shown for a moment.
    LaunchedEffect(itemChanges) {
        if (itemChanges > 0 && !locked) {
            controlsVisible = true
            touchedAt = System.currentTimeMillis()
        }
    }

    BackHandler(enabled = optionsOpen || locked) {
        if (optionsOpen) optionsOpen = false else lockShownAt = System.currentTimeMillis()
    }

    val density = LocalDensity.current
    val navigationBottom = WindowInsets.navigationBars.getBottom(density)
    val controlsHeightPx = with(density) { CONTROLS_BOTTOM.toPx() } + navigationBottom

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val showControls = controlsVisible && !locked && !optionsOpen && errorText == null
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setKeepContentOnPlayerReset(true)
                    // Subtitles in white with a black edge, readable over any picture, as on
                    // Windows Phone; the film's own colours and fonts are left out.
                    subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setStyle(
                            CaptionStyleCompat(
                                android.graphics.Color.WHITE,
                                android.graphics.Color.TRANSPARENT,
                                android.graphics.Color.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                android.graphics.Color.BLACK,
                                null
                            )
                        )
                        setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * 1.1f)
                    }
                    this.player = player
                }
            },
            update = { view ->
                view.resizeMode = if (options.fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                // Lifted over the line along the bottom while the controls are up, and out of the
                // part of a filled picture that runs off the screen.
                view.subtitleView?.setBottomPaddingFraction(
                    subtitleLift(
                        viewWidth = view.width.toFloat(),
                        viewHeight = view.height.toFloat(),
                        videoAspect = videoAspect,
                        fill = options.fill,
                        coveredPx = if (showControls && !isInPip) controlsHeightPx else 0f
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isInPip) return@Box

        // Taps anywhere: one shows or hides the controls, two at a side jump ten seconds and each
        // tap after them ten more; two fingers spread the picture to fill the screen, or pinch it
        // back. While locked, a touch only shows the lock.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    if (locked) {
                        detectTapGestures { lockShownAt = System.currentTimeMillis() }
                        return@pointerInput
                    }
                    detectTapGestures(
                        onTap = { offset ->
                            val third = size.width / 3f
                            val side = when {
                                offset.x < third -> -1
                                offset.x > third * 2 -> 1
                                else -> 0
                            }
                            if (side != 0 && side == jumpSide) {
                                jump(side)
                            } else {
                                controlsVisible = !controlsVisible
                            }
                            touchedAt = System.currentTimeMillis()
                        },
                        onDoubleTap = { offset ->
                            val third = size.width / 3f
                            val side = when {
                                offset.x < third -> -1
                                offset.x > third * 2 -> 1
                                else -> 0
                            }
                            if (side == 0) onTogglePlay() else jump(side)
                            touchedAt = System.currentTimeMillis()
                        }
                    )
                }
                .pointerInput(locked) {
                    if (locked) return@pointerInput
                    // Filling the screen is Pro's, by pinch as by the options.
                    detectPinch { spread -> if (Pro.unlocked) optionsHandler.onFill(spread) }
                }
        )

        if (isBuffering && errorText == null) {
            CircularProgressIndicator(color = accent, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }

        AnimatedVisibility(
            visible = showControls,
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
                        RingButton(Icons.Default.MoreHoriz, stringResource(R.string.video_more), enabled = true) {
                            // Sound tracks, subtitles, speed, fill and what comes next are Pro's.
                            Pro.orTell(context) { optionsOpen = true }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        RingButton(Icons.Default.Close, stringResource(R.string.common_close_cap), enabled = true, onClick = onClose)
                    }
                    if (resumedFromMs > 0L) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Text(
                                text = stringResource(R.string.video_resumed_from, formatTime(resumedFromMs)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                text = stringResource(R.string.video_start_over),
                                style = MaterialTheme.typography.bodyMedium,
                                color = accent,
                                maxLines = 1,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable {
                                        touchedAt = System.currentTimeMillis()
                                        onStartOver()
                                    }
                                    .padding(vertical = 6.dp)
                            )
                        }
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

                // ── Where in the film, and the films either side of it ──
                SeekLine(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    accent = accent,
                    onSeekTo = onSeekTo,
                    onTouched = { touchedAt = System.currentTimeMillis() },
                    between = if (hasPrevious || hasNext) {
                        {
                            RingButton(Icons.Default.SkipPrevious, stringResource(R.string.video_previous), enabled = true) {
                                touchedAt = System.currentTimeMillis()
                                onPrevious()
                            }
                            Spacer(modifier = Modifier.width(20.dp))
                            RingButton(Icons.Default.SkipNext, stringResource(R.string.video_next), enabled = hasNext) {
                                touchedAt = System.currentTimeMillis()
                                onNext()
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                )
            }
        }

        // The seconds just jumped, at the side they went, over the controls if they are up.
        if (jumpSide != 0 && jumpSeconds > 0) {
            Text(
                text = stringResource(if (jumpSide < 0) R.string.video_back_n else R.string.video_forward_n, jumpSeconds),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 22.sp),
                color = Color.White,
                modifier = Modifier
                    .align(if (jumpSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ── The lock, shown at a touch while the screen is locked ──
        AnimatedVisibility(
            visible = locked && lockShownAt > 0L,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(260)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RingButton(Icons.Default.Lock, stringResource(R.string.video_unlock), enabled = true, size = 72.dp) {
                    locked = false
                    lockShownAt = 0L
                    controlsVisible = true
                    touchedAt = System.currentTimeMillis()
                }
                Text(
                    text = stringResource(R.string.video_unlock_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = optionsOpen,
            enter = fadeIn(tween(160)) + slideInHorizontally(tween(260)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { it / 3 },
            modifier = Modifier.fillMaxSize()
        ) {
            VideoOptionsPanel(
                options = options,
                handler = optionsHandler,
                onLock = {
                    optionsOpen = false
                    controlsVisible = false
                    locked = true
                    lockShownAt = System.currentTimeMillis()
                },
                onDismiss = { optionsOpen = false }
            )
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
                if (hasNext) {
                    Text(
                        text = stringResource(R.string.video_next),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                        color = Color.White,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clickable(onClick = onNext)
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
 * How far up the subtitles sit, as a share of the picture's height: the usual small margin,
 * plus whatever of the picture is hidden at the bottom - cut off by filling the screen, or
 * covered by the controls.
 */
internal fun subtitleLift(viewWidth: Float, viewHeight: Float, videoAspect: Float, fill: Boolean, coveredPx: Float): Float {
    val margin = SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION
    if (viewWidth <= 0f || viewHeight <= 0f) return margin
    val pictureHeight = when {
        videoAspect <= 0f -> viewHeight
        fill -> maxOf(viewHeight, viewWidth / videoAspect)
        else -> minOf(viewHeight, viewWidth / videoAspect)
    }
    val pictureBottom = (viewHeight + pictureHeight) / 2f
    val hidden = pictureBottom - (viewHeight - coveredPx)
    return (margin + maxOf(0f, hidden) / pictureHeight).coerceAtMost(0.9f)
}

/**
 * Two fingers spreading apart ([onPinch] with true) or closing in (false), once per gesture.
 * One finger is left alone for the taps; two are taken from them.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectPinch(onPinch: (Boolean) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var startDistance = 0f
        var decided = false
        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2) {
                val distance = (pressed[0].position - pressed[1].position).getDistance()
                if (startDistance == 0f) startDistance = distance
                if (!decided && startDistance > 0f) {
                    val ratio = distance / startDistance
                    if (ratio > PINCH_RATIO) { onPinch(true); decided = true }
                    else if (ratio < 1f / PINCH_RATIO) { onPinch(false); decided = true }
                }
                event.changes.forEach { it.consume() }
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
    modifier: Modifier = Modifier,
    between: (@Composable () -> Unit)? = null
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(shownMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Spacer(modifier = Modifier.weight(1f))
            if (between != null) {
                between()
                Spacer(modifier = Modifier.weight(1f))
            }
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
private const val JUMP_WINDOW_MS = 800L
private const val CONTROLS_LINGER_MS = 3_000L
private const val LOCK_LINGER_MS = 2_500L
private const val PINCH_RATIO = 1.25f
/** The seek line and the buttons under it, from the bottom of the screen up. */
private val CONTROLS_BOTTOM = 112.dp
