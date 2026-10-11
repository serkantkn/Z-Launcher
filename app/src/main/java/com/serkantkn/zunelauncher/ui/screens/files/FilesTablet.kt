package com.serkantkn.zunelauncher.ui.screens.files

import android.os.Environment
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import com.serkantkn.zunelauncher.util.FilesView
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.data.model.StorageVolumeInfo
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.FileCategory
import com.serkantkn.zunelauncher.util.FileSort
import com.serkantkn.zunelauncher.util.category
import com.serkantkn.zunelauncher.util.formatFileSize
import com.serkantkn.zunelauncher.util.matchesFileQuery
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The standard folders the tablet lists under "libraries", each in its kind's colour. */
private enum class FilesLibrary(val folder: String, val labelRes: Int, val category: FileCategory) {
    PICTURES("Pictures", R.string.files_cat_pictures, FileCategory.IMAGE),
    CAMERA("DCIM", R.string.files_cat_camera, FileCategory.IMAGE),
    MUSIC("Music", R.string.files_cat_music, FileCategory.AUDIO),
    VIDEOS("Movies", R.string.files_cat_videos, FileCategory.VIDEO),
    DOCUMENTS("Documents", R.string.files_cat_documents, FileCategory.DOCUMENT),
    DOWNLOADS("Download", R.string.files_cat_downloads, FileCategory.DOCUMENT)
}

/**
 * The files hub on a tablet, in the Metro manner of Windows 8's own apps rather than the phone's
 * pivot stretched across a wide screen.
 *
 * Down the left, as in the other tablet hubs, the places: this device's storage with how full
 * each one is, the libraries, and the cloud drives. On the right the place itself — a round
 * back arrow and its name in large light type (a tap on the name lists the folders above it),
 * the search box, and the folder's contents in columns that sort by a tap on their heading.
 * Moving into a folder brings it in from the right; going back brings the last one in from the
 * left.
 *
 * Everything that changes a file is the view model's, exactly as on the phone; this only lays it
 * out. The commands stay in the bar along the bottom, which [FilesHubScreen] draws over this.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilesTabletLayout(
    viewModel: FilesHubViewModel,
    filesView: FilesView,
    inCloud: Boolean,
    onInCloudChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    canGoBack: Boolean,
    permissionGate: @Composable () -> Unit,
    cloudPage: @Composable (PaddingValues) -> Unit,
    onOpenFile: (FileItemModel) -> Unit,
    onItemLongClick: (FileItemModel) -> Unit,
    onAddCloudAccount: () -> Unit,
    bottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    val hasPermission by viewModel.hasPermission.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val currentVolume by viewModel.currentVolume.collectAsState()
    val volumes by viewModel.volumes.collectAsState()
    val fileItems by viewModel.fileItems.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val deepResults by viewModel.deepResults.collectAsState()
    val cloudAccounts by viewModel.cloudAccounts.collectAsState()
    val activeCloudAccount by viewModel.activeCloudAccount.collectAsState()
    val cloudPath by viewModel.cloudPath.collectAsState()

    val primaryRoot = remember { Environment.getExternalStorageDirectory() }

    /** Opens a folder as a step the back arrow can return from (the trail is the view model's). */
    fun goTo(directory: File) {
        onInCloudChange(false)
        viewModel.visit(directory)
    }

    fun goToVolume(volume: StorageVolumeInfo) {
        onInCloudChange(false)
        viewModel.visitVolume(volume)
    }

    fun goToCloud(account: CloudAccount) {
        onInCloudChange(true)
        if (activeCloudAccount?.id != account.id) viewModel.openCloudAccount(account)
    }

    // ── Where the page is, for the entrance: deeper comes in from the right, back from the left ──
    val placeKey = if (inCloud) "cloud:" + cloudPath.joinToString("/") { it.id } else currentDirectory.absolutePath
    val entrance = remember { Animatable(1f) }
    var lastKey by remember { mutableStateOf(placeKey) }
    var fromRight by remember { mutableStateOf(true) }
    val animationsOn = LocalAnimationsEnabled.current
    LaunchedEffect(placeKey) {
        if (placeKey == lastKey) return@LaunchedEffect
        fromRight = placeKey.startsWith(lastKey) || placeKey.startsWith("cloud:") != lastKey.startsWith("cloud:")
        lastKey = placeKey
        if (!animationsOn) return@LaunchedEffect
        entrance.snapTo(0f)
        entrance.animateTo(1f, tween(ENTRANCE_MILLIS, easing = FastOutSlowInEasing))
    }
    val shiftPx = with(LocalDensity.current) { ENTRANCE_SHIFT.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val paneWidth = if (maxWidth < NARROW_FROM) PANE_WIDTH_NARROW else PANE_WIDTH

        Row(modifier = Modifier.fillMaxSize()) {
            // ── The places ──
            Column(
                modifier = Modifier
                    .width(paneWidth)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 48.dp, end = 16.dp, top = 40.dp, bottom = bottomPadding)
            ) {
                Text(
                    text = stringResource(R.string.hub_files),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = ink.copy(alpha = 0.9f),
                    maxLines = 1
                )

                PaneSection(stringResource(R.string.files_pane_device))
                volumes.forEach { volume ->
                    val here = !inCloud && currentVolume?.root?.absolutePath == volume.root.absolutePath &&
                        FilesLibrary.entries.none { isInside(currentDirectory, File(primaryRoot, it.folder)) }
                    VolumeEntry(volume = volume, chosen = here, ink = ink) { goToVolume(volume) }
                }

                PaneSection(stringResource(R.string.files_pane_libraries))
                FilesLibrary.entries.forEach { library ->
                    val folder = File(primaryRoot, library.folder)
                    PaneEntry(
                        label = stringResource(library.labelRes).lowercase(Locale.getDefault()),
                        chosen = !inCloud && isInside(currentDirectory, folder),
                        ink = ink,
                        marker = categoryColor(library.category, zuneColors.accentColor)
                    ) { goTo(folder) }
                }

                PaneSection(stringResource(R.string.files_tab_cloud))
                cloudAccounts.forEach { account ->
                    PaneEntry(
                        label = account.displayName.ifBlank { account.email },
                        chosen = inCloud && activeCloudAccount?.id == account.id,
                        ink = ink,
                        icon = Icons.Default.Cloud
                    ) { goToCloud(account) }
                }
                PaneEntry(
                    label = stringResource(R.string.files_pane_add_account),
                    chosen = false,
                    ink = ink.copy(alpha = 0.6f),
                    small = true
                ) {
                    onInCloudChange(true)
                    onAddCloudAccount()
                }
            }

            // ── The place itself ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 40.dp, end = 40.dp)
            ) {
                PlaceHeader(
                    viewModel = viewModel,
                    inCloud = inCloud,
                    canGoBack = canGoBack,
                    onBack = onBack,
                    onAncestor = { goTo(it) },
                    ink = ink
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .graphicsLayer {
                            val p = entrance.value
                            alpha = p
                            translationX = (1f - p) * shiftPx * if (fromRight) 1f else -1f
                        }
                ) {
                    when {
                        inCloud -> cloudPage(PaddingValues(bottom = bottomPadding))
                        !hasPermission -> permissionGate()
                        else -> {
                            val shown = deepResults ?: fileItems.filter {
                                searchQuery.isBlank() || matchesFileQuery(it.name, searchQuery)
                            }
                            val open: (FileItemModel) -> Unit = { item ->
                                when {
                                    viewModel.selection.value.isNotEmpty() -> viewModel.toggleSelection(item)
                                    item.isDirectory -> goTo(item.file)
                                    else -> onOpenFile(item)
                                }
                            }
                            if (filesView == FilesView.THUMBNAILS) {
                                ThumbnailsView(
                                    viewModel = viewModel,
                                    items = shown,
                                    bottomPadding = bottomPadding,
                                    onOpen = open,
                                    onLongClick = onItemLongClick
                                )
                            } else DetailsView(
                                viewModel = viewModel,
                                items = shown,
                                bottomPadding = bottomPadding,
                                showPathInsteadOfType = deepResults != null,
                                volumeRoot = currentVolume?.root,
                                ink = ink,
                                onOpen = open,
                                onLongClick = onItemLongClick
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
// THE PLACES
// ════════════════════════════════════════════════════════════

@Composable
private fun ColumnScope.PaneSection(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, letterSpacing = 0.5.sp),
        color = LocalZuneColors.current.textMuted,
        modifier = Modifier.padding(top = 26.dp, bottom = 4.dp)
    )
}

@Composable
private fun PaneEntry(
    label: String,
    chosen: Boolean,
    ink: Color,
    marker: Color? = null,
    icon: ImageVector? = null,
    small: Boolean = false,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val source = remember { MutableInteractionSource() }
    val colour by animateColorAsState(
        targetValue = if (chosen) zuneColors.accentColor else ink.copy(alpha = if (small) 1f else 0.8f),
        animationSpec = tween(180),
        label = "files_pane_entry"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource = source)
            .clickable(indication = null, interactionSource = source, onClick = onClick)
            .padding(vertical = 5.dp)
    ) {
        if (marker != null) {
            Box(modifier = Modifier.width(3.dp).height(22.dp).background(marker))
            Spacer(modifier = Modifier.width(10.dp))
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = if (small) 17.sp else 24.sp,
                letterSpacing = (-0.3).sp
            ),
            color = colour,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A volume: its name, and under it how full it is as a thin line and in words. */
@Composable
private fun VolumeEntry(volume: StorageVolumeInfo, chosen: Boolean, ink: Color, onClick: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        PaneEntry(
            label = volume.label.lowercase(Locale.getDefault()),
            chosen = chosen,
            ink = ink,
            onClick = onClick
        )
        val fraction = volume.fraction
        if (fraction != null) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(ink.copy(alpha = 0.12f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (fraction > 0.9f) Color(0xFFE51400) else zuneColors.accentColor)
                )
            }
            Text(
                text = stringResource(
                    R.string.files_free_of,
                    formatFileSize(volume.freeBytes),
                    formatFileSize(volume.totalBytes)
                ),
                style = MaterialTheme.typography.labelMedium,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// THE HEADER
// ════════════════════════════════════════════════════════════

/**
 * The round back arrow, the place's name — a tap on it lists the folders above, Windows 8's
 * header menu — and on the right how many things are here and the search box.
 */
@Composable
private fun PlaceHeader(
    viewModel: FilesHubViewModel,
    inCloud: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onAncestor: (File) -> Unit,
    ink: Color
) {
    val zuneColors = LocalZuneColors.current
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val currentVolume by viewModel.currentVolume.collectAsState()
    val fileItems by viewModel.fileItems.collectAsState()
    val deepResults by viewModel.deepResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val busyMessage by viewModel.busyMessage.collectAsState()
    val pasteProgress by viewModel.pasteProgress.collectAsState()
    val transfer by viewModel.transfer.collectAsState()
    val cloudPath by viewModel.cloudPath.collectAsState()
    val cloudItems by viewModel.cloudItems.collectAsState()

    val volumeRoot = currentVolume?.root
    val atRoot = volumeRoot?.absolutePath == currentDirectory.absolutePath
    val primaryRoot = remember { Environment.getExternalStorageDirectory() }
    val library = FilesLibrary.entries.firstOrNull {
        File(primaryRoot, it.folder).absolutePath == currentDirectory.absolutePath
    }
    val title = when {
        inCloud -> cloudPath.lastOrNull()?.title ?: stringResource(R.string.files_tab_cloud)
        deepResults != null -> stringResource(R.string.files_search_results)
        library != null -> stringResource(library.labelRes).lowercase(Locale.getDefault())
        atRoot -> currentVolume?.label?.lowercase(Locale.getDefault()) ?: stringResource(R.string.files_internal_storage)
        else -> currentDirectory.name
    }
    // The folders above this one, nearest first, down to the top of the volume.
    val ancestors = remember(currentDirectory, volumeRoot) {
        if (volumeRoot == null) emptyList() else buildList {
            var parent = currentDirectory.parentFile
            while (parent != null && parent.absolutePath.startsWith(volumeRoot.absolutePath)) {
                add(parent)
                if (parent.absolutePath == volumeRoot.absolutePath) break
                parent = parent.parentFile
            }
        }
    }
    var menuOpen by remember { mutableStateOf(false) }
    val count = if (inCloud) cloudItems.size else deepResults?.size ?: fileItems.size

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .border(2.dp, ink.copy(alpha = if (canGoBack) 0.8f else 0.25f), androidx.compose.foundation.shape.CircleShape)
                    .clickable(
                        enabled = canGoBack,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onBack
                    )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.files_back),
                    tint = ink.copy(alpha = if (canGoBack) 0.9f else 0.3f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        enabled = !inCloud && ancestors.isNotEmpty(),
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { menuOpen = true }
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 42.sp,
                            letterSpacing = (-1).sp
                        ),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!inCloud && ancestors.isNotEmpty()) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.files_path_menu),
                            tint = ink.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 6.dp, top = 8.dp).size(28.dp)
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFF2F2F2),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    ancestors.forEach { folder ->
                        val label = if (folder.absolutePath == volumeRoot?.absolutePath) {
                            currentVolume?.label?.lowercase(Locale.getDefault()).orEmpty()
                        } else {
                            folder.name
                        }
                        DropdownMenuItem(
                            text = { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Light, color = ink) },
                            onClick = {
                                menuOpen = false
                                onAncestor(folder)
                            }
                        )
                    }
                }
            }
            Text(
                text = pluralStringResource(R.plurals.files_item_count, count, count),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (!inCloud) {
                SearchBox(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    ink = ink
                )
            } else {
                SearchBox(
                    query = searchQuery,
                    onQueryChange = {
                        viewModel.updateSearchQuery(it)
                        viewModel.searchCloud(it)
                    },
                    ink = ink
                )
            }
        }

        // ── What is going on: a copy, a search, an upload ──
        pasteProgress?.let { progress ->
            TabletPasteProgress(
                progress = progress,
                onPauseToggle = viewModel::togglePausePaste,
                onCancel = viewModel::cancelPaste,
                ink = ink,
                modifier = Modifier.padding(start = 60.dp, top = 4.dp)
            )
        }
        busyMessage?.takeIf { pasteProgress == null }?.let { message ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 60.dp, top = 4.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = stringResource(R.string.common_cancel),
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.textMuted,
                    modifier = Modifier
                        .clickable { viewModel.cancelPaste() }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        if (isSearching) {
            Text(
                text = stringResource(R.string.files_searching),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.accentColor,
                modifier = Modifier.padding(start = 60.dp, top = 4.dp)
            )
        }
        if (!inCloud && searchQuery.isNotBlank() && deepResults == null) {
            Text(
                text = stringResource(R.string.files_search_deep),
                style = MaterialTheme.typography.labelLarge,
                color = zuneColors.accentLink,
                modifier = Modifier
                    .padding(start = 60.dp, top = 4.dp)
                    .clickable { viewModel.searchDeep() }
            )
        }
        transfer?.let { active ->
            TransferBar(
                transfer = active,
                onCancel = { viewModel.cancelTransfer() },
                modifier = Modifier.padding(start = 60.dp, top = 6.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

/** Windows 8.1's search box: a plain rectangle, a magnifier at its end. */
@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit, ink: Color) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(SEARCH_WIDTH)
            .height(36.dp)
            .border(1.5.dp, ink.copy(alpha = 0.35f))
            .padding(start = 10.dp, end = 6.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.files_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(color = ink, fontSize = 15.sp),
                cursorBrush = SolidColor(zuneColors.accentColor),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Icon(Icons.Default.Search, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

// ════════════════════════════════════════════════════════════
// DETAILS
// ════════════════════════════════════════════════════════════

/**
 * The contents as Windows 8's apps listed them in detail: a column of names, and beside them the
 * date, the kind and the size, each under a heading in plain words. A tap on a heading sorts by
 * it; another tap turns the order round.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DetailsView(
    viewModel: FilesHubViewModel,
    items: List<FileItemModel>,
    bottomPadding: Dp,
    showPathInsteadOfType: Boolean,
    volumeRoot: File?,
    ink: Color,
    onOpen: (FileItemModel) -> Unit,
    onLongClick: (FileItemModel) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val sort by viewModel.sort.collectAsState()
    val ascending by viewModel.sortAscending.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val listState = rememberLazyListState()
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()) }
    val folderLabel = stringResource(R.string.files_folder)
    val plainFile = stringResource(R.string.files_type_plain)
    val fileOfType = stringResource(R.string.files_type_file, "%s")

    fun sortBy(column: FileSort) {
        if (sort == column) viewModel.toggleSortDirection() else viewModel.setSort(column)
    }

    Column(modifier = Modifier.fillMaxSize().padding(start = 60.dp)) {
        // ── The headings ──
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(modifier = Modifier.width(GLYPH_SIZE + 14.dp))
            ColumnHeading(stringResource(R.string.files_col_name), sort == FileSort.NAME, ascending, Modifier.weight(1f)) { sortBy(FileSort.NAME) }
            ColumnHeading(stringResource(R.string.files_col_modified), sort == FileSort.DATE, ascending, Modifier.width(DATE_WIDTH)) { sortBy(FileSort.DATE) }
            ColumnHeading(stringResource(R.string.files_col_type), sort == FileSort.TYPE, ascending, Modifier.width(TYPE_WIDTH)) { sortBy(FileSort.TYPE) }
            ColumnHeading(stringResource(R.string.files_col_size), sort == FileSort.SIZE, ascending, Modifier.width(SIZE_WIDTH), alignEnd = true) { sortBy(FileSort.SIZE) }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ink.copy(alpha = 0.1f)))

        if (items.isEmpty()) {
            MetroEmpty(
                message = errorMessage ?: stringResource(
                    if (searchQuery.isNotBlank()) R.string.files_not_found else R.string.files_empty_folder
                ),
                actionLabel = null,
                onAction = {}
            )
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding)
        ) {
            items(items, key = { it.file.absolutePath }) { item ->
                val selected = item.file.absolutePath in selection
                val category = item.category()
                val source = remember { MutableInteractionSource() }
                val kind = when {
                    showPathInsteadOfType -> item.file.parentFile?.absolutePath
                        ?.removePrefix(volumeRoot?.absolutePath.orEmpty())?.ifBlank { "/" }.orEmpty()
                    item.isDirectory -> folderLabel
                    item.extension.isBlank() -> plainFile
                    // Locale.ROOT: in Turkish "zip".uppercase() is "ZİP".
                    else -> fileOfType.replace("%s", item.extension.uppercase(Locale.ROOT))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .background(if (selected) zuneColors.accentColor.copy(alpha = 0.18f) else Color.Transparent)
                        .combinedClickable(
                            interactionSource = source,
                            indication = null,
                            onClick = { onOpen(item) },
                            onLongClick = { onLongClick(item) }
                        )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(GLYPH_SIZE)
                            .background(categoryColor(category, zuneColors.accentColor))
                    ) {
                        Icon(glyphOf(category), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    DetailText(remember(item.lastModified) { dateFormat.format(Date(item.lastModified)) }, Modifier.width(DATE_WIDTH))
                    DetailText(kind, Modifier.width(TYPE_WIDTH))
                    DetailText(
                        if (item.isDirectory) "" else formatFileSize(item.size),
                        Modifier.width(SIZE_WIDTH),
                        alignEnd = true
                    )
                }
            }
        }
    }
}

/**
 * The contents as pictures, the way Windows 8's apps laid out a folder: tiles in columns that
 * fill the height of the screen and run off to the right.
 */
@Composable
private fun ThumbnailsView(
    viewModel: FilesHubViewModel,
    items: List<FileItemModel>,
    bottomPadding: Dp,
    onOpen: (FileItemModel) -> Unit,
    onLongClick: (FileItemModel) -> Unit
) {
    val selection by viewModel.selection.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    if (items.isEmpty()) {
        Box(modifier = Modifier.padding(start = 60.dp)) {
            MetroEmpty(
                message = errorMessage ?: stringResource(
                    if (searchQuery.isNotBlank()) R.string.files_not_found else R.string.files_empty_folder
                ),
                actionLabel = null,
                onAction = {}
            )
        }
        return
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(start = 60.dp, bottom = bottomPadding)) {
        val rows = ((maxHeight + TILE_GAP) / (TILE_SIZE + TILE_CAPTION + TILE_GAP)).toInt().coerceAtLeast(1)
        // Exactly as tall as its rows: a grid given the whole height shares it out between them,
        // and each tile then sat at the top of a cell with a gap under it.
        val gridHeight = (TILE_SIZE + TILE_CAPTION) * rows + TILE_GAP * (rows - 1) + 6.dp
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rows),
            modifier = Modifier.fillMaxWidth().height(gridHeight),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(TILE_GAP),
            contentPadding = PaddingValues(top = 6.dp, end = 40.dp)
        ) {
            items(items, key = { it.file.absolutePath }) { item ->
                FileTile(
                    item = item,
                    selected = item.file.absolutePath in selection,
                    tileSize = TILE_SIZE,
                    onClick = { onOpen(item) },
                    onLongClick = { onLongClick(item) }
                )
            }
        }
    }
}

@Composable
private fun ColumnHeading(
    text: String,
    active: Boolean,
    ascending: Boolean,
    modifier: Modifier,
    alignEnd: Boolean = false,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Text(
        text = if (active) text + if (ascending) "  ▲" else "  ▼" else text,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        color = if (active) zuneColors.accentColor else zuneColors.textMuted,
        maxLines = 1,
        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
        modifier = modifier
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun DetailText(text: String, modifier: Modifier, alignEnd: Boolean = false) {
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

/** The glyph a kind of file wears in its coloured square. */
internal fun glyphOf(category: FileCategory): ImageVector = when (category) {
    FileCategory.FOLDER -> Icons.Default.Folder
    FileCategory.IMAGE -> Icons.Default.Image
    FileCategory.VIDEO -> Icons.Default.Movie
    FileCategory.AUDIO -> Icons.Default.MusicNote
    FileCategory.DOCUMENT -> Icons.Default.Description
    FileCategory.ARCHIVE -> Icons.Default.FolderZip
    FileCategory.APP -> Icons.Default.Android
    FileCategory.OTHER -> Icons.Default.InsertDriveFile
}

/** Whether [file] is [folder] or somewhere inside it. */
private fun isInside(file: File, folder: File): Boolean {
    val path = file.absolutePath
    val base = folder.absolutePath
    return path == base || path.startsWith("$base/")
}

private val PANE_WIDTH = 320.dp
private val PANE_WIDTH_NARROW = 250.dp
private val NARROW_FROM = 1000.dp
private val SEARCH_WIDTH = 260.dp
private val ROW_HEIGHT = 48.dp
private val GLYPH_SIZE = 30.dp
private val DATE_WIDTH = 170.dp
private val TYPE_WIDTH = 150.dp
private val SIZE_WIDTH = 90.dp
private val ENTRANCE_SHIFT = 60.dp
private val TILE_SIZE = 150.dp
private val TILE_CAPTION = 48.dp
private val TILE_GAP = 14.dp
private const val ENTRANCE_MILLIS = 300

// ════════════════════════════════════════════════════════════
// DETAILS
// ════════════════════════════════════════════════════════════

/**
 * A file's details on the tablet: a panel that slides in from the right edge, as Windows 8's
 * settings did, over the folder dimmed behind it — what the file holds, then the facts, then
 * open and share at its foot. A tap on the dimmed folder, or Back, puts it away.
 */
@Composable
internal fun FileDetailsPanel(
    item: FileItemModel?,
    measure: suspend (FileItemModel) -> Pair<Long, Int>,
    onOpen: (FileItemModel) -> Unit,
    onShare: (FileItemModel) -> Unit,
    onClose: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    // The panel keeps the last file while it slides away, so it does not empty as it goes.
    var shown by remember { mutableStateOf(item) }
    if (item != null) shown = item
    val progress = remember { Animatable(0f) }
    LaunchedEffect(item) {
        progress.animateTo(if (item != null) 1f else 0f, tween(PANEL_MILLIS, easing = FastOutSlowInEasing))
    }
    val current = shown ?: return
    if (item == null && progress.value == 0f) return

    var measured by remember(current.file.absolutePath) { mutableStateOf<Pair<Long, Int>?>(null) }
    LaunchedEffect(current.file.absolutePath) { measured = measure(current) }
    val facts = fileFacts(current, measured)
    val panelPx = with(LocalDensity.current) { PANEL_WIDTH.toPx() }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClose)
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(PANEL_WIDTH)
                .fillMaxHeight()
                .graphicsLayer { translationX = (1f - progress.value) * panelPx }
                .background(if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFF7F7F7))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .verticalScroll(rememberScrollState())
                .padding(start = 28.dp, end = 28.dp, top = 40.dp, bottom = navBottom + 24.dp)
        ) {
            Text(
                text = stringResource(R.string.files_details).lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = 1.sp),
                color = ink.copy(alpha = 0.9f)
            )
            Text(
                text = current.name,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 26.sp),
                color = ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
            )
            FilePreview(current, modifier = Modifier.fillMaxWidth().height(PREVIEW_HEIGHT))
            facts.forEach { (label, value) ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = zuneColors.textMuted,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = ink
                )
            }
            Row(modifier = Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (!current.isDirectory) {
                    PanelLink(stringResource(R.string.files_open).lowercase(Locale.getDefault())) { onOpen(current) }
                    PanelLink(stringResource(R.string.files_share).lowercase(Locale.getDefault())) { onShare(current) }
                } else {
                    PanelLink(stringResource(R.string.files_open).lowercase(Locale.getDefault())) { onOpen(current) }
                }
            }
        }
    }
}

@Composable
private fun PanelLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
        color = LocalZuneColors.current.accentLink,
        modifier = Modifier
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick)
            .padding(vertical = 6.dp)
    )
}

private val PANEL_WIDTH = 400.dp
private val PREVIEW_HEIGHT = 240.dp
private const val PANEL_MILLIS = 260

