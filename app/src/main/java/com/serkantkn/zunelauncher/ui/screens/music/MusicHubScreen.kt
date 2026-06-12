package com.serkantkn.zunelauncher.ui.screens.music

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.ui.theme.ZuneExtendedColors
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import java.util.concurrent.TimeUnit

@Composable
fun MusicHubScreen(
    onBack: () -> Unit = {},
    viewModel: MusicHubViewModel = viewModel()
) {
    val mediaState by viewModel.mediaState.collectAsState()
    val dominantColor by viewModel.dominantColor.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    
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
        val backgroundColor = if (mediaState.albumArt != null) Color.Black.copy(alpha = 0.8f) else MaterialTheme.colorScheme.background
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

            val pagerState = rememberPagerState(pageCount = { 4 })
            val pages = listOf("oynatılıyor", "albümler", "sanatçılar", "şarkılar")

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "müzik",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = animatedAccent,
                    modifier = Modifier.padding(
                        top = 60.dp,
                        bottom = 4.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal
                    )
                )

                ZunePivotTabs(
                    tabs = pages,
                    pagerState = pagerState,
                    onSelected = { index ->
                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                    },
                    modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                )

                HorizontalPager(
                    state = pagerState,
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
                contentDescription = "Album Art",
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
                    text = "No Media",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title and Artist
        Text(
            text = mediaState.title.ifEmpty { "Bilinmeyen Şarkı" },
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = Color.White,
            maxLines = 1
        )
        Text(
            text = mediaState.artist.ifEmpty { "Bilinmeyen Sanatçı" },
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
                        contentDescription = "Previous",
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
                        contentDescription = "Play/Pause",
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
                        contentDescription = "Next",
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
        Text("Albümler (Yapım Aşamasında)", color = LocalZuneColors.current.textMuted)
    }
}

@Composable
fun ArtistsPage(viewModel: MusicHubViewModel) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Sanatçılar (Yapım Aşamasında)", color = LocalZuneColors.current.textMuted)
    }
}

@Composable
fun SongsPage(viewModel: MusicHubViewModel) {
    val songs by viewModel.localSongs.collectAsState()
    
    if (songs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Müzik bulunamadı", color = LocalZuneColors.current.textMuted)
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
