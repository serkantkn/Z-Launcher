package com.serkantkn.zunelauncher.ui.tv

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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
 * The video most recently left part way, by the player's own record of where each was left. Only
 * one still in the media store counts: a record outlives a deleted file.
 */
private fun lastVideo(context: Context): TvContinue? {
    if (!canRead(context, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE)) return null
    val left = VideoResumeStore(context).all()
        .filterKeys { it.startsWith(MEDIA_PREFIX) }
        .entries
        .sortedByDescending { it.value.second }
    for ((key, value) in left) {
        val id = key.removePrefix(MEDIA_PREFIX).toLongOrNull() ?: continue
        val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
        val projection = arrayOf(MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DURATION)
        val found = context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: cursor.getString(1).orEmpty()
            title to cursor.getLong(2)
        } ?: continue
        return TvContinue(
            uri = uri,
            title = found.first,
            positionMs = value.first,
            durationMs = found.second,
            thumbnail = thumbnailOf(context, uri, VIDEO_THUMB)
        )
    }
    return null
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
