package com.serkantkn.zunelauncher.ui.screens.pictures

import android.app.Activity
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.MediaAlbum
import com.serkantkn.zunelauncher.data.model.MediaImage
import com.serkantkn.zunelauncher.ui.components.PhotoViewer
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

@Composable
fun PicturesHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicturesHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val allImages by viewModel.allImages.collectAsState()
    val cameraRollImages by viewModel.cameraRollImages.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val favoriteImages by viewModel.favoriteImages.collectAsState()
    val favoritePhotoIds by viewModel.favoritePhotoIds.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()

    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasPermission) {
            viewModel.loadMedia()
        }
    }

    val tabs = listOf("film rulosu", "albümler", "favoriler")
    val actualPageCount = tabs.size
    val loopCount = 1000
    val initialPage = (loopCount / 2) * actualPageCount
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { loopCount * actualPageCount }
    )
    val coroutineScope = rememberCoroutineScope()

    var viewingPhotosList by remember { mutableStateOf<List<MediaImage>?>(null) }
    var viewingInitialIndex by remember { mutableIntStateOf(0) }
    var editingPhoto by remember { mutableStateOf<MediaImage?>(null) }

    val deleteIntentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.loadMedia()
            viewingPhotosList = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onPermissionResult(granted)
        }
    )

    // Intercept back button if editing a photo, viewing a photo or looking inside an album
    BackHandler(enabled = editingPhoto != null || viewingPhotosList != null || selectedAlbum != null) {
        if (editingPhoto != null) {
            editingPhoto = null
        } else if (viewingPhotosList != null) {
            viewingPhotosList = null
        } else if (selectedAlbum != null) {
            viewModel.selectAlbum(null)
        }
    }

    ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
        Box(modifier = Modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val density = LocalDensity.current
        val screenWidthPx = with(density) { screenWidthDp.toPx() }
        val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
        val overflowYPx = with(density) { (-24).dp.toPx() }
        val isWideScreen = LocalIsWideScreen.current

        val renderPage: @Composable (String) -> Unit = { tabName ->
            ZunePageTransition {
                when (tabName) {
                    "film rulosu" -> {
                        if (cameraRollImages.isEmpty()) {
                            EmptyStateView(stringResource(R.string.no_photos))
                        } else {
                            PhotoGrid(images = cameraRollImages) { clickedPhoto ->
                                viewingPhotosList = cameraRollImages
                                viewingInitialIndex = cameraRollImages.indexOf(clickedPhoto).coerceAtLeast(0)
                            }
                        }
                    }

                    "albümler" -> {
                        if (selectedAlbum != null) {
                            val albumImages =
                                allImages.filter { it.bucketId == selectedAlbum!!.bucketId }
                            PhotoGrid(images = albumImages) { clickedPhoto ->
                                viewingPhotosList = albumImages
                                viewingInitialIndex = albumImages.indexOf(clickedPhoto).coerceAtLeast(0)
                            }
                        } else {
                            if (albums.isEmpty()) {
                                EmptyStateView(stringResource(R.string.no_albums))
                            } else {
                                AlbumGrid(albums = albums) { viewModel.selectAlbum(it) }
                            }
                        }
                    }

                    "favoriler" -> {
                        if (favoriteImages.isEmpty()) {
                            EmptyStateView(stringResource(R.string.no_favorites))
                        } else {
                            PhotoGrid(images = favoriteImages) { clickedPhoto ->
                                viewingPhotosList = favoriteImages
                                viewingInitialIndex = favoriteImages.indexOf(clickedPhoto).coerceAtLeast(0)
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (isWideScreen) {
                Text(
                    text = "fotoğraflar",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        lineHeight = 96.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    modifier = Modifier.padding(
                            start = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
                        top = 4.dp,
                        bottom = 24.dp
                    ).graphicsLayer { translationY = overflowYPx }
                )

                // Selected Album Context
                AnimatedContent(
                    targetState = selectedAlbum,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "album_title"
                ) { album ->
                    if (album != null) {
                        Text(
                            text = album.bucketName.lowercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = zuneColors.accentColor,
                            modifier = Modifier.padding(
                                bottom = ZuneDimens.SpacingMd,
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = ZuneDimens.ScreenPaddingHorizontal
                            )
                        )
                    }
                }

                if (!hasPermission) {
                    PermissionRequestView(
                        onRequestPermission = {
                            val permission =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    android.Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                            permissionLauncher.launch(permission)
                        }
                    )
                } else if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = zuneColors.accentColor)
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                                start = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
                            end = 48.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(48.dp)
                    ) {
                        items(tabs.size) { index ->
                            Column(modifier = Modifier.width(360.dp).fillMaxHeight()) {
                                Text(
                                    text = tabs[index],
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                                    color = zuneColors.accentColor,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    renderPage(tabs[index])
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "fotoğraflar",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(
                        top = 28.dp,
                        bottom = 4.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal
                    )
                )

                // Tabs -> Zune Pivot
                ZunePivotTabs(
                    tabs = tabs,
                    pagerState = pagerState,
                    onSelected = { index ->
                        val current = pagerState.currentPage
                        val size = actualPageCount
                        val currentActual = ((current % size) + size) % size
                        var diff = index - currentActual
                        if (diff > size / 2) {
                            diff -= size
                        } else if (diff < -size / 2) {
                            diff += size
                        }
                        val targetPage = current + diff
                        coroutineScope.launch { pagerState.animateScrollToPage(targetPage) }
                        viewModel.selectAlbum(null)
                    },
                    modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                )

                // Selected Album Title Context
                AnimatedContent(
                    targetState = selectedAlbum,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "album_title"
                ) { album ->
                    if (album != null) {
                        Text(
                            text = album.bucketName.lowercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = zuneColors.accentColor,
                            modifier = Modifier.padding(
                                bottom = ZuneDimens.SpacingMd,
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = ZuneDimens.ScreenPaddingHorizontal
                            )
                        )
                    }
                }

                // Content Area
                if (!hasPermission) {
                    PermissionRequestView(
                        onRequestPermission = {
                            val permission =
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    android.Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                            permissionLauncher.launch(permission)
                        }
                    )
                } else if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = zuneColors.accentColor)
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ZuneDimens.ScreenPaddingHorizontal,
                            end = 48.dp
                        ),
                        pageSpacing = 24.dp
                    ) { page ->
                        val actualPage = page % actualPageCount
                        renderPage(tabs[actualPage])
                    }
                }
            } // close else
        } // close Column

        // Full Screen Photo Viewer Overlay (outside Column in root Box)
        PhotoViewer(
            photos = viewingPhotosList,
            initialIndex = viewingInitialIndex,
            favoritePhotoIds = favoritePhotoIds,
            onDismiss = { viewingPhotosList = null },
            onToggleFavorite = { photo -> viewModel.toggleFavorite(photo.id) },
            onEditPhoto = { photo -> editingPhoto = photo },
            onDeletePhoto = { photo ->
                val activity = context as? Activity ?: return@PhotoViewer
                viewModel.deletePhoto(
                    activity = activity,
                    photo = photo,
                    onIntentSenderRequired = { intentSender ->
                        deleteIntentSenderLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    },
                    onDeleted = {
                        viewingPhotosList = null
                    }
                )
            }
        )

        // Full Screen Photo Editor Overlay
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
    }
} // close root Box
}

@Composable
private fun PhotoGrid(
    images: List<MediaImage>,
    onPhotoClick: (MediaImage) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(images, key = { it.id }) { image ->
            AsyncImage(
                model = image.uri,
                contentDescription = image.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(Color.DarkGray)
                    .clickable { onPhotoClick(image) }
            )
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<MediaAlbum>,
    onAlbumClick: (MediaAlbum) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(albums, key = { it.bucketId }) { album ->
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clickable { onAlbumClick(album) }
            ) {
                AsyncImage(
                    model = album.coverUri,
                    contentDescription = album.bucketName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray)
                )
                // Dark overlay gradient for text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000))
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = album.bucketName.lowercase(),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${album.photoCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRequestView(onRequestPermission: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = Modifier.padding(top = ZuneDimens.SpacingHuge)
    ) {
        Text(
            text = stringResource(R.string.storage_permission_title).lowercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.storage_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            color = zuneColors.textMuted
        )
        Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = zuneColors.accentColor,
                contentColor = Color.White
            )
        ) {
            Text(text = stringResource(R.string.grant_permission).lowercase())
        }
    }
}

@Composable
private fun EmptyStateView(message: String) {
    Text(
        text = message.lowercase(),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.textDim,
        modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
    )
}
