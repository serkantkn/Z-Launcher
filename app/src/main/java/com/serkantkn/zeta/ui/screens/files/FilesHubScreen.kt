package com.serkantkn.zeta.ui.screens.files

import com.serkantkn.zeta.ui.animation.pageHingeAway
import com.serkantkn.zeta.ui.animation.pageHingeIn
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FolderOpen
import com.serkantkn.zeta.ui.components.Win8TextDialog
import com.serkantkn.zeta.ui.components.Win8MessageDialog
import com.serkantkn.zeta.ui.components.Win8MenuItem
import com.serkantkn.zeta.ui.components.Win8CommandBar
import com.serkantkn.zeta.ui.components.Win8Command
import com.serkantkn.zeta.util.FilesView
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.FileItemModel
import com.serkantkn.zeta.data.repository.CloudDocument
import com.serkantkn.zeta.ui.animation.HingeAnimation
import com.serkantkn.zeta.ui.animation.ZuneTitleZoomOverlay
import com.serkantkn.zeta.ui.animation.rememberZuneTitleZoomState
import com.serkantkn.zeta.ui.animation.rememberZuneZoomAnchor
import com.serkantkn.zeta.ui.animation.ZuneZoomAnchor
import com.serkantkn.zeta.ui.animation.zuneZoomAnchor
import com.serkantkn.zeta.ui.components.ZuneDialogButton
import com.serkantkn.zeta.ui.components.ZuneFlipDialog
import com.serkantkn.zeta.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zeta.util.FileViewer
import com.serkantkn.zeta.util.viewerFor
import com.serkantkn.zeta.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.MetroSubScreen
import com.serkantkn.zeta.ui.components.MetroTextField
import com.serkantkn.zeta.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zeta.ui.components.WpBarAction
import com.serkantkn.zeta.ui.components.WpBarMenuItem
import com.serkantkn.zeta.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zeta.ui.components.ZuneLoopingPager
import com.serkantkn.zeta.ui.components.ZunePermissionRequest
import com.serkantkn.zeta.ui.components.ZunePermissionRequestStyle
import com.serkantkn.zeta.ui.components.ZunePivotTabs
import com.serkantkn.zeta.ui.components.ZuneSearchBar
import com.serkantkn.zeta.ui.components.ZuneWideHubTitle
import com.serkantkn.zeta.ui.components.rememberLoopingPagerState
import com.serkantkn.zeta.ui.screens.messaging.MessagingSheet
import com.serkantkn.zeta.ui.screens.messaging.SheetAction
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens
import com.serkantkn.zeta.util.FileSort
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import com.serkantkn.zeta.ui.animation.rememberHingeSpec

/**
 * The Files hub.
 *
 * Opening a folder is the Zune HD move: everything on the page fades, the folder's own name lifts
 * off the list, comes toward you, and lands as the one large word at the top of the page it opened
 * — so the heading of what you are looking at is literally the thing you touched to get here.
 * Inside a folder that word is the whole heading: no hub name, no pivot. Touching it goes back up,
 * and the word jumps back down onto the row it came from as the list behind it returns.
 *
 * At the top of a volume the hub is its usual self: the pivot, with the volume's name as its first
 * word.
 */
@Composable
fun FilesHubScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilesHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val isWideScreen = LocalIsWideScreen.current
    val scope = rememberCoroutineScope()

    val hasPermission by viewModel.hasPermission.collectAsState()
    val currentDirectory by viewModel.currentDirectory.collectAsState()
    val currentVolume by viewModel.currentVolume.collectAsState()
    val volumes by viewModel.volumes.collectAsState()
    val fileItems by viewModel.fileItems.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val deepResults by viewModel.deepResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val busyMessage by viewModel.busyMessage.collectAsState()
    val pasteProgress by viewModel.pasteProgress.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()

    val cloudDocuments by viewModel.cloudDocuments.collectAsState()
    val cloudApps by viewModel.cloudApps.collectAsState()
    val cloudMessage by viewModel.cloudMessage.collectAsState()
    val cloudSaveRequest by viewModel.cloudSaveRequest.collectAsState()
    val transfer by viewModel.transfer.collectAsState()
    val inRecycleBin by viewModel.inRecycleBin.collectAsState()
    val recycleBinEntries by viewModel.recycleBin.collectAsState()
    val binUi = rememberRecycleBinUiState()

    // ── The flights ────────────────────────────────────────────────────────
    val localZoom = rememberZuneTitleZoomState()
    val animationsEnabled = LocalAnimationsEnabled.current
    // Where a folder's name lands: the heading of the folder page. It is measured even while the
    // pivot is showing, by a silent copy of the heading, so the first jump knows where to go.
    val headingAnchor = rememberZuneZoomAnchor()
    localZoom.bindTitle(headingAnchor, FOLDER_HEADING_SIZE)
    // Where each row's name sits, by path, so a word coming back down knows where its row is.
    val rowAnchors = remember { mutableMapOf<String, ZuneZoomAnchor>() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val dragState = rememberFilesDragState(viewModel)
    val filesViewSetting by viewModel.view.collectAsState()
    // Nothing chosen yet: the tablet shows pictures, the phone its list — the same two views on
    // both, only the starting one differs.
    // Windows 8's folder of tiles on the phone as on the tablet; the list is a choice in the menu.
    val filesView = filesViewSetting ?: FilesView.THUMBNAILS

    /** Scrolls whichever of the two views is showing; slot 0 is the crumbs above the files. */
    suspend fun scrollBrowseTo(index: Int) {
        if (filesView == FilesView.THUMBNAILS) gridState.scrollToItem(index) else listState.scrollToItem(index)
    }
    // Everything but the word in flight: gone while it jumps, back once it has landed.
    val contentAlpha = remember { Animatable(1f) }
    var transitioning by remember { mutableStateOf(false) }

    // The list settles in behind the word as it lands.
    val listEntry = remember { Animatable(1f) }
    LaunchedEffect(currentDirectory) {
        // A jump runs its own entrance; this is for the breadcrumb, the categories, quick access.
        if (transitioning) return@LaunchedEffect
        listEntry.snapTo(0f)
        listEntry.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
                // A cloud app installed while the hub was away belongs in the cloud page's line.
                viewModel.refreshCloudApps()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { viewModel.refreshPermissionState() }
    )

    // The cloud is the phone's own document picker: it lists Drive, OneDrive, Dropbox or whatever
    // else is installed, so there is nothing to sign in to here.
    val cloudPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.onCloudPicked(uris) }

    fun pickFromCloud() {
        runCatching { cloudPicker.launch(arrayOf("*/*")) }.onFailure { viewModel.reportNoPicker() }
    }

    // Saving goes the other way through the same picker: it asks where, makes an empty document
    // there, and the view model writes the file into it.
    val cloudSaver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onCloudSaveTarget(result.data?.data?.takeIf { result.resultCode == android.app.Activity.RESULT_OK })
    }
    LaunchedEffect(cloudSaveRequest) {
        if (cloudSaveRequest == null) return@LaunchedEffect
        val request = viewModel.takeCloudSaveRequest() ?: return@LaunchedEffect
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = request.mimeType
            putExtra(Intent.EXTRA_TITLE, request.file.name)
        }
        runCatching { cloudSaver.launch(intent) }.onFailure {
            viewModel.onCloudSaveTarget(null)
            viewModel.reportNoPicker()
        }
    }

    // ── Screen state ───────────────────────────────────────────────────────
    var isSearchOpen by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<FileItemModel?>(null) }
    // A document from the cloud held down: the phone's sheet, or on the tablet the one chosen,
    // whose commands the bar then shows, as for a file on the device.
    var cloudSheetTarget by remember { mutableStateOf<CloudDocument?>(null) }
    var cloudSelected by remember { mutableStateOf<CloudDocument?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var textPrompt by remember { mutableStateOf<TextPrompt?>(null) }
    var detailsTarget by remember { mutableStateOf<FileItemModel?>(null) }
    var viewerTarget by remember { mutableStateOf<FileItemModel?>(null) }
    var deleteTarget by remember { mutableStateOf<FileItemModel?>(null) }
    var deleteSelectionPending by remember { mutableStateOf(false) }
    val hingeSpec = rememberHingeSpec()
    val subScreenHinge = remember { Animatable(0f) }

    fun openSubScreen() {
        scope.launch {
            subScreenHinge.animateTo(1f, hingeSpec)
        }
    }

    fun closeSubScreen(onClosed: () -> Unit) {
        scope.launch {
            subScreenHinge.animateTo(0f, hingeSpec)
            onClosed()
        }
    }

    val browsingLabel = when {
        deepResults != null -> stringResource(R.string.files_search_results)
        currentDirectory.absolutePath == currentVolume?.root?.absolutePath ->
            currentVolume?.label ?: stringResource(R.string.files_internal_storage)
        else -> currentDirectory.name
    }

    val tabs = listOf(
        browsingLabel,
        stringResource(R.string.files_tab_categories),
        stringResource(R.string.files_tab_quick),
        stringResource(R.string.files_tab_cloud)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    // The tablet has no pivot: the cloud is a place in its left column, and its back arrow walks
    // back through the places visited rather than only up the folder tree.
    var tabletInCloud by remember { mutableStateOf(false) }
    val canGoBack by viewModel.canGoBack.collectAsState()

    /** The tablet's step back; false when there is nowhere left to go but out of the hub. */
    fun tabletBack(): Boolean {
        if (tabletInCloud) {
            tabletInCloud = false
            cloudSelected = null
            return true
        }
        return viewModel.goBack()
    }

    // Inside a folder the page is the folder's: one large word and its list. At the top of a
    // volume it is the pivot.
    val volumeRootPath = currentVolume?.root?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath
    val inFolder = hasPermission && currentDirectory.absolutePath != volumeRootPath

    /**
     * Opens a folder. The page fades out while the folder's name jumps from its row up to the
     * heading; the folder's own list comes in behind the word once it has landed.
     */
    fun enterFolder(item: FileItemModel, anchor: ZuneZoomAnchor) {
        if (transitioning) return
        val from = anchor.bounds
        if (from == null || headingAnchor.bounds == null || !animationsEnabled) {
            viewModel.visit(item.file)
            return
        }
        scope.launch {
            transitioning = true
            val fade = launch { contentAlpha.animateTo(0f, tween(FADE_MILLIS, easing = FastOutSlowInEasing)) }
            val flight = launch { localZoom.flyToTitle(item.name, from, FileRowNameSize) }
            // The old page is gone before the new one is read, so nothing swaps in mid-fade.
            fade.join()
            viewModel.visit(item.file)
            listEntry.snapTo(0f)
            runCatching { scrollBrowseTo(0) }
            // The list starts in while the word is still settling, so the two arrive together
            // rather than the page waiting on a word that is already nearly there.
            delay(LIST_LEAD_MILLIS)
            launch { contentAlpha.animateTo(1f, tween(FADE_MILLIS)) }
            launch { listEntry.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
            flight.join()
            transitioning = false
        }
    }

    /**
     * Goes up one folder, the jump played backwards: the list fades, the heading drops down onto
     * the row it came from in the folder above, and that folder's list returns around it.
     *
     * False when there is no folder to leave, which is when the back key leaves the hub.
     */
    fun leaveFolder(): Boolean {
        val target = viewModel.backTarget() ?: return false
        if (transitioning) return true
        val leaving = currentDirectory
        val parent = viewModel.parentOfCurrent()
        val start = headingAnchor.bounds
        if (!animationsEnabled) {
            viewModel.goBack()
            return true
        }
        // Back to somewhere other than the folder above — the folder a library was opened
        // from, the other volume — has no row for the word to land on; the page fades over.
        if (!inFolder || parent == null || start == null || target.absolutePath != parent.absolutePath) {
            scope.launch {
                transitioning = true
                contentAlpha.animateTo(0f, tween(FADE_MILLIS, easing = FastOutSlowInEasing))
                viewModel.goBack()
                listEntry.snapTo(0f)
                runCatching { scrollBrowseTo(0) }
                launch { contentAlpha.animateTo(1f, tween(FADE_MILLIS)) }
                listEntry.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
                transitioning = false
            }
            return true
        }
        scope.launch {
            transitioning = true
            // The folder above is read while this one's list is still fading, so the heading
            // never has to stand over an empty page waiting for the storage.
            val above = async { viewModel.readDirectory(parent) }
            contentAlpha.animateTo(0f, tween(FADE_MILLIS, easing = FastOutSlowInEasing))
            val items = above.await()
            listEntry.snapTo(1f)
            viewModel.showDirectory(parent, items)
            viewModel.consumeBackStop(parent)
            // The row the word lands on is scrolled into view, unseen, and measured there.
            val path = leaving.absolutePath
            val index = items.indexOfFirst { it.file.absolutePath == path }
            if (index >= 0) {
                // The crumbs sit in the list's first slot, above the rows.
                runCatching { scrollBrowseTo(index + 1) }
                withFrameNanos { }
                withFrameNanos { }
            }
            val to = rowAnchors[path]?.bounds
            if (to != null) localZoom.flyToRowBounds(leaving.name, to, FileRowNameSize)
            transitioning = false
            contentAlpha.animateTo(1f, tween(FADE_MILLIS))
        }
        return true
    }

    /** A file: shown here when the hub knows how, handed to another app when it does not. */
    fun openItem(item: FileItemModel) {
        val viewer = viewerFor(item.extension)
        if (viewer == FileViewer.VIDEO) {
            // A film goes to the launcher's own player, which can also keep it in a corner.
            com.serkantkn.zeta.ui.screens.video.VideoPlayer.open(context, item.file, withFolder = true)
        } else if (viewer == FileViewer.NONE) {
            viewModel.openFile(context, item.file)
        } else {
            viewerTarget = item
            openSubScreen()
        }
    }

    BackHandler {
        when {
            deleteTarget != null -> deleteTarget = null
            deleteSelectionPending -> deleteSelectionPending = false
            cloudSelected != null -> cloudSelected = null
            viewerTarget != null -> closeSubScreen { viewerTarget = null }
            detailsTarget != null -> if (isWideScreen) detailsTarget = null else closeSubScreen { detailsTarget = null }
            textPrompt != null -> closeSubScreen { textPrompt = null }
            sheetTarget != null -> sheetTarget = null
            cloudSheetTarget != null -> cloudSheetTarget = null
            showSortSheet -> showSortSheet = false
            showVolumeSheet -> showVolumeSheet = false
            selection.isNotEmpty() -> viewModel.clearSelection()
            deepResults != null -> viewModel.clearDeepSearch()
            isSearchOpen -> { isSearchOpen = false; viewModel.updateSearchQuery("") }
            isWideScreen && searchQuery.isNotBlank() -> viewModel.updateSearchQuery("")
            isWideScreen -> if (!tabletBack()) onClose()
            !leaveFolder() -> onClose()
        }
    }

    val listBottomPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
    val pagePadding = PaddingValues(top = 4.dp, bottom = listBottomPadding)

    // ── The pages ──────────────────────────────────────────────────────────
    @Composable
    fun BrowsePage() {
        if (!hasPermission) {
            StoragePermissionGate(context, permissionLauncher)
            return
        }
        val shown = deepResults ?: fileItems.filter {
            searchQuery.isBlank() ||
                com.serkantkn.zeta.util.matchesFileQuery(it.name, searchQuery)
        }

        @Composable
        fun BrowseHeader() {
            Column(modifier = Modifier.fillMaxWidth()) {
                val crumbs = breadcrumbsOf(currentDirectory, currentVolume) { viewModel.visit(it) }
                // At the top of a volume the path is just the volume, which the title already says.
                if (crumbs.size > 1) FilesBreadcrumb(crumbs = crumbs, folders = crumbFoldersOf(currentDirectory, currentVolume))
                val volume = currentVolume
                if (volume != null && deepResults == null && currentDirectory.absolutePath == volume.root.absolutePath) {
                    StorageBar(
                        volume = volume,
                        showLabel = false,
                        onClick = if (volumes.size > 1) ({ showVolumeSheet = true }) else null
                    )
                }
                pasteProgress?.let { progress ->
                    PhonePasteProgress(progress, onPauseToggle = viewModel::togglePausePaste, onCancel = viewModel::cancelPaste)
                }
                if (busyMessage != null && pasteProgress == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = busyMessage.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = zuneColors.accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(vertical = 6.dp)
                        )
                        // A long copy can be stopped; what was finished before stays.
                        Text(
                            text = stringResource(R.string.common_cancel),
                            style = MaterialTheme.typography.labelLarge,
                            color = zuneColors.textMuted,
                            modifier = Modifier
                                .clickable { viewModel.cancelPaste() }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }
                if (isSearching) {
                    Text(
                        text = stringResource(R.string.files_searching),
                        style = MaterialTheme.typography.labelLarge,
                        color = zuneColors.accentColor,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        @Composable
        fun BrowseEmpty() {
            MetroEmpty(
                message = errorMessage ?: stringResource(
                    when {
                        deepResults != null -> R.string.files_not_found
                        searchQuery.isNotBlank() -> R.string.files_not_found
                        else -> R.string.files_empty_folder
                    }
                ),
                actionLabel = if (searchQuery.isNotBlank() && deepResults == null) {
                    stringResource(R.string.files_search_deep)
                } else {
                    null
                },
                onAction = { viewModel.searchDeep() }
            )
        }

        @Composable
        fun SearchDeeper() {
            Text(
                text = stringResource(R.string.files_search_deep),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .clickable { viewModel.searchDeep() }
            )
        }

        // The crumbs are the first slot in either view; the files start after them.
        if (filesView == FilesView.THUMBNAILS) FilesCursorFollower(shown, gridState, firstItem = 1)
        else FilesCursorFollower(shown, listState, firstItem = 1)

        if (filesView == FilesView.THUMBNAILS) {
            // The same folder as pictures, three or so to a row; the word still flies from a
            // tile's name to the heading and back.
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(PHONE_TILE_MIN),
                modifier = Modifier.fillMaxSize(),
                contentPadding = pagePadding,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "crumbs", span = { GridItemSpan(maxLineSpan) }) { BrowseHeader() }
                if (shown.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { BrowseEmpty() }
                }
                itemsIndexed(shown, key = { _, item -> item.file.absolutePath }) { index, item ->
                    val anchor = rowAnchors.getOrPut(item.file.absolutePath) { ZuneZoomAnchor() }
                    FileTile(
                        item = item,
                        selected = item.file.absolutePath in selection,
                        tileSize = null,
                        nameAnchor = anchor,
                        onClick = {
                            when {
                                selection.isNotEmpty() -> viewModel.toggleSelection(item)
                                item.isDirectory -> enterFolder(item, anchor)
                                else -> openItem(item)
                            }
                        },
                        onLongClick = { sheetTarget = item },
                        modifier = Modifier.w10mStaggeredAnimation({ listEntry.value }, index).filesItem(item)
                    )
                }
                if (deepResults == null && searchQuery.isNotBlank() && shown.isNotEmpty()) {
                    item(key = "deeper", span = { GridItemSpan(maxLineSpan) }) { SearchDeeper() }
                }
            }
            return
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding
        ) {
            item(key = "crumbs") { BrowseHeader() }

            if (shown.isEmpty()) {
                item(key = "empty") { BrowseEmpty() }
            }

            itemsIndexedFiles(shown) { index, item ->
                val anchor = rowAnchors.getOrPut(item.file.absolutePath) { ZuneZoomAnchor() }
                FileRow(
                    item = item,
                    nameAnchor = anchor,
                    isSelected = item.file.absolutePath in selection,
                    selectionMode = selection.isNotEmpty(),
                    subtitleOverride = if (deepResults != null) {
                        item.file.parentFile?.absolutePath
                            ?.removePrefix(currentVolume?.root?.absolutePath.orEmpty())
                            ?.ifBlank { "/" }
                    } else {
                        null
                    },
                    onClick = {
                        when {
                            selection.isNotEmpty() -> viewModel.toggleSelection(item)
                            item.isDirectory -> enterFolder(item, anchor)
                            else -> openItem(item)
                        }
                    },
                    onLongClick = { sheetTarget = item },
                    modifier = Modifier.w10mStaggeredAnimation({ listEntry.value }, index).filesItem(item)
                )
            }

            if (deepResults == null && searchQuery.isNotBlank() && shown.isNotEmpty()) {
                item(key = "deeper") { SearchDeeper() }
            }
        }
    }

    @Composable
    fun PageAt(index: Int) {
        when (index) {
            BROWSE_TAB -> BrowsePage()
            CATEGORIES_TAB -> CategoriesPage(
                onOpen = { folder -> viewModel.visit(File(Environment.getExternalStorageDirectory(), folder)) },
                contentPadding = pagePadding
            )
            QUICK_TAB -> QuickAccessPage(
                volumes = volumes,
                onOpenVolume = { viewModel.visitVolume(it) },
                onOpen = { viewModel.visit(it) },
                contentPadding = pagePadding,
                recycleBinCount = recycleBinEntries.size,
                onOpenRecycleBin = { viewModel.openRecycleBin() }
            )
            else -> CloudPage(
                documents = cloudDocuments,
                cloudApps = cloudApps,
                message = cloudMessage,
                contentPadding = pagePadding,
                onPick = { pickFromCloud() },
                onOpen = { viewModel.openCloudDocument(context, it) },
                onLongClick = { cloudSheetTarget = it }
            )
        }
    }

    // What is chosen, for both bars.
    val locale = java.util.Locale.getDefault()
    val shownItems = deepResults ?: fileItems
    val singleSelected = if (selection.size == 1) shownItems.firstOrNull { it.file.absolutePath in selection } else null
    val selectionHasFiles = shownItems.any { it.file.absolutePath in selection && !it.isDirectory }

    // ── The bar ────────────────────────────────────────────────────────────
    val inCloud = if (isWideScreen) tabletInCloud else pager.currentPage == CLOUD_TAB

    // A save to the cloud is mostly started from a folder, where the cloud page and its message
    // line are not showing; there the word comes as a toast instead. Only a message that arrives
    // while the page is away does: one already read on the page stays there.
    val cloudPageShowing by rememberUpdatedState(inCloud && (isWideScreen || (!inFolder && !inRecycleBin)))
    LaunchedEffect(cloudMessage) {
        val message = cloudMessage ?: return@LaunchedEffect
        if (cloudPageShowing) return@LaunchedEffect
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        viewModel.clearCloudMessage()
    }
    val barActions = when {
        selection.isNotEmpty() -> listOf(
            WpBarAction(Icons.Default.ContentCopy, stringResource(R.string.files_copy)) { viewModel.copySelection() },
            WpBarAction(Icons.Default.ContentCut, stringResource(R.string.files_cut)) { viewModel.cutSelection() },
            WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) { deleteSelectionPending = true }
        )

        // The page's own large action, again where a thumb finds it.
        inCloud -> listOf(
            WpBarAction(Icons.Default.FolderOpen, stringResource(R.string.files_open)) { pickFromCloud() }
        )

        else -> listOf(
            WpBarAction(Icons.Default.CreateNewFolder, stringResource(R.string.files_new_folder)) {
                textPrompt = TextPrompt.NewFolder
                openSubScreen()
            },
            WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
                isSearchOpen = !isSearchOpen
                if (!isSearchOpen) viewModel.updateSearchQuery("")
            },
            WpBarAction(Icons.Default.Sort, stringResource(R.string.files_sort)) { showSortSheet = true }
        ).filter { !isWideScreen || it.icon != Icons.Default.Search } + if (clipboard != null) {
            listOf(WpBarAction(Icons.Default.ContentPaste, stringResource(R.string.files_paste)) { viewModel.paste() })
        } else {
            emptyList()
        }
    }

    val barMenu = buildList {
        if (selection.isNotEmpty()) {
            add(WpBarMenuItem(stringResource(R.string.files_select_all)) { viewModel.selectAll() })
            if (selectionHasFiles) {
                add(WpBarMenuItem(stringResource(R.string.files_cloud_save_to)) { viewModel.saveSelectionToCloud(context) })
            }
            add(WpBarMenuItem(stringResource(R.string.common_cancel)) { viewModel.clearSelection() })
        } else if (!inCloud) {
            add(
                WpBarMenuItem(
                    stringResource(
                        if (filesView == FilesView.THUMBNAILS) R.string.files_view_list else R.string.files_view_thumbnails
                    )
                ) {
                    viewModel.setView(if (filesView == FilesView.THUMBNAILS) FilesView.LIST else FilesView.THUMBNAILS)
                }
            )
            add(
                WpBarMenuItem(
                    if (showHidden) stringResource(R.string.files_hide_hidden)
                    else stringResource(R.string.files_show_hidden)
                ) { viewModel.setShowHidden(!showHidden) }
            )
            if (volumes.size > 1) {
                add(WpBarMenuItem(stringResource(R.string.files_volumes)) { showVolumeSheet = true })
            }
            if (clipboard != null) {
                add(WpBarMenuItem(stringResource(R.string.files_clear_clipboard)) { viewModel.clearClipboard() })
            }
            add(WpBarMenuItem(stringResource(R.string.common_refresh)) { viewModel.reload() })
        }
    }

    // ── The tablet's bar: Windows 8's, what is chosen on the left and the page's own on the right ──
    val cloudChosen = cloudSelected
    val tabletLeft: List<Win8Command> = if (isWideScreen && inCloud && cloudChosen != null) buildList {
        add(Win8Command(Icons.Default.FolderOpen, stringResource(R.string.files_open).lowercase(locale)) {
            cloudSelected = null
            viewModel.openCloudDocument(context, cloudChosen)
        })
        add(Win8Command(Icons.Default.Clear, stringResource(R.string.files_cloud_forget)) {
            cloudSelected = null
            viewModel.forgetCloudDocument(cloudChosen)
        })
    } else if (!isWideScreen || inCloud || selection.isEmpty()) emptyList() else buildList {
        singleSelected?.let { item ->
            add(Win8Command(Icons.Default.FolderOpen, stringResource(R.string.files_open).lowercase(locale)) {
                viewModel.clearSelection()
                if (item.isDirectory) viewModel.visit(item.file) else openItem(item)
            })
            if (!item.isDirectory) {
                add(Win8Command(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.files_bar_open_with)) {
                    viewModel.openWith(context, item.file)
                })
                add(Win8Command(Icons.Default.Share, stringResource(R.string.files_share).lowercase(locale)) {
                    viewModel.shareFile(context, item.file)
                })
            }
            add(Win8Command(Icons.Default.Edit, stringResource(R.string.files_rename).lowercase(locale)) {
                textPrompt = TextPrompt.Rename(item)
            })
            add(Win8Command(Icons.Default.Info, stringResource(R.string.files_details).lowercase(locale)) {
                // The tablet's details slide in from the right edge (FileDetailsPanel below).
                detailsTarget = item
            })
            if (item.isDirectory && clipboard != null) {
                add(Win8Command(Icons.Default.ContentPaste, stringResource(R.string.files_paste_here)) {
                    viewModel.paste(item.file)
                })
            }
        }
        add(Win8Command(Icons.Default.ContentCopy, stringResource(R.string.files_copy)) { viewModel.copySelection() })
        add(Win8Command(Icons.Default.ContentCut, stringResource(R.string.files_cut)) { viewModel.cutSelection() })
        add(Win8Command(Icons.Default.Delete, stringResource(R.string.common_delete).lowercase(locale)) { deleteSelectionPending = true })
        if (selectionHasFiles) {
            add(Win8Command(Icons.Default.CloudUpload, stringResource(R.string.files_cloud_save_to)) { viewModel.saveSelectionToCloud(context) })
        }
    }
    val tabletRight: List<Win8Command> = when {
        !isWideScreen -> emptyList()
        inCloud && cloudChosen != null -> listOf(
            Win8Command(Icons.Default.Deselect, stringResource(R.string.files_bar_clear_selection)) { cloudSelected = null }
        )
        inCloud -> listOf(
            Win8Command(Icons.Default.FolderOpen, stringResource(R.string.files_open).lowercase(locale)) { pickFromCloud() }
        )
        selection.isNotEmpty() -> listOf(
            Win8Command(Icons.Default.SelectAll, stringResource(R.string.files_bar_select_all)) { viewModel.selectAll() },
            Win8Command(Icons.Default.Deselect, stringResource(R.string.files_bar_clear_selection)) { viewModel.clearSelection() }
        )
        else -> buildList {
            add(Win8Command(Icons.Default.CreateNewFolder, stringResource(R.string.files_new_folder)) { textPrompt = TextPrompt.NewFolder })
            if (clipboard != null) {
                add(Win8Command(Icons.Default.ContentPaste, stringResource(R.string.files_paste)) { viewModel.paste() })
                add(Win8Command(Icons.Default.Clear, stringResource(R.string.files_bar_clear_clipboard)) { viewModel.clearClipboard() })
            }
            add(Win8Command(Icons.Default.Sort, stringResource(R.string.files_sort), menu = { close ->
                FileSort.entries.forEach { option ->
                    Win8MenuItem(sortLabel(option).lowercase(locale), chosen = option == sort) {
                        viewModel.setSort(option)
                        close()
                    }
                }
                Win8MenuItem(
                    stringResource(if (sortAscending) R.string.files_sort_descending else R.string.files_sort_ascending).lowercase(locale)
                ) {
                    viewModel.toggleSortDirection()
                    close()
                }
            }))
            add(Win8Command(
                if (filesView == FilesView.THUMBNAILS) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                stringResource(R.string.files_bar_view),
                menu = { close ->
                    Win8MenuItem(stringResource(R.string.files_view_thumbs_short), chosen = filesView == FilesView.THUMBNAILS) {
                        viewModel.setView(FilesView.THUMBNAILS)
                        close()
                    }
                    Win8MenuItem(stringResource(R.string.files_view_details_short), chosen = filesView == FilesView.LIST) {
                        viewModel.setView(FilesView.LIST)
                        close()
                    }
                }
            ))
            add(Win8Command(
                if (showHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                stringResource(R.string.files_bar_hidden)
            ) { viewModel.setShowHidden(!showHidden) })
            add(Win8Command(Icons.Default.Refresh, stringResource(R.string.common_refresh).lowercase(locale)) { viewModel.reload() })
        }
    }

    // ── A keyboard and a mouse (FilesKeyboard.kt): the same keys on phone and tablet ──
    val keyboard = remember { FilesKeyboard() }
    // What the keys work on is exactly what is on screen, the search box's filter included, so
    // Ctrl+A never chooses something hidden that Delete would then take with it.
    val keyItems = deepResults ?: fileItems.filter {
        searchQuery.isBlank() || com.serkantkn.zeta.util.matchesFileQuery(it.name, searchQuery)
    }
    val keyPaths = keyItems.map { it.file.absolutePath }
    val browsingFiles = hasPermission && if (isWideScreen) !tabletInCloud else inFolder || pager.currentPage == BROWSE_TAB
    LaunchedEffect(currentDirectory, deepResults) { keyboard.reset() }

    fun keyFocusedItem(): FileItemModel? =
        keyboard.focusedPath?.takeIf { keyboard.cursorShown }?.let { path -> keyItems.firstOrNull { it.file.absolutePath == path } }

    /** Enter: what the bar's "open" does, a folder by the same jump a tap makes. */
    fun openFromKeys(item: FileItemModel) {
        viewModel.clearSelection()
        val anchor = rowAnchors[item.file.absolutePath]
        when {
            !item.isDirectory -> openItem(item)
            !isWideScreen && anchor != null -> enterFolder(item, anchor)
            else -> viewModel.visit(item.file)
        }
    }

    /** Escape: the first half of Back's order, the putting away; it never leaves a folder. */
    fun dismissFromKeys() {
        when {
            deleteTarget != null -> deleteTarget = null
            deleteSelectionPending -> deleteSelectionPending = false
            cloudSelected != null -> cloudSelected = null
            viewerTarget != null -> closeSubScreen { viewerTarget = null }
            detailsTarget != null -> if (isWideScreen) detailsTarget = null else closeSubScreen { detailsTarget = null }
            textPrompt != null -> closeSubScreen { textPrompt = null }
            sheetTarget != null -> sheetTarget = null
            cloudSheetTarget != null -> cloudSheetTarget = null
            showSortSheet -> showSortSheet = false
            showVolumeSheet -> showVolumeSheet = false
            // The bin keeps a selection of its own (see the recycle bin overlays).
            viewModel.binSelection.value.isNotEmpty() -> viewModel.clearBinSelection()
            selection.isNotEmpty() -> viewModel.clearSelection()
            deepResults != null -> viewModel.clearDeepSearch()
            isSearchOpen -> { isSearchOpen = false; viewModel.updateSearchQuery("") }
            isWideScreen && searchQuery.isNotBlank() -> viewModel.updateSearchQuery("")
            else -> keyboard.cursorShown = false
        }
    }

    fun onKey(command: FilesKeyCommand): Boolean {
        if (command == FilesKeyCommand.Dismiss) {
            dismissFromKeys()
            return true
        }
        // A viewer, a page or a sheet over the folder has the screen; only Escape reaches past it.
        val covered = viewerTarget != null || sheetTarget != null || cloudSheetTarget != null ||
            showSortSheet || showVolumeSheet || (!isWideScreen && (detailsTarget != null || textPrompt != null))
        if (covered) return false
        when (command) {
            FilesKeyCommand.Back -> return when {
                isWideScreen -> { tabletBack(); true }
                // The cloud page has no folders to climb out of.
                inCloud -> false
                browsingFiles -> { leaveFolder(); true }
                else -> false
            }
            FilesKeyCommand.Refresh -> {
                if (inCloud) viewModel.refreshCloudApps() else viewModel.reload()
                return true
            }
            FilesKeyCommand.Search -> {
                if (!isWideScreen) isSearchOpen = true
                keyboard.searchWanted = true
                return true
            }
            else -> Unit
        }
        if (!browsingFiles) return false
        val focused = keyFocusedItem()
        when (command) {
            FilesKeyCommand.Copy -> if (selection.isNotEmpty()) viewModel.copySelection() else viewModel.copyItem(focused ?: return false)
            FilesKeyCommand.Cut -> if (selection.isNotEmpty()) viewModel.cutSelection() else viewModel.cutItem(focused ?: return false)
            FilesKeyCommand.Paste -> if (clipboard != null) viewModel.paste() else return false
            FilesKeyCommand.SelectAll -> viewModel.setSelection(keyPaths)
            FilesKeyCommand.Delete, FilesKeyCommand.DeletePermanently -> when {
                selection.isNotEmpty() -> deleteSelectionPending = true
                focused != null -> deleteTarget = focused
                else -> return false
            }
            FilesKeyCommand.Rename -> {
                val item = singleSelected ?: focused?.takeIf { selection.isEmpty() } ?: return false
                textPrompt = TextPrompt.Rename(item)
                if (!isWideScreen) openSubScreen()
            }
            FilesKeyCommand.Open -> openFromKeys(focused ?: singleSelected ?: return false)
            FilesKeyCommand.Up -> {
                val parent = viewModel.parentOfCurrent() ?: return true
                // On the phone the word flies back down when the step back is the folder above.
                if (!isWideScreen && viewModel.backTarget()?.absolutePath == parent.absolutePath) leaveFolder()
                else viewModel.visit(parent)
            }
            FilesKeyCommand.Toggle -> {
                val item = focused ?: return false
                viewModel.toggleSelection(item)
                keyboard.anchorPath = item.file.absolutePath
            }
            is FilesKeyCommand.Move -> {
                if (keyPaths.isEmpty()) return true
                val from = keyboard.focusedPath?.let { keyPaths.indexOf(it) } ?: -1
                // The first arrow starts from what is chosen, or the first item.
                val to = if (from < 0) {
                    keyPaths.indexOfFirst { it in selection }.coerceAtLeast(0)
                } else {
                    moveCursor(from, keyPaths.size, command.direction, keyboard.shape)
                }
                val path = keyPaths[to]
                keyboard.cursorShown = true
                keyboard.moveTo(path, extend = command.extend)
                if (command.extend) viewModel.setSelection(rangeBetween(keyPaths, keyboard.anchorPath, path))
            }
            else -> return false
        }
        return true
    }

    // The mouse: the second button is the long press — the tablet chooses the thing so its bar
    // shows what can be done with it, the phone opens its sheet — and Ctrl and Shift choose.
    SideEffect {
        keyboard.onSecondaryClick = { item ->
            val path = item.file.absolutePath
            if (isWideScreen) {
                if (path !in viewModel.selection.value) viewModel.setSelection(listOf(path))
            } else {
                sheetTarget = item
            }
            keyboard.moveTo(path)
        }
        keyboard.onCtrlClick = { item ->
            viewModel.toggleSelection(item)
            keyboard.moveTo(item.file.absolutePath)
        }
        keyboard.onShiftClick = { item ->
            val path = item.file.absolutePath
            viewModel.setSelection(rangeBetween(keyPaths, keyboard.anchorPath ?: keyboard.focusedPath, path))
            keyboard.focusedPath = path
        }
    }

    // A lambda, not ::onKey: Compose keeps a function reference from the first composition, and
    // that onKey still saw the empty folder of the hub's first frame, so every key found nothing.
    FilesKeyboardRoot(keyboard = keyboard, onKey = { command -> onKey(command) }, modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pageHingeAway { subScreenHinge.value }
        ) {
            ZuneHubEntranceLayout { bottomBarModifier ->
                // A Box in which tiles, rows and places can be dragged onto folders, ghost on top.
                FilesDragArea(state = dragState, modifier = Modifier.fillMaxSize()) {
                    if (isWideScreen) {
                        FilesTabletLayout(
                            viewModel = viewModel,
                            filesView = filesView,
                            inCloud = tabletInCloud,
                            onInCloudChange = {
                                tabletInCloud = it
                                if (!it) cloudSelected = null
                                if (it) viewModel.closeRecycleBin()
                            },
                            onBack = { tabletBack() },
                            canGoBack = tabletInCloud || canGoBack || viewModel.parentOfCurrent() != null,
                            permissionGate = { StoragePermissionGate(context, permissionLauncher) },
                            // The phone's cloud page, set beside the places under the header.
                            cloudPage = { padding ->
                                CloudPage(
                                    documents = cloudDocuments,
                                    cloudApps = cloudApps,
                                    message = cloudMessage,
                                    contentPadding = padding,
                                    onPick = { pickFromCloud() },
                                    onOpen = { document ->
                                        cloudSelected = null
                                        viewModel.openCloudDocument(context, document)
                                    },
                                    // Windows 8: a hold chooses, and the bar says what can be done.
                                    onLongClick = { document ->
                                        cloudSelected = if (cloudSelected?.uri == document.uri) null else document
                                    },
                                    selected = cloudSelected,
                                    modifier = Modifier.padding(start = 60.dp).widthIn(max = TABLET_CLOUD_WIDTH)
                                )
                            },
                            onOpenFile = { openItem(it) },
                            // Windows 8: holding a thing chooses it, and the bar shows what can be done.
                            onItemLongClick = { viewModel.toggleSelection(it) },
                            bottomPadding = listBottomPadding,
                            modifier = Modifier.graphicsLayer { alpha = contentAlpha.value }
                        )
                    } else {
                        // The heading's place, measured whether or not a folder is open.
                        FolderHeading(
                            text = browsingLabel,
                            alpha = 0f,
                            anchor = headingAnchor,
                            onClick = null
                        )

                        @Composable
                        fun SearchField() {
                            AnimatedVisibility(visible = isSearchOpen) {
                                ZuneSearchBar(
                                    query = searchQuery,
                                    onQueryChange = { viewModel.updateSearchQuery(it) },
                                    placeholder = stringResource(R.string.files_search_hint),
                                    modifier = Modifier.padding(
                                        horizontal = ZuneDimens.ScreenPaddingHorizontal,
                                        vertical = 6.dp
                                    ).filesTypingField(search = true)
                                )
                            }
                        }

                        if (inRecycleBin) {
                            RecycleBinPhonePage(viewModel = viewModel, ui = binUi, bottomPadding = listBottomPadding)
                        } else if (inFolder) {
                            // ── The folder's page: its name, then its list ──
                            Column(modifier = Modifier.fillMaxSize()) {
                                FolderHeading(
                                    text = currentDirectory.name,
                                    alpha = localZoom.titleAlpha,
                                    anchor = headingAnchor,
                                    onClick = { leaveFolder() }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .graphicsLayer { alpha = contentAlpha.value }
                                ) {
                                    SearchField()
                                    // A file sent to the cloud from inside a folder shows its bar here.
                                    transfer?.let { active ->
                                        TransferBar(
                                            transfer = active,
                                            onCancel = { viewModel.cancelTransfer() },
                                            modifier = Modifier.padding(
                                                horizontal = ZuneDimens.ScreenPaddingHorizontal,
                                                vertical = 6.dp
                                            )
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                                    ) {
                                        BrowsePage()
                                    }
                                    Spacer(modifier = Modifier.height(com.serkantkn.zeta.ui.components.bottomBarHeight()))
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = contentAlpha.value }
                            ) {
                                val zuneTitled = !isWideScreen && com.serkantkn.zeta.ui.animation.LocalZuneMotion.current
                                if (isWideScreen) {
                                    ZuneWideHubTitle(text = stringResource(R.string.hub_files))
                                } else if (zuneTitled) {
                                    // The Zune layout: the hub's name huge and heavy at the top,
                                    // where its word from the list lands, the headings small under it.
                                    com.serkantkn.zeta.ui.components.ZuneTitledHeader(
                                        title = stringResource(R.string.hub_files),
                                        key = com.serkantkn.zeta.data.model.HubType.FILES.name,
                                        tabs = tabs,
                                        pager = pager
                                    )
                                } else {
                                    Text(
                                        text = stringResource(R.string.hub_files),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 18.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        color = if (zuneColors.isDark) {
                                            Color.White.copy(alpha = 0.9f)
                                        } else {
                                            Color.Black.copy(alpha = 0.85f)
                                        },
                                        maxLines = 1,
                                        modifier = Modifier.padding(
                                            start = ZuneDimens.ScreenPaddingHorizontal,
                                            top = 48.dp,
                                            bottom = 4.dp
                                        )
                                    )
                                }

                                if (!zuneTitled) ZunePivotTabs(
                                    tabs = tabs,
                                    state = pager,
                                    startPadding = if (isWideScreen) 48.dp else ZuneDimens.ScreenPaddingHorizontal
                                )

                                SearchField()

                                transfer?.let { active ->
                                    TransferBar(
                                        transfer = active,
                                        onCancel = { viewModel.cancelTransfer() },
                                        modifier = Modifier.padding(
                                            horizontal = ZuneDimens.ScreenPaddingHorizontal,
                                            vertical = 6.dp
                                        )
                                    )
                                }

                                ZuneLoopingPager(
                                    state = pager,
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                ) { page ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                                    ) {
                                        PageAt(page)
                                    }
                                }

                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }

                    }

                    if (isWideScreen) {
                        val binCommands = recycleBinTabletCommands(viewModel, binUi)
                        Win8CommandBar(
                            left = if (inRecycleBin) binCommands.first else tabletLeft,
                            right = if (inRecycleBin) binCommands.second else tabletRight,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(bottomBarModifier)
                                .graphicsLayer { alpha = contentAlpha.value }
                        )
                    } else {
                        val binBar = recycleBinPhoneBar(viewModel, binUi)
                        WindowsPhoneBottomBar(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .then(bottomBarModifier)
                                .graphicsLayer { alpha = contentAlpha.value },
                            actions = if (inRecycleBin) binBar.first else barActions,
                            menuItems = if (inRecycleBin) binBar.second else barMenu
                        )
                    }
                }
            }
        }

        // The words in flight, above everything.
        ZuneTitleZoomOverlay(state = localZoom, letterSpacing = (-3).sp)

        // ── Sub-screens on the hinge ──
        // On the tablet a name is asked for in Windows 8's band instead (below).
        val prompt = textPrompt.takeIf { !isWideScreen }
        val phoneDetails = detailsTarget.takeIf { !isWideScreen }
        if (prompt != null || viewerTarget != null || phoneDetails != null || subScreenHinge.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pageHingeIn { subScreenHinge.value }
            ) {
                val details = phoneDetails
                val viewing = viewerTarget
                when {
                    viewing != null -> FileViewerScreen(
                        item = viewing,
                        viewer = viewerFor(viewing.extension),
                        onOpenWith = { viewModel.openWith(context, viewing.file) },
                        onShare = { viewModel.shareFile(context, viewing.file) },
                        onClose = { closeSubScreen { viewerTarget = null } }
                    )

                    details != null -> FileDetailsScreen(
                        item = details,
                        measure = { viewModel.measure(details) },
                        onClose = { closeSubScreen { detailsTarget = null } }
                    )

                    prompt != null -> TextPromptScreen(
                        prompt = prompt,
                        onConfirm = { text ->
                            when (prompt) {
                                TextPrompt.NewFolder -> viewModel.createDirectory(text)
                                is TextPrompt.Rename -> viewModel.renameItem(prompt.item, text)
                            }
                            closeSubScreen { textPrompt = null }
                        },
                        onClose = { closeSubScreen { textPrompt = null } }
                    )
                }
            }
        }

        // ── Long press ──
        sheetTarget?.let { item ->
            MessagingSheet(title = item.name, onDismiss = { sheetTarget = null }) {
                if (!item.isDirectory) {
                    SheetAction(label = stringResource(R.string.files_open)) {
                        sheetTarget = null
                        openItem(item)
                    }
                    SheetAction(label = stringResource(R.string.files_open_with)) {
                        viewModel.openWith(context, item.file)
                        sheetTarget = null
                    }
                }
                SheetAction(label = stringResource(R.string.files_select)) {
                    viewModel.toggleSelection(item)
                    sheetTarget = null
                }
                SheetAction(label = stringResource(R.string.files_copy)) {
                    viewModel.copyItem(item)
                    sheetTarget = null
                }
                SheetAction(label = stringResource(R.string.files_cut)) {
                    viewModel.cutItem(item)
                    sheetTarget = null
                }
                if (clipboard != null && item.isDirectory) {
                    SheetAction(label = stringResource(R.string.files_paste_here)) {
                        viewModel.paste(item.file)
                        sheetTarget = null
                    }
                }
                if (!item.isDirectory) {
                    SheetAction(label = stringResource(R.string.files_share)) {
                        viewModel.shareFile(context, item.file)
                        sheetTarget = null
                    }
                    SheetAction(label = stringResource(R.string.files_cloud_save_to)) {
                        viewModel.saveFilesToCloud(context, listOf(item.file))
                        sheetTarget = null
                    }
                }
                SheetAction(label = stringResource(R.string.files_rename)) {
                    textPrompt = TextPrompt.Rename(item)
                    sheetTarget = null
                    openSubScreen()
                }
                SheetAction(label = stringResource(R.string.files_details)) {
                    detailsTarget = item
                    sheetTarget = null
                    openSubScreen()
                }
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error
                ) {
                    deleteTarget = item
                    sheetTarget = null
                }
            }
        }

        // ── The tablet's details, from the right edge ──
        if (isWideScreen) {
            FileDetailsPanel(
                item = detailsTarget,
                measure = { viewModel.measure(it) },
                onOpen = { item ->
                    detailsTarget = null
                    viewModel.clearSelection()
                    if (item.isDirectory) viewModel.visit(item.file) else openItem(item)
                },
                onShare = { item -> viewModel.shareFile(context, item.file) },
                onClose = { detailsTarget = null }
            )
        }

        // ── The tablet asks for a name in Windows 8's band ──
        if (isWideScreen) {
            textPrompt?.let { asking ->
                Win8TextDialog(
                    title = stringResource(
                        when (asking) {
                            TextPrompt.NewFolder -> R.string.files_new_folder
                            else -> R.string.files_rename
                        }
                    ).lowercase(locale),
                    initial = when (asking) {
                        is TextPrompt.Rename -> asking.item.name
                        else -> ""
                    },
                    confirmLabel = stringResource(R.string.common_ok),
                    dismissLabel = stringResource(R.string.common_cancel),
                    onConfirm = { text ->
                        when (asking) {
                            TextPrompt.NewFolder -> viewModel.createDirectory(text)
                            is TextPrompt.Rename -> viewModel.renameItem(asking.item, text)
                        }
                        textPrompt = null
                    },
                    onDismiss = { textPrompt = null }
                )
            }
        }

        // ── A paste that found the name taken asks what to do ──
        PasteConflictDialog(viewModel, isWideScreen)

        // ── Deleting asks first ──
        deleteTarget?.takeIf { isWideScreen }?.let { item ->
            Win8MessageDialog(
                title = stringResource(R.string.files_delete_title),
                confirmLabel = stringResource(R.string.common_delete),
                dismissLabel = stringResource(R.string.common_cancel),
                destructive = true,
                onConfirm = {
                    viewModel.deleteItem(item)
                    deleteTarget = null
                },
                onDismiss = { deleteTarget = null }
            ) {
                Text(stringResource(R.string.files_delete_confirm, item.name), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        if (isWideScreen && deleteSelectionPending) {
            Win8MessageDialog(
                title = stringResource(R.string.files_delete_title),
                confirmLabel = stringResource(R.string.common_delete),
                dismissLabel = stringResource(R.string.common_cancel),
                destructive = true,
                onConfirm = {
                    viewModel.deleteSelection()
                    deleteSelectionPending = false
                },
                onDismiss = { deleteSelectionPending = false }
            ) {
                Text(stringResource(R.string.files_delete_many_confirm, selection.size), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
        deleteTarget?.takeIf { !isWideScreen }?.let { item ->
            DeleteConfirmDialog(
                message = stringResource(R.string.files_delete_confirm, item.name),
                onConfirm = {
                    viewModel.deleteItem(item)
                    deleteTarget = null
                },
                onDismiss = { deleteTarget = null }
            )
        }
        if (!isWideScreen && deleteSelectionPending) {
            DeleteConfirmDialog(
                message = stringResource(R.string.files_delete_many_confirm, selection.size),
                onConfirm = {
                    viewModel.deleteSelection()
                    deleteSelectionPending = false
                },
                onDismiss = { deleteSelectionPending = false }
            )
        }

        // ── The recycle bin's sheet, its questions, and Back while it is open ──
        RecycleBinOverlays(viewModel = viewModel, ui = binUi, isWideScreen = isWideScreen)

        cloudSheetTarget?.let { document ->
            MessagingSheet(title = document.name, onDismiss = { cloudSheetTarget = null }) {
                SheetAction(label = stringResource(R.string.files_open)) {
                    cloudSheetTarget = null
                    viewModel.openCloudDocument(context, document)
                }
                // Off the list only; the document stays in the cloud, and the picker finds it again.
                SheetAction(label = stringResource(R.string.files_cloud_forget)) {
                    cloudSheetTarget = null
                    viewModel.forgetCloudDocument(document)
                }
            }
        }

        if (showSortSheet) {
            MessagingSheet(
                title = stringResource(R.string.files_sort),
                onDismiss = { showSortSheet = false }
            ) {
                FileSort.entries.forEach { option ->
                    SheetAction(
                        label = sortLabel(option),
                        color = if (option == sort) zuneColors.accentColor else null
                    ) {
                        viewModel.setSort(option)
                        showSortSheet = false
                    }
                }
                SheetAction(
                    label = stringResource(
                        if (sortAscending) R.string.files_sort_descending else R.string.files_sort_ascending
                    )
                ) {
                    viewModel.toggleSortDirection()
                    showSortSheet = false
                }
            }
        }

        if (showVolumeSheet) {
            MessagingSheet(
                title = stringResource(R.string.files_volumes),
                onDismiss = { showVolumeSheet = false }
            ) {
                volumes.forEach { volume ->
                    SheetAction(
                        label = volume.label,
                        color = if (volume.root == currentVolume?.root) zuneColors.accentColor else null
                    ) {
                        viewModel.visitVolume(volume)
                        showVolumeSheet = false
                    }
                }
            }
        }
    }
}

// ── Small pieces ────────────────────────────────────────────────────────────

/**
 * The one large word at the top of a folder's page — and, drawn at nothing, the place a folder's
 * name is measured against before its page exists. Both use the same modifiers, so both sit at
 * the same spot.
 */
@Composable
private fun FolderHeading(
    text: String,
    alpha: Float,
    anchor: ZuneZoomAnchor?,
    onClick: (() -> Unit)?
) {
    val zuneColors = LocalZuneColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = FOLDER_HEADING_SIZE,
            letterSpacing = (-2).sp,
            lineHeight = 66.sp
        ),
        color = if (zuneColors.isDark) Color.White else Color.Black,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .padding(
                start = ZuneDimens.ScreenPaddingHorizontal,
                top = 44.dp,
                end = ZuneDimens.ScreenPaddingHorizontal,
                bottom = 6.dp
            )
            .alpha(alpha)
            .then(if (anchor != null) Modifier.zuneZoomAnchor(anchor) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
    )
}

/** "Are you sure": the flip dialog, with delete in the warning colour. */
@Composable
private fun DeleteConfirmDialog(message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    ZuneFlipDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.files_delete_title),
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
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

/** What a text-entry sub-screen is being opened for. */
internal sealed interface TextPrompt {
    data object NewFolder : TextPrompt
    data class Rename(val item: FileItemModel) : TextPrompt
}

@Composable
private fun TextPromptScreen(
    prompt: TextPrompt,
    onConfirm: (String) -> Unit,
    onClose: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val initial = when (prompt) {
        is TextPrompt.Rename -> prompt.item.name
        else -> ""
    }
    var text by remember(prompt) { mutableStateOf(initial) }

    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_files),
        title = stringResource(
            when (prompt) {
                TextPrompt.NewFolder -> R.string.files_new_folder
                else -> R.string.files_rename
            }
        ),
        onClose = onClose
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MetroTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.files_name_hint),
                modifier = Modifier.filesTypingField()
            )
            Text(
                text = stringResource(R.string.common_save_cap).lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .clickable { if (text.isNotBlank()) onConfirm(text) }
            )
        }
    }
}

@Composable
private fun StoragePermissionGate(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>
) {
    ZunePermissionRequest(
        title = stringResource(R.string.files_permission_title),
        message = stringResource(R.string.files_permission_message),
        buttonLabel = stringResource(R.string.files_permission_button),
        style = ZunePermissionRequestStyle.Centered,
        icon = Icons.Default.CreateNewFolder,
        onRequest = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                }.onFailure {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                }
            } else {
                launcher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    )
}

/** The path as pressable words, from the volume down to where you are. */
private fun breadcrumbsOf(
    directory: File,
    volume: com.serkantkn.zeta.data.model.StorageVolumeInfo?,
    onNavigate: (File) -> Unit
): List<Pair<String, () -> Unit>> {
    val root = volume?.root ?: Environment.getExternalStorageDirectory()
    val crumbs = mutableListOf<Pair<String, () -> Unit>>()
    crumbs += (volume?.label ?: root.name) to { onNavigate(root) }
    val relative = directory.absolutePath.removePrefix(root.absolutePath).trim('/')
    if (relative.isNotEmpty()) {
        var walked = root
        relative.split('/').forEach { part ->
            walked = File(walked, part)
            val target = walked
            crumbs += part to { onNavigate(target) }
        }
    }
    return crumbs
}

/** The folder each step of [breadcrumbsOf]'s path stands for, in the same order. */
private fun crumbFoldersOf(directory: File, volume: com.serkantkn.zeta.data.model.StorageVolumeInfo?): List<File> {
    val root = volume?.root ?: directory
    val folders = mutableListOf(root)
    val relative = directory.absolutePath.removePrefix(root.absolutePath).trim('/')
    if (relative.isNotEmpty()) {
        var walked = root
        relative.split('/').forEach { part ->
            walked = File(walked, part)
            folders += walked
        }
    }
    return folders
}

@Composable
private fun sortLabel(sort: FileSort): String = stringResource(
    when (sort) {
        FileSort.NAME -> R.string.files_sort_name
        FileSort.DATE -> R.string.files_sort_date
        FileSort.SIZE -> R.string.files_sort_size
        FileSort.TYPE -> R.string.files_sort_type
    }
)

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedFiles(
    items: List<FileItemModel>,
    row: @Composable (Int, FileItemModel) -> Unit
) {
    items.forEachIndexed { index, item ->
        item(key = item.file.absolutePath) { row(index, item) }
    }
}

private const val BROWSE_TAB = 0
private const val CATEGORIES_TAB = 1
private const val QUICK_TAB = 2
private const val CLOUD_TAB = 3

/** The folder page's heading, which is where a folder's name lands. */
private val FOLDER_HEADING_SIZE = 60.sp

/** How quickly the page goes when a word takes off, and comes back once it has landed. */
private const val FADE_MILLIS = 160

/** How long after the page changes the new list starts in, while the word is still landing. */
private const val LIST_LEAD_MILLIS = 120L

/** How wide the cloud page runs on a tablet; its lines read badly across the whole screen. */
private val TABLET_CLOUD_WIDTH = 720.dp

/** The narrowest a picture may be in the phone's thumbnails grid; three fit across a phone. */
private val PHONE_TILE_MIN = 100.dp
