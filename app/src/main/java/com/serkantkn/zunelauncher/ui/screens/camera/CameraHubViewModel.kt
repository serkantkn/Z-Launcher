package com.serkantkn.zunelauncher.ui.screens.camera

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CameraEvent
import com.serkantkn.zunelauncher.data.model.CameraFacing
import com.serkantkn.zunelauncher.data.model.CameraMode
import com.serkantkn.zunelauncher.data.model.CameraSettings
import com.serkantkn.zunelauncher.data.model.FlashMode
import com.serkantkn.zunelauncher.data.model.TimerOption
import com.serkantkn.zunelauncher.di.appContainer
import com.serkantkn.zunelauncher.util.MediaSaver
import com.serkantkn.zunelauncher.util.PanoramaPlanner
import com.serkantkn.zunelauncher.util.ZuneLog
import com.serkantkn.zunelauncher.util.averageFrames
import com.serkantkn.zunelauncher.util.brighten
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/** The last thing the camera saved, for the corner thumbnail. */
data class LastShot(val uri: Uri, val isVideo: Boolean)

/** What the viewfinder is busy doing, if anything. */
enum class CaptureState { IDLE, COUNTING_DOWN, CAPTURING, RECORDING, SWEEPING }

class CameraHubViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraDataStore = application.appContainer.cameraDataStore

    val engine = CameraEngine(application)

    val settings: StateFlow<CameraSettings> = cameraDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CameraSettings())

    private val _capabilities = MutableStateFlow(CameraCapabilities())
    val capabilities: StateFlow<CameraCapabilities> = _capabilities.asStateFlow()

    private val _state = MutableStateFlow(CaptureState.IDLE)
    val state: StateFlow<CaptureState> = _state.asStateFlow()

    /** Seconds still to go on the self-timer. */
    private val _countdown = MutableStateFlow(0)
    val countdown: StateFlow<Int> = _countdown.asStateFlow()

    private val _recordedSeconds = MutableStateFlow(0)
    val recordedSeconds: StateFlow<Int> = _recordedSeconds.asStateFlow()

    /** How far the panorama sweep has come, 0..1, and what it has gathered so far. */
    private val _sweepProgress = MutableStateFlow(0f)
    val sweepProgress: StateFlow<Float> = _sweepProgress.asStateFlow()

    private val _sweepPreview = MutableStateFlow<Bitmap?>(null)
    val sweepPreview: StateFlow<Bitmap?> = _sweepPreview.asStateFlow()

    /** The last thing saved, for the corner thumbnail. */
    private val _lastShot = MutableStateFlow<LastShot?>(null)
    val lastShot: StateFlow<LastShot?> = _lastShot.asStateFlow()

    val events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 4)

    // ── Panorama and night gather frames; these hold what they have gathered ──────────────────

    private var planner: PanoramaPlanner? = null
    private var canvas: Bitmap? = null
    private var nightFrames = mutableListOf<IntArray>()
    private var nightSize: Pair<Int, Int>? = null
    private var wantedNightFrames = 0
    private var stripsSincePreview = 0

    @Volatile
    private var yaw = 0f

    private val sensors = application.getSystemService(SensorManager::class.java)

    /**
     * Which sensor says how far the phone has turned.
     *
     * Most phones fuse one for you; some — emulators among them — offer only the game or
     * geomagnetic flavour, and a few offer none at all, in which case the gyroscope's own turn
     * rate is added up instead. Any of the four is enough to sweep a panorama.
     */
    private val rotationSensor: Sensor? = listOf(
        Sensor.TYPE_ROTATION_VECTOR,
        // Next best is the compass-referenced one: its heading is absolute, where the game
        // vector's is arbitrary and drifts as the sweep goes on.
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
        Sensor.TYPE_GAME_ROTATION_VECTOR
    ).firstNotNullOfOrNull { type -> sensors?.getDefaultSensor(type) }

    private val gyroscope: Sensor? =
        if (rotationSensor != null) null else sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var lastGyroNanos = 0L

    private val yawListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                // Held upright, the phone's own up axis is the one a sideways pan turns about.
                val previous = lastGyroNanos
                lastGyroNanos = event.timestamp
                if (previous != 0L) yaw += event.values[1] * (event.timestamp - previous) / 1e9f
                return
            }
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            yaw = orientation[0]
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** True when nothing on this phone can tell the sweep how far it has turned. */
    val canSweep: Boolean = rotationSensor != null || gyroscope != null

    // ── Settings ─────────────────────────────────────────────────────────────────────────────

    fun setMode(mode: CameraMode) {
        if (_state.value != CaptureState.IDLE) return
        cancelSweep()
        viewModelScope.launch { cameraDataStore.setMode(mode) }
    }

    fun toggleFacing() {
        viewModelScope.launch {
            val current = settings.first().facing
            cameraDataStore.setFacing(
                if (current == CameraFacing.BACK) CameraFacing.FRONT else CameraFacing.BACK
            )
        }
    }

    fun cycleFlash() {
        viewModelScope.launch {
            val order = FlashMode.entries
            val next = order[(order.indexOf(settings.first().flash) + 1) % order.size]
            cameraDataStore.setFlash(next)
        }
    }

    fun cycleTimer() {
        viewModelScope.launch {
            val order = TimerOption.entries
            val next = order[(order.indexOf(settings.first().timer) + 1) % order.size]
            cameraDataStore.setTimer(next)
        }
    }

    fun toggleGrid() {
        viewModelScope.launch { cameraDataStore.setGridLines(!settings.first().gridLines) }
    }

    fun toggleMirror() {
        viewModelScope.launch {
            cameraDataStore.setMirrorFrontCamera(!settings.first().mirrorFrontCamera)
        }
    }

    fun onCapabilities(capabilities: CameraCapabilities) {
        _capabilities.value = capabilities
    }

    // ── The shutter ──────────────────────────────────────────────────────────────────────────

    /** The one button: what it does depends on the mode and on what is already happening. */
    fun onShutter(hasAudioPermission: Boolean) {
        val current = settings.value
        when {
            _state.value == CaptureState.RECORDING -> stopRecording()
            _state.value == CaptureState.SWEEPING -> finishSweep()
            _state.value != CaptureState.IDLE -> Unit
            current.mode == CameraMode.VIDEO -> startRecording(hasAudioPermission)
            current.mode == CameraMode.PANORAMA ->
                if (canSweep) startSweep() else events.tryEmit(CameraEvent.Failed(R.string.camera_sweep_unavailable))
            else -> viewModelScope.launch { runTimerThen { capture(current) } }
        }
    }

    private suspend fun runTimerThen(action: suspend () -> Unit) {
        val seconds = settings.value.timer.seconds
        if (seconds > 0) {
            _state.value = CaptureState.COUNTING_DOWN
            for (remaining in seconds downTo 1) {
                _countdown.value = remaining
                kotlinx.coroutines.delay(1000)
                if (_state.value != CaptureState.COUNTING_DOWN) return   // cancelled
            }
            _countdown.value = 0
        }
        action()
    }

    private suspend fun capture(current: CameraSettings) {
        _state.value = CaptureState.CAPTURING
        val uri = if (current.mode == CameraMode.NIGHT && !_capabilities.value.vendorNightMode) {
            gatherNightShot()
        } else {
            engine.takePhoto(current.mirrorFrontCamera, current.facing)
        }
        _state.value = CaptureState.IDLE
        if (uri != null) {
            _lastShot.value = LastShot(uri, isVideo = false)
            events.tryEmit(CameraEvent.Saved(uri.toString(), isVideo = false))
        } else {
            events.tryEmit(CameraEvent.Failed(R.string.camera_error_capture))
        }
    }

    fun cancelTimer() {
        if (_state.value == CaptureState.COUNTING_DOWN) {
            _state.value = CaptureState.IDLE
            _countdown.value = 0
        }
    }

    // ── Video ────────────────────────────────────────────────────────────────────────────────

    private fun startRecording(hasAudioPermission: Boolean) {
        _state.value = CaptureState.RECORDING
        _recordedSeconds.value = 0
        engine.startRecording(hasAudioPermission) { uri ->
            _state.value = CaptureState.IDLE
            if (uri != null) {
                _lastShot.value = LastShot(uri, isVideo = true)
                events.tryEmit(CameraEvent.Saved(uri.toString(), isVideo = true))
            } else {
                events.tryEmit(CameraEvent.Failed(R.string.camera_error_record))
            }
        }
        viewModelScope.launch {
            while (_state.value == CaptureState.RECORDING) {
                kotlinx.coroutines.delay(1000)
                if (_state.value == CaptureState.RECORDING) _recordedSeconds.value++
            }
        }
    }

    private fun stopRecording() = engine.stopRecording()

    // ── Night: several frames added together ─────────────────────────────────────────────────

    private suspend fun gatherNightShot(): Uri? {
        nightFrames = mutableListOf()
        nightSize = null
        wantedNightFrames = NIGHT_FRAMES
        // The analyser fills the list; this waits for it rather than driving it.
        var waited = 0
        while (nightFrames.size < wantedNightFrames && waited < NIGHT_TIMEOUT_MILLIS) {
            kotlinx.coroutines.delay(50)
            waited += 50
        }
        wantedNightFrames = 0
        val frames = nightFrames.toList()
        val size = nightSize
        nightFrames = mutableListOf()
        if (frames.isEmpty() || size == null) return null

        return withContext(Dispatchers.Default) {
            val averaged = averageFrames(frames) ?: return@withContext null
            val lifted = brighten(averaged, NIGHT_GAIN)
            val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(lifted, 0, size.first, 0, 0, size.first, size.second)
            MediaSaver.saveJpeg(getApplication(), bitmap, MediaSaver.fileName("NIGHT", "jpg"))
        }
    }

    // ── Panorama: strips laid down as the phone turns ────────────────────────────────────────

    private fun startSweep() {
        lastGyroNanos = 0L
        sensors?.registerListener(
            yawListener,
            rotationSensor ?: gyroscope,
            SensorManager.SENSOR_DELAY_GAME
        )
        planner = null
        canvas = null
        stripsSincePreview = 0
        _sweepProgress.value = 0f
        _sweepPreview.value = null
        _state.value = CaptureState.SWEEPING
    }

    fun cancelSweep() {
        if (_state.value == CaptureState.SWEEPING) _state.value = CaptureState.IDLE
        sensors?.unregisterListener(yawListener)
        planner = null
        canvas = null
        _sweepProgress.value = 0f
        _sweepPreview.value = null
    }

    private fun finishSweep() {
        val picture = canvas
        val plan = planner
        _state.value = CaptureState.CAPTURING
        sensors?.unregisterListener(yawListener)
        viewModelScope.launch {
            val uri = if (picture == null || plan == null || plan.canvasWidth() < MIN_PANORAMA_WIDTH) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    val cropped = Bitmap.createBitmap(picture, 0, 0, plan.canvasWidth(), picture.height)
                    MediaSaver.saveJpeg(getApplication(), cropped, MediaSaver.fileName("PANO", "jpg"))
                }
            }
            planner = null
            canvas = null
            _sweepProgress.value = 0f
            _sweepPreview.value = null
            _state.value = CaptureState.IDLE
            if (uri != null) {
                _lastShot.value = LastShot(uri, isVideo = false)
                events.tryEmit(CameraEvent.Saved(uri.toString(), isVideo = false))
            } else {
                events.tryEmit(CameraEvent.Failed(R.string.camera_error_panorama_short))
            }
        }
    }

    /**
     * Every preview frame in the modes that read them. Runs on the camera's own thread, so it
     * does its work there and only hands finished pictures back.
     */
    fun onFrame(frame: ImageProxy) {
        try {
            when {
                wantedNightFrames > 0 -> collectNightFrame(frame)
                _state.value == CaptureState.SWEEPING -> addSweepStrip(frame)
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "a frame could not be read", e)
        } finally {
            frame.close()
        }
    }

    private fun collectNightFrame(frame: ImageProxy) {
        if (nightFrames.size >= wantedNightFrames) return
        val bitmap = frame.toUprightBitmap() ?: return
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        nightSize = bitmap.width to bitmap.height
        nightFrames.add(pixels)
    }

    private fun addSweepStrip(frame: ImageProxy) {
        val bitmap = frame.toUprightBitmap() ?: return
        var plan = planner
        if (plan == null) {
            val stripWidth = max(MIN_STRIP_WIDTH, bitmap.width / STRIP_DIVISOR)
            val perRadian = PanoramaPlanner.pixelsPerRadian(bitmap.width, _capabilities.value.horizontalFov)
            val wide = (PanoramaPlanner.TARGET_SWEEP_RADIANS * perRadian).roundToInt() + stripWidth
            plan = PanoramaPlanner(
                frameWidth = bitmap.width,
                stripWidth = stripWidth,
                horizontalFov = _capabilities.value.horizontalFov,
                maxWidth = wide.coerceIn(stripWidth * 8, PanoramaPlanner.MAX_CANVAS_WIDTH)
            )
            planner = plan
            canvas = Bitmap.createBitmap(plan.maxWidth, bitmap.height, Bitmap.Config.ARGB_8888)
        }
        val target = plan.onYaw(yaw) ?: return
        val sheet = canvas ?: return
        val source = Rect(plan.sourceLeft(), 0, plan.sourceLeft() + plan.stripWidth, bitmap.height)
        val destination = Rect(target, 0, target + plan.stripWidth, sheet.height)
        Canvas(sheet).drawBitmap(bitmap, source, destination, null)
        _sweepProgress.value = plan.progress
        // The sheet is as wide as the sweep is allowed to get, so the preview is the part of it
        // that has actually been filled; copying that every few strips is cheap enough.
        if (target == 0 || ++stripsSincePreview >= PREVIEW_EVERY_STRIPS) {
            stripsSincePreview = 0
            _sweepPreview.value = Bitmap.createBitmap(sheet, 0, 0, plan.canvasWidth(), sheet.height)
        }
        if (plan.isFull) viewModelScope.launch { finishSweep() }
    }

    /** The analyser's frames arrive the way the sensor read them; this puts them upright. */
    private fun ImageProxy.toUprightBitmap(): Bitmap? = try {
        val raw = toBitmap()
        val degrees = imageInfo.rotationDegrees
        if (degrees == 0) raw else {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        }
    } catch (e: Exception) {
        ZuneLog.w(TAG, "frame would not convert", e)
        null
    }

    override fun onCleared() {
        super.onCleared()
        sensors?.unregisterListener(yawListener)
        engine.release()
    }

    private companion object {
        const val TAG = "CameraHubViewModel"

        /** Enough frames to quieten the noise without the shot feeling stuck. */
        const val NIGHT_FRAMES = 8
        const val NIGHT_TIMEOUT_MILLIS = 4000
        const val NIGHT_GAIN = 1.6f

        const val STRIP_DIVISOR = 20
        const val MIN_STRIP_WIDTH = 24
        const val MIN_PANORAMA_WIDTH = 300

        /** How often the gathered picture is copied out for the strip under the viewfinder. */
        const val PREVIEW_EVERY_STRIPS = 3
    }
}
