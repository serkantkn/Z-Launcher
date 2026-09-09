package com.serkantkn.zunelauncher.ui.keyboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * Flat Metro surface colours shared by the keys and every panel: a near-black (or near-white)
 * board, slightly lighter keys, and the launcher's accent for anything pressed or selected.
 */
data class KeyboardPalette(
    val board: Color,
    val key: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val isDark: Boolean
)

@Composable
fun rememberKeyboardPalette(): KeyboardPalette {
    val zuneColors = LocalZuneColors.current
    val isDark = zuneColors.isDark
    val accent = zuneColors.accentColor
    return remember(isDark, accent) {
        if (isDark) {
            KeyboardPalette(
                board = Color(0xFF0B0B0B),
                key = Color(0xFF1F1F1F),
                text = Color.White,
                muted = Color(0xFF9A9A9A),
                accent = accent,
                isDark = true
            )
        } else {
            KeyboardPalette(
                board = Color(0xFFE4E4E4),
                key = Color.White,
                text = Color(0xFF1A1A1A),
                muted = Color(0xFF6B6B6B),
                accent = accent,
                isDark = false
            )
        }
    }
}
