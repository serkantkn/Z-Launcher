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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.serkantkn.zunelauncher.ui.components.ZuneEmptyState
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

@Composable
fun PicturesHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PicturesHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val hasPermission by viewModel.hasPermission.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
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

    val tabs = listOf(stringResource(R.string.pics_tab_camera_roll), stringResource(R.string.pics_tab_albums), stringResource(R.string.people_tab_favorites))
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

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
        val isWideScreen = LocalIsWideScreen.current

        val renderPage: @Composable (Int) -> Unit = { tabIndex ->
            ZunePageTransition {
                when (tabIndex) {
                    0 -> {
                        if (cameraRollImages.isEmpty()) {
                            ZuneEmptyState(errorMessage?.let { stringResource(R.string.pics_load_failed, it) } ?: stringResource(R.string.no_photos))
                        } else {
                            PhotoGrid(images = cameraRollImages) { clickedPhoto ->
                                viewingPhotosList = cameraRollImages
                                viewingInitialIndex = cameraRollImages.indexOf(clickedPhoto).coerceAtLeast(0)
                            }
                        }
                    }

                    1 -> {
                        if (selectedAlbum != null) {
                            val albumImages =
                                allImages.filter { it.bucketId == selectedAlbum!!.bucketId }
                            PhotoGrid(images = albumImages) { clickedPhoto ->
                                viewingPhotosList = albumImages
                                viewingInitialIndex = albumImages.indexOf(clickedPhoto).coerceAtLeast(0)
                            }
                        } else {
                            if (albums.isEmpty()) {
                                ZuneEmptyState(errorMessage?.let { stringResource(R.string.pics_albums_load_failed, it) } ?: stringResource(R.string.no_albums))
                            } else {
                                AlbumGrid(albums = albums) { viewModel.selectAlbum(it) }
                            }
                        }
                    }

                    2 -> {
                        if (favoriteImages.isEmpty()) {
                            ZuneEmptyState(errorMessage?.let { stringResource(R.string.pics_load_failed, it) } ?: stringResource(R.string.no_favorites))
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
                ZuneWideHubTitle(text = stringResource(R.string.pictures_hub))

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
                    PicturesPermissionRequest(
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
                    ZuneWidePanorama(tabs = tabs, fillPageHeight = true) { index ->
                        renderPage(index)
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.pictures_hub),
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
                    state = pager,
                    onSelected = { viewModel.selectAlbum(null) },
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
                    PicturesPermissionRequest(
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
                    ZuneLoopingPager(
                        state = pager,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ZuneDimens.ScreenPaddingHorizontal,
                            end = 48.dp
                        ),
                        pageSpacing = 24.dp
                    ) { page ->
                        renderPage(page)
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

/** Storage permission prompt shared by the phone and wide-screen branches. */
@Composable
private fun PicturesPermissionRequest(onRequestPermission: () -> Unit) {
    ZunePermissionRequest(
        title = stringResource(R.string.storage_permission_title).lowercase(),
        message = stringResource(R.string.storage_permission_message),
        buttonLabel = stringResource(R.string.grant_permission).lowercase(),
        onRequest = onRequestPermission
    )
}
