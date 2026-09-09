package com.serkantkn.zunelauncher.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.ClipboardEntry
import com.serkantkn.zunelauncher.util.KeyboardLayouts

/**
 * Clipboard history. An input method may read the clipboard while it is on screen, so every clip
 * the keyboard sees is kept here; pinned ones stay until they are unpinned.
 */
@Composable
fun ClipboardPanel(
    entries: List<ClipboardEntry>,
    palette: KeyboardPalette,
    height: Dp,
    rowHeight: Dp,
    onPaste: (ClipboardEntry) -> Unit,
    onPin: (ClipboardEntry) -> Unit,
    onDelete: (ClipboardEntry) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(palette.board)
            .padding(bottom = bottomPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.keyboard_clipboard_title),
                color = palette.muted,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.keyboard_clipboard_clear),
                color = palette.accent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClear
                )
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.keyboard_clipboard_empty),
                    color = palette.muted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(palette.key)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPaste(entry) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = entry.text,
                                color = palette.text,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = if (entry.pinned) palette.accent else palette.muted,
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .size(18.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onPin(entry) }
                            )
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = palette.muted,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .size(18.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { onDelete(entry) }
                            )
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
            PanelKey(palette = palette, modifier = Modifier.weight(1.5f), onClick = onClose) {
                Text(text = KeyboardLayouts.LETTERS_LABEL, color = palette.text, fontSize = 13.sp)
            }
            PanelKey(palette = palette, modifier = Modifier.weight(5f), onClick = onClose) {
                Text(
                    text = stringResource(R.string.keyboard_panel_back),
                    color = palette.muted,
                    fontSize = 13.sp
                )
            }
            PanelKey(palette = palette, modifier = Modifier.weight(1.5f), onClick = onBackspace) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = null,
                    tint = palette.text
                )
            }
        }
    }
}
