package com.serkantkn.zunelauncher.ui.screens.files

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.FileViewer
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The hub's own viewers, so an ordinary file opens where it was found rather than somewhere else.
 *
 * Text is read and shown, a picture is drawn and can be pinched, a sound or a film plays with one
 * button and a line to scrub, and a PDF is drawn page under page. Anything more — a spreadsheet,
 * an archive — is not pretended at; that file goes to another app.
 */
@Composable
internal fun FileViewerScreen(
    item: FileItemModel,
    viewer: FileViewer,
    onOpenWith: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_files),
        title = item.name,
        onClose = onClose,
        modifier = modifier,
        lowercaseTitle = false
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (viewer) {
                    FileViewer.TEXT -> TextViewer(item.file)
                    FileViewer.IMAGE -> ImageViewer(item.file)
                    FileViewer.AUDIO -> AudioPlayer(item.file)
                    FileViewer.VIDEO -> VideoPlayer(item.file)
                    FileViewer.PDF -> PdfViewer(item.file)
                    FileViewer.NONE -> Unit
                }
            }
            // The way out to another app, in the accent, the way every sub-page's actions are.
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = padding.calculateBottomPadding())
            ) {
                ActionWord(stringResource(R.string.files_open_with), onOpenWith)
                ActionWord(stringResource(R.string.files_share).lowercase(Locale.getDefault()), onShare)
            }
        }
    }
}

@Composable
private fun ActionWord(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp),
        color = LocalZuneColors.current.accentColor,
        modifier = Modifier
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

// ── Text ────────────────────────────────────────────────────────────────────

/** The file's text, as it is, in a face where columns line up. Long files show their beginning. */
@Composable
private fun TextViewer(file: File) {
    val zuneColors = LocalZuneColors.current
    var text by remember(file) { mutableStateOf<String?>(null) }
    var truncated by remember(file) { mutableStateOf(false) }
    var failed by remember(file) { mutableStateOf(false) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = ByteArray(TEXT_LIMIT_BYTES + 1)
                val read = file.inputStream().use { it.read(bytes) }.coerceAtLeast(0)
                truncated = read > TEXT_LIMIT_BYTES
                text = String(bytes, 0, minOf(read, TEXT_LIMIT_BYTES), Charsets.UTF_8)
            }.onFailure {
                ZuneLog.w("FileViewers", "text could not be read", it)
                failed = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (truncated) {
            Text(
                text = stringResource(R.string.files_viewer_too_big),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.accentColor,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        when {
            failed -> Text(stringResource(R.string.files_viewer_cannot), color = zuneColors.textMuted)
            text == null -> Text(stringResource(R.string.files_cloud_loading), color = zuneColors.textMuted)
            else -> SelectionContainer {
                Text(
                    text = text.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

/** Half a megabyte is more than anyone reads on a phone and less than freezes it. */
private const val TEXT_LIMIT_BYTES = 512 * 1024

// ── Picture ─────────────────────────────────────────────────────────────────

/** The picture, fitted; pinch to look closer, drag to move about, double-tap to come back. */
@Composable
private fun ImageViewer(file: File) {
    var scale by remember(file) { mutableFloatStateOf(1f) }
    var offsetX by remember(file) { mutableFloatStateOf(0f) }
    var offsetY by remember(file) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(file) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    if (scale > 1f) {
                        offsetX += pan.x
                        offsetY += pan.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }
            }
            .pointerInput(file) {
                detectTapGestures(onDoubleTap = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                })
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

// ── Sound ───────────────────────────────────────────────────────────────────

/** One button and a line: the whole of what a sound needs. */
@Composable
private fun AudioPlayer(file: File) {
    val zuneColors = LocalZuneColors.current
    var ready by remember(file) { mutableStateOf(false) }
    var failed by remember(file) { mutableStateOf(false) }
    var playing by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var position by remember(file) { mutableIntStateOf(0) }

    val player = remember(file) {
        MediaPlayer().apply {
            runCatching {
                setDataSource(file.absolutePath)
                setOnPreparedListener {
                    ready = true
                    duration = it.duration
                    it.start()
                    playing = true
                }
                setOnCompletionListener { playing = false }
                setOnErrorListener { _, _, _ -> failed = true; true }
                prepareAsync()
            }.onFailure {
                ZuneLog.w("FileViewers", "sound could not be opened", it)
                failed = true
            }
        }
    }
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }

    // The line follows the sound while it plays.
    LaunchedEffect(player, playing) {
        while (isActive && playing) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(250)
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        if (failed) {
            Text(stringResource(R.string.files_viewer_cannot), color = zuneColors.textMuted)
            return@Column
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundButton(
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = stringResource(if (playing) R.string.files_viewer_pause else R.string.files_viewer_play),
                enabled = ready
            ) {
                if (playing) player.pause() else player.start()
                playing = !playing
            }
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = "${formatClock(position)} / ${formatClock(duration)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Slider(
            value = position.toFloat(),
            onValueChange = { position = it.roundToInt() },
            onValueChangeFinished = { runCatching { player.seekTo(position) } },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = ready,
            colors = metroSliderColors(),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// ── Film ────────────────────────────────────────────────────────────────────

/** The picture in a box, the same button and line beneath it. */
@Composable
private fun VideoPlayer(file: File) {
    val zuneColors = LocalZuneColors.current
    var playing by remember(file) { mutableStateOf(false) }
    var ready by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var position by remember(file) { mutableIntStateOf(0) }
    var view by remember(file) { mutableStateOf<VideoView?>(null) }

    DisposableEffect(file) { onDispose { runCatching { view?.stopPlayback() } } }

    LaunchedEffect(view, playing) {
        while (isActive && playing) {
            position = runCatching { view?.currentPosition ?: 0 }.getOrDefault(position)
            delay(250)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener {
                        ready = true
                        duration = it.duration
                        start()
                        playing = true
                    }
                    setOnCompletionListener { playing = false }
                    setOnErrorListener { _, _, _ -> true }
                    setOnClickListener {
                        if (isPlaying) pause() else start()
                        playing = isPlaying
                    }
                    view = this
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
            RoundButton(
                icon = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = stringResource(if (playing) R.string.files_viewer_pause else R.string.files_viewer_play),
                enabled = ready
            ) {
                val v = view ?: return@RoundButton
                if (v.isPlaying) v.pause() else v.start()
                playing = v.isPlaying
            }
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = "${formatClock(position)} / ${formatClock(duration)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Slider(
            value = position.toFloat(),
            onValueChange = { position = it.roundToInt() },
            onValueChangeFinished = { runCatching { view?.seekTo(position) } },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            enabled = ready,
            colors = metroSliderColors()
        )
        if (!ready) {
            Text(stringResource(R.string.files_cloud_loading), color = zuneColors.textMuted)
        }
    }
}

// ── PDF ─────────────────────────────────────────────────────────────────────

/**
 * Every page, one under the other, drawn as wide as the screen. Pages are drawn when they scroll
 * into view and let go again as they leave, so a long document costs no more than a short one.
 */
@Composable
private fun PdfViewer(file: File) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }.roundToInt()
        .coerceAtMost(PDF_MAX_WIDTH_PX)

    val document = remember(file) {
        runCatching {
            PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        }.onFailure { ZuneLog.w("FileViewers", "pdf could not be opened", it) }.getOrNull()
    }
    // The renderer draws one page at a time and is not to be asked twice at once.
    val lock = remember(file) { Mutex() }
    DisposableEffect(document) { onDispose { runCatching { document?.close() } } }

    if (document == null) {
        Text(stringResource(R.string.files_viewer_cannot), color = zuneColors.textMuted)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        for (index in 0 until document.pageCount) {
            item(key = index) {
                PdfPage(document, lock, index, widthPx, document.pageCount)
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfRenderer, lock: Mutex, index: Int, widthPx: Int, count: Int) {
    val zuneColors = LocalZuneColors.current
    var bitmap by remember(document, index) { mutableStateOf<Bitmap?>(null) }
    var ratio by remember(document, index) { mutableFloatStateOf(1.414f) }

    LaunchedEffect(document, index, widthPx) {
        val drawn = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    document.openPage(index).use { page ->
                        val height = (widthPx.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                        ratio = widthPx.toFloat() / height
                        Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { out ->
                            out.eraseColor(android.graphics.Color.WHITE)
                            page.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                }.onFailure { ZuneLog.w("FileViewers", "pdf page $index", it) }.getOrNull()
            }
        }
        bitmap = drawn
    }
    // The page's memory goes back when it scrolls out of the list.
    DisposableEffect(document, index) { onDispose { bitmap?.recycle(); bitmap = null } }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .background(Color.White)
        ) {
            bitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            text = stringResource(R.string.files_viewer_page, index + 1, count),
            style = MaterialTheme.typography.labelSmall,
            color = zuneColors.textMuted,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** Wider than any phone; a page drawn larger than this is only heavier, not sharper. */
private const val PDF_MAX_WIDTH_PX = 1600

// ── Shared ──────────────────────────────────────────────────────────────────

/** The bottom bar's round button, borrowed for play and pause. */
@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val ink = MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 1f else 0.35f)
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(2.dp, ink, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = ink, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun metroSliderColors() = SliderDefaults.colors(
    thumbColor = LocalZuneColors.current.accentColor,
    activeTrackColor = LocalZuneColors.current.accentColor,
    inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
)

private fun formatClock(millis: Int): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}
