package com.serkantkn.zunelauncher.ui.tv

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import com.serkantkn.zunelauncher.ui.screens.video.VideoResumeStore
import com.serkantkn.zunelauncher.util.ZuneLog

/** A video left part way in the launcher's own player: what Start offers to carry on with. */
internal data class TvContinue(
    val uri: Uri,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val thumbnail: ImageBitmap?
) {
    /** How far in, 0-1. */
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** What Start's tiles show besides their names, read from the TV when Start comes back. */
internal data class TvLive(
    val continueWatching: TvContinue? = null,
    val latestPhoto: ImageBitmap? = null
)

/**
 * Reads [TvLive]. Off the main thread: it asks the media store and decodes pictures. Anything
 * the launcher may not read - no permission yet - is simply left out.
 */
internal fun loadTvLive(context: Context): TvLive = TvLive(
    continueWatching = runCatching { lastVideo(context) }
        .onFailure { ZuneLog.w(TAG, "the last video could not be read", it) }
        .getOrNull(),
    latestPhoto = runCatching { lastPhoto(context) }
        .onFailure { ZuneLog.w(TAG, "the last photo could not be read", it) }
        .getOrNull()
)

/**
 * The video most recently left part way, by the player's own record of where each was left: one
 * from the media store, or a file the launcher handed the player (a film on a USB stick, opened
 * from files). Only one still there counts - a record outlives a deleted file - and one that
 * cannot be read is passed over for the next.
 */
private fun lastVideo(context: Context): TvContinue? {
    val left = VideoResumeStore(context).all().entries.sortedByDescending { it.value.second }
    val ownFiles = "${context.packageName}.fileprovider"
    val mayReadMedia = canRead(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE)
    for ((key, value) in left) {
        val found = runCatching {
            when {
                key.startsWith(MEDIA_PREFIX) && mayReadMedia -> mediaVideo(context, key, value.first)
                Uri.parse(key).authority == ownFiles -> fileVideo(context, Uri.parse(key), value.first)
                else -> null
            }
        }.onFailure { ZuneLog.w(TAG, "a video left part way could not be read", it) }.getOrNull()
        if (found != null) return found
    }
    return null
}

private fun mediaVideo(context: Context, key: String, positionMs: Long): TvContinue? {
    val id = key.removePrefix(MEDIA_PREFIX).toLongOrNull() ?: return null
    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    val projection = arrayOf(MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION)
    val found = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@use null
        val title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: cursor.getString(1).orEmpty()
        title to cursor.getLong(2)
    } ?: return null
    return TvContinue(uri, found.first, positionMs, found.second, thumbnailOf(context, uri, VIDEO_THUMB))
}

/** A file through the launcher's own provider: its name, its length and the frame it was left at. */
private fun fileVideo(context: Context, uri: Uri, positionMs: Long): TvContinue? {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val frame = runCatching {
            retriever.getScaledFrameAtTime(positionMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, VIDEO_THUMB.width, VIDEO_THUMB.height)
        }.getOrNull()
        TvContinue(uri, name.substringBeforeLast('.'), positionMs, duration, frame?.asImageBitmap())
    } finally {
        retriever.release()
    }
}

/** The newest picture on the TV - a cast, a screenshot, something copied over. */
private fun lastPhoto(context: Context): ImageBitmap? {
    if (!canRead(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE)) return null
    val id = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Images.Media._ID),
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null } ?: return null
    return thumbnailOf(context, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id), PHOTO_THUMB)
}

private fun thumbnailOf(context: Context, uri: Uri, size: Size): ImageBitmap? = runCatching {
    val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, size, null)
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Video.Thumbnails.getThumbnail(
            context.contentResolver, ContentUris.parseId(uri), MediaStore.Video.Thumbnails.MINI_KIND, null
        )
    }
    bitmap?.asImageBitmap()
}.getOrNull()

private fun canRead(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private const val MEDIA_PREFIX = "media:"
private val VIDEO_THUMB = Size(640, 360)
private val PHOTO_THUMB = Size(480, 480)
private const val TAG = "TvLive"
