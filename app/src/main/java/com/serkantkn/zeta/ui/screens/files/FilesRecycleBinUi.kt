package com.serkantkn.zeta.ui.screens.files

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.StorageVolumeInfo
import com.serkantkn.zeta.data.repository.RecycleBinEntry
import com.serkantkn.zeta.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.MetroRule
import com.serkantkn.zeta.ui.components.Win8Command
import com.serkantkn.zeta.ui.components.Win8MessageDialog
import com.serkantkn.zeta.ui.components.WpBarAction
import com.serkantkn.zeta.ui.components.WpBarMenuItem
import com.serkantkn.zeta.ui.components.ZuneDialogButton
import com.serkantkn.zeta.ui.components.ZuneFlipDialog
import com.serkantkn.zeta.ui.theme.ZuneDimens
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.screens.messaging.MessagingSheet
import com.serkantkn.zeta.ui.screens.messaging.SheetAction
import com.serkantkn.zeta.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.util.FileCategory
import com.serkantkn.zeta.util.categoryOf
import com.serkantkn.zeta.util.formatFileSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The recycle bin, on both layouts.
 *
 * The bin does the same things on the phone and the tablet — restore, delete permanently, empty —
 * and only looks like the device it is on: on the phone a page in the manner of an open folder,
 * its name the one large word at the top, with a sheet on a long press and the bar's menu; on the
 * tablet a place like any other in the content area, in columns, with Windows 8's bar. The disk
 * work is all the view model's ([FilesHubViewModel.restore] and the rest).
 */

/** What the screen remembers about the bin: whose sheet is open, which question is being asked. */
@Stable
internal class RecycleBinUiState {
    var sheetTarget by mutableStateOf<RecycleBinEntry?>(null)
    var confirmForever by mutableStateOf<List<RecycleBinEntry>?>(null)
    var confirmEmpty by mutableStateOf(false)
}

@Composable
internal fun rememberRecycleBinUiState(): RecycleBinUiState = remember { RecycleBinUiState() }

// ════════════════════════════════════════════════════════════
// THE WAY IN
// ════════════════════════════════════════════════════════════

/** The tablet's entry at the foot of the places, with how many things wait in the bin. */
@Composable
internal fun RecycleBinPaneEntry(count: Int, chosen: Boolean, ink: Color, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val source = remember { MutableInteractionSource() }
    val colour by animateColorAsState(
        targetValue = if (chosen) zuneColors.accentColor else ink.copy(alpha = 0.8f),
        animationSpec = tween(180),
        label = "files_bin_entry"
    )
    // The same rhythm as the sections above it: a small heading, then the entry in large type.
    Text(
        text = stringResource(R.string.files_bin_title),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.5.sp),
        color = zuneColors.textMuted,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp)
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource = source)
            .clickable(indication = null, interactionSource = source, onClick = onClick)
            .padding(vertical = 5.dp)
    ) {
        Icon(
            if (count > 0) Icons.Default.Delete else Icons.Default.DeleteSweep,
            contentDescription = null,
            tint = colour,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = pluralStringResource(R.plurals.files_item_count, count, count),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 24.sp,
                letterSpacing = (-0.3).sp
            ),
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The phone's row on the quick access page, under the shortcuts. */
@Composable
internal fun RecycleBinQuickRow(count: Int, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val source = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        MetroRule(title = stringResource(R.string.files_bin_title))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wpTilt(source)
                .clickable(interactionSource = source, indication = null, onClick = onClick)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(zuneColors.accentColor)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.files_bin_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = FileRowNameSize
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                Text(
                    text = pluralStringResource(R.plurals.files_item_count, count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.textMuted,
                    maxLines = 1
                )
            }
            Text(text = "›", style = MaterialTheme.typography.titleMedium, color = zuneColors.textDim)
        }
    }
}

// ════════════════════════════════════════════════════════════
// THE PHONE
// ════════════════════════════════════════════════════════════

/**
 * The bin as a phone page: its name as the large word at the top, the way an open folder's is
 * (a tap on it goes back), and the deleted things in rows that say where each one came from and
 * when it went. A tap opens a thing's sheet; once something is chosen, taps choose.
 */
@Composable
internal fun RecycleBinPhonePage(
    viewModel: FilesHubViewModel,
    ui: RecycleBinUiState,
    bottomPadding: Dp
) {
    val zuneColors = LocalZuneColors.current
    val entries by viewModel.recycleBin.collectAsState()
    val selection by viewModel.binSelection.collectAsState()
    val volumes by viewModel.volumes.collectAsState()
    val busyMessage by viewModel.busyMessage.collectAsState()
    val errorMessage by viewModel.binMessage.collectAsState()
    val entrance = rememberBinEntrance()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.files_bin_title),
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = PHONE_HEADING_SIZE,
                letterSpacing = (-2).sp,
                lineHeight = 66.sp
            ),
            color = if (zuneColors.isDark) Color.White else Color.Black,
            maxLines = 1,
            softWrap = false,
            // Windows Phone let a long heading run off the edge rather than shrinking it.
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 44.dp, bottom = 6.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { viewModel.closeRecycleBin() }
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
            contentPadding = PaddingValues(top = 4.dp, bottom = bottomPadding)
        ) {
            item(key = "note") {
                Column(modifier = Modifier.fillMaxWidth().w10mStaggeredAnimation({ entrance.value }, 0)) {
                    Text(
                        text = stringResource(R.string.files_bin_note),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                        color = zuneColors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    (busyMessage ?: errorMessage)?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (busyMessage != null) zuneColors.accentColor else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            }
            if (entries.isEmpty()) {
                item(key = "empty") { MetroEmpty(message = stringResource(R.string.files_bin_is_empty)) }
            }
            itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                BinPhoneRow(
                    entry = entry,
                    location = locationOf(entry, volumes),
                    selected = entry.id in selection,
                    selectionMode = selection.isNotEmpty(),
                    onClick = {
                        if (selection.isNotEmpty()) viewModel.toggleBinSelection(entry) else ui.sheetTarget = entry
                    },
                    onLongClick = { ui.sheetTarget = entry },
                    modifier = Modifier.w10mStaggeredAnimation({ entrance.value }, index + 1)
                )
            }
        }
        Spacer(modifier = Modifier.height(72.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BinPhoneRow(
    entry: RecycleBinEntry,
    location: String,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val source = remember { MutableInteractionSource() }
    val deleted = remember(entry.deletedAt) {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(entry.deletedAt))
    }
    val folderLabel = stringResource(R.string.files_folder)
    val size = if (entry.isDirectory) folderLabel else formatFileSize(entry.size)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .wpTilt(source)
            .combinedClickable(interactionSource = source, indication = null, onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.dp, if (selected) zuneColors.accentColor else zuneColors.textMuted)
                    .background(if (selected) zuneColors.accentColor else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(52.dp)
                .background(categoryColor(categoryOfEntry(entry), zuneColors.accentColor))
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = FileRowNameSize),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = location,
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$size • " + stringResource(R.string.files_bin_deleted_on, deleted),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** The phone's bar inside the bin: what is chosen gets restore and delete; the menu empties it. */
@Composable
internal fun recycleBinPhoneBar(
    viewModel: FilesHubViewModel,
    ui: RecycleBinUiState
): Pair<List<WpBarAction>, List<WpBarMenuItem>> {
    val entries by viewModel.recycleBin.collectAsState()
    val selection by viewModel.binSelection.collectAsState()
    val restore = stringResource(R.string.files_bin_restore)
    val forever = stringResource(R.string.files_bin_delete_forever)
    val selectAll = stringResource(R.string.files_select_all)
    val cancel = stringResource(R.string.common_cancel)
    val emptyBin = stringResource(R.string.files_bin_empty_action)
    if (selection.isNotEmpty()) {
        return listOf(
            WpBarAction(Icons.Default.Restore, restore) { viewModel.restore(viewModel.binSelectedEntries()) },
            WpBarAction(Icons.Default.DeleteForever, forever) { ui.confirmForever = viewModel.binSelectedEntries() }
        ) to listOf(
            WpBarMenuItem(selectAll) { viewModel.selectAllInBin() },
            WpBarMenuItem(cancel) { viewModel.clearBinSelection() }
        )
    }
    return emptyList<WpBarAction>() to buildList {
        if (entries.isNotEmpty()) {
            add(WpBarMenuItem(selectAll) { viewModel.selectAllInBin() })
            add(WpBarMenuItem(emptyBin) { ui.confirmEmpty = true })
        }
    }
}

// ════════════════════════════════════════════════════════════
// THE TABLET
// ════════════════════════════════════════════════════════════

/**
 * The bin as a tablet place: the round back arrow and its name in large light type, then the
 * deleted things in columns — name, where it was, when it went, how big. Holding or tapping a
 * row chooses it, as Windows 8 chose things it could not open; the bar shows what can be done.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RecycleBinTabletPlace(
    viewModel: FilesHubViewModel,
    ink: Color,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val entries by viewModel.recycleBin.collectAsState()
    val selection by viewModel.binSelection.collectAsState()
    val volumes by viewModel.volumes.collectAsState()
    val busyMessage by viewModel.busyMessage.collectAsState()
    val errorMessage by viewModel.binMessage.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()) }
    val folderLabel = stringResource(R.string.files_folder)
    val total = remember(entries) { entries.sumOf { it.size } }

    // Comes in from the right, as any place deeper than the last one does.
    val entrance = rememberBinEntrance()
    val shiftPx = with(LocalDensity.current) { 60.dp.toPx() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .border(2.dp, ink.copy(alpha = 0.8f), CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { viewModel.closeRecycleBin() }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.files_back),
                    tint = ink.copy(alpha = 0.9f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = stringResource(R.string.files_bin_title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 42.sp,
                    letterSpacing = (-1).sp
                ),
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = pluralStringResource(R.plurals.files_item_count, entries.size, entries.size) +
                    if (total > 0L) " · " + formatFileSize(total) else "",
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Text(
            text = stringResource(R.string.files_bin_note),
            style = MaterialTheme.typography.labelLarge,
            color = zuneColors.textMuted,
            modifier = Modifier.padding(start = 60.dp, top = 4.dp)
        )
        (busyMessage ?: errorMessage)?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = if (busyMessage != null) zuneColors.accentColor else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 60.dp, top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(start = 60.dp)
                .graphicsLayer {
                    alpha = entrance.value
                    translationX = (1f - entrance.value) * shiftPx
                }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Spacer(modifier = Modifier.width(BIN_GLYPH + 14.dp))
                BinHeading(stringResource(R.string.files_col_name), Modifier.weight(1f))
                BinHeading(stringResource(R.string.files_bin_col_location), Modifier.width(LOCATION_WIDTH))
                BinHeading(stringResource(R.string.files_bin_col_deleted), Modifier.width(DELETED_WIDTH))
                BinHeading(stringResource(R.string.files_col_size), Modifier.width(SIZE_WIDTH), alignEnd = true)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.1f)))

            if (entries.isEmpty()) {
                MetroEmpty(message = stringResource(R.string.files_bin_is_empty))
                return@Column
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomPadding)) {
                itemsIndexed(entries, key = { _, entry -> entry.id }) { _, entry ->
                    val selected = entry.id in selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(if (selected) zuneColors.accentColor.copy(alpha = 0.18f) else Color.Transparent)
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.toggleBinSelection(entry) },
                                onLongClick = { viewModel.toggleBinSelection(entry) }
                            )
                    ) {
                        val category = categoryOfEntry(entry)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(BIN_GLYPH).background(categoryColor(category, zuneColors.accentColor))
                        ) {
                            Icon(glyphOf(category), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        )
                        BinCell(locationOf(entry, volumes), Modifier.width(LOCATION_WIDTH))
                        BinCell(remember(entry.deletedAt) { dateFormat.format(Date(entry.deletedAt)) }, Modifier.width(DELETED_WIDTH))
                        BinCell(
                            if (entry.isDirectory) folderLabel.lowercase(Locale.getDefault()) else formatFileSize(entry.size),
                            Modifier.width(SIZE_WIDTH),
                            alignEnd = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BinHeading(text: String, modifier: Modifier, alignEnd: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        color = LocalZuneColors.current.textMuted,
        maxLines = 1,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun BinCell(text: String, modifier: Modifier, alignEnd: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = modifier.padding(end = 8.dp)
    )
}

/**
 * The tablet's bar inside the bin: restore and delete permanently for what is chosen on the left;
 * choosing and emptying on the right.
 */
@Composable
internal fun recycleBinTabletCommands(
    viewModel: FilesHubViewModel,
    ui: RecycleBinUiState
): Pair<List<Win8Command>, List<Win8Command>> {
    val entries by viewModel.recycleBin.collectAsState()
    val selection by viewModel.binSelection.collectAsState()
    val left = if (selection.isEmpty()) emptyList() else listOf(
        Win8Command(Icons.Default.Restore, stringResource(R.string.files_bin_restore)) {
            viewModel.restore(viewModel.binSelectedEntries())
        },
        Win8Command(Icons.Default.DeleteForever, stringResource(R.string.files_bin_delete_forever)) {
            ui.confirmForever = viewModel.binSelectedEntries()
        }
    )
    val right = buildList {
        if (selection.isNotEmpty()) {
            add(Win8Command(Icons.Default.Deselect, stringResource(R.string.files_bar_clear_selection)) { viewModel.clearBinSelection() })
        }
        add(Win8Command(Icons.Default.SelectAll, stringResource(R.string.files_bar_select_all), enabled = entries.isNotEmpty()) {
            viewModel.selectAllInBin()
        })
        add(Win8Command(Icons.Default.DeleteSweep, stringResource(R.string.files_bin_empty_action), enabled = entries.isNotEmpty()) {
            ui.confirmEmpty = true
        })
    }
    return left to right
}

// ════════════════════════════════════════════════════════════
// SHEETS, QUESTIONS AND BACK
// ════════════════════════════════════════════════════════════

/**
 * What floats over the bin: the phone's sheet for one thing, the questions asked before anything
 * is deleted for good (Windows 8's band on the tablet, the flip dialog on the phone), and Back,
 * which puts away a sheet, then a selection, then the bin itself.
 *
 * Composed after the hub's own Back handler, so it is asked first while the bin is open.
 */
@Composable
internal fun RecycleBinOverlays(
    viewModel: FilesHubViewModel,
    ui: RecycleBinUiState,
    isWideScreen: Boolean
) {
    val inBin by viewModel.inRecycleBin.collectAsState()
    val selection by viewModel.binSelection.collectAsState()
    val entries by viewModel.recycleBin.collectAsState()

    BackHandler(enabled = inBin) {
        when {
            ui.sheetTarget != null -> ui.sheetTarget = null
            selection.isNotEmpty() -> viewModel.clearBinSelection()
            else -> viewModel.closeRecycleBin()
        }
    }

    ui.sheetTarget?.takeIf { inBin }?.let { entry ->
        MessagingSheet(title = entry.name, onDismiss = { ui.sheetTarget = null }) {
            SheetAction(label = stringResource(R.string.files_bin_restore)) {
                viewModel.restore(listOf(entry))
                ui.sheetTarget = null
            }
            SheetAction(label = stringResource(R.string.files_select)) {
                viewModel.toggleBinSelection(entry)
                ui.sheetTarget = null
            }
            SheetAction(
                label = stringResource(R.string.files_bin_delete_forever),
                color = MaterialTheme.colorScheme.error
            ) {
                ui.confirmForever = listOf(entry)
                ui.sheetTarget = null
            }
        }
    }

    ui.confirmForever?.let { chosen ->
        val message = if (chosen.size == 1) {
            stringResource(R.string.files_bin_forever_confirm, chosen.single().name)
        } else {
            stringResource(R.string.files_bin_forever_many_confirm, chosen.size)
        }
        BinConfirm(
            isWideScreen = isWideScreen,
            title = stringResource(R.string.files_bin_delete_forever),
            message = message,
            onConfirm = {
                viewModel.deleteForever(chosen)
                ui.confirmForever = null
            },
            onDismiss = { ui.confirmForever = null }
        )
    }

    if (ui.confirmEmpty) {
        BinConfirm(
            isWideScreen = isWideScreen,
            title = stringResource(R.string.files_bin_empty_title),
            message = stringResource(R.string.files_bin_empty_confirm, entries.size),
            onConfirm = {
                viewModel.emptyRecycleBin()
                ui.confirmEmpty = false
            },
            onDismiss = { ui.confirmEmpty = false }
        )
    }
}

@Composable
private fun BinConfirm(
    isWideScreen: Boolean,
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (isWideScreen) {
        Win8MessageDialog(
            title = title,
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            destructive = true,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
        return
    }
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = title,
        confirmButton = {
            ZuneDialogButton(
                text = stringResource(R.string.files_delete_cap),
                onClick = { dismissWithAnim { onConfirm() } },
                borderColor = MaterialTheme.colorScheme.error
            )
        },
        dismissButton = {
            ZuneDialogButton(
                text = stringResource(R.string.common_cancel_cap),
                onClick = { dismissWithAnim { onDismiss() } },
                borderColor = zuneColors.textMuted
            )
        }
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}

// ════════════════════════════════════════════════════════════
// SMALL PIECES
// ════════════════════════════════════════════════════════════

/** Plays the bin's entrance once when it opens; at rest when animations are off. */
@Composable
private fun rememberBinEntrance(): Animatable<Float, AnimationVector1D> {
    val animationsOn = LocalAnimationsEnabled.current
    val entrance = remember { Animatable(if (animationsOn) 0f else 1f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(ENTRANCE_MILLIS, easing = FastOutSlowInEasing))
    }
    return entrance
}

private fun categoryOfEntry(entry: RecycleBinEntry): FileCategory =
    categoryOf(entry.extension, entry.isDirectory)

/**
 * Where a thing was deleted from, as a person would say it: the volume's name and the folders
 * under it ("Internal storage/DCIM/Camera") rather than "/storage/emulated/0/DCIM/Camera".
 */
private fun locationOf(entry: RecycleBinEntry, volumes: List<StorageVolumeInfo>): String {
    val parent = entry.originalParent
    val volume = volumes
        .filter { parent == it.root.absolutePath || parent.startsWith(it.root.absolutePath + "/") }
        .maxByOrNull { it.root.absolutePath.length }
        ?: return parent
    val relative = parent.removePrefix(volume.root.absolutePath).trim('/')
    return if (relative.isEmpty()) volume.label else "${volume.label}/$relative"
}

private val PHONE_HEADING_SIZE = 60.sp
private val BIN_GLYPH = 30.dp
private val LOCATION_WIDTH = 260.dp
private val DELETED_WIDTH = 170.dp
private val SIZE_WIDTH = 90.dp
private const val ENTRANCE_MILLIS = 300
