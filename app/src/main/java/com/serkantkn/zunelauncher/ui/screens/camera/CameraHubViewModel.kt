package com.serkantkn.zunelauncher.ui.screens.camera

import android.Manifest
import android.app.Application
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.core.content.ContextCompat
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
import com.serkantkn.zunelauncher.util.CameraGeometry
import com.serkantkn.zunelauncher.util.FrameAlign
import com.serkantkn.zunelauncher.util.MediaSaver
import com.serkantkn.zunelauncher.util.PanoramaSheet
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
import kotlin.math.abs
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

    /** What the lens is zoomed to, in multiples of its widest view. */
    private val _zoom = MutableStateFlow(1f)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    val events = MutableSharedFlow<CameraEvent>(extraBufferCapacity = 4)

    // ── The night stack holds what it has gathered until there is enough of it ────────────────

    private var nightFrames = mutableListOf<IntArray>()
    private var nightSize: Pair<Int, Int>? = null
    private var wantedNightFrames = 0

    @Volatile
    private var yaw = 0f

    /** False until the sensor has actually reported; its first reading is a place, not a turn. */
    @Volatile
    private var yawSeen = false

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
                yawSeen = true
                return
            }
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientation)
            yaw = orientation[0]
            yawSeen = true
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** True when nothing on this phone can tell the sweep how far it has turned. */
    val canSweep: Boolean = rotationSensor != null || gyroscope != null

    init {
        // Windows Phone's viewfinder always had the last picture waiting in the corner, whether
        // it was taken a second ago or last week. Without this the corner is empty until the
        // first shot of the visit, and the way through to the pictures is hidden.
        viewModelScope.launch { _lastShot.value = withContext(Dispatchers.IO) { newestShot() } }
    }

    /**
     * The newest thing this camera saved, found in the album it saves into.
     *
     * Only this launcher's own album is looked at: the corner is a way back to the shot that was
     * just taken, and a screenshot or a downloaded picture standing in for it would be a lie.
     */
    private fun newestShot(): LastShot? {
        if (!canReadTheGallery()) return null
        val photo = newestIn(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        val video = newestIn(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        return when {
            photo == null && video == null -> null
            video == null -> LastShot(photo!!.second, isVideo = false)
            photo == null -> LastShot(video.second, isVideo = true)
            video.first > photo.first -> LastShot(video.second, isVideo = true)
            else -> LastShot(photo.second, isVideo = false)
        }
    }

    /** When it arrived and where it is, for the newest item of one kind, or null if there is none. */
    private fun newestIn(collection: Uri): Pair<Long, Uri>? = try {
        val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        // Before Android 10 there is no relative path to narrow by, so the newest one stands.
        val inOurAlbum = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        getApplication<Application>().contentResolver.query(
            collection,
            columns,
            if (inOurAlbum) "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" else null,
            if (inOurAlbum) arrayOf("%${MediaSaver.ALBUM}%") else null,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
            val added = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
            added to ContentUris.withAppendedId(collection, id)
        }
    } catch (e: Exception) {
        ZuneLog.w(TAG, "the gallery would not say what the last shot was", e)
        null
    }

    private fun canReadTheGallery(): Boolean {
        val wanted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(getApplication(), wanted) ==
            PackageManager.PERMISSION_GRANTED
    }

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
        // A rebind puts the lens back to its widest, so the reading has to go back with it.
        _zoom.value = engine.currentZoom()
    }

    /** A pinch on the viewfinder. The factor multiplies, so the feel is the same at every zoom. */
    fun onPinch(factor: Float) {
        val (min, max) = engine.zoomLimits()
        if (!CameraGeometry.canZoom(min, max)) return
        val next = CameraGeometry.zoomAfterPinch(
            current = _zoom.value,
            factor = factor,
            min = min,
            max = max
        )
        if (next != _zoom.value) {
            _zoom.value = next
            engine.setZoom(next)
        }
    }

    /** A tap on the viewfinder: focus and meter there. */
    fun focusAt(point: MeteringPoint): Boolean = engine.focusAt(point)

    /**
     * Which way up the phone is being held, from the sensor rather than from the display.
     *
     * The sensor reports every degree; only the four quarter-turns mean anything to a picture, so
     * the engine only hears about it when the answer actually changes.
     */
    private var lastSurfaceRotation = Int.MIN_VALUE

    fun onDeviceOrientation(degrees: Int) {
        val rotation = CameraGeometry.surfaceRotationFor(degrees)
        if (rotation == lastSurfaceRotation) return
        lastSurfaceRotation = rotation
        engine.setTargetRotation(rotation)
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

    // ── Panorama: measured from the pictures, not from the sensor ────────────────────────────

    private var sheet: PanoramaSheet? = null
    private var canvas: Bitmap? = null
    private var lastProfile: FrameAlign.Profile? = null
    private var framePixels: IntArray? = null

    /** Learned as the sweep goes: how far the scene slides for a radian of turn, on this lens. */
    private var pixelsPerRadian = 0f
    private var lastYaw: Float? = null

    /** How wide the picture has to get to be half a turn, once the sweep knows its own scale. */
    private var targetWidth = 0
    private var framesSincePreview = 0

    private fun startSweep() {
        lastGyroNanos = 0L
        sensors?.registerListener(
            yawListener,
            rotationSensor ?: gyroscope,
            SensorManager.SENSOR_DELAY_GAME
        )
        clearSweep()
        // A sweep across a window and back into the room walks through several stops of light.
        engine.lockExposure(true)
        _state.value = CaptureState.SWEEPING
    }

    fun cancelSweep() {
        if (_state.value == CaptureState.SWEEPING) _state.value = CaptureState.IDLE
        sensors?.unregisterListener(yawListener)
        engine.lockExposure(false)
        clearSweep()
    }

    private fun clearSweep() {
        sheet = null
        canvas = null
        lastProfile = null
        framePixels = null
        pixelsPerRadian = 0f
        lastYaw = null
        yawSeen = false
        targetWidth = 0
        framesSincePreview = 0
        _sweepProgress.value = 0f
        _sweepPreview.value = null
    }

    private fun finishSweep() {
        val plan = sheet
        val sheetBitmap = canvas
        _state.value = CaptureState.CAPTURING
        sensors?.unregisterListener(yawListener)
        engine.lockExposure(false)
        viewModelScope.launch {
            val uri = if (
                plan == null || sheetBitmap == null ||
                plan.filledWidth < PanoramaSheet.MIN_PANORAMA_WIDTH ||
                plan.filledHeight <= 0
            ) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    // Only the band every frame covered is kept: a sweep that drifts upwards
                    // leaves the early frames' floor and the late ones' ceiling with nothing
                    // beside them.
                    val cropped = Bitmap.createBitmap(
                        sheetBitmap,
                        plan.filledLeft,
                        plan.filledTop,
                        plan.filledWidth,
                        plan.filledHeight
                    )
                    MediaSaver.saveJpeg(getApplication(), cropped, MediaSaver.fileName("PANO", "jpg"))
                }
            }
            clearSweep()
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
                _state.value == CaptureState.SWEEPING -> addSweepFrame(frame)
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

    /**
     * One frame of the sweep.
     *
     * How far the scene has moved is read out of the picture rather than off the sensor, and the
     * piece of the frame that is copied across is exactly as wide as that movement. The sensor is
     * kept for two things it is genuinely good for: saying how far round the sweep has come, and
     * standing in when the camera is pointed at something too plain to measure — a bare wall, a
     * clear sky — at the scale the readable frames have taught it.
     */
    private fun addSweepFrame(frame: ImageProxy) {
        val bitmap = frame.toUprightBitmap() ?: return
        val width = bitmap.width
        val height = bitmap.height
        val buffer = framePixels?.takeIf { it.size == width * height }
            ?: IntArray(width * height).also { framePixels = it }
        bitmap.getPixels(buffer, 0, width, 0, 0, width, height)

        val profile = FrameAlign.profileOf(buffer, width, height) ?: return
        val previous = lastProfile
        lastProfile = profile

        val yawNow = if (yawSeen) yaw else null
        val since = lastYaw
        val turned = if (yawNow != null && since != null) {
            PanoramaSheet.shortestAngle(since, yawNow)
        } else {
            0f
        }
        if (yawNow != null) lastYaw = yawNow
        if (previous == null) return

        val halfFrame = width / 2
        val measured = FrameAlign.shiftBetween(
            previous = previous,
            next = profile,
            maxDx = halfFrame,
            maxDy = height / VERTICAL_SEARCH_DIVISOR
        )

        val dx: Int
        val dy: Int
        if (measured != null) {
            dx = measured.dx
            dy = measured.dy
            learnScale(dx, turned, width)
        } else {
            if (pixelsPerRadian == 0f) return
            dx = (turned * pixelsPerRadian).roundToInt()
            dy = 0
        }
        if (dx == 0) return

        // Nothing may move by more than half a frame at a time: beyond that the two frames no
        // longer overlap enough to be sure of anything, and a gap would be left on the sheet.
        val step = dx.coerceIn(-halfFrame, halfFrame)

        val plan = sheet
        if (plan == null) {
            val fresh = PanoramaSheet(
                frameWidth = width,
                frameHeight = height,
                maxWidth = sheetWidthFor(width),
                verticalMargin = (height * PanoramaSheet.VERTICAL_MARGIN_FRACTION).toInt()
            )
            val opening = fresh.start(step) ?: return
            sheet = fresh
            canvas = Bitmap.createBitmap(fresh.maxWidth, fresh.canvasHeight, Bitmap.Config.ARGB_8888)
            paste(bitmap, opening)
            return
        }

        val next = plan.advance(step, dy) ?: return
        paste(bitmap, next)

        // How far along the sweep is, measured in the picture it has actually gathered rather
        // than in what the sensor claims. A sensor's jitter adds up to a turn it never made.
        val reach = if (targetWidth > 0) targetWidth else plan.maxWidth
        _sweepProgress.value = (plan.filledWidth.toFloat() / reach).coerceIn(0f, 1f)

        if (++framesSincePreview >= PREVIEW_EVERY_FRAMES) {
            framesSincePreview = 0
            publishPreview()
        }
        // Half a turn is a panorama; past that it is a room seen twice.
        if (plan.isFull || plan.filledWidth >= reach) viewModelScope.launch { finishSweep() }
    }

    /**
     * Teaches the sweep how far the scene slides for a turn of a given size.
     *
     * This is what the lens's own specification was supposed to say and never says accurately.
     * Readings from turns too small to mean anything are ignored, and the answer is held inside
     * what any phone lens could plausibly be doing, so that one bad pair of frames cannot decide
     * that a panorama is finished when it has barely started.
     */
    private fun learnScale(dx: Int, turned: Float, frameWidth: Int) {
        if (abs(turned) <= MIN_LEARNING_TURN) return
        val learned = dx / turned
        val plausible = learned.coerceIn(frameWidth * NARROWEST_LENS, frameWidth * WIDEST_LENS)
        pixelsPerRadian = if (pixelsPerRadian == 0f) {
            plausible
        } else {
            pixelsPerRadian * (1f - LEARNING_RATE) + plausible * LEARNING_RATE
        }
        targetWidth = (PanoramaSheet.TARGET_SWEEP_RADIANS * abs(pixelsPerRadian)).toInt()
    }

    /**
     * How wide a sheet to lay out for half a turn.
     *
     * Once the first pair of frames has been read, the sweep knows roughly how far the scene
     * slides for a turn of a given size, and a sheet cut to that is a few megabytes rather than
     * twenty. Before it knows anything, it takes the largest it is allowed.
     */
    private fun sheetWidthFor(frameWidth: Int): Int {
        if (pixelsPerRadian == 0f) return PanoramaSheet.MAX_CANVAS_WIDTH
        val needed = PanoramaSheet.TARGET_SWEEP_RADIANS * abs(pixelsPerRadian) * SHEET_HEADROOM
        return (needed.toInt() + frameWidth)
            .coerceIn(frameWidth * 4, PanoramaSheet.MAX_CANVAS_WIDTH)
    }

    private fun paste(frame: Bitmap, paste: PanoramaSheet.Paste) {
        val sheetBitmap = canvas ?: return
        Canvas(sheetBitmap).drawBitmap(
            frame,
            Rect(paste.sourceLeft, 0, paste.sourceRight, frame.height),
            Rect(paste.left, paste.top, paste.left + paste.width, paste.top + frame.height),
            null
        )
    }

    /**
     * The strip under the viewfinder.
     *
     * Drawn straight into a small bitmap rather than copied out of the sheet and shrunk: the sheet
     * is twenty megabytes and this happens every few frames.
     */
    private fun publishPreview() {
        val plan = sheet ?: return
        val sheetBitmap = canvas ?: return
        if (plan.filledWidth <= 0 || plan.filledHeight <= 0) return
        val width = (plan.filledWidth * PREVIEW_HEIGHT / plan.filledHeight).coerceIn(1, PREVIEW_MAX_WIDTH)
        val preview = Bitmap.createBitmap(width, PREVIEW_HEIGHT, Bitmap.Config.ARGB_8888)
        Canvas(preview).drawBitmap(
            sheetBitmap,
            Rect(
                plan.filledLeft,
                plan.filledTop,
                plan.filledLeft + plan.filledWidth,
                plan.filledTop + plan.filledHeight
            ),
            Rect(0, 0, width, PREVIEW_HEIGHT),
            null
        )
        _sweepPreview.value = preview
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

        /** How far up and down a frame is searched for its neighbour, as a fraction of its height. */
        const val VERTICAL_SEARCH_DIVISOR = 12

        /** A turn smaller than this teaches the sweep nothing about its own scale. */
        const val MIN_LEARNING_TURN = 0.004f

        /** How quickly the learned scale follows the newest measurement. */
        const val LEARNING_RATE = 0.25f

        /**
         * What a phone lens could plausibly be: between a quarter of a radian across and two.
         * A reading outside that came from a sensor that stumbled, not from a lens.
         */
        const val NARROWEST_LENS = 0.5f
        const val WIDEST_LENS = 4f

        /** Room to spare on the sheet, in case the first reading was an optimistic one. */
        const val SHEET_HEADROOM = 1.3f

        /** How often the gathered picture is drawn into the strip under the viewfinder. */
        const val PREVIEW_EVERY_FRAMES = 4
        const val PREVIEW_HEIGHT = 96
        const val PREVIEW_MAX_WIDTH = 1600
    }
}
