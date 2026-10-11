package com.serkantkn.zunelauncher.ui.screens.files

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.ui.animation.ZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.animation.zuneZoomAnchor
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.FileCategory
import com.serkantkn.zunelauncher.util.category
import com.serkantkn.zunelauncher.util.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The picture a file is shown by in the thumbnails view: a photo or a video's frame, a song's
 * cover, an app's own icon — and for everything else, and until the picture is ready, the
 * kind's glyph on the kind's colour, the same square the list shows small.
 */
@Composable
internal fun FileThumb(item: FileItemModel, modifier: Modifier = Modifier, glyphSize: Dp = 40.dp) {
    val zuneColors = LocalZuneColors.current
    val category = item.category()
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(categoryColor(category, zuneColors.accentColor))
    ) {
        Icon(glyphOf(category), contentDescription = null, tint = Color.White, modifier = Modifier.size(glyphSize))
        when (category) {
            FileCategory.IMAGE, FileCategory.VIDEO -> AsyncImage(
                model = remember(item.file, item.lastModified) {
                    ImageRequest.Builder(context)
                        .data(item.file)
                        .size(THUMB_PX)
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            FileCategory.AUDIO, FileCategory.APP -> {
                val picture by produceState<ImageBitmap?>(null, item.file, item.lastModified) {
                    value = FileThumbnails.load(context, item)
                }
                picture?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        contentScale = if (category == FileCategory.AUDIO) ContentScale.Crop else ContentScale.Fit,
                        modifier = if (category == FileCategory.AUDIO) Modifier.fillMaxSize() else Modifier.fillMaxSize(0.6f)
                    )
                }
            }
            else -> Unit
        }
    }
}

/** The covers and app icons, read once and kept for as long as there is room. */
internal object FileThumbnails {
    private val cache = LruCache<String, ImageBitmap>(48)

    suspend fun load(context: Context, item: FileItemModel): ImageBitmap? {
        val key = item.file.absolutePath + "#" + item.lastModified
        cache.get(key)?.let { return it }
        val picture = withContext(Dispatchers.IO) {
            runCatching {
                when (item.category()) {
                    FileCategory.AUDIO -> MediaMetadataRetriever().let { retriever ->
                        try {
                            retriever.setDataSource(item.file.absolutePath)
                            retriever.embeddedPicture?.let { bytes ->
                                val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
                            }
                        } finally {
                            retriever.release()
                        }
                    }
                    FileCategory.APP -> {
                        val pm = context.packageManager
                        pm.getPackageArchiveInfo(item.file.absolutePath, 0)?.applicationInfo?.let { info ->
                            // The archive's own paths, or it cannot find the icon inside itself.
                            info.sourceDir = item.file.absolutePath
                            info.publicSourceDir = item.file.absolutePath
                            info.loadIcon(pm).toBitmap(THUMB_PX / 2, THUMB_PX / 2).asImageBitmap()
                        }
                    }
                    else -> null
                }
            }.getOrNull()
        }
        if (picture != null) cache.put(key, picture)
        return picture
    }
}

/**
 * One file in the thumbnails view, on the phone and the tablet alike: its picture, then its name
 * and what it is. Chosen, it wears Windows 8's mark — an accent frame and a check in the
 * corner.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileTile(
    item: FileItemModel,
    selected: Boolean,
    /** A fixed size on the tablet's sideways grid; null fills the cell, as the phone's grid does. */
    tileSize: Dp?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    nameAnchor: ZuneZoomAnchor? = null
) {
    val zuneColors = LocalZuneColors.current
    val source = remember { MutableInteractionSource() }
    val folderLabel = stringResource(R.string.files_folder)
    Column(
        modifier = modifier
            .then(if (tileSize != null) Modifier.width(tileSize) else Modifier.fillMaxWidth())
            .wpTilt(interactionSource = source)
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            modifier = if (tileSize != null) Modifier.size(tileSize) else Modifier.fillMaxWidth().aspectRatio(1f)
        ) {
            FileThumb(item, modifier = Modifier.fillMaxSize(), glyphSize = tileSize?.times(0.3f) ?: 36.dp)
            if (selected) SelectionMark(zuneColors.accentColor)
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 17.sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp)
                .then(if (nameAnchor != null) Modifier.zuneZoomAnchor(nameAnchor) else Modifier)
        )
        Text(
            text = if (item.isDirectory) folderLabel else formatFileSize(item.size),
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textMuted,
            maxLines = 1
        )
    }
}

/** Windows 8's selection mark: a frame in the accent and a check in a corner triangle. */
@Composable
private fun SelectionMark(accent: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(3.dp, accent)
            .drawWithContent {
                drawContent()
                val corner = 34.dp.toPx()
                val triangle = Path().apply {
                    moveTo(size.width - corner, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, corner)
                    close()
                }
                drawPath(triangle, accent)
                val tick = Path().apply {
                    moveTo(size.width - corner * 0.45f, corner * 0.28f)
                    lineTo(size.width - corner * 0.30f, corner * 0.43f)
                    lineTo(size.width - corner * 0.10f, corner * 0.16f)
                }
                drawPath(tick, Color.White, style = Stroke(width = 2.dp.toPx()))
            }
    )
}

private const val THUMB_PX = 320
