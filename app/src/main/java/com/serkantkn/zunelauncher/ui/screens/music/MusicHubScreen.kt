package com.serkantkn.zunelauncher.ui.screens.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

@Composable
fun MusicHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val tabs = listOf("koleksiyon", "sanatçılar", "albümler", "şarkılar")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Text(
            text = "müzik",
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

        ZunePivotTabs(
            tabs = tabs,
            pagerState = pagerState,
            onSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
            },
            modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
        )

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
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "yakında",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Thin
                        ),
                        color = zuneColors.textDim,
                        modifier = Modifier.padding(top = ZuneDimens.SpacingHuge)
                    )
    
                    Text(
                        text = "${tabs[page]} yakında burada görünecek",
                        style = MaterialTheme.typography.bodyMedium,
                        color = zuneColors.textDim,
                        modifier = Modifier.padding(top = ZuneDimens.SpacingMd)
                    )
                }
            }
        }
    }
}
