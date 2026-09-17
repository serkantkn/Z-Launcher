package com.serkantkn.zunelauncher.ui.screens.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CameraFacing
import com.serkantkn.zunelauncher.data.model.CameraMode
import com.serkantkn.zunelauncher.data.model.FlashMode
import com.serkantkn.zunelauncher.data.model.TimerOption
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.util.Locale

/**
 * Everything drawn over the viewfinder.
 *
 * It lives apart from the hub itself because the hub's own job — holding the camera open, reading
 * the gestures, answering the shutter — has nothing to do with how a square button looks.
 */

/** The thin strip of squares Windows Phone put along the top of the viewfinder. */
@Composable
internal fun CameraTopBar(
    flash: FlashMode,
    timer: TimerOption,
    gridLines: Boolean,
    facing: CameraFacing,
    mirrorFront: Boolean,
    canFlip: Boolean,
    hasFlash: Boolean,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onGrid: () -> Unit,
    onMirror: () -> Unit,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.35f))
            .statusBarsPadding()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 10.dp)
    ) {
        if (hasFlash) {
            CameraSquareButton(
                icon = when (flash) {
                    FlashMode.AUTO -> Icons.Default.FlashAuto
                    FlashMode.ON -> Icons.Default.FlashOn
                    FlashMode.OFF -> Icons.Default.FlashOff
                },
                label = stringResource(flash.labelRes),
                active = flash != FlashMode.OFF,
                onClick = onFlash
            )
        }
        CameraSquareButton(
            icon = Icons.Default.Timer,
            label = stringResource(timer.labelRes),
            active = timer != TimerOption.OFF,
            onClick = onTimer
        )
        CameraSquareButton(
            icon = Icons.Default.Grid3x3,
            label = stringResource(R.string.camera_grid),
            active = gridLines,
            onClick = onGrid
        )
        // Whether a selfie is kept the way the viewfinder showed it or the way the lens saw it is
        // only ever a question about the front camera, so it is only ever asked there.
        if (facing == CameraFacing.FRONT) {
            CameraSquareButton(
                icon = Icons.Default.Flip,
                label = stringResource(R.string.camera_mirror),
                active = mirrorFront,
                onClick = onMirror
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (canFlip) {
            CameraSquareButton(
                icon = Icons.Default.Cameraswitch,
                label = stringResource(R.string.camera_flip),
                active = false,
                onClick = onFlip
            )
        }
    }
}

@Composable
private fun CameraSquareButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(if (active) zuneColors.accentColor else Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.85f),
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** Rule of thirds, drawn the way the Windows Phone viewfinder drew it: hairlines, no shadow. */
@Composable
internal fun GridOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 1f
        val ink = Color.White.copy(alpha = 0.35f)
        for (i in 1..2) {
            val x = size.width * i / 3f
            val y = size.height * i / 3f
            drawLine(ink, Offset(x, 0f), Offset(x, size.height), stroke)
            drawLine(ink, Offset(0f, y), Offset(size.width, y), stroke)
        }
    }
}

/**
 * The square that lands where the viewfinder was touched.
 *
 * Windows Phone drew a plain white rectangle that arrived slightly too big and settled — no
 * pulsing, no colour, no sound. It says "measured here" and then gets out of the way.
 */
@Composable
internal fun FocusReticle(point: Offset, tick: Int, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val settle = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }

    LaunchedEffect(tick) {
        fade.snapTo(1f)
        settle.snapTo(0f)
        settle.animateTo(1f, tween(200, easing = FastOutSlowInEasing))
        fade.animateTo(0f, tween(320, delayMillis = 900))
    }

    val side = with(density) { RETICLE_SIDE.dp.toPx() }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
    ) {
        // Too big on arrival, exact once it has settled.
        val scale = 1.45f - 0.45f * settle.value
        val half = side * scale / 2f
        drawRect(
            color = Color.White,
            topLeft = Offset(point.x - half, point.y - half),
            size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
            style = Stroke(width = with(density) { 1.5.dp.toPx() })
        )
    }
}

private const val RETICLE_SIDE = 72f

/** How far in the lens has been pinched, said plainly and only while it is being said. */
@Composable
internal fun ZoomReadout(zoom: Float, modifier: Modifier = Modifier) {
    Text(
        text = String.format(Locale.getDefault(), "%.1f×", zoom),
        fontSize = 15.sp,
        fontWeight = FontWeight.Light,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

/** The modes, the shutter, and whatever the mode in hand needs to say. */
@Composable
internal fun CameraBottomBar(
    mode: CameraMode,
    state: CaptureState,
    recordedSeconds: Int,
    sweepProgress: Float,
    sweepPreview: ImageBitmap?,
    usesVendorNight: Boolean,
    canSweep: Boolean,
    lastShot: LastShot?,
    onMode: (CameraMode) -> Unit,
    onShutter: () -> Unit,
    onOpenLast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .navigationBarsPadding()
            .padding(top = 10.dp, bottom = 14.dp)
    ) {
        if (mode == CameraMode.PANORAMA && state == CaptureState.SWEEPING) {
            SweepStrip(progress = sweepProgress, preview = sweepPreview)
        }

        ModeStrip(mode = mode, enabled = state == CaptureState.IDLE, onMode = onMode)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, start = ZuneDimens.ScreenPaddingHorizontal, end = ZuneDimens.ScreenPaddingHorizontal)
        ) {
            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterStart) {
                if (lastShot != null) {
                    val frame = Modifier
                        .size(46.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.7f))
                        .clickable(onClick = onOpenLast)
                    if (lastShot.isVideo) {
                        // A film has no still to show without decoding it, so it says it is a film.
                        Box(modifier = frame.background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.camera_open_last),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        coil.compose.AsyncImage(
                            model = lastShot.uri,
                            contentDescription = stringResource(R.string.camera_open_last),
                            contentScale = ContentScale.Crop,
                            modifier = frame
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            ShutterButton(mode = mode, state = state, onClick = onShutter)
            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
                val caption = when {
                    state == CaptureState.RECORDING -> "%d:%02d".format(recordedSeconds / 60, recordedSeconds % 60)
                    state == CaptureState.SWEEPING -> "%d%%".format((sweepProgress * 100).toInt())
                    mode == CameraMode.NIGHT && !usesVendorNight -> stringResource(R.string.camera_night_stacked)
                    else -> ""
                }
                if (caption.isNotEmpty()) {
                    Text(
                        text = caption,
                        fontSize = 13.sp,
                        color = if (state == CaptureState.RECORDING) zuneColors.accentColor else Color.White,
                        maxLines = 2
                    )
                }
            }
        }

        if (mode == CameraMode.PANORAMA) {
            Text(
                text = stringResource(
                    when {
                        !canSweep -> R.string.camera_sweep_unavailable
                        state == CaptureState.SWEEPING -> R.string.camera_sweep_running
                        else -> R.string.camera_sweep_hint
                    }
                ),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
            )
        }
    }
}

/** The modes as pivot headings, the way every other hub in the launcher names its pages. */
@Composable
private fun ModeStrip(mode: CameraMode, enabled: Boolean, onMode: (CameraMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        CameraMode.entries.forEach { candidate ->
            val selected = candidate == mode
            Text(
                text = stringResource(candidate.titleRes),
                fontSize = 21.sp,
                fontWeight = FontWeight.Light,
                color = when {
                    selected -> Color.White
                    enabled -> Color.White.copy(alpha = 0.42f)
                    else -> Color.White.copy(alpha = 0.2f)
                },
                modifier = Modifier
                    .clickable(
                        enabled = enabled,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onMode(candidate) }
                    .padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun ShutterButton(mode: CameraMode, state: CaptureState, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val busy = state == CaptureState.CAPTURING
    val scale by animateFloatAsState(if (busy) 0.88f else 1f, tween(140), label = "shutter")

    val fill = when {
        // A sweep and a recording are both things you press again to stop, so they look alike.
        state == CaptureState.RECORDING || state == CaptureState.SWEEPING -> zuneColors.accentColor
        mode == CameraMode.VIDEO -> Color(0xFFE53935)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .size(74.dp)
            .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size((56 * scale).dp)
                .background(fill.copy(alpha = if (busy) 0.6f else 1f), CircleShape)
        )
        if (state == CaptureState.RECORDING || state == CaptureState.SWEEPING) {
            // A square inside the ring is the universal "this is the stop button".
            Box(modifier = Modifier.size(22.dp).background(Color.White))
        }
    }
}

/** The panorama as it is being gathered, with how far round the sweep has come. */
@Composable
private fun SweepStrip(progress: Float, preview: ImageBitmap?) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.3f))
        ) {
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.FillHeight,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(zuneColors.accentColor)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
internal fun PermissionNotice(onAsk: () -> Unit, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.camera_permission_title),
            fontSize = 30.sp,
            fontWeight = FontWeight.Light,
            color = Color.White
        )
        Text(
            text = stringResource(R.string.camera_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Text(
            text = stringResource(R.string.camera_permission_button),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
            modifier = Modifier
                .background(zuneColors.accentColor)
                .clickable(onClick = onAsk)
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}
