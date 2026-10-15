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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import com.serkantkn.zunelauncher.ui.screens.video.NaturalOrder
import com.serkantkn.zunelauncher.ui.screens.video.VideoOrder
import com.serkantkn.zunelauncher.ui.screens.video.VideoPlayer
import com.serkantkn.zunelauncher.ui.screens.video.VideoResumeStore
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

    val watched by viewModel.videoResume.collectAsState()
    val order by viewModel.videoOrder.collectAsState()
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

    // The ones left part way, the last watched first: a film is carried on with more often than
    // it is started.
    val carryOn = remember(shown, watched) {
        shown.filter { (watched[it.resumeKey]?.first ?: 0L) > 0L }
            .sortedByDescending { watched[it.resumeKey]?.second ?: 0L }
            .take(if (isWideScreen) CARRY_ON_WIDE else CARRY_ON_PHONE)
    }
    val sections: List<Pair<String?, List<LocalVideo>>> = remember(shown, order) {
        when (order) {
            VideoOrder.DATE -> listOf(null to shown)
            VideoOrder.NAME -> listOf(null to shown.sortedWith(compareBy(NaturalOrder) { it.title }))
            VideoOrder.FOLDER -> shown.groupBy { it.folder }
                .toSortedMap(NaturalOrder)
                .map { (folder, inFolder) -> folder to inFolder.sortedWith(compareBy(NaturalOrder) { it.title }) }
        }
    }
    val open: (LocalVideo) -> Unit = { video -> VideoPlayer.open(context, video.uri, video.title, video.mimeType) }

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
        item(key = "order", span = { GridItemSpan(maxLineSpan) }) {
            OrderPicker(order, onPick = viewModel::setVideoOrder)
        }
        if (carryOn.isNotEmpty()) {
            item(key = "carry-on", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(stringResource(R.string.music_videos_continue))
            }
            items(carryOn, key = { "carry-on-${it.id}" }) { video ->
                VideoTile(video, watched[video.resumeKey]?.first ?: 0L, showLeft = true) { open(video) }
            }
        }
        sections.forEach { (folder, inSection) ->
            // A folder is headed by its name; the one list of all, only to part it from the ones
            // being carried on with.
            if (folder != null || carryOn.isNotEmpty()) {
                item(key = "section-${folder ?: "all"}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeading(
                        text = folder?.ifBlank { "—" }?.lowercase(Locale.getDefault()) ?: stringResource(R.string.music_videos_all),
                        count = if (folder != null) inSection.size else null
                    )
                }
            }
            items(inSection, key = { it.id }) { video ->
                VideoTile(video, watched[video.resumeKey]?.first ?: 0L) { open(video) }
            }
        }
    }
}

/** The ways to lay the videos out, as Windows Phone wrote a choice of views: words in a row. */
@Composable
private fun OrderPicker(order: VideoOrder, onPick: (VideoOrder) -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), modifier = Modifier.padding(bottom = 2.dp)) {
        listOf(
            VideoOrder.DATE to R.string.music_videos_by_date,
            VideoOrder.NAME to R.string.music_videos_by_name,
            VideoOrder.FOLDER to R.string.music_videos_by_folder
        ).forEach { (choice, label) ->
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 19.sp),
                color = if (choice == order) MaterialTheme.colorScheme.onBackground else zuneColors.textMuted,
                modifier = Modifier.clickable { onPick(choice) }.padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun SectionHeading(text: String, count: Int? = null) {
    val zuneColors = LocalZuneColors.current
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light, fontSize = 24.sp),
            color = zuneColors.accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = zuneColors.textMuted,
                modifier = Modifier.padding(start = 10.dp, bottom = 3.dp)
            )
        }
    }
}

@Composable
private fun VideoTile(video: LocalVideo, watchedMs: Long, showLeft: Boolean = false, onOpen: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val fraction = if (video.durationMs > 0 && watchedMs > 0) (watchedMs.toFloat() / video.durationMs).coerceIn(0f, 1f) else 0f
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
                    text = if (showLeft && fraction > 0f) {
                        stringResource(R.string.music_videos_left, formatTime(video.durationMs - watchedMs))
                    } else {
                        formatTime(video.durationMs)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (fraction > 0f) WATCHED_LINE else 0.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            // How much of it has been watched, as a line in the accent along the bottom.
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(WATCHED_LINE)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(zuneColors.accentColor)
                    )
                }
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

/** The name the player remembers a video of the media store by. */
private val LocalVideo.resumeKey: String get() = VideoResumeStore.keyOf(uri)

private val WATCHED_LINE = 4.dp
private const val CARRY_ON_PHONE = 4
private const val CARRY_ON_WIDE = 6
