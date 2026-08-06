package com.serkantkn.zunelauncher.ui.screens.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

private enum class SettingsTab(val title: String) {
    LOOK("görünüm"),
    HUBS("hub"),
    NOTIFICATIONS("bildirimler"),
    DISPLAY("ekran"),
    SOUND("ses"),
    CONNECTIVITY("bağlantılar"),
    SYSTEM("sistem"),
    ABOUT("hakkında")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()
    val fontScale by viewModel.fontScale.collectAsState()
    val animationsEnabled by viewModel.animationsEnabled.collectAsState()
    val socialHubLayout by viewModel.socialHubLayout.collectAsState()
    val directCallEnabled by viewModel.directCallEnabled.collectAsState()
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val tabs = SettingsTab.entries
    val actualPageCount = tabs.size
    val loopCount = 1000
    val initialPage = (loopCount / 2) * actualPageCount
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { loopCount * actualPageCount }
    )
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp.dp
        val density = LocalDensity.current
        val screenWidthPx = with(density) { screenWidthDp.toPx() }
        val parallaxMultiplierPx = with(density) { 40.dp.toPx() }
        val overflowYPx = with(density) { (-24).dp.toPx() }

        if (isWideScreen) {
            Text(
                text = "ayarlar",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 96.sp,
                    letterSpacing = (-4).sp,
                    lineHeight = 96.sp
                ),
                color = if (zuneColors.isDark) Color.White else Color.Black,
                modifier = Modifier.padding(
                    start = 72.dp,
                    top = 4.dp,
                    bottom = 24.dp
                ).graphicsLayer { translationY = overflowYPx }
            )

            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 72.dp,
                    end = 48.dp
                ),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(48.dp)
            ) {
                items(tabs.size) { index ->
                    Column(modifier = Modifier.width(360.dp)) {
                        Text(
                            text = tabs[index].title,
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Light),
                            color = zuneColors.accentColor,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        when (tabs[index]) {
                            SettingsTab.LOOK -> LookSettingsPage(
                                viewModel = viewModel,
                                themeMode = themeMode,
                                accentColor = accentColor,
                                fontScale = fontScale,
                                animationsEnabled = animationsEnabled,
                                onThemeModeChanged = viewModel::setThemeMode,
                                onAccentColorChanged = viewModel::setAccentColor,
                                onFontScaleChanged = viewModel::setFontScale,
                                onAnimationsChanged = viewModel::setAnimationsEnabled
                            )
                            SettingsTab.HUBS -> HubSettingsPage(
                                viewModel = viewModel,
                                socialHubLayout = socialHubLayout,
                                directCallEnabled = directCallEnabled,
                                onSocialHubLayoutChanged = viewModel::setSocialHubLayout,
                                onDirectCallChanged = viewModel::setDirectCallEnabled,
                                onClearBrowserHistory = viewModel::clearBrowserHistory
                            )
                            SettingsTab.NOTIFICATIONS -> NotificationsSettingsPage(viewModel = viewModel)
                            SettingsTab.DISPLAY -> DisplaySettingsPage(viewModel = viewModel)
                            SettingsTab.SOUND -> SoundSettingsPage(viewModel = viewModel)
                            SettingsTab.CONNECTIVITY -> ConnectivitySettingsPage(viewModel = viewModel)
                            SettingsTab.SYSTEM -> SystemSettingsPage(viewModel = viewModel)
                            SettingsTab.ABOUT -> AboutSettingsPage()
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
                    text = "ayarlar",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        lineHeight = 96.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        translationX = translationX1
                        translationY = overflowYPx
                    }
                )
                Text(
                    text = "ayarlar",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 96.sp,
                        letterSpacing = (-4).sp,
                        lineHeight = 96.sp
                    ),
                    color = if (zuneColors.isDark) Color.White else Color.Black,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.graphicsLayer {
                        translationX = translationX2
                        translationY = overflowYPx
                    }
                )
            }

            ZunePivotTabs(
                tabs = tabs.map { it.title },
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
                    scope.launch { pagerState.animateScrollToPage(targetPage) }
                },
                modifier = Modifier.padding(top = 12.dp, bottom = 18.dp)
            )

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
                when (tabs[actualPage]) {
                    SettingsTab.LOOK -> LookSettingsPage(
                        viewModel = viewModel,
                        themeMode = themeMode,
                        accentColor = accentColor,
                        fontScale = fontScale,
                        animationsEnabled = animationsEnabled,
                        onThemeModeChanged = viewModel::setThemeMode,
                        onAccentColorChanged = viewModel::setAccentColor,
                        onFontScaleChanged = viewModel::setFontScale,
                        onAnimationsChanged = viewModel::setAnimationsEnabled
                    )
                    SettingsTab.HUBS -> HubSettingsPage(
                        viewModel = viewModel,
                        socialHubLayout = socialHubLayout,
                        directCallEnabled = directCallEnabled,
                        onSocialHubLayoutChanged = viewModel::setSocialHubLayout,
                        onDirectCallChanged = viewModel::setDirectCallEnabled,
                        onClearBrowserHistory = viewModel::clearBrowserHistory
                    )
                    SettingsTab.NOTIFICATIONS -> NotificationsSettingsPage(viewModel = viewModel)
                    SettingsTab.DISPLAY -> DisplaySettingsPage(viewModel = viewModel)
                    SettingsTab.SOUND -> SoundSettingsPage(viewModel = viewModel)
                    SettingsTab.CONNECTIVITY -> ConnectivitySettingsPage(viewModel = viewModel)
                    SettingsTab.SYSTEM -> SystemSettingsPage(viewModel = viewModel)
                    SettingsTab.ABOUT -> AboutSettingsPage()
                }
            }
        }
    }
}
