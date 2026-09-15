package com.serkantkn.zunelauncher.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Converts an Android Drawable to a Compose ImageBitmap for rendering in Image composables.
 */
fun Drawable.toImageBitmap(): ImageBitmap {
    val bitmap = Bitmap.createBitmap(
        intrinsicWidth.coerceAtLeast(1),
        intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

/**
 * The same icon, square.
 *
 * Android masks a modern icon into whatever shape the phone's own launcher prefers — usually a
 * circle. Metro has no circles: every icon on Windows Phone was a square, edge to edge. So an
 * adaptive icon is drawn here the long way, its two layers onto a square canvas with no mask over
 * them, and everything else is simply drawn as it is.
 */
fun Drawable.toSquareImageBitmap(sizePx: Int = SQUARE_ICON_PX): ImageBitmap {
    val edge = sizePx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && this is AdaptiveIconDrawable) {
        background?.let { layer ->
            layer.setBounds(0, 0, edge, edge)
            layer.draw(canvas)
        }
        foreground?.let { layer ->
            layer.setBounds(0, 0, edge, edge)
            layer.draw(canvas)
        }
    } else {
        setBounds(0, 0, edge, edge)
        draw(canvas)
    }
    return bitmap.asImageBitmap()
}

/** Big enough for the largest place the list draws an icon, on the densest screen. */
private const val SQUARE_ICON_PX = 168
