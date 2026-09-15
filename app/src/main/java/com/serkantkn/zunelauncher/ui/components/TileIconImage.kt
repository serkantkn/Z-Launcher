package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.util.TileIconFace

/**
 * An app's icon on a tile.
 *
 * A [TileIconFace.Glyph] is a shape and nothing more, so it is painted in the tile's own ink the
 * way every Windows Phone tile glyph was; a [TileIconFace.Picture] is drawn as it is; and where
 * there was no shape to draw, the app's initial stands in its place.
 */
@Composable
fun TileIconImage(
    face: TileIconFace,
    size: Dp,
    ink: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    when (face) {
        is TileIconFace.Glyph -> Image(
            bitmap = face.bitmap,
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(ink),
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size)
        )

        is TileIconFace.Picture -> Image(
            bitmap = face.bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size)
        )

        is TileIconFace.Letter -> Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = face.text,
                style = TextStyle(
                    color = ink,
                    fontSize = letterSizeOf(size),
                    fontWeight = FontWeight.Light
                )
            )
        }

        TileIconFace.None -> Unit
    }
}

/** A letter standing in for an icon fills its box the way a glyph would. */
private fun letterSizeOf(size: Dp): TextUnit = (size.value * 0.82f).sp
