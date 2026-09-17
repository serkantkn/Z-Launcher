package com.serkantkn.zunelauncher.ui.screens.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
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
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.CameraGeometry
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlin.math.abs

/**
 * The camera hub.
 *
 * Windows Phone gave the camera the whole screen and put everything else out of the way: the
 * viewfinder is the page, the controls are a thin strip of squares along the top, and the modes
 * read as pivot headings under the shutter. There is no shutter sound, no chrome over the picture,
 * and nothing between pressing the button and the shot.
 *
 * The picture itself takes the gestures: a tap measures the light and focus where it landed, a
 * pinch zooms. [onOpenPictures] is how the corner thumbnail gets back to the shot — through the
 * launcher's own pictures hub, not through whatever else on the phone claims to open a JPEG.
 */
@Composable
fun CameraHubScreen(
    onBack: () -> Unit,
    onOpenPictures: (Uri) -> Unit,
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
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()

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
    var microphoneAsked by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    // Where the last tap landed, and a count so the same spot tapped twice plays again.
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var focusTick by remember { mutableIntStateOf(0) }
    var zoomShowing by remember { mutableStateOf(false) }

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

    // A photo taken with the phone on its side is stored on its side unless something says which
    // way up it was held; the display cannot say, because with auto-rotate off it never moves.
    DisposableEffect(hasCamera) {
        val watcher = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                viewModel.onDeviceOrientation(orientation)
            }
        }
        if (hasCamera && watcher.canDetectOrientation()) watcher.enable()
        onDispose { watcher.disable() }
    }

    // A viewfinder that goes dark while it is being aimed is no viewfinder.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
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

    // The zoom says itself while it is being changed and then stops saying itself.
    LaunchedEffect(zoom) {
        if (zoom <= 1.01f) {
            zoomShowing = false
        } else {
            zoomShowing = true
            kotlinx.coroutines.delay(1500)
            zoomShowing = false
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

            // The gestures sit on a sheet of their own over the picture. The preview is an
            // Android view borrowed into Compose, and it is left to do the one thing it is for.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(previewView) {
                        detectTapGestures { offset ->
                            val point = previewView.meteringPointFactory
                                .createPoint(offset.x, offset.y)
                            if (viewModel.focusAt(point)) {
                                focusPoint = offset
                                focusTick++
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        // A lens with nothing to give a pinch simply never moves; the check for
                        // that belongs where the lens is, not here.
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            if (abs(gestureZoom - 1f) > CameraGeometry.PINCH_DEAD_ZONE) {
                                viewModel.onPinch(gestureZoom)
                            }
                        }
                    }
            )

            if (settings.gridLines) GridOverlay()

            focusPoint?.let { FocusReticle(point = it, tick = focusTick) }

            CameraTopBar(
                flash = settings.flash,
                timer = settings.timer,
                gridLines = settings.gridLines,
                facing = settings.facing,
                mirrorFront = settings.mirrorFrontCamera,
                canFlip = capabilities.hasFrontCamera,
                hasFlash = capabilities.hasFlash,
                onFlash = viewModel::cycleFlash,
                onTimer = viewModel::cycleTimer,
                onGrid = viewModel::toggleGrid,
                onMirror = viewModel::toggleMirror,
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

            AnimatedVisibility(
                visible = zoomShowing,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(260)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 210.dp)
            ) {
                ZoomReadout(zoom = zoom)
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
                    // Asking for the microphone used to happen while the take was already
                    // running, which recorded the first attempt silently. It is asked first now,
                    // and a refusal is taken as an answer rather than asked again every press.
                    if (settings.mode == CameraMode.VIDEO && !hasMicrophone && !microphoneAsked) {
                        microphoneAsked = true
                        askForMicrophone.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.onShutter(hasMicrophone)
                    }
                },
                onOpenLast = { lastShot?.uri?.let(onOpenPictures) },
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
