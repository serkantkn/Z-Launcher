package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.data.model.WpGlyphs
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Picking the picture one tile shows: the launcher's own Windows Phone glyphs, anything in the
 * icon pack the user has chosen, a picture out of the gallery, or back to the tile's own icon.
 *
 * Laid out as a full page rather than a dialog, the way Windows Phone's own choosers were — there
 * are a few hundred things to look through and a small box is no way to do it.
 */
@Composable
fun TileIconPicker(
    /** Whose icon is being chosen — the tile's name, under the heading. */
    subject: String,
    current: TileIcon,
    packPackage: String?,
    packDrawables: List<String>,
    packPreview: (String) -> ImageBitmap?,
    onPick: (TileIcon) -> Unit,
    onPickPicture: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    BackHandler(onBack = onDismiss)

    // A page rather than a dialog window, so it covers the status bar the way the hubs do.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(PICKER_Z)
            .background(if (zuneColors.isDark) Color.Black else Color.White)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 18.dp, bottom = 10.dp)
            ) {
                Text(
                    text = stringResource(R.string.tile_icon_title),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 34.sp
                    ),
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subject,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                contentPadding = PaddingValues(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal,
                    bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item(span = { GridItemSpan(GRID_COLUMNS) }) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    ) {
                        PickerAction(
                            icon = { tint ->
                                Icon(Icons.Default.Refresh, null, tint = tint, modifier = Modifier.size(18.dp))
                            },
                            label = stringResource(R.string.tile_icon_default),
                            selected = current == TileIcon.Default,
                            onClick = { onPick(TileIcon.Default) },
                            modifier = Modifier.weight(1f)
                        )
                        PickerAction(
                            icon = { tint ->
                                Icon(Icons.Default.ImageIcon, null, tint = tint, modifier = Modifier.size(18.dp))
                            },
                            label = stringResource(R.string.tile_icon_gallery),
                            selected = current is TileIcon.Picture,
                            onClick = onPickPicture,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(span = { GridItemSpan(GRID_COLUMNS) }) {
                    SectionHeader(stringResource(R.string.tile_icon_glyph_section))
                }

                WpGlyphs.GROUPS.forEach { group ->
                    item(span = { GridItemSpan(GRID_COLUMNS) }, key = "group_${group.id}") {
                        GroupHeader(group.id)
                    }
                    items(group.names, key = { "glyph_$it" }) { name ->
                        GlyphCell(
                            glyph = name,
                            selected = current == TileIcon.Glyph(name),
                            onClick = { onPick(TileIcon.Glyph(name)) }
                        )
                    }
                }

                if (!packPackage.isNullOrBlank() && packDrawables.isNotEmpty()) {
                    item(span = { GridItemSpan(GRID_COLUMNS) }, key = "pack_header") {
                        SectionHeader(stringResource(R.string.tile_icon_pack_section))
                    }
                    items(packDrawables, key = { "pack_$it" }) { name ->
                        PackCell(
                            bitmap = packPreview(name),
                            selected = current == TileIcon.Pack(packPackage, name),
                            onClick = { onPick(TileIcon.Pack(packPackage, name)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val zuneColors = LocalZuneColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 22.sp),
        color = if (zuneColors.isDark) Color.White else Color.Black,
        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)
    )
}

@Composable
private fun GroupHeader(groupId: String) {
    val label = when (groupId) {
        "communication" -> R.string.tile_icon_group_communication
        "media" -> R.string.tile_icon_group_media
        "places" -> R.string.tile_icon_group_places
        "work" -> R.string.tile_icon_group_work
        "system" -> R.string.tile_icon_group_system
        else -> R.string.tile_icon_group_life
    }
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelMedium,
        color = LocalZuneColors.current.textMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

/** One of the launcher's own glyphs, shown the way a tile would show it. */
@Composable
private fun GlyphCell(glyph: String, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val id = remember(glyph) {
        @Suppress("DISCOURAGED_API_USAGE")
        context.resources.getIdentifier(WpGlyphs.resourceName(glyph), "drawable", context.packageName)
    }
    PickerCell(selected = selected, onClick = onClick) {
        if (id != 0) {
            Icon(
                painter = painterResource(id),
                contentDescription = glyph,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/** One drawable out of the icon pack, drawn exactly as the pack drew it. */
@Composable
private fun PackCell(bitmap: ImageBitmap?, selected: Boolean, onClick: () -> Unit) {
    PickerCell(selected = selected, onClick = onClick) {
        bitmap?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun PickerCell(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(zuneColors.accentColor.copy(alpha = if (selected) 1f else 0.45f))
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.25f)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() }
    )
}

@Composable
private fun PickerAction(
    icon: @Composable (Color) -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (selected) Color.White else if (zuneColors.isDark) Color.White else Color.Black
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .background(if (selected) zuneColors.accentColor else Color.Transparent)
            .border(1.dp, if (selected) Color.White else zuneColors.textMuted.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        icon(ink)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private const val GRID_COLUMNS = 6

/** Above the board, its edit bar and anything else the Start screen is showing. */
private const val PICKER_Z = 50f
