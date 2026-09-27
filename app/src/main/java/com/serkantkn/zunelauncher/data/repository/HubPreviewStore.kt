package com.serkantkn.zunelauncher.data.repository

import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * What each running hub looked like when it was left.
 *
 * Windows Phone's task switcher showed a picture of the app, not its icon, and the picture was
 * taken at the moment you left it. This does the same: the window is copied just before the hub
 * turns away, at a third of its size, and the running-hubs strip shows it when a tile is held.
 */
object HubPreviewStore {

    /** A third of a 1080-wide screen is enough to read a page at a glance and costs ~1 MB. */
    private const val SCALE = 3

    private val _previews = MutableStateFlow<Map<HubType, ImageBitmap>>(emptyMap())
    val previews: StateFlow<Map<HubType, ImageBitmap>> = _previews.asStateFlow()

    /**
     * Copies the window as it stands and files it under [hub]. Returns when the copy is done, so
     * the caller can hold the hub still until its picture has been taken.
     */
    suspend fun capture(view: View, hub: HubType) {
        val window = (view.context as? Activity)?.window ?: return
        val width = view.width / SCALE
        val height = view.height / SCALE
        if (width <= 0 || height <= 0) return
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            ZuneLog.e(TAG, "no room for a preview of $hub", e)
            return
        }
        val copied = suspendCancellableCoroutine { continuation ->
            try {
                PixelCopy.request(window, bitmap, { result ->
                    continuation.resume(result == PixelCopy.SUCCESS)
                }, Handler(Looper.getMainLooper()))
            } catch (e: IllegalArgumentException) {
                // A window without a surface yet, which can happen if the key arrives early.
                ZuneLog.e(TAG, "the window would not be copied", e)
                continuation.resume(false)
            }
        }
        if (copied) {
            _previews.value = _previews.value + (hub to bitmap.asImageBitmap())
        } else {
            bitmap.recycle()
        }
    }

    /** Throws away the pictures of hubs that are no longer running. */
    fun keepOnly(hubs: Collection<HubType>) {
        val current = _previews.value
        val kept = current.filterKeys { it in hubs }
        if (kept.size != current.size) _previews.value = kept
    }

    private const val TAG = "HubPreviewStore"
}
