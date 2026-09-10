package com.serkantkn.zunelauncher.ui.screens.weather

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.WeatherSky
import com.serkantkn.zunelauncher.data.model.WindUnit
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneHubEntranceLayout
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubTitle
import com.serkantkn.zunelauncher.ui.components.ZuneWidePanorama
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

/** How far the title slides left for each pivot page, and how far it runs off the top. */
private val HEADER_DRIFT_PER_PAGE = 40.dp
private val HEADER_OVERFLOW_Y = (-24).dp

/** Space given back under the title — the empty tail of its 96sp line — so the pivot sits close. */
private val HEADER_TRIM = 34.dp

private const val PAGE_TODAY = 0
private const val PAGE_DAYS = 1
private const val PAGE_DETAILS = 2
private const val PAGE_AIR = 3
private const val PAGE_PLACES = 4

/**
 * Weather hub. The house skeleton — ZuneHubEntranceLayout → 18sp header → ZunePivotTabs →
 * ZuneLoopingPager → WindowsPhoneBottomBar — with the painted sky behind all of it, sliding a
 * little as the pivot moves so the pages feel like they sit in front of the weather.
 */
@Composable
fun WeatherHubScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeatherHubViewModel = viewModel()
) {
    val isWideScreen = LocalIsWideScreen.current
    val coroutineScope = rememberCoroutineScope()

    val snapshot by viewModel.snapshot.collectAsState()
    val summaries by viewModel.placeSummaries.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val canAddPlace by viewModel.canAddPlace.collectAsState()
    val temperatureUnit by viewModel.temperatureUnit.collectAsState()
    val windUnit by viewModel.windUnit.collectAsState()
    val animatedSky by viewModel.animatedSky.collectAsState()
    val updatesLeft by viewModel.updatesLeft.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val message by viewModel.message.collectAsState()

    val tabs = listOf(
        stringResource(R.string.weather_tab_today),
        stringResource(R.string.weather_tab_days),
        stringResource(R.string.weather_tab_details),
        stringResource(R.string.weather_tab_air),
        stringResource(R.string.weather_tab_places)
    )
    val pager = rememberLoopingPagerState(pageCount = tabs.size)

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.useMyLocation() }

    fun askForLocation() {
        locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    LaunchedEffect(Unit) { viewModel.onOpened() }
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3200)
            viewModel.clearMessage()
        }
    }
    BackHandler(enabled = true) { onBack() }

    // The sky follows whatever the selected place is doing, and falls back to a plain overcast
    // day before the first forecast has landed.
    val sky = snapshot?.now?.condition?.sky ?: WeatherSky.OVERCAST
    val isDay = snapshot?.now?.isDay ?: true
    val parallax = remember(pager) { pager }.let {
        val page = it.pagerState.currentPage + it.pagerState.currentPageOffsetFraction
        (page / (tabs.size - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val parallaxMultiplierPx = with(density) { HEADER_DRIFT_PER_PAGE.toPx() }
    // Negative: the title is meant to run off the top of the screen, as on the People hub.
    val overflowYPx = with(density) { HEADER_OVERFLOW_Y.toPx() }
    val headerTrimPx = with(density) { HEADER_TRIM.roundToPx() }
    val startPaddingPx = with(density) { ZuneDimens.ScreenPaddingHorizontal.toPx() }
    // Filled in once the title has been laid out; until then the drift is the plain one.
    var titleWidthPx by remember { mutableFloatStateOf(0f) }

    val bottomBarClearance = 56.dp + 16.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val bottomBarActions = listOf(
        WpBarAction(Icons.Default.Refresh, stringResource(R.string.common_refresh)) {
            viewModel.refreshManually()
        },
        WpBarAction(Icons.Default.LocationOn, stringResource(R.string.weather_tab_places)) {
            coroutineScope.launch { pager.animateScrollToPage(PAGE_PLACES) }
        }
    )
    val bottomBarMenuItems = buildList {
        add(
            WpBarMenuItem(
                stringResource(
                    if (temperatureUnit == TemperatureUnit.CELSIUS) R.string.weather_unit_fahrenheit
                    else R.string.weather_unit_celsius
                )
            ) {
                viewModel.setTemperatureUnit(
                    if (temperatureUnit == TemperatureUnit.CELSIUS) TemperatureUnit.FAHRENHEIT
                    else TemperatureUnit.CELSIUS
                )
            }
        )
        add(
            WpBarMenuItem(stringResource(nextWindUnit(windUnit).labelRes)) {
                viewModel.setWindUnit(nextWindUnit(windUnit))
            }
        )
        add(
            WpBarMenuItem(
                stringResource(
                    if (animatedSky) R.string.weather_animated_sky_turn_off
                    else R.string.weather_animated_sky_turn_on
                )
            ) { viewModel.toggleAnimatedSky() }
        )
        add(WpBarMenuItem(stringResource(R.string.weather_use_my_location)) { askForLocation() })
    }

    @Composable
    fun PageFor(index: Int) {
        val current = snapshot
        when {
            index == PAGE_PLACES -> ZunePageTransition {
                WeatherPlacesPage(
                    summaries = summaries,
                    results = searchResults,
                    isSearching = isSearching,
                    canAddPlace = canAddPlace,
                    temperatureUnit = temperatureUnit,
                    onSearch = { viewModel.search(it) },
                    onAdd = { viewModel.addPlace(it) },
                    onSelect = { viewModel.selectPlace(it) },
                    onRemove = { viewModel.removePlace(it) },
                    onUseMyLocation = { askForLocation() }
                )
            }

            current == null -> ZunePageTransition {
                WeatherEmptyPage(
                    isLoading = isRefreshing,
                    onAddPlace = { coroutineScope.launch { pager.animateScrollToPage(PAGE_PLACES) } }
                )
            }

            index == PAGE_TODAY -> ZunePageTransition {
                WeatherTodayPage(
                    snapshot = current,
                    temperatureUnit = temperatureUnit,
                    updatesLeft = updatesLeft,
                    dailyAllowance = viewModel.dailyAllowance,
                    isPremium = viewModel.isPremium
                )
            }

            index == PAGE_DAYS -> ZunePageTransition {
                WeatherDaysPage(snapshot = current, temperatureUnit = temperatureUnit)
            }

            index == PAGE_DETAILS -> ZunePageTransition {
                WeatherDetailsPage(
                    snapshot = current,
                    temperatureUnit = temperatureUnit,
                    windUnit = windUnit
                )
            }

            else -> ZunePageTransition {
                WeatherAirPage(air = current.airQuality, isPremium = viewModel.isPremium)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        WeatherSkyBackground(
            sky = sky,
            isDay = isDay,
            animated = animatedSky,
            parallax = parallax
        )

        ZuneHubEntranceLayout { bottomBarModifier ->
            if (isWideScreen) {
                Column(modifier = Modifier.fillMaxSize()) {
                    ZuneWideHubTitle(text = stringResource(R.string.hub_weather))
                    ZuneWidePanorama(
                        tabs = tabs,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        fillPageHeight = true
                    ) { index -> PageFor(index) }
                    Spacer(modifier = Modifier.height(bottomBarClearance))
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // The People hub's header, exactly: the hub's own name in panorama type,
                    // running off the top of the screen and drifting sideways as the pivot moves,
                    // with a second copy waiting a screen away for when the pivot loops round.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // Measured like the People hub's title so it sits at exactly the same
                            // height, then asked to report less room than it takes, which is what
                            // brings the pivot up close underneath instead of leaving a hole.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val trimmed = (placeable.height - headerTrimPx).coerceAtLeast(0)
                                layout(placeable.width, trimmed) { placeable.place(0, 0) }
                            }
                            .padding(top = 28.dp, bottom = 4.dp, start = ZuneDimens.ScreenPaddingHorizontal)
                    ) {
                        val pageCount = pager.pageCount
                        val cycle = (pager.pagerState.currentPage + pager.pagerState.currentPageOffsetFraction) % pageCount
                        val actualCycle = if (cycle < 0) cycle + pageCount else cycle
                        val threshold = (pageCount - 1).toFloat()

                        // A title too long for the screen pans far enough that its last letters
                        // arrive on the last pivot page; one that fits just drifts, as on People.
                        val overhangPx = (titleWidthPx - (screenWidthPx - startPaddingPx)).coerceAtLeast(0f)
                        val driftPerPage = maxOf(parallaxMultiplierPx, overhangPx / threshold.coerceAtLeast(1f))

                        val translationX1: Float
                        val translationX2: Float
                        if (actualCycle <= threshold) {
                            translationX1 = -actualCycle * driftPerPage
                            translationX2 = screenWidthPx
                        } else {
                            val fraction = actualCycle - threshold
                            translationX1 = -threshold * driftPerPage - fraction * screenWidthPx
                            translationX2 = screenWidthPx - fraction * screenWidthPx
                        }

                        WeatherHeaderTitle(
                            translationX = translationX1,
                            translationY = overflowYPx,
                            onWidthMeasured = { titleWidthPx = it }
                        )
                        WeatherHeaderTitle(translationX2, overflowYPx)
                    }
                    ZunePivotTabs(
                        tabs = tabs,
                        state = pager,
                        fontSize = 36.sp,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                            PageFor(page)
                        }
                    }
                    Spacer(modifier = Modifier.height(bottomBarClearance))
                }
            }

            WindowsPhoneBottomBar(
                modifier = Modifier.align(Alignment.BottomCenter).then(bottomBarModifier),
                actions = bottomBarActions,
                menuItems = bottomBarMenuItems
            )
        }

        // The refresh turn: the same spinner whether new readings arrived or the cached forecast
        // stood, so the hub always answers the touch.
        AnimatedVisibility(
            visible = isRefreshing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            RefreshBadge()
        }

        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .statusBarsPadding()
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp)
            ) {
                Text(
                    text = message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * One copy of the panorama title, at the People hub's size in every language. It is never shrunk
 * to fit: a long name simply runs off the right edge, and the drift that comes with each pivot
 * page brings the rest of it into view.
 */
@Composable
private fun WeatherHeaderTitle(
    translationX: Float,
    translationY: Float,
    onWidthMeasured: (Float) -> Unit = {}
) {
    Text(
        text = stringResource(R.string.hub_weather),
        style = MaterialTheme.typography.displayLarge.copy(
            fontWeight = FontWeight.Light,
            fontSize = 96.sp,
            letterSpacing = (-4).sp,
            lineHeight = 96.sp
        ),
        color = Color.White,
        maxLines = 1,
        softWrap = false,
        onTextLayout = { onWidthMeasured(it.size.width.toFloat()) },
        // Measured without a width limit, so a long name is laid out in full rather than clipped
        // at the screen edge — otherwise the drift would slide an empty space into view.
        modifier = Modifier
            .wrapContentWidth(align = Alignment.Start, unbounded = true)
            .graphicsLayer {
                this.translationX = translationX
                this.translationY = translationY
            }
    )
}

/** The turning arrow at the top of the hub while a refresh runs. */
@Composable
private fun RefreshBadge() {
    val transition = rememberInfiniteTransition(label = "weather_refresh")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "weather_refresh_angle"
    )
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(top = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp).rotate(angle)
            )
            Text(
                text = stringResource(R.string.weather_refreshing),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

/** Before there is a place to show: an invitation, not an error. */
@Composable
private fun WeatherEmptyPage(isLoading: Boolean, onAddPlace: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onAddPlace)
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(
                if (isLoading) R.string.weather_loading else R.string.weather_empty_title
            ),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, fontWeight = FontWeight.Light),
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.weather_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = stringResource(R.string.weather_add_place),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Color.White,
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.14f))
                .padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

private fun nextWindUnit(current: WindUnit): WindUnit = when (current) {
    WindUnit.KMH -> WindUnit.MS
    WindUnit.MS -> WindUnit.MPH
    WindUnit.MPH -> WindUnit.KMH
}
