package com.serkantkn.zunelauncher.ui.screens.pictures

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
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
    val albums by viewModel.albums.collectAsState()
    val favoriteImages by viewModel.favoriteImages.collectAsState()
    val favoritePhotoIds by viewModel.favoritePhotoIds.collectAsState()
    val selectedAlbum by viewModel.selectedAlbum.collectAsState()

    val tabs = listOf("film rulosu", "albümler", "favoriler")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    
    var viewingPhoto by remember { mutableStateOf<MediaImage?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            viewModel.onPermissionResult(granted)
        }
    )

    // Intercept back button if viewing a photo or looking inside an album
    BackHandler(enabled = viewingPhoto != null || selectedAlbum != null) {
        if (viewingPhoto != null) {
            viewingPhoto = null
        } else if (selectedAlbum != null) {
            viewModel.selectAlbum(null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Text(
                text = "fotoğraflar",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                ),
                color = zuneColors.accentColor,
                modifier = Modifier.padding(
                    top = 60.dp,
                    bottom = 4.dp,
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal
                )
            )

            // Tabs -> Zune Pivot
            ZunePivotTabs(
                tabs = tabs,
                pagerState = pagerState,
                onSelected = { index ->
                    coroutineScope.launch { pagerState.animateScrollToPage(index) }
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
                        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = 48.dp
                    ),
                    pageSpacing = 24.dp
                ) { page ->
                    ZunePageTransition {
                        when (tabs[page]) {
                        "film rulosu" -> {
                            if (allImages.isEmpty()) {
                                EmptyStateView(stringResource(R.string.no_photos))
                            } else {
                                PhotoGrid(images = allImages) { viewingPhoto = it }
                            }
                        }
                        "albümler" -> {
                            if (selectedAlbum != null) {
                                val albumImages = allImages.filter { it.bucketId == selectedAlbum!!.bucketId }
                                PhotoGrid(images = albumImages) { viewingPhoto = it }
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
                                PhotoGrid(images = favoriteImages) { viewingPhoto = it }
                            }
                        }
                    }
                }
            }
        }
        }

        // Full Screen Viewer Overlay
        PhotoViewer(
            photo = viewingPhoto,
            isFavorite = viewingPhoto?.id?.toString() in favoritePhotoIds,
            onDismiss = { viewingPhoto = null },
            onToggleFavorite = { viewingPhoto?.let { viewModel.toggleFavorite(it.id) } }
        )
    }
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
