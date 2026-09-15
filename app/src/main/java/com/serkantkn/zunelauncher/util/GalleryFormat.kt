package com.serkantkn.zunelauncher.util

import com.serkantkn.zunelauncher.data.model.MediaImage
import java.util.Locale

/**
 * The gallery's own arithmetic: how long a video runs, which day a picture belongs to, and whether
 * it answers to what somebody typed. No Android here, so it can be tested.
 */

/** A video's length, the way a player shows it: `1:23`, or `1:02:03` once there is an hour. */
fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

/**
 * The pictures grouped by the day they arrived, newest day first.
 *
 * The store hands back seconds since the epoch, not milliseconds — a detail worth naming, because
 * multiplying in the wrong place puts every photo in 1970 and the grid then has one heading.
 */
fun groupByDay(items: List<MediaImage>): List<Pair<Long, List<MediaImage>>> =
    items.groupBy { startOfDay(it.dateAdded * 1000L) }
        .toList()
        .sortedByDescending { it.first }
        .map { (day, ofDay) -> day to ofDay.sortedByDescending { it.dateAdded } }

/** Whether a picture answers to a typed query, folded so Turkish case works. */
fun MediaImage.matchesQuery(query: String): Boolean {
    val needle = foldForSearch(query.trim())
    if (needle.isEmpty()) return true
    return foldForSearch("$displayName $bucketName").contains(needle)
}

/**
 * How many columns a grid of this many pictures should have by default.
 *
 * A handful of pictures shown three across looks like a mistake; a thousand shown three across is
 * a very long scroll. This is only the starting point — it can always be changed by hand.
 */
fun defaultGridColumns(count: Int): Int = when {
    count <= 6 -> 2
    count <= 60 -> 3
    else -> 4
}

/** The columns the grid can be set to. */
val GRID_COLUMN_CHOICES = listOf(2, 3, 4, 5)

/**
 * What the camera wrote into the picture: the body, the lens settings, the moment.
 *
 * Read on a background thread — it opens the file — and returns only the lines that are actually
 * there. A screenshot or a picture that came through a chat app has none of this, and a details
 * panel full of blank rows tells somebody less than a short one.
 */
fun readPhotoExif(
    context: android.content.Context,
    uri: android.net.Uri,
    cameraLabel: String,
    lensLabel: String,
    exposureLabel: String
): List<Pair<String, String>> = runCatching {
    context.contentResolver.openInputStream(uri)?.use { stream ->
        val exif = android.media.ExifInterface(stream)
        buildList {
            val make = exif.getAttribute(android.media.ExifInterface.TAG_MAKE)?.trim().orEmpty()
            val model = exif.getAttribute(android.media.ExifInterface.TAG_MODEL)?.trim().orEmpty()
            val body = listOf(make, model).filter { it.isNotBlank() }.distinct().joinToString(" ")
            if (body.isNotBlank()) add(cameraLabel to body)

            val focal = exif.getAttribute(android.media.ExifInterface.TAG_FOCAL_LENGTH)
                ?.let { raw ->
                    // EXIF writes this as a fraction, "2750/100".
                    val parts = raw.split('/')
                    if (parts.size == 2) {
                        val top = parts[0].toDoubleOrNull()
                        val bottom = parts[1].toDoubleOrNull()
                        if (top != null && bottom != null && bottom != 0.0) {
                            String.format(Locale.getDefault(), "%.0f mm", top / bottom)
                        } else null
                    } else raw.toDoubleOrNull()?.let {
                        String.format(Locale.getDefault(), "%.0f mm", it)
                    }
                }
            val aperture = exif.getAttribute(android.media.ExifInterface.TAG_F_NUMBER)
                ?.toDoubleOrNull()
                ?.let { String.format(Locale.getDefault(), "f/%.1f", it) }
            val lens = listOfNotNull(focal, aperture).joinToString(" · ")
            if (lens.isNotBlank()) add(lensLabel to lens)

            val shutter = exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME)
                ?.toDoubleOrNull()
                ?.let { seconds ->
                    if (seconds >= 1.0) {
                        String.format(Locale.getDefault(), "%.1f s", seconds)
                    } else {
                        "1/${Math.round(1.0 / seconds)} s"
                    }
                }
            val iso = exif.getAttributeInt(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
                .takeIf { it > 0 }?.let { "ISO $it" }
            val exposure = listOfNotNull(shutter, iso).joinToString(" · ")
            if (exposure.isNotBlank()) add(exposureLabel to exposure)
        }
    }.orEmpty()
}.getOrElse {
    ZuneLog.w("GalleryFormat", "the picture's exif block could not be read", it)
    emptyList()
}
