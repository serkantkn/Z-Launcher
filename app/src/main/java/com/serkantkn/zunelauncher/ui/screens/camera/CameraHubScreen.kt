package com.serkantkn.zunelauncher.ui.screens.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CameraEvent
import com.serkantkn.zunelauncher.data.model.CameraMode
import com.serkantkn.zunelauncher.data.model.FlashMode
import com.serkantkn.zunelauncher.data.model.TimerOption
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.ZuneLog

/**
 * The camera hub.
 *
 * Windows Phone gave the camera the whole screen and put everything else out of the way: the
 * viewfinder is the page, the controls are a thin strip of squares along the top, and the modes
 * read as pivot headings under the shutter. There is no shutter sound, no chrome over the picture,
 * and nothing between pressing the button and the shot.
 */
@Composable
fun CameraHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val zuneColors = LocalZuneColors.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val countdown by viewModel.countdown.collectAsStateWithLifecycle()
    val recordedSeconds by viewModel.recordedSeconds.collectAsStateWithLifecycle()
    val sweepProgress by viewModel.sweepProgress.collectAsStateWithLifecycle()
    val sweepPreview by viewModel.sweepPreview.collectAsStateWithLifecycle()
    val lastShot by viewModel.lastShot.collectAsStateWithLifecycle()

    fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var hasCamera by remember { mutableStateOf(granted(Manifest.permission.CAMERA)) }
    var hasMicrophone by remember { mutableStateOf(granted(Manifest.permission.RECORD_AUDIO)) }
    var notice by remember { mutableStateOf<String?>(null) }

    val askForCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamera = it
    }
    val askForMicrophone = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasMicrophone = it
    }

    LaunchedEffect(Unit) {
        if (!hasCamera) askForCamera.launch(Manifest.permission.CAMERA)
    }

    // Rebinding is what switches modes and lenses; the flash is only a setting on what is bound.
    LaunchedEffect(hasCamera, settings.mode, settings.facing) {
        if (!hasCamera) return@LaunchedEffect
        try {
            val capabilities = viewModel.engine.bind(
                owner = lifecycleOwner,
                surfaceProvider = previewView.surfaceProvider,
                mode = settings.mode,
                facing = settings.facing,
                flash = settings.flash,
                onFrame = if (settings.mode.readsFrames) viewModel::onFrame else null
            )
            viewModel.onCapabilities(capabilities)
        } catch (e: Exception) {
            ZuneLog.e("CameraHubScreen", "camera would not start", e)
            notice = context.getString(R.string.camera_error_open)
        }
    }

    LaunchedEffect(settings.flash, settings.mode) {
        viewModel.engine.applyFlash(settings.mode, settings.flash)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            notice = when (event) {
                is CameraEvent.Saved -> context.getString(
                    if (event.isVideo) R.string.camera_saved_video else R.string.camera_saved_photo
                )

                is CameraEvent.Failed -> context.getString(event.messageRes)
            }
        }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            kotlinx.coroutines.delay(2200)
            notice = null
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.engine.unbind() }
    }

    BackHandler {
        when (state) {
            CaptureState.SWEEPING -> viewModel.cancelSweep()
            CaptureState.COUNTING_DOWN -> viewModel.cancelTimer()
            else -> onBack()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasCamera) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

            if (settings.gridLines) GridOverlay()

            CameraTopBar(
                flash = settings.flash,
                timer = settings.timer,
                gridLines = settings.gridLines,
                canFlip = capabilities.hasFrontCamera,
                hasFlash = capabilities.hasFlash,
                onFlash = viewModel::cycleFlash,
                onTimer = viewModel::cycleTimer,
                onGrid = viewModel::toggleGrid,
                onFlip = viewModel::toggleFacing,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            if (countdown > 0) {
                Text(
                    text = countdown.toString(),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Light,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            CameraBottomBar(
                mode = settings.mode,
                state = state,
                recordedSeconds = recordedSeconds,
                sweepProgress = sweepProgress,
                sweepPreview = sweepPreview?.asImageBitmap(),
                usesVendorNight = capabilities.vendorNightMode,
                canSweep = viewModel.canSweep,
                lastShot = lastShot,
                onMode = viewModel::setMode,
                onShutter = {
                    if (settings.mode == CameraMode.VIDEO && !hasMicrophone) {
                        askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    viewModel.onShutter(hasMicrophone)
                },
                onOpenLast = {
                    lastShot?.uri?.let { uri ->
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            ZuneLog.w("CameraHubScreen", "nothing here opens pictures", e)
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else {
            PermissionNotice(
                onAsk = { askForCamera.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        AnimatedVisibility(
            visible = notice != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(240)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 90.dp)
        ) {
            Text(
                text = notice.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier
                    .background(zuneColors.accentColor)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/** The thin strip of squares Windows Phone put along the top of the viewfinder. */
@Composable
private fun CameraTopBar(
    flash: FlashMode,
    timer: TimerOption,
    gridLines: Boolean,
    canFlip: Boolean,
    hasFlash: Boolean,
    onFlash: () -> Unit,
    onTimer: () -> Unit,
    onGrid: () -> Unit,
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
private fun GridOverlay() {
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

/** The modes, the shutter, and whatever the mode in hand needs to say. */
@Composable
private fun CameraBottomBar(
    mode: CameraMode,
    state: CaptureState,
    recordedSeconds: Int,
    sweepProgress: Float,
    sweepPreview: androidx.compose.ui.graphics.ImageBitmap?,
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
private fun SweepStrip(progress: Float, preview: androidx.compose.ui.graphics.ImageBitmap?) {
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
private fun PermissionNotice(onAsk: () -> Unit, modifier: Modifier = Modifier) {
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
