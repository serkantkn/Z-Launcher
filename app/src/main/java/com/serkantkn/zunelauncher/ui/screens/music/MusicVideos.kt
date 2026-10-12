package com.serkantkn.zunelauncher.ui.screens.music

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.repository.LocalVideo
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.screens.video.VideoPlayer
import com.serkantkn.zunelauncher.ui.screens.video.formatTime
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.util.Locale

/**
 * The videos on the phone, as the "videos" of music + videos: a frame from each as a wide tile,
 * its length on it, its name under it. A tap plays it in the launcher's own player.
 */
@Composable
internal fun VideosPage(viewModel: MusicHubViewModel) {
    val hasPermission by viewModel.hasVideoPermission.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val context = LocalContext.current
    val isWideScreen = LocalIsWideScreen.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshVideoPermission() }

    LaunchedEffect(Unit) { viewModel.refreshVideoPermission() }

    if (!hasPermission) {
        ZunePermissionRequest(
            title = stringResource(R.string.music_video_permission_title).lowercase(Locale.getDefault()),
            message = stringResource(R.string.music_video_permission_message),
            buttonLabel = stringResource(R.string.grant_permission).lowercase(Locale.getDefault()),
            onRequest = {
                permissionLauncher.launch(
                    when {
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
                            android.Manifest.permission.READ_MEDIA_VIDEO,
                            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                        )
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(android.Manifest.permission.READ_MEDIA_VIDEO)
                        else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                )
            }
        )
        return
    }

    val shown = remember(videos, query) {
        if (query.isBlank()) videos else videos.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }
    if (shown.isEmpty()) {
        MusicEmpty(
            message = stringResource(if (query.isBlank()) R.string.music_no_videos else R.string.music_no_match),
            hint = if (query.isBlank()) stringResource(R.string.music_no_videos_hint) else null
        )
        return
    }

    LazyVerticalGrid(
        columns = if (isWideScreen) GridCells.Adaptive(220.dp) else GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(
            start = if (isWideScreen) 0.dp else ZuneDimens.ScreenPaddingHorizontal,
            end = if (isWideScreen) 0.dp else ZuneDimens.ScreenPaddingHorizontal,
            bottom = 120.dp
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        items(shown, key = { it.id }) { video ->
            VideoTile(video) { VideoPlayer.open(context, video.uri, video.title, video.mimeType) }
        }
    }
}

@Composable
private fun VideoTile(video: LocalVideo, onOpen: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(zuneColors.accentColor.copy(alpha = 0.25f))
        ) {
            // A frame from the film itself, drawn by the image loader's video decoder.
            AsyncImage(
                model = video.uri,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.durationMs > 0) {
                Text(
                    text = formatTime(video.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = video.title.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 17.sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (video.folder.isNotBlank()) {
            Text(
                text = video.folder.lowercase(Locale.getDefault()),
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
