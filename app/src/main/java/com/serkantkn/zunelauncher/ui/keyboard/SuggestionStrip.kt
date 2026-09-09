package com.serkantkn.zunelauncher.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.Suggestion

/**
 * The bar above the keys: the clipboard on the left, dictation and the quick settings on the
 * right, and the word candidates in between, separated by the hairlines Windows Phone uses. The
 * word auto-correct would apply is drawn in the accent colour; holding a candidate forgets it.
 */
@Composable
fun SuggestionStrip(
    suggestions: List<Suggestion>,
    palette: KeyboardPalette,
    height: Dp,
    onPick: (Suggestion) -> Unit,
    onForget: (Suggestion) -> Unit,
    onOpenClipboard: () -> Unit,
    onOpenVoice: () -> Unit,
    onOpenQuickSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(palette.board),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StripIcon(
            icon = Icons.Default.ContentPaste,
            palette = palette,
            onClick = onOpenClipboard
        )

        suggestions.forEachIndexed { index, suggestion ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .width(1.dp)
                        .background(palette.muted.copy(alpha = 0.35f))
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(suggestion.word) {
                        detectTapGestures(
                            onTap = { onPick(suggestion) },
                            onLongPress = { onForget(suggestion) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = suggestion.word,
                    color = when {
                        suggestion.isCorrection -> palette.accent
                        suggestion.isTyped -> palette.muted
                        else -> palette.text
                    },
                    fontSize = 16.sp,
                    fontWeight = if (suggestion.isCorrection) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
        // Keeps the cells evenly wide when there are fewer than three candidates.
        if (suggestions.size < MAX_SUGGESTIONS) {
            Box(
                modifier = Modifier
                    .weight((MAX_SUGGESTIONS - suggestions.size).toFloat())
                    .fillMaxHeight()
            )
        }

        StripIcon(
            icon = Icons.Default.Mic,
            palette = palette,
            onClick = onOpenVoice
        )

        StripIcon(
            icon = Icons.Default.Tune,
            palette = palette,
            onClick = onOpenQuickSettings
        )
    }
}

/** The clipboard and microphone shortcuts that flank the candidates. */
@Composable
private fun StripIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    palette: KeyboardPalette,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(42.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = palette.muted)
    }
}

/** How many candidates the strip shows at once. */
const val MAX_SUGGESTIONS = 3
