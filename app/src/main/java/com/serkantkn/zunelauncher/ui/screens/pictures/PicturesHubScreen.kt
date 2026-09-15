package com.serkantkn.zunelauncher.ui.screens.pictures

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.data.repository.PicturesBridge
import com.serkantkn.zunelauncher.ui.animation.ZuneTitleZoomOverlay
import com.serkantkn.zunelauncher.ui.animation.rememberZuneTitleZoomState
import com.serkantkn.zunelauncher.ui.animation.rememberZuneZoomAnchor
import com.serkantkn.zunelauncher.ui.components.MetroTextField
import com.serkantkn.zunelauncher.ui.components.PhotoViewer
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.screens.messaging.MessagingSheet
import com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.GRID_COLUMN_CHOICES
import com.serkantkn.zunelauncher.util.defaultGridColumns
import com.serkantkn.zunelauncher.util.groupByDay
import com.serkantkn.zunelauncher.util.matchesQuery
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The pictures hub: the camera roll, the albums, and whatever has been kept.
 *
 * Three things shape it. It holds videos as well as stills, because the launcher's own camera
 * records both and a gallery that hides half of what was taken is not a gallery. It has the bottom
 * bar every other hub has, so choosing, searching and sharing live where they live everywhere else
 * rather than nowhere. And opening an album flies the album's name up into the pivot, the way the
 * Zune HD threw a title towards you when you went into it — the one piece of motion this hub was
 * missing while every other hub had its own.
 */
@Composable
fun PicturesHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicturesHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val allImages by viewModel.allImages.collectAsState()
    val cameraRollImages by viewModel.cameraRollImages.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val favoriteImages by viewModel.favoriteImages.collectAsState()
    val favoritePhotoIds by viewModel.favoritePhotoIds.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val storedColumns by viewModel.storedColumns.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val pinnedAlbums by viewModel.pinnedAlbums.collectAsState()
    val pendingAlbum by PicturesBridge.pendingAlbum.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasPermission) viewModel.loadMedia()
    }


    val unsortedLabel = stringResource(R.string.pics_unsorted_album)
    val albumsLabel = stringResource(R.string.pics_tab_albums)
    val albumTabLabel = selectedAlbum?.bucketName?.ifBlank { unsortedLabel }?.lowercase(Locale.getDefault())
        ?: albumsLabel

    val tabs = listOf(
        stringResource(R.string.pics_tab_camera_roll),
        albumTabLabel,
        stringResource(R.string.pics_tab_favorites)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    // A pinned album tile asks for its album; the ask waits until the media store has been read.
    LaunchedEffect(pendingAlbum, albums) {
        val wanted = pendingAlbum ?: return@LaunchedEffect
        if (viewModel.openPendingAlbum(wanted)) {
            pager.scrollToPage(ALBUMS_TAB)
            PicturesBridge.consume()
        }
    }

    // ── The flight ─────────────────────────────────────────────────────────
    // The album's name leaves its tile, swells as it crosses the screen and lands as the pivot
    // word; the grid underneath settles in behind it.
    val albumZoom = rememberZuneTitleZoomState()
    val albumTabAnchor = rememberZuneZoomAnchor()
    albumZoom.bindTitle(albumTabAnchor, PIVOT_FONT_SIZE)

    val gridEntry = remember { Animatable(1f) }
    LaunchedEffect(selectedAlbum?.bucketId) {
        gridEntry.snapTo(0f)
        gridEntry.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
    }

    var viewingPhotosList by remember { mutableStateOf<List<MediaImage>?>(null) }
    var viewingInitialIndex by remember { mutableIntStateOf(0) }
    var editingPhoto by remember { mutableStateOf<MediaImage?>(null) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var showColumnsSheet by remember { mutableStateOf(false) }

    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadMedia()
            viewModel.clearSelection()
            viewingPhotosList = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { granted -> viewModel.onPermissionResult(granted.values.any { it }) }
    )

    fun requestMediaPermission() {
        val wanted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionLauncher.launch(wanted)
    }

    /** Opens an album and sends its name up into the pivot. */
    fun openAlbum(album: MediaAlbum, from: Rect?) {
        viewModel.clearSelection()
        viewModel.selectAlbum(album)
        val label = album.bucketName.ifBlank { unsortedLabel }.lowercase(Locale.getDefault())
        if (from != null) {
            scope.launch { albumZoom.flyToTitle(label, from, ALBUM_TILE_NAME_SIZE) }
        }
    }

    /** Leaves an album; the name drops back towards the tiles it came from. */
    fun leaveAlbum() {
        val leaving = albumTabLabel
        val anchorBounds = albumTabAnchor.bounds
        viewModel.selectAlbum(null)
        viewModel.clearSelection()
        if (anchorBounds != null) {
            scope.launch {
                albumZoom.flyToRowBounds(
                    label = leaving,
                    toBounds = Rect(
                        left = anchorBounds.left,
                        top = anchorBounds.top + with(density) { 200.dp.toPx() },
                        right = anchorBounds.right,
                        bottom = anchorBounds.bottom
                    ),
                    toFontSize = ALBUM_TILE_NAME_SIZE
                )
            }
        }
    }

    BackHandler(
        enabled = editingPhoto != null || viewingPhotosList != null || selectionMode ||
            isSearchOpen || showColumnsSheet || selectedAlbum != null
    ) {
        when {
            editingPhoto != null -> editingPhoto = null
            viewingPhotosList != null -> viewingPhotosList = null
            showColumnsSheet -> showColumnsSheet = false
            selectionMode -> viewModel.clearSelection()
            isSearchOpen -> {
                isSearchOpen = false
                viewModel.setSearchQuery("")
            }
            selectedAlbum != null -> leaveAlbum()
        }
    }

    // ── What each page is showing ──────────────────────────────────────────
    val albumImages = remember(allImages, selectedAlbum) {
        selectedAlbum?.let { album -> allImages.filter { it.bucketId == album.bucketId } } ?: emptyList()
    }
    val searchResults = remember(allImages, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else allImages.filter { it.matchesQuery(searchQuery) }
    }

    // Opening a picture from the search page left the keyboard standing over the viewer's own
    // bar, because the field underneath still had the focus.
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(viewingPhotosList != null) {
        if (viewingPhotosList != null) keyboard?.hide()
    }

    fun openViewer(list: List<MediaImage>, item: MediaImage) {
        if (item.isVideo) {
            viewModel.playVideo(context, item)
            return
        }
        // The viewer shows stills; a roll with clips in it still opens at the right picture.
        val stills = list.filter { !it.isVideo }
        viewingPhotosList = stills
        viewingInitialIndex = stills.indexOf(item).coerceAtLeast(0)
    }

    fun onTileClick(list: List<MediaImage>, item: MediaImage) {
        if (selectionMode) viewModel.toggleSelection(item) else openViewer(list, item)
    }

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = LocalIsWideScreen.current

            val columnsFor: (Int) -> Int = { count ->
                if (storedColumns > 0) storedColumns else defaultGridColumns(count)
            }

            val renderPage: @Composable (Int) -> Unit = { tabIndex ->
                ZunePageTransition {
                    when (tabIndex) {
                        0 -> MediaDayGrid(
                            items = cameraRollImages,
                            columns = columnsFor(cameraRollImages.size),
                            favourites = favoritePhotoIds,
                            selection = selection,
                            selectionMode = selectionMode,
                            onClick = { onTileClick(cameraRollImages, it) },
                            onLongClick = { viewModel.toggleSelection(it) },
                            empty = {
                                PicturesEmpty(
                                    message = errorMessage?.let { stringResource(R.string.pics_load_failed, it) }
                                        ?: stringResource(R.string.pics_empty_camera_roll),
                                    hint = if (errorMessage == null) {
                                        stringResource(R.string.pics_empty_camera_roll_hint)
                                    } else null
                                )
                            }
                        )

                        1 -> if (selectedAlbum != null) {
                            MediaDayGrid(
                                items = albumImages,
                                columns = columnsFor(albumImages.size),
                                favourites = favoritePhotoIds,
                                selection = selection,
                                selectionMode = selectionMode,
                                onClick = { onTileClick(albumImages, it) },
                                onLongClick = { viewModel.toggleSelection(it) },
                                modifier = Modifier.graphicsLayer {
                                    // The grid arrives a beat behind the name that flew up.
                                    alpha = gridEntry.value
                                    translationY = (1f - gridEntry.value) * 40.dp.toPx()
                                },
                                empty = { PicturesEmpty(stringResource(R.string.no_photos)) }
                            )
                        } else {
                            AlbumsGrid(
                                albums = albums,
                                onOpen = ::openAlbum,
                                empty = {
                                    PicturesEmpty(
                                        message = errorMessage?.let {
                                            stringResource(R.string.pics_albums_load_failed, it)
                                        } ?: stringResource(R.string.no_albums),
                                        hint = if (errorMessage == null) {
                                            stringResource(R.string.pics_empty_albums_hint)
                                        } else null
                                    )
                                }
                            )
                        }

                        2 -> MediaDayGrid(
                            items = favoriteImages,
                            columns = columnsFor(favoriteImages.size),
                            favourites = favoritePhotoIds,
                            selection = selection,
                            selectionMode = selectionMode,
                            onClick = { onTileClick(favoriteImages, it) },
                            onLongClick = { viewModel.toggleSelection(it) },
                            empty = {
                                PicturesEmpty(
                                    message = stringResource(R.string.no_favorites),
                                    hint = stringResource(R.string.pics_empty_favorites_hint)
                                )
                            }
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    ZuneWideHubTitle(text = stringResource(R.string.pictures_hub))
                } else {
                    Text(
                        text = stringResource(R.string.pictures_hub),
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
                        softWrap = false,
                        modifier = Modifier.padding(
                            top = 28.dp,
                            bottom = 4.dp,
                            start = ZuneDimens.ScreenPaddingHorizontal
                        )
                    )
                }

                if (!hasPermission) {
                    ZunePermissionRequest(
                        title = stringResource(R.string.storage_permission_title).lowercase(Locale.getDefault()),
                        message = stringResource(R.string.storage_permission_message),
                        buttonLabel = stringResource(R.string.grant_permission).lowercase(Locale.getDefault()),
                        onRequest = { requestMediaPermission() }
                    )
                } else if (isSearchOpen) {
                    SearchPage(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        results = searchResults,
                        columns = columnsFor(searchResults.size),
                        favourites = favoritePhotoIds,
                        onClick = { openViewer(searchResults, it) }
                    )
                } else if (isLoading && allImages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = zuneColors.accentColor)
                    }
                } else if (isWideScreen) {
                    ZuneWidePanorama(tabs = tabs, fillPageHeight = true) { index -> renderPage(index) }
                } else {
                    ZunePivotTabs(
                        tabs = tabs,
                        state = pager,
                        firstTabAnchor = albumTabAnchor,
                        firstTabAlpha = albumZoom.titleAlpha,
                        zoomTabIndex = ALBUMS_TAB,
                        onSelected = {
                            if (selectedAlbum != null) viewModel.selectAlbum(null)
                            viewModel.clearSelection()
                        },
                        modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                    )

                    ZuneLoopingPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ZuneDimens.ScreenPaddingHorizontal,
                            end = 48.dp
                        ),
                        pageSpacing = 24.dp
                    ) { page -> renderPage(page) }
                }
            }

            // ── The bottom bar ─────────────────────────────────────────────
            val chosen = remember(selection, allImages) { allImages.filter { it.id in selection } }
            val barActions = if (selectionMode) {
                listOf(
                    WpBarAction(Icons.Default.Share, stringResource(R.string.pics_action_share)) {
                        viewModel.share(context, chosen)
                    },
                    WpBarAction(Icons.Default.Favorite, stringResource(R.string.pics_action_favorite)) {
                        chosen.forEach { viewModel.toggleFavorite(it) }
                        viewModel.clearSelection()
                    },
                    WpBarAction(Icons.Default.Delete, stringResource(R.string.pics_action_delete)) {
                        val activity = context as? Activity ?: return@WpBarAction
                        viewModel.deletePhotos(
                            activity = activity,
                            photos = chosen,
                            onIntentSenderRequired = { sender ->
                                deleteIntentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
                            },
                            onDeleted = { viewModel.clearSelection() }
                        )
                    },
                    WpBarAction(Icons.Default.Check, stringResource(R.string.pics_action_done)) {
                        viewModel.clearSelection()
                    }
                )
            } else {
                listOf(
                    WpBarAction(Icons.Default.CheckCircle, stringResource(R.string.pics_action_select)) {
                        viewModel.startSelection()
                    },
                    WpBarAction(Icons.Default.Search, stringResource(R.string.pics_action_search)) {
                        isSearchOpen = true
                    },
                    WpBarAction(Icons.Default.Refresh, stringResource(R.string.pics_action_refresh)) {
                        viewModel.loadMedia()
                    }
                )
            }

            val barMenu = buildList {
                if (selectionMode) {
                    add(WpBarMenuItem(stringResource(R.string.pics_menu_select_all)) {
                        val page = (pager.currentPage % tabs.size).let { if (it < 0) it + tabs.size else it }
                        viewModel.selectAll(
                            when {
                                page == 1 && selectedAlbum != null -> albumImages
                                page == 2 -> favoriteImages
                                else -> cameraRollImages
                            }
                        )
                    })
                    if (chosen.size == 1 && !chosen.first().isVideo) {
                        add(WpBarMenuItem(stringResource(R.string.pics_menu_wallpaper)) {
                            viewModel.setAsWallpaper(context, chosen.first())
                            viewModel.clearSelection()
                        })
                    }
                }
                val album = selectedAlbum
                if (album != null && !selectionMode) {
                    val pinned = album.bucketId in pinnedAlbums
                    add(
                        WpBarMenuItem(
                            stringResource(
                                if (pinned) R.string.pics_menu_unpin_album else R.string.pics_menu_pin_album
                            )
                        ) { viewModel.togglePinAlbum(album) }
                    )
                }
                add(WpBarMenuItem(stringResource(R.string.pics_menu_columns)) { showColumnsSheet = true })
            }

            WindowsPhoneBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
                actions = barActions,
                menuItems = barMenu
            )

            // How many are in hand, above the bar so it is never in the way of the pictures.
            AnimatedVisibility(
                visible = selectionMode,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
            ) {
                Text(
                    text = stringResource(R.string.pics_selected_count, selection.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier
                        .background(zuneColors.accentColor)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            PhotoViewer(
                photos = viewingPhotosList,
                initialIndex = viewingInitialIndex,
                favoritePhotoIds = favoritePhotoIds,
                onDismiss = { viewingPhotosList = null },
                onToggleFavorite = { photo -> viewModel.toggleFavorite(photo) },
                onEditPhoto = { photo -> editingPhoto = photo },
                onShare = { photo -> viewModel.share(context, listOf(photo)) },
                onSetWallpaper = { photo -> viewModel.setAsWallpaper(context, photo) },
                onDeletePhoto = { photo ->
                    val activity = context as? Activity ?: return@PhotoViewer
                    viewModel.deletePhoto(
                        activity = activity,
                        photo = photo,
                        onIntentSenderRequired = { sender ->
                            deleteIntentSenderLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        },
                        onDeleted = { viewingPhotosList = null }
                    )
                }
            )

            editingPhoto?.let { photoToEdit ->
                PhotoEditorScreen(
                    photo = photoToEdit,
                    onSave = { editedBitmap ->
                        viewModel.saveEditedPhoto(editedBitmap)
                        editingPhoto = null
                    },
                    onCancel = { editingPhoto = null }
                )
            }

            if (showColumnsSheet) {
                MessagingSheet(
                    title = stringResource(R.string.pics_columns_title),
                    onDismiss = { showColumnsSheet = false }
                ) {
                    SheetAction(
                        label = stringResource(R.string.pics_columns_auto),
                        color = if (storedColumns == 0) zuneColors.accentColor else null
                    ) {
                        viewModel.setColumns(0)
                        showColumnsSheet = false
                    }
                    GRID_COLUMN_CHOICES.forEach { choice ->
                        SheetAction(
                            label = stringResource(R.string.pics_columns_value, choice),
                            color = if (storedColumns == choice) zuneColors.accentColor else null
                        ) {
                            viewModel.setColumns(choice)
                            showColumnsSheet = false
                        }
                    }
                }
            }
        }
    }

    // The word in flight, above everything else on the screen.
    ZuneTitleZoomOverlay(state = albumZoom, letterSpacing = (-3).sp)
}

/**
 * A grid of pictures with a heading over each day's worth.
 *
 * The headings are what keep a long roll navigable; without them a thousand thumbnails are one
 * undifferentiated wall. A heading spans the full width of the grid, whatever it has been set to.
 */
@Composable
private fun MediaDayGrid(
    items: List<MediaImage>,
    columns: Int,
    favourites: Set<String>,
    selection: Set<Long>,
    selectionMode: Boolean,
    onClick: (MediaImage) -> Unit,
    onLongClick: (MediaImage) -> Unit,
    modifier: Modifier = Modifier,
    empty: @Composable () -> Unit
) {
    if (items.isEmpty()) {
        empty()
        return
    }

    val days = remember(items) { groupByDay(items) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = modifier.fillMaxSize()
    ) {
        days.forEach { (day, ofDay) ->
            item(key = "day_$day", span = { GridItemSpan(maxLineSpan) }) {
                DayHeader(dayMillis = day, count = ofDay.size)
            }
            items(ofDay, key = { it.stableKey }) { item ->
                MediaTile(
                    item = item,
                    isFavorite = item.stableKey in favourites || item.id.toString() in favourites,
                    isSelected = item.id in selection,
                    selectionMode = selectionMode,
                    onClick = { onClick(item) },
                    onLongClick = { onLongClick(item) }
                )
            }
        }
    }
}

/** The albums, two across, each opening with a flight. */
@Composable
private fun AlbumsGrid(
    albums: List<MediaAlbum>,
    onOpen: (MediaAlbum, Rect?) -> Unit,
    empty: @Composable () -> Unit
) {
    if (albums.isEmpty()) {
        empty()
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.bucketId }) { album ->
            // Where the tile's name sits is where its flight begins, so each tile keeps its own
            // bounds; the name lifts off from the picture the finger actually touched.
            var bounds by remember { mutableStateOf<Rect?>(null) }
            AlbumTile(
                album = album,
                onClick = { onOpen(album, bounds) },
                modifier = Modifier.onGloballyPositioned { coords ->
                    val box = coords.boundsInRoot()
                    // The name is written across the bottom-left of the tile, not its middle.
                    bounds = Rect(
                        left = box.left + 10f,
                        top = box.bottom - 56f,
                        right = box.right,
                        bottom = box.bottom - 24f
                    )
                }
            )
        }
    }
}

/** Searching across everything, by picture name or the folder it sits in. */
@Composable
private fun SearchPage(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<MediaImage>,
    columns: Int,
    favourites: Set<String>,
    onClick: (MediaImage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
    ) {
        Spacer(modifier = Modifier.height(ZuneDimens.SpacingMd))
        MetroTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.pics_search_hint)
        )
        Spacer(modifier = Modifier.height(ZuneDimens.SpacingMd))

        if (query.isNotBlank() && results.isEmpty()) {
            PicturesEmpty(stringResource(R.string.pics_empty_search, query))
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(results, key = { it.stableKey }) { item ->
                    MediaTile(
                        item = item,
                        isFavorite = item.stableKey in favourites || item.id.toString() in favourites,
                        isSelected = false,
                        selectionMode = false,
                        onClick = { onClick(item) },
                        onLongClick = { onClick(item) }
                    )
                }
            }
        }
    }
}

/** Which pivot page the albums live on. */
private const val ALBUMS_TAB = 1

/** The pivot's own size, so a word that flies into it lands at exactly the right scale. */
private val PIVOT_FONT_SIZE = 72.sp

/** The size an album's name is written at on its tile: where a flight starts, and ends. */
private val ALBUM_TILE_NAME_SIZE = 16.sp
