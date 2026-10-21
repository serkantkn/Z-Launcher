package com.serkantkn.zeta.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.Note
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.ui.theme.LocalZuneColors

/**
 * A single pinned note on Start (or, when [note] is null, the quick-capture tile). It keeps the
 * note's own colour instead of the accent, and turns over to the first lines of the body or the
 * top checklist rows. A locked note never shows what it says.
 */
@Composable
fun W10MNoteTile(
    note: Note?,
    span: Int,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerStyle: TileCornerStyle = TileCornerStyle.ROUNDED,
    gridColumns: Int = 4,
    spacing: Dp = 8.dp
) {
    val zuneColors = LocalZuneColors.current

    val tileColor = remember(note?.colorHex, zuneColors.accentColor) {
        val hex = note?.colorHex
        if (hex.isNullOrBlank()) zuneColors.accentColor
        else try {
            Color(android.graphics.Color.parseColor(hex))
        } catch (e: IllegalArgumentException) {
            zuneColors.accentColor
        }
    }

    val backText: String? = remember(note) {
        when {
            note == null || note.isLocked -> null
            note.isChecklist -> note.items.take(12).joinToString("\n") { (if (it.isChecked) "☑ " else "☐ ") + it.text }.ifBlank { null }
            note.content.isNotBlank() -> note.content.trim().take(600)
            else -> null
        }
    }

    val shape = TileShape.of(span)
    // The wide and large tiles show the note itself on their front; only the medium one turns over.
    val showsBody = backText != null && (shape == TileShape.WIDE || shape == TileShape.LARGE)
    val label = note?.displayTitle?.lowercase() ?: stringResource(R.string.notes_quick_note)
    val icon = when {
        note == null -> Icons.Default.Add
        note.isLocked -> Icons.Default.Lock
        note.isChecklist -> Icons.Default.Checklist
        else -> Icons.Default.StickyNote2
    }

    W10MTileSurface(
        liveKey = "note:${note?.id ?: "quick"}",
        span = span,
        gridColumns = gridColumns,
        spacing = spacing,
        isEditing = isEditing,
        isDragging = isDragging,
        highlighted = isMergeTarget,
        cornerStyle = cornerStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        onRemoveClick = onRemoveClick,
        onResizeClick = onResizeClick,
        modifier = modifier,
        tileColor = tileColor,
        back = if (backText != null && shape == TileShape.MEDIUM) {
            {
                val fg = tileForegroundColor(tileColor)
                TileFace(span) { m ->
                    Column(modifier = Modifier.fillMaxSize().padding(m.pad)) {
                        TileCaption(icon, label, m, fg)
                        NoteText(backText, m, fg, Modifier.padding(top = m.dp(4f)).weight(1f))
                    }
                }
            }
        } else null,
        front = {
            val fg = tileForegroundColor(tileColor)
            if (isEditing && !LocalTileIsPreview.current) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            if (showsBody) {
                TileFace(span) { m ->
                    Column(modifier = Modifier.fillMaxSize().padding(m.pad)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(m.dp(22f)))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = m.text(if (m.isLarge) 22f else 18f), fontWeight = FontWeight.Normal),
                                color = fg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = m.dp(8f))
                            )
                        }
                        NoteText(backText!!, m, fg, Modifier.padding(top = m.dp(8f)).weight(1f))
                    }
                }
                return@W10MTileSurface
            }
            NamedFace(span, label.takeIf { span > 1 }, fg) { size, glyphModifier, _ ->
                Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = glyphModifier.size(size))
            }
        }
    )
}

/** A note's words, or its checklist one row a line, filling what is left of the tile. */
@Composable
private fun NoteText(text: String, m: TileMetrics, ink: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontSize = m.text(if (m.isLarge) 17f else 14f),
            lineHeight = m.text(if (m.isLarge) 23f else 18f)
        ),
        color = ink,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
