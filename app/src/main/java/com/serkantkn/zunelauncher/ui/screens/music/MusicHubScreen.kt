package com.serkantkn.zunelauncher.ui.screens.music

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import java.util.concurrent.TimeUnit

@Composable
fun MusicHubScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: MusicHubViewModel = viewModel()
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()
    
    // Animate accent color transition
    val animatedAccent by animateColorAsState(
        targetValue = dominantColor ?: LocalZuneColors.current.accentColor,
        animationSpec = tween(durationMillis = 1000),
        label = "AccentColorAnimation"
    )

    // Override theme for this hub
    val currentZuneColors = LocalZuneColors.current
    val musicHubColors = currentZuneColors.copy(
        accentColor = animatedAccent
    )

    CompositionLocalProvider(LocalZuneColors provides musicHubColors) {
        ZuneHubEntranceLayout(modifier = modifier) { bottomBarModifier ->
            val backgroundColor = if (mediaState.albumArt != null) Color.Black.copy(alpha = 0.8f) else Color.Transparent
            Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            
            // Dynamic Album Art Background
            mediaState.albumArt?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f // Dim the background
                )
            }

            val pages = listOf(stringResource(R.string.music_tab_now_playing), stringResource(R.string.pics_tab_albums), stringResource(R.string.music_tab_artists), stringResource(R.string.music_tab_songs))
            val pager = rememberLoopingPagerState(pageCount = pages.size)

            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp.dp
            val density = LocalDensity.current
            val screenWidthPx = with(density) { screenWidthDp.toPx() }
            val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
            val overflowYPx = with(density) { (-24).dp.toPx() }
            val isWideScreen = LocalIsWideScreen.current
            
            Column(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    ZuneWideHubTitle(text = stringResource(R.string.music_hub))

                    ZuneWidePanorama(tabs = pages) { index ->
                        when (index) {
                            0 -> NowPlayingPage(viewModel)
                            1 -> AlbumsPage(viewModel)
                            2 -> ArtistsPage(viewModel)
                            3 -> SongsPage(viewModel)
                        }
                    }
                } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = 28.dp,
                            bottom = 4.dp,
                            start = ZuneDimens.ScreenPaddingHorizontal
                        )
                ) {
                    val pageCount = pager.pageCount
                    val cycle = (pager.pagerState.currentPage + pager.pagerState.currentPageOffsetFraction) % pageCount
                    val actualCycle = if (cycle < 0) cycle + pageCount else cycle
                    val threshold = (pageCount - 1).toFloat()

                    val translationX1: Float
                    val translationX2: Float

                    if (actualCycle <= threshold) {
                        translationX1 = -actualCycle * parallaxMultiplierPx
                        translationX2 = screenWidthPx
                    } else {
                        val fraction = actualCycle - threshold
                        translationX1 = -threshold * parallaxMultiplierPx - fraction * screenWidthPx
                        translationX2 = screenWidthPx - fraction * screenWidthPx
                    }

                    Text(
                        text = stringResource(R.string.music_hub),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 96.sp,
                            letterSpacing = (-4).sp,
                            lineHeight = 96.sp
                        ),
                        color = if (LocalZuneColors.current.isDark) Color.White else Color.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            translationX = translationX1
                            translationY = overflowYPx
                        }
                    )
                    Text(
                        text = stringResource(R.string.music_hub),
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 96.sp,
                            letterSpacing = (-4).sp,
                            lineHeight = 96.sp
                        ),
                        color = if (LocalZuneColors.current.isDark) Color.White else Color.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            translationX = translationX2
                            translationY = overflowYPx
                        }
                    )
                }

                ZunePivotTabs(
                    tabs = pages,
                    state = pager,
                    fontSize = 36.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                )

                ZuneLoopingPager(
                    state = pager,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> NowPlayingPage(viewModel)
                        1 -> AlbumsPage(viewModel)
                        2 -> ArtistsPage(viewModel)
                        3 -> SongsPage(viewModel)
                    }
                }
                }
            }
        }
    }
}
}

@Composable
fun NowPlayingPage(viewModel: MusicHubViewModel) {
    val mediaState by viewModel.mediaState.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))
        
        // Album Art
        if (mediaState.albumArt != null) {
            Image(
                bitmap = mediaState.albumArt!!.asImageBitmap(),
                contentDescription = stringResource(R.string.music_album_art),
                modifier = Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(Color.DarkGray, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.music_no_media),
                    color = Color.LightGray,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title and Artist
        Text(
            text = mediaState.title.ifEmpty { stringResource(R.string.music_unknown_song) },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = Color.White,
            maxLines = 1
        )
        Text(
            text = mediaState.artist.ifEmpty { stringResource(R.string.music_unknown_artist) },
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            color = LocalZuneColors.current.accentColor,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Controls
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(Color(0x33FFFFFF), RoundedCornerShape(24.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.globalMediaController.skipToPrevious() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.music_previous),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                IconButton(
                    onClick = { viewModel.globalMediaController.playPause() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (mediaState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.music_play_pause),
                        tint = LocalZuneColors.current.accentColor,
                        modifier = Modifier.size(64.dp)
                    )
                }
                
                IconButton(
                    onClick = { viewModel.globalMediaController.skipToNext() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.music_next),
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun AlbumsPage(viewModel: MusicHubViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.music_albums_wip), color = LocalZuneColors.current.textMuted)
    }
}

@Composable
fun ArtistsPage(viewModel: MusicHubViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.music_artists_wip), color = LocalZuneColors.current.textMuted)
    }
}

@Composable
fun SongsPage(viewModel: MusicHubViewModel) {
    val songs by viewModel.localSongs.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(errorMessage?.let { stringResource(R.string.music_load_failed, it) } ?: stringResource(R.string.music_not_found), color = LocalZuneColors.current.textMuted)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = ZuneDimens.ScreenPaddingHorizontal,
                bottom = 120.dp
            )
        ) {
            itemsIndexed(songs) { index, song ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.playLocalSong(index) }
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White,
                        maxLines = 1
                    )
                    
                    val durationStr = String.format(
                        "%02d:%02d",
                        TimeUnit.MILLISECONDS.toMinutes(song.duration),
                        TimeUnit.MILLISECONDS.toSeconds(song.duration) - 
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(song.duration))
                    )
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalZuneColors.current.textMuted,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            text = durationStr,
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalZuneColors.current.textDim
                        )
                    }
                }
            }
        }
    }
}
