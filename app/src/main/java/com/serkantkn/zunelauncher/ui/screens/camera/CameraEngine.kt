package com.serkantkn.zunelauncher.ui.screens.camera

import android.content.ContentValues
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.serkantkn.zunelauncher.data.model.CameraFacing
import com.serkantkn.zunelauncher.data.model.CameraMode
import com.serkantkn.zunelauncher.data.model.FlashMode
import com.serkantkn.zunelauncher.util.MediaSaver
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** What came back from binding the camera: what it can do. */
data class CameraCapabilities(
    val hasFrontCamera: Boolean = false,
    val hasFlash: Boolean = false,
    /** True when the phone's own night extension is doing the work rather than the fallback. */
    val vendorNightMode: Boolean = false
)

/**
 * The camera itself: binding the right use cases for the mode on show, taking the picture, and
 * recording the video.
 *
 * Each mode binds only what it needs. Every phone has a limit on how many streams it will run at
 * once, and asking for a still capture, a recorder and a frame analyser together is the quickest
 * way to be told no by a mid-range device.
 */
class CameraEngine(private val context: Context) {

    private var provider: ProcessCameraProvider? = null
    private var extensions: ExtensionsManager? = null
    private var camera: Camera? = null

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    /**
     * Which way up the phone was last held, so a shot taken sideways is stored the right way up.
     * Kept here rather than on the use case because the use cases are thrown away on every rebind.
     */
    private var targetRotation: Int = Surface.ROTATION_0

    val executor: ExecutorService = Executors.newSingleThreadExecutor()

    val isRecording: Boolean get() = recording != null

    /**
     * Puts the camera on screen in [mode]. [onFrame] is called for every preview frame in the
     * modes that read them — night and panorama — and not bound at all in the modes that do not.
     */
    suspend fun bind(
        owner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        mode: CameraMode,
        facing: CameraFacing,
        flash: FlashMode,
        onFrame: ((ImageProxy) -> Unit)?
    ): CameraCapabilities {
        val cameraProvider = provider ?: awaitProvider().also { provider = it }
        cameraProvider.unbindAll()
        recording = null

        val base = selectorFor(facing)
        val nightAvailable = mode == CameraMode.NIGHT && isNightExtensionAvailable(cameraProvider, base)
        val selector = if (nightAvailable) {
            extensions?.getExtensionEnabledCameraSelector(base, ExtensionMode.NIGHT) ?: base
        } else {
            base
        }

        val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
        val useCases = mutableListOf<androidx.camera.core.UseCase>(preview)

        imageCapture = null
        videoCapture = null

        when (mode) {
            CameraMode.PHOTO -> useCases += stillCapture(flash).also { imageCapture = it }

            CameraMode.NIGHT -> {
                useCases += stillCapture(flash).also { imageCapture = it }
                // Without a vendor night mode the frames themselves are what makes the picture.
                if (!nightAvailable && onFrame != null) useCases += frameReader(onFrame, NIGHT_FRAME_SIZE)
            }

            CameraMode.VIDEO -> {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                    )
                    .build()
                useCases += VideoCapture.withOutput(recorder).also { videoCapture = it }
            }

            // The panorama is built out of these frames, so they are as big as the phone will
            // comfortably hand over; the night stack keeps eight of them in memory at once and
            // is happier small.
            CameraMode.PANORAMA -> if (onFrame != null) {
                useCases += frameReader(onFrame, PANORAMA_FRAME_SIZE)
            }
        }

        camera = cameraProvider.bindToLifecycle(owner, selector, *useCases.toTypedArray())
        applyFlash(mode, flash)
        setTargetRotation(targetRotation)

        return CameraCapabilities(
            hasFrontCamera = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA),
            hasFlash = camera?.cameraInfo?.hasFlashUnit() == true,
            vendorNightMode = nightAvailable
        )
    }

    fun unbind() {
        recording?.stop()
        recording = null
        provider?.unbindAll()
        camera = null
    }

    fun release() {
        unbind()
        executor.shutdown()
    }

    /** Video's light is a torch rather than a flash: it has to stay on for the whole take. */
    fun applyFlash(mode: CameraMode, flash: FlashMode) {
        imageCapture?.flashMode = when (flash) {
            FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
        }
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            camera?.cameraControl?.enableTorch(mode == CameraMode.VIDEO && flash == FlashMode.ON)
        }
    }

    /**
     * Records which way up the phone is held, so the picture is stored that way round.
     *
     * The display's own rotation is no help here: with auto-rotate off it never changes, and the
     * shot taken with the phone on its side would be saved on its side.
     */
    fun setTargetRotation(rotation: Int) {
        targetRotation = rotation
        imageCapture?.targetRotation = rotation
        videoCapture?.targetRotation = rotation
    }

    /**
     * Focuses and meters where the viewfinder was touched.
     *
     * The measurement is given a few seconds and then let go of, the way Windows Phone did it: a
     * tap is a hint about this shot, not a lock the person then has to remember to undo.
     */
    fun focusAt(point: MeteringPoint): Boolean {
        val control = camera?.cameraControl ?: return false
        return try {
            control.startFocusAndMetering(
                FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                ).setAutoCancelDuration(FOCUS_HOLD_SECONDS, TimeUnit.SECONDS).build()
            )
            true
        } catch (e: Exception) {
            ZuneLog.w(TAG, "the lens would not focus there", e)
            false
        }
    }

    /** Sets the zoom outright; the caller has already worked out what the lens will allow. */
    fun setZoom(ratio: Float) {
        camera?.cameraControl?.setZoomRatio(ratio)
    }

    /**
     * What the lens will zoom between, asked now rather than remembered from the bind.
     *
     * The range arrives with the camera rather than with the binding call, so a copy taken the
     * instant the use cases were bound can still say "this lens does not zoom" about one that does.
     */
    fun zoomLimits(): Pair<Float, Float> {
        val state = camera?.cameraInfo?.zoomState?.value ?: return 1f to 1f
        return state.minZoomRatio to state.maxZoomRatio
    }

    /** What the lens is zoomed to now, or 1x when nothing is bound. */
    fun currentZoom(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f

    /** Takes the still and hands back where it was saved, or null if it could not be. */
    suspend fun takePhoto(mirrorFront: Boolean, facing: CameraFacing): Uri? {
        val capture = imageCapture ?: return null
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, MediaSaver.fileName("IMG", "jpg"))
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/${MediaSaver.ALBUM}")
            }
        }
        val metadata = ImageCapture.Metadata().apply {
            // A selfie is kept the way it looked in the viewfinder.
            isReversedHorizontal = mirrorFront && facing == CameraFacing.FRONT
        }
        val options = ImageCapture.OutputFileOptions
            .Builder(context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            .setMetadata(metadata)
            .build()

        return suspendCancellableCoroutine { continuation ->
            try {
                capture.takePicture(
                    options,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            continuation.resume(output.savedUri)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            ZuneLog.e(TAG, "takePicture failed", exception)
                            continuation.resume(null)
                        }
                    }
                )
            } catch (e: Exception) {
                // A use case that was unbound between the press and here throws rather than
                // calling back, and a coroutine nobody resumes never lets go of the shutter.
                ZuneLog.e(TAG, "takePicture would not start", e)
                continuation.resume(null)
            }
        }
    }

    /** Starts recording; [onFinished] is handed the saved file, or null when it went wrong. */
    fun startRecording(withAudio: Boolean, onFinished: (Uri?) -> Unit) {
        val capture = videoCapture ?: return onFinished(null)
        if (recording != null) return

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, MediaSaver.fileName("VID", "mp4"))
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/${MediaSaver.ALBUM}")
            }
        }
        val options = MediaStoreOutputOptions
            .Builder(context.contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(values)
            .build()

        recording = try {
            capture.output
                .prepareRecording(context, options)
                .apply { if (withAudio) withAudioEnabled() }
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    if (event is VideoRecordEvent.Finalize) {
                        recording = null
                        onFinished(if (event.hasError()) null else event.outputResults.outputUri)
                    }
                }
        } catch (e: Exception) {
            ZuneLog.e(TAG, "the recording would not start", e)
            onFinished(null)
            null
        }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun stillCapture(flash: FlashMode): ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .build()
        .also {
            it.flashMode = when (flash) {
                FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
                FlashMode.ON -> ImageCapture.FLASH_MODE_ON
                FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            }
        }

    private fun frameReader(onFrame: (ImageProxy) -> Unit, size: Size): ImageAnalysis =
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(size, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    )
                    .build()
            )
            .build()
            .also { analysis -> analysis.setAnalyzer(executor) { frame -> onFrame(frame) } }

    /**
     * Holds the exposure and the white balance still.
     *
     * A sweep across a window and back into a room walks through several stops of light. Left to
     * itself the camera follows, and the panorama comes out in bands of different brightness with
     * a visible step at every join. Windows Phone locked both on the first frame of a sweep.
     */
    fun lockExposure(locked: Boolean) {
        val control = camera?.cameraControl ?: return
        try {
            val options = if (locked) {
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AE_LOCK, true)
                    .setCaptureRequestOption(CaptureRequest.CONTROL_AWB_LOCK, true)
                    .build()
            } else {
                CaptureRequestOptions.Builder().build()
            }
            Camera2CameraControl.from(control).setCaptureRequestOptions(options)
        } catch (e: Exception) {
            ZuneLog.w(TAG, "this camera will not hold its exposure", e)
        }
    }

    private fun selectorFor(facing: CameraFacing): CameraSelector = when (facing) {
        CameraFacing.FRONT -> CameraSelector.DEFAULT_FRONT_CAMERA
        CameraFacing.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
    }

    private suspend fun isNightExtensionAvailable(
        cameraProvider: ProcessCameraProvider,
        selector: CameraSelector
    ): Boolean = try {
        val manager = extensions ?: awaitExtensions(cameraProvider).also { extensions = it }
        manager?.isExtensionAvailable(selector, ExtensionMode.NIGHT) == true
    } catch (e: Exception) {
        ZuneLog.w(TAG, "no camera extensions on this phone", e)
        false
    }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(context))
    }

    private suspend fun awaitExtensions(
        cameraProvider: ProcessCameraProvider
    ): ExtensionsManager? = suspendCancellableCoroutine { cont ->
        val future = ExtensionsManager.getInstanceAsync(context, cameraProvider)
        future.addListener({
            cont.resume(try { future.get() } catch (_: Exception) { null })
        }, ContextCompat.getMainExecutor(context))
    }

    private companion object {
        const val TAG = "CameraEngine"

        /** How long a tapped focus is held before the lens goes back to deciding for itself. */
        const val FOCUS_HOLD_SECONDS = 4L

        /** What the panorama and the night stack ask their frames to be. */
        val PANORAMA_FRAME_SIZE = Size(1280, 720)
        val NIGHT_FRAME_SIZE = Size(640, 480)
    }
}
