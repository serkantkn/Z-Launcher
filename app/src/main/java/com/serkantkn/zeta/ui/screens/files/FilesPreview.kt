package com.serkantkn.zeta.ui.screens.files

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.serkantkn.zeta.data.model.FileItemModel
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.util.FileCategory
import com.serkantkn.zeta.util.FileViewer
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.category
import com.serkantkn.zeta.util.viewerFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A look at what a file holds, for its details: a photo whole, a video's frame, a PDF's first
 * page, a text's first lines, a song's cover with a button to hear it — and for anything else
 * its kind's glyph, large. The same on the phone's details page and the tablet's side panel.
 */
@Composable
internal fun FilePreview(item: FileItemModel, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val viewer = remember(item.extension) { viewerFor(item.extension) }
    Box(contentAlignment = Alignment.Center, modifier = modifier.background(ink.copy(alpha = 0.06f))) {
        when {
            item.isDirectory -> FileThumb(item, modifier = Modifier.fillMaxSize(), glyphSize = 72.dp)
            viewer == FileViewer.IMAGE -> AsyncImage(
                model = remember(item.file, item.lastModified) {
                    ImageRequest.Builder(context).data(item.file).size(PREVIEW_PX).crossfade(true).build()
                },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            item.category() == FileCategory.VIDEO -> {
                FileThumb(item, modifier = Modifier.fillMaxSize(), glyphSize = 72.dp)
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(8.dp)
                )
            }
            viewer == FileViewer.PDF -> {
                val page by produceState<ImageBitmap?>(null, item.file, item.lastModified) {
                    value = firstPdfPage(item.file)
                }
                val shown = page
                if (shown != null) {
                    Image(shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                } else {
                    FileThumb(item, modifier = Modifier.fillMaxSize(), glyphSize = 72.dp)
                }
            }
            viewer == FileViewer.TEXT -> {
                val text by produceState<String?>(null, item.file, item.lastModified) {
                    value = firstText(item.file)
                }
                val shown = text
                if (shown != null) {
                    Text(
                        text = shown,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp),
                        color = ink.copy(alpha = 0.85f),
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                } else {
                    FileThumb(item, modifier = Modifier.fillMaxSize(), glyphSize = 72.dp)
                }
            }
            item.category() == FileCategory.AUDIO -> {
                FileThumb(item, modifier = Modifier.fillMaxSize(), glyphSize = 72.dp)
                AudioToggle(item.file)
            }
            else -> FileThumb(item, modifier = Modifier.fillMaxSize(), glyphSize = 72.dp)
        }
    }
}

/** A ringed play button over the cover; leaving the details stops the song. */
@Composable
private fun AudioToggle(file: File) {
    var player by remember(file) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(file) { mutableStateOf(false) }
    DisposableEffect(file) {
        onDispose {
            runCatching { player?.release() }
            player = null
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(60.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .border(2.dp, Color.White, CircleShape)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                val current = player ?: runCatching {
                    MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnCompletionListener { playing = false }
                        prepare()
                    }
                }.onFailure { ZuneLog.w("FilePreview", "the song would not play", it) }.getOrNull()
                    ?.also { player = it }
                if (current != null) {
                    if (playing) current.pause() else current.start()
                    playing = !playing
                }
            }
    ) {
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(30.dp)
        )
    }
}

private suspend fun firstPdfPage(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(0).use { page ->
                    val width = PREVIEW_PX
                    val height = (width.toFloat() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap.asImageBitmap()
                }
            }
        }
    }.getOrNull()
}

private suspend fun firstText(file: File): String? = withContext(Dispatchers.IO) {
    runCatching {
        file.inputStream().use { input ->
            val bytes = ByteArray(TEXT_PEEK_BYTES)
            val read = input.read(bytes).coerceAtLeast(0)
            String(bytes, 0, read, Charsets.UTF_8)
        }
    }.getOrNull()
}

private const val PREVIEW_PX = 720
private const val TEXT_PEEK_BYTES = 2048
