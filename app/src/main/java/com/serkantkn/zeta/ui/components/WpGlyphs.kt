package com.serkantkn.zeta.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Windows Phone's messaging glyph: a square-cornered speech balloon with its tail at the bottom
 * left, and a smiling face let into it — two eyes and a grin, cut out of the balloon so the tile
 * shows through, the way the Messaging tile drew its :-)
 *
 * Drawn on the 24-unit grid Material's icons use, so it sits in a tile at the same size as the
 * other hubs' glyphs.
 */
val WpMessagingGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "WpMessaging",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            // The balloon and its tail.
            moveTo(2f, 3f)
            lineTo(22f, 3f)
            lineTo(22f, 17f)
            lineTo(10.5f, 17f)
            lineTo(5.5f, 21.5f)
            lineTo(5.5f, 17f)
            lineTo(2f, 17f)
            close()
            // The eyes.
            moveTo(8f, 6.5f)
            lineTo(10f, 6.5f)
            lineTo(10f, 9.5f)
            lineTo(8f, 9.5f)
            close()
            moveTo(14f, 6.5f)
            lineTo(16f, 6.5f)
            lineTo(16f, 9.5f)
            lineTo(14f, 9.5f)
            close()
            // The grin.
            moveTo(6.8f, 11f)
            curveTo(8f, 15f, 16f, 15f, 17.2f, 11f)
            lineTo(15.6f, 11f)
            curveTo(14.6f, 13.2f, 9.4f, 13.2f, 8.4f, 11f)
            close()
        }
    }.build()
}
