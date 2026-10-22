package com.serkantkn.zeta.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.AppInfo
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.ui.screens.home.StartTileUIModel
import com.serkantkn.zeta.util.TileIconFace

/**
 * A folder of tiles, drawn the way Windows 10 Mobile drew them: the tiles inside shrunk into a
 * grid on the folder's face, with its name along the bottom.
 *
 * While the folder is open its face gives all that up for a single arrow pointing back up at
 * itself — the tiles are on the board below, so showing them twice says nothing, and the arrow is
 * the way back out.
 *
 * The folder does not turn over — there is nothing behind it — and it is not a folder target
 * itself, since folders never nest.
 */
@Composable
fun W10MFolderTile(
    folder: StartTileUIModel.Folder,
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean,
    isOpen: Boolean,
    cornerStyle: TileCornerStyle,
    appFace: (AppInfo) -> TileIconFace,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = folder.name.ifBlank { stringResource(R.string.start_folder_default_name) }

    W10MTileSurface(
        liveKey = folder.id,
        span = span,
        gridColumns = gridColumns,
        spacing = spacing,
        isEditing = isEditing,
        isDragging = isDragging,
        cornerStyle = cornerStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        onRemoveClick = onRemoveClick,
        onResizeClick = onResizeClick,
        modifier = modifier,
        highlighted = isMergeTarget,
        // A folder is the one tile that still answers a tap while the board is being edited.
        clickableWhileEditing = true
    ) {
        val fg = tileForegroundColor()

        if (isOpen) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = stringResource(R.string.start_folder_close),
                tint = fg,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (span == 1) 26.dp else 44.dp)
            )
            return@W10MTileSurface
        }

        // As many shrunk tiles as keep them about square: four on the small tile, nine on the
        // medium one, two rows of six on the wide one and sixteen on the large one.
        val (columns, rows) = when (TileShape.of(span)) {
            TileShape.SMALL -> 2 to 2
            TileShape.MEDIUM -> 3 to 3
            TileShape.WIDE -> 6 to 2
            TileShape.LARGE -> 4 to 4
        }
        val perSide = columns
        val shown = folder.children.take(columns * rows)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 8.dp,
                    end = 8.dp,
                    top = 8.dp,
                    bottom = if (span > 1) 26.dp else 6.dp
                ),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val chunks = shown.chunked(perSide)
            chunks.forEach { rowChildren ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rowChildren.forEach { child ->
                        FolderChip(
                            child = child,
                            appFace = appFace,
                            foreground = fg,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                    // Keeps the last row's tiles the same size as the rows above it.
                    repeat(perSide - rowChildren.size) {
                        Box(modifier = Modifier.weight(1f).fillMaxSize())
                    }
                }
            }
            // Rows the folder has no tiles for still take their share, so the chips stay square.
            repeat(rows - chunks.size) { Box(modifier = Modifier.fillMaxWidth().weight(1f)) }
        }

        if (span > 1) TileLabel(name, span, gridColumns, fg)
    }
}

/**
 * One tile shrunk onto the folder's face: an app's icon, a hub's glyph, or a note. On a small
 * tile the chips are a few pixels across, so what is on them is drawn smaller and clipped: a
 * 20dp icon used to spill over the edges of a 14dp chip.
 */
@Composable
private fun FolderChip(
    child: StartTileUIModel,
    appFace: (AppInfo) -> TileIconFace,
    foreground: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .clipToBounds()
            .background(Color.White.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        // Drawn to the chip's size, which runs from a few pixels to a quarter of a large tile.
        val side = minOf(maxWidth, maxHeight)
        val iconSize = side * 0.56f
        val glyphPad = side * 0.2f
        when (child) {
            is StartTileUIModel.App -> TileIconImage(
                face = appFace(child.appInfo),
                size = iconSize,
                ink = foreground,
                contentDescription = child.appInfo.label
            )

            is StartTileUIModel.Hub -> ChipGlyphOf(child.hubType, foreground, glyphPad)

            is StartTileUIModel.ExternalHubTile -> TileIconImage(
                face = externalHubFace(androidx.compose.ui.platform.LocalContext.current, child.hub),
                size = iconSize,
                ink = foreground,
                contentDescription = hubTitle(child.hub)
            )

            is StartTileUIModel.Person -> Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.fillMaxSize().padding(glyphPad)
            )

            is StartTileUIModel.Thread -> Icon(
                imageVector = Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.fillMaxSize().padding(glyphPad)
            )

            // A pinned record inside a folder shows its cover.
            is StartTileUIModel.MusicAlbum -> {
                val cover = child.artUri
                if (cover != null) {
                    coil.compose.AsyncImage(
                        model = cover,
                        contentDescription = child.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.fillMaxSize().padding(glyphPad)
                    )
                }
            }

            // A pinned album inside a folder shows its newest picture, not a glyph.
            is StartTileUIModel.Album -> {
                val cover = child.covers.firstOrNull()
                if (cover != null) {
                    coil.compose.AsyncImage(
                        model = cover,
                        contentDescription = child.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = foreground,
                        modifier = Modifier.fillMaxSize().padding(glyphPad)
                    )
                }
            }

            is StartTileUIModel.Web -> Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.fillMaxSize().padding(glyphPad)
            )

            is StartTileUIModel.NoteTile, is StartTileUIModel.QuickNote -> Icon(
                imageVector = Icons.Default.StickyNote2,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.fillMaxSize().padding(glyphPad)
            )

            // Folders never nest, so this cannot happen; drawing nothing is the safe answer.
            is StartTileUIModel.Folder -> Unit
        }
    }
}

@Composable
private fun ChipGlyphOf(hubType: HubType, foreground: Color, pad: Dp) {
    Icon(
        imageVector = getHubIcon(hubType),
        contentDescription = null,
        tint = foreground,
        modifier = Modifier.fillMaxSize().padding(pad)
    )
}

/** The four-square mark used where a folder needs an icon of its own. */
@Composable
fun BoxScope.FolderGlyph(color: Color, size: Dp) {
    Column(
        modifier = Modifier.size(size).align(Alignment.Center),
        verticalArrangement = Arrangement.spacedBy(size * 0.12f)
    ) {
        repeat(2) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(size * 0.12f)
            ) {
                repeat(2) {
                    Box(modifier = Modifier.weight(1f).fillMaxSize().background(color))
                }
            }
        }
    }
}
