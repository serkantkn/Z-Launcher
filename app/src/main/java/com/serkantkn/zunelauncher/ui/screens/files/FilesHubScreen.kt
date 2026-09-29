package com.serkantkn.zunelauncher.ui.screens.files

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.CloudItem
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.ui.animation.HingeAnimation
import com.serkantkn.zunelauncher.ui.animation.ZuneTitleZoomOverlay
import com.serkantkn.zunelauncher.ui.animation.rememberZuneTitleZoomState
import com.serkantkn.zunelauncher.ui.animation.rememberZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.animation.ZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.animation.zuneZoomAnchor
import com.serkantkn.zunelauncher.ui.components.ZuneDialogButton
import com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.util.FileViewer
import com.serkantkn.zunelauncher.util.viewerFor
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zunelauncher.ui.components.MetroEmpty
import com.serkantkn.zunelauncher.ui.components.MetroSubScreen
import com.serkantkn.zunelauncher.ui.components.MetroTextField
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequestStyle
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.messaging.MessagingSheet
import com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.FileSort
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec

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
    val sort by viewModel.sort.collectAsState()
    val sortAscending by viewModel.sortAscending.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()

    val cloudAccounts by viewModel.cloudAccounts.collectAsState()
    val activeCloudAccount by viewModel.activeCloudAccount.collectAsState()
    val cloudPath by viewModel.cloudPath.collectAsState()
    val cloudItems by viewModel.cloudItems.collectAsState()
    val cloudBusy by viewModel.cloudBusy.collectAsState()
    val cloudMessage by viewModel.cloudMessage.collectAsState()
    val cloudQuota by viewModel.cloudQuota.collectAsState()
    val cloudSearchResults by viewModel.cloudSearchResults.collectAsState()
    val transfer by viewModel.transfer.collectAsState()

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
    // Everything but the word in flight: gone while it jumps, back once it has landed.
    val contentAlpha = remember { Animatable(1f) }
    var transitioning by remember { mutableStateOf(false) }

    val cloudZoom = rememberZuneTitleZoomState()
    val cloudTitleAnchor = rememberZuneZoomAnchor()
    cloudZoom.bindTitle(cloudTitleAnchor, CLOUD_TITLE_SIZE)

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
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { viewModel.refreshPermissionState() }
    )

    val uploadPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.uploadPickedUri(uri) }

    val googleSignIn = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            runCatching { viewModel.googleResultFromIntent(data) }
                .onSuccess { viewModel.completeGoogleSignIn(it) }
                .onFailure { viewModel.reportCloudError(it) }
        } else {
            viewModel.reportCloudError(
                com.serkantkn.zunelauncher.data.model.CloudException(R.string.cloud_error_cancelled)
            )
        }
    }

    fun startGoogleSignIn() {
        viewModel.googleAuthorizeTask()
            .addOnSuccessListener { result ->
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    googleSignIn.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    viewModel.completeGoogleSignIn(result)
                }
            }
            .addOnFailureListener { viewModel.reportCloudError(it) }
    }

    // ── Screen state ───────────────────────────────────────────────────────
    var isSearchOpen by remember { mutableStateOf(false) }
    var sheetTarget by remember { mutableStateOf<FileItemModel?>(null) }
    var cloudSheetTarget by remember { mutableStateOf<CloudItem?>(null) }
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
            viewModel.navigateTo(item.file)
            return
        }
        scope.launch {
            transitioning = true
            val fade = launch { contentAlpha.animateTo(0f, tween(FADE_MILLIS, easing = FastOutSlowInEasing)) }
            val flight = launch { localZoom.flyToTitle(item.name, from, FileRowNameSize) }
            // The old page is gone before the new one is read, so nothing swaps in mid-fade.
            fade.join()
            viewModel.navigateTo(item.file)
            listEntry.snapTo(0f)
            runCatching { listState.scrollToItem(0) }
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
        if (!inFolder) return false
        if (transitioning) return true
        val leaving = currentDirectory
        val parent = viewModel.parentOfCurrent() ?: return false
        val start = headingAnchor.bounds
        if (start == null || !animationsEnabled) {
            viewModel.navigateUp()
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
            // The row the word lands on is scrolled into view, unseen, and measured there.
            val path = leaving.absolutePath
            val index = items.indexOfFirst { it.file.absolutePath == path }
            if (index >= 0) {
                // The crumbs sit in the list's first slot, above the rows.
                runCatching { listState.scrollToItem(index + 1) }
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
        if (viewerFor(item.extension) == FileViewer.NONE) {
            viewModel.openFile(context, item.file)
        } else {
            viewerTarget = item
            openSubScreen()
        }
    }

    BackHandler {
        val browsingCloud = activeCloudAccount != null && pager.currentPage == CLOUD_TAB
        when {
            deleteTarget != null -> deleteTarget = null
            deleteSelectionPending -> deleteSelectionPending = false
            viewerTarget != null -> closeSubScreen { viewerTarget = null }
            detailsTarget != null -> closeSubScreen { detailsTarget = null }
            textPrompt != null -> closeSubScreen { textPrompt = null }
            sheetTarget != null -> sheetTarget = null
            cloudSheetTarget != null -> cloudSheetTarget = null
            showSortSheet -> showSortSheet = false
            showVolumeSheet -> showVolumeSheet = false
            selection.isNotEmpty() -> viewModel.clearSelection()
            deepResults != null -> viewModel.clearDeepSearch()
            isSearchOpen -> { isSearchOpen = false; viewModel.updateSearchQuery("") }
            browsingCloud && viewModel.cloudNavigateUp() -> Unit
            browsingCloud -> viewModel.closeCloudAccount()
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
                com.serkantkn.zunelauncher.util.matchesFileQuery(it.name, searchQuery)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = pagePadding
        ) {
            item(key = "crumbs") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val crumbs = breadcrumbsOf(currentDirectory, currentVolume) { viewModel.loadDirectory(it) }
                    // At the top of a volume the path is just the volume, which the title already says.
                    if (crumbs.size > 1) FilesBreadcrumb(crumbs = crumbs)
                    val volume = currentVolume
                    if (volume != null && deepResults == null && currentDirectory.absolutePath == volume.root.absolutePath) {
                        StorageBar(
                            volume = volume,
                            showLabel = false,
                            onClick = if (volumes.size > 1) ({ showVolumeSheet = true }) else null
                        )
                    }
                    if (busyMessage != null) {
                        Text(
                            text = busyMessage.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = zuneColors.accentColor,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
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

            if (shown.isEmpty()) {
                item(key = "empty") {
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
                    modifier = Modifier.w10mStaggeredAnimation({ listEntry.value }, index)
                )
            }

            if (deepResults == null && searchQuery.isNotBlank() && shown.isNotEmpty()) {
                item(key = "deeper") {
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
            }
        }
    }

    @Composable
    fun PageAt(index: Int) {
        when (index) {
            BROWSE_TAB -> BrowsePage()
            CATEGORIES_TAB -> CategoriesPage(
                onOpen = { folder -> viewModel.navigateTo(File(Environment.getExternalStorageDirectory(), folder)) },
                contentPadding = pagePadding
            )
            QUICK_TAB -> QuickAccessPage(
                volumes = volumes,
                onOpenVolume = { viewModel.openVolume(it) },
                onOpen = { viewModel.navigateTo(it) },
                contentPadding = pagePadding
            )
            else -> CloudPage(
                accounts = cloudAccounts,
                activeAccount = activeCloudAccount,
                path = cloudPath,
                items = cloudSearchResults ?: cloudItems,
                isSearchResult = cloudSearchResults != null,
                quota = cloudQuota,
                isBusy = cloudBusy,
                message = cloudMessage,
                titleAnchor = cloudTitleAnchor,
                titleAlpha = cloudZoom.titleAlpha,
                contentPadding = pagePadding,
                onSignInGoogle = { startGoogleSignIn() },
                onOpenAccount = { viewModel.openCloudAccount(it) },
                onSignOut = { viewModel.signOutCloudAccount(it) },
                onOpenItem = { item, bounds ->
                    if (item.isFolder) {
                        viewModel.openCloudFolder(item)
                        if (bounds != null) {
                            scope.launch { cloudZoom.flyToTitle(item.name, bounds, FileRowNameSize) }
                        }
                    } else {
                        viewModel.openCloudItem(item)
                    }
                },
                onItemLongClick = { cloudSheetTarget = it },
                onCrumbClick = { viewModel.cloudNavigateUp() }
            )
        }
    }

    // ── The bar ────────────────────────────────────────────────────────────
    val inCloud = pager.currentPage == CLOUD_TAB
    val barActions = when {
        selection.isNotEmpty() -> listOf(
            WpBarAction(Icons.Default.ContentCopy, stringResource(R.string.files_copy)) { viewModel.copySelection() },
            WpBarAction(Icons.Default.ContentPaste, stringResource(R.string.files_cut)) { viewModel.cutSelection() },
            WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) { deleteSelectionPending = true }
        )

        inCloud && activeCloudAccount != null -> listOf(
            WpBarAction(Icons.Default.CreateNewFolder, stringResource(R.string.files_new_folder)) {
                textPrompt = TextPrompt.NewCloudFolder
                openSubScreen()
            },
            WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
                isSearchOpen = !isSearchOpen
            },
            WpBarAction(Icons.Default.Refresh, stringResource(R.string.common_refresh)) {
                viewModel.reloadCloudFolder()
            }
        )

        inCloud -> emptyList()

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
        ) + if (clipboard != null) {
            listOf(WpBarAction(Icons.Default.Check, stringResource(R.string.files_paste)) { viewModel.paste() })
        } else {
            emptyList()
        }
    }

    val barMenu = buildList {
        if (selection.isNotEmpty()) {
            add(WpBarMenuItem(stringResource(R.string.files_select_all)) { viewModel.selectAll() })
            add(WpBarMenuItem(stringResource(R.string.common_cancel)) { viewModel.clearSelection() })
        } else if (inCloud) {
            if (activeCloudAccount != null) {
                add(WpBarMenuItem(stringResource(R.string.files_upload)) { uploadPicker.launch("*/*") })
                add(WpBarMenuItem(stringResource(R.string.files_cloud_close)) { viewModel.closeCloudAccount() })
            }
        } else {
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

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = subScreenHinge.value
                    rotationY = -HingeAnimation.MAX_ROTATION_DEGREES * p
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                    alpha = (1f - p * 1.5f).coerceIn(0f, 1f)
                }
        ) {
            ZuneHubEntranceLayout { bottomBarModifier ->
                Box(modifier = Modifier.fillMaxSize()) {
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
                                onQueryChange = {
                                    viewModel.updateSearchQuery(it)
                                    if (inCloud) viewModel.searchCloud(it)
                                },
                                placeholder = stringResource(R.string.files_search_hint),
                                modifier = Modifier.padding(
                                    horizontal = ZuneDimens.ScreenPaddingHorizontal,
                                    vertical = 6.dp
                                )
                            )
                        }
                    }

                    if (inFolder) {
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
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                                ) {
                                    BrowsePage()
                                }
                                Spacer(modifier = Modifier.height(72.dp))
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { alpha = contentAlpha.value }
                        ) {
                            if (isWideScreen) {
                                ZuneWideHubTitle(text = stringResource(R.string.hub_files))
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

                            ZunePivotTabs(
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

                    WindowsPhoneBottomBar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .then(bottomBarModifier)
                            .graphicsLayer { alpha = contentAlpha.value },
                        actions = barActions,
                        menuItems = barMenu
                    )
                }
            }
        }

        // The words in flight, above everything.
        ZuneTitleZoomOverlay(state = localZoom, letterSpacing = (-3).sp)
        ZuneTitleZoomOverlay(state = cloudZoom, letterSpacing = (-1).sp)

        // ── Sub-screens on the hinge ──
        val prompt = textPrompt
        if (prompt != null || viewerTarget != null || detailsTarget != null || subScreenHinge.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = subScreenHinge.value
                        rotationY = HingeAnimation.MAX_ROTATION_DEGREES * (1f - p)
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = HingeAnimation.CAMERA_DISTANCE_MULTIPLIER * density.density
                        alpha = (p * 1.5f - 0.2f).coerceIn(0f, 1f)
                    }
            ) {
                val details = detailsTarget
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
                                TextPrompt.NewCloudFolder -> viewModel.createCloudFolder(text)
                                is TextPrompt.Rename -> viewModel.renameItem(prompt.item, text)
                                is TextPrompt.RenameCloud -> viewModel.renameCloudItem(prompt.item, text)
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

        // ── Deleting asks first ──
        deleteTarget?.let { item ->
            DeleteConfirmDialog(
                message = stringResource(R.string.files_delete_confirm, item.name),
                onConfirm = {
                    viewModel.deleteItem(item)
                    deleteTarget = null
                },
                onDismiss = { deleteTarget = null }
            )
        }
        if (deleteSelectionPending) {
            DeleteConfirmDialog(
                message = stringResource(R.string.files_delete_many_confirm, selection.size),
                onConfirm = {
                    viewModel.deleteSelection()
                    deleteSelectionPending = false
                },
                onDismiss = { deleteSelectionPending = false }
            )
        }

        cloudSheetTarget?.let { item ->
            MessagingSheet(title = item.name, onDismiss = { cloudSheetTarget = null }) {
                if (!item.isFolder) {
                    SheetAction(label = stringResource(R.string.files_cloud_save)) {
                        viewModel.saveCloudItemToDevice(item)
                        cloudSheetTarget = null
                    }
                    SheetAction(label = stringResource(R.string.files_share)) {
                        viewModel.shareCloudItem(item)
                        cloudSheetTarget = null
                    }
                }
                SheetAction(label = stringResource(R.string.files_rename)) {
                    textPrompt = TextPrompt.RenameCloud(item)
                    cloudSheetTarget = null
                    openSubScreen()
                }
                SheetAction(
                    label = stringResource(R.string.common_delete),
                    color = MaterialTheme.colorScheme.error
                ) {
                    viewModel.deleteCloudItem(item)
                    cloudSheetTarget = null
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
                        viewModel.openVolume(volume)
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
    data object NewCloudFolder : TextPrompt
    data class Rename(val item: FileItemModel) : TextPrompt
    data class RenameCloud(val item: CloudItem) : TextPrompt
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
        is TextPrompt.RenameCloud -> prompt.item.name
        else -> ""
    }
    var text by remember(prompt) { mutableStateOf(initial) }

    MetroSubScreen(
        breadcrumb = stringResource(R.string.hub_files),
        title = stringResource(
            when (prompt) {
                TextPrompt.NewFolder, TextPrompt.NewCloudFolder -> R.string.files_new_folder
                else -> R.string.files_rename
            }
        ),
        onClose = onClose
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            MetroTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = stringResource(R.string.files_name_hint)
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
    volume: com.serkantkn.zunelauncher.data.model.StorageVolumeInfo?,
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

/** The cloud page's heading, which is where a drive folder's name lands. */
internal val CLOUD_TITLE_SIZE = 34.sp
