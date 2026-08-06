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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.ui.theme.ZuneExtendedColors
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import androidx.lifecycle.viewmodel.compose.viewModel
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

            val pages = listOf("oynatılıyor", "albümler", "sanatçılar", "şarkılar")
            val actualPageCount = pages.size
            val loopCount = 1000
            val initialPage = (loopCount / 2) * actualPageCount
            val pagerState = rememberPagerState(
                initialPage = initialPage,
                pageCount = { loopCount * actualPageCount }
            )

            val configuration = LocalConfiguration.current
            val screenWidthDp = configuration.screenWidthDp.dp
            val density = LocalDensity.current
            val screenWidthPx = with(density) { screenWidthDp.toPx() }
            val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
            val overflowYPx = with(density) { (-24).dp.toPx() }
            val isWideScreen = LocalIsWideScreen.current
            
            Column(modifier = Modifier.fillMaxSize()) {
                if (isWideScreen) {
                    Text(
                        text = "müzik",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Light,
                            fontSize = 96.sp,
                            letterSpacing = (-4).sp,
                            lineHeight = 96.sp
                        ),
                        color = if (LocalZuneColors.current.isDark) Color.White else Color.Black,
                        modifier = Modifier.padding(
                            start = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
                            top = 4.dp,
                            bottom = 24.dp
                        ).graphicsLayer { translationY = overflowYPx }
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
                            end = 48.dp
                        ),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(48.dp)
                    ) {
                        items(pages.size) { index ->
                            Column(modifier = Modifier.width(360.dp)) {
                                Text(
                                    text = pages[index],
                                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                                    color = LocalZuneColors.current.accentColor,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                                when (index) {
                                    0 -> NowPlayingPage(viewModel)
                                    1 -> AlbumsPage(viewModel)
                                    2 -> ArtistsPage(viewModel)
                                    3 -> SongsPage(viewModel)
                                }
                            }
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
                    val cycle = (pagerState.currentPage + pagerState.currentPageOffsetFraction) % actualPageCount
                    val actualCycle = if (cycle < 0) cycle + actualPageCount else cycle
                    val threshold = (actualPageCount - 1).toFloat()

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
                        text = "müzik",
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
                        text = "müzik",
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
                    },
                    modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val actualPage = page % actualPageCount
                    when (actualPage) {
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
