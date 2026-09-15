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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import com.serkantkn.zunelauncher.ui.animation.rememberHingeSpec

/**
 * The Files hub.
 *
 * Opening a folder is the Zune HD move: the folder's own name lifts off the list, comes toward
 * you, and lands as the word at the top of the page it opened — so the heading of what you are
 * looking at is literally the thing you touched to get here. Going back reverses it.
 *
 * The pivot's first word is therefore not a fixed tab name but wherever you are: at the top of a
 * volume it is the volume, and inside a folder it is the folder.
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
    val localTitleAnchor = rememberZuneZoomAnchor()
    localZoom.bindTitle(localTitleAnchor, PIVOT_FONT_SIZE)

    val cloudZoom = rememberZuneTitleZoomState()
    val cloudTitleAnchor = rememberZuneZoomAnchor()
    cloudZoom.bindTitle(cloudTitleAnchor, CLOUD_TITLE_SIZE)

    // The list settles in behind the word as it lands.
    val listEntry = remember { Animatable(1f) }
    LaunchedEffect(currentDirectory) {
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

    /** Opens a folder and sends its name up into the title. */
    fun enterFolder(item: FileItemModel, boundsProvider: () -> androidx.compose.ui.geometry.Rect?) {
        val from = boundsProvider()
        viewModel.navigateTo(item.file)
        if (from != null) {
            scope.launch { localZoom.flyToTitle(item.name, from, FileRowNameSize) }
        }
    }

    fun leaveFolder(): Boolean {
        val leaving = browsingLabel
        val anchorBounds = localTitleAnchor.bounds
        val moved = viewModel.navigateUp()
        if (moved && anchorBounds != null) {
            // The word drops back into the list it came from, roughly where its row will appear.
            scope.launch {
                localZoom.flyToRowBounds(
                    label = leaving,
                    toBounds = androidx.compose.ui.geometry.Rect(
                        left = anchorBounds.left,
                        top = anchorBounds.top + with(density) { 180.dp.toPx() },
                        right = anchorBounds.right,
                        bottom = anchorBounds.bottom
                    ),
                    toFontSize = FileRowNameSize
                )
            }
        }
        return moved
    }

    BackHandler {
        val browsingCloud = activeCloudAccount != null && pager.currentPage == CLOUD_TAB
        when {
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
                val anchor = rememberZuneZoomAnchor()
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
                            item.isDirectory -> enterFolder(item) { anchor.bounds }
                            else -> viewModel.openFile(context, item.file)
                        }
                    },
                    onLongClick = { sheetTarget = item },
                    modifier = Modifier.w10mStaggeredAnimation(listEntry.value, index)
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
            WpBarAction(Icons.Default.Delete, stringResource(R.string.common_delete)) { viewModel.deleteSelection() }
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
                    Column(modifier = Modifier.fillMaxSize()) {
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
                            firstTabAnchor = localTitleAnchor,
                            firstTabAlpha = localZoom.titleAlpha,
                            startPadding = if (isWideScreen) 48.dp else ZuneDimens.ScreenPaddingHorizontal
                        )

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

                    WindowsPhoneBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
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
        if (prompt != null || subScreenHinge.value > 0f) {
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
                when {
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
                SheetAction(label = stringResource(R.string.files_select)) {
                    viewModel.toggleSelection(item)
                    sheetTarget = null
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
                    viewModel.deleteItem(item)
                    sheetTarget = null
                }
            }
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

/** The pivot's own size, which is where a folder's name lands. */
private val PIVOT_FONT_SIZE = 72.sp

/** The cloud page's heading, which is where a drive folder's name lands. */
internal val CLOUD_TITLE_SIZE = 34.sp
