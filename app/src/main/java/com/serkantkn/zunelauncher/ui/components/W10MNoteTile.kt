package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.background
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
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

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
            note.isChecklist -> note.items.take(4).joinToString("\n") { (if (it.isChecked) "☑ " else "☐ ") + it.text }.ifBlank { null }
            note.content.isNotBlank() -> note.content.trim().take(160)
            else -> null
        }
    }

    val iconSize = if (span == 1) 22.dp else if (gridColumns >= 8) 24.dp else 28.dp
    val labelSize = if (span == 1) 11.sp else 14.sp
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
        back = if (backText != null) {
            {
                val fg = tileForegroundColor(tileColor)
                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        color = fg.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = backText,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = fg,
                        maxLines = if (span >= 4) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        } else null,
        front = {
            val fg = tileForegroundColor(tileColor)
            if (isEditing) {
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.4f)))
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier
                    .align(if (span == 1) Alignment.Center else Alignment.TopStart)
                    .padding(if (span == 1) 0.dp else 10.dp)
                    .size(iconSize)
            )
            if (span > 1 || note == null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = labelSize,
                        fontWeight = FontWeight.Normal
                    ),
                    color = fg,
                    maxLines = if (span >= 4) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
                )
            }
        }
    )
}
