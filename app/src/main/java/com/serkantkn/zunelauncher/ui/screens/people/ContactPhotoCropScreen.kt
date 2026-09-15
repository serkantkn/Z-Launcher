package com.serkantkn.zunelauncher.ui.screens.people

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Framing a picture for somebody's contact photo.
 *
 * A contact photo is square wherever it is shown — on the card, on the tile, beside an incoming
 * call — so this crops to a square rather than to the screen: the window is what gets captured,
 * and what falls outside it is not part of the picture.
 */
@Composable
internal fun ContactPhotoCropScreen(
    uri: Uri,
    onApply: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val coroutineScope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    /** The side of the square window in pixels, which the crop maths needs. */
    var windowPx by remember { mutableStateOf(0) }

    LaunchedEffect(uri) {
        source = withContext(Dispatchers.IO) { decodeScaled(context, uri) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val picture = source
        if (picture == null) {
            Text(
                text = stringResource(R.string.settings_image_loading),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            // Only what is inside this square becomes the photograph.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(androidx.compose.ui.graphics.RectangleShape)
                    .onSizeChanged { windowPx = it.width }
            ) {
                Image(
                    bitmap = picture.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset += pan
                            }
                        }
                )
            }

            Text(
                text = stringResource(R.string.people_photo_crop_help),
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 24.dp, end = 24.dp)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(24.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(stringResource(R.string.common_cancel_cap))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val cut = withContext(Dispatchers.Default) {
                                cutOut(picture, windowPx, scale, offset.x, offset.y)
                            }
                            if (cut != null) onApply(cut) else onCancel()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = zuneColors.accentColor,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.common_apply_cap))
                }
            }
        }
    }
}

/**
 * Reads the picture down to something a screen can hold.
 *
 * A photograph off a modern camera is tens of megapixels; decoding one at full size to crop a
 * 512-pixel square is how an app runs out of memory.
 */
private fun decodeScaled(context: android.content.Context, uri: Uri): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    var sample = 1
    while (bounds.outWidth / sample > MAX_EDGE_PX || bounds.outHeight / sample > MAX_EDGE_PX) {
        sample *= 2
    }

    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inSampleSize = sample })
    }
} catch (e: Exception) {
    ZuneLog.e(TAG, "could not read the picture", e)
    null
}

/** Takes the framed square out of the picture. */
private fun cutOut(
    source: Bitmap,
    windowPx: Int,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): Bitmap? = try {
    val rect = cropRectFor(source.width, source.height, windowPx, scale, offsetX, offsetY)
    Bitmap.createBitmap(source, rect.left, rect.top, rect.width, rect.height)
} catch (e: Exception) {
    ZuneLog.e(TAG, "could not cut the picture out", e)
    null
}

private const val MAX_EDGE_PX = 1600
private const val TAG = "ContactPhotoCrop"
