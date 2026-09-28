package com.serkantkn.zunelauncher.ui.screens.apps

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.BuildConfig
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.animation.w10mStaggeredAnimation
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneLoopingPager
import com.serkantkn.zunelauncher.ui.components.ZunePageTransition
import com.serkantkn.zunelauncher.ui.components.ZunePivotTabs
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch

/**
 * The app list.
 *
 * Windows Phone's bones — one column, square icons, an accent square per letter, the lean under a
 * finger, and the alphabet as a whole screen you fall into from any heading — dressed in Zune's
 * type: a light lowercase word-mark across the top that runs off the right edge, hairlines instead
 * of boxes, and a lot of air.
 *
 * The pivot is the Zune part of the idea: the same apps read three ways — all of them, the ones
 * actually opened, and the ones that just arrived.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppsHubScreen(
    isCurrentPage: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: AppsHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val coroutineScope = rememberCoroutineScope()

    val groupedApps by viewModel.groupedApps.collectAsState()
    val availableLetters by viewModel.availableLetters.collectAsState()
    val favoritePackages by viewModel.favoritePackages.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val showingHidden by viewModel.showingHidden.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val newApps by viewModel.newApps.collectAsState()
    val frequentApps by viewModel.frequentApps.collectAsState()
    val usageGranted by viewModel.usageGranted.collectAsState()
    val iconPackKey by viewModel.iconPackKey.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var jumpListOpen by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<AppInfo?>(null) }
    var menuAnchorY by remember { mutableStateOf(0f) }

    val tabs = listOf(
        stringResource(R.string.apps_tab_all),
        stringResource(R.string.apps_tab_new)
    )
    // Not looping: the app list is itself a page of the launcher's own pager, so the pivot has to
    // let go at its ends — left of "tümü" is the Start screen, and right of "yeni" is nothing.
    val pager = rememberLoopingPagerState(pageCount = tabs.size, looping = false)
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()

    // ── The turnstile the whole list arrives on ──
    val animationProgress = remember { Animatable(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var clickedItemKey by remember { mutableStateOf<String?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsage()
                coroutineScope.launch {
                    animationProgress.snapTo(0f)
                    animationProgress.animateTo(1f, tween(ENTRANCE_MILLIS, easing = LinearEasing))
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            clickedItemKey = null
            animationProgress.snapTo(1f)
        } else if (animationProgress.value > 1f) {
            clickedItemKey = null
            animationProgress.snapTo(0f)
            animationProgress.animateTo(1f, tween(ENTRANCE_MILLIS, easing = LinearEasing))
        }
    }

    fun handleLaunch(key: String, action: () -> Unit) {
        clickedItemKey = key
        coroutineScope.launch {
            animationProgress.animateTo(2f, tween(EXIT_MILLIS, easing = LinearEasing))
            action()
        }
    }

    // ── The flat list the "all" page scrolls through ──
    val flatList = remember(groupedApps, frequentApps, showingHidden) {
        buildList {
            // The most-used apps sit above the alphabet rather than behind a pivot of their own:
            // five of them, close enough to the top of the screen to be worth the place.
            if (frequentApps.isNotEmpty() && !showingHidden) {
                add(AppsListItem.Frequent)
                frequentApps.forEach { app -> add(AppsListItem.Often(app)) }
            }
            groupedApps.forEach { (letter, apps) ->
                add(AppsListItem.Header(letter))
                apps.forEach { app -> add(AppsListItem.App(app)) }
            }
        }
    }
    // The pinned one is the last letter at or above the top of the window. A letter only wants a
    // band behind it while it is standing still over the list; scrolling past, it is a heading
    // like any other.
    val pinnedHeaderIndex by remember(flatList) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            (first downTo 0).firstOrNull { flatList.getOrNull(it) is AppsListItem.Header } ?: -1
        }
    }
    val letterIndexMap = remember(flatList) {
        buildMap {
            flatList.forEachIndexed { index, item ->
                if (item is AppsListItem.Header) put(item.letter, index)
            }
        }
    }

    val openJumpList: () -> Unit = {
        coroutineScope.launch { pager.animateScrollToPage(PAGE_ALL) }
        jumpListOpen = true
    }

    BackHandler(enabled = jumpListOpen || menuTarget != null || isSearchActive) {
        when {
            menuTarget != null -> menuTarget = null
            jumpListOpen -> jumpListOpen = false
            isSearchActive -> {
                isSearchActive = false
                viewModel.clearSearch()
            }
        }
    }

    // ── The bar ──
    val bottomBarActions = listOf(
        WpBarAction(Icons.Default.Search, stringResource(R.string.common_search)) {
            isSearchActive = !isSearchActive
            if (!isSearchActive) viewModel.clearSearch()
        },
        WpBarAction(Icons.Default.SortByAlpha, stringResource(R.string.apps_jump_title)) {
            openJumpList()
        }
    )
    val bottomBarMenuItems = buildList {
        if (hiddenApps.isNotEmpty()) {
            add(
                WpBarMenuItem(
                    stringResource(
                        if (showingHidden) R.string.apps_show_all else R.string.apps_show_hidden
                    )
                ) {
                    // The hidden apps are a view of the whole list, not of one pivot, and a
                    // half-typed search over them would only be in the way.
                    isSearchActive = false
                    viewModel.clearSearch()
                    coroutineScope.launch { pager.animateScrollToPage(PAGE_ALL) }
                    viewModel.toggleShowingHidden()
                }
            )
        }
        if (!usageGranted) {
            add(WpBarMenuItem(stringResource(R.string.apps_usage_access)) {
                viewModel.openUsageAccessSettings(context)
            })
        }
    }

    val bottomBarClearance = 56.dp + 12.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // ── One app, wherever it is drawn ──
    @Composable
    fun AppEntry(app: AppInfo, index: Int, inGrid: Boolean) {
        val key = "app_${app.packageName}"
        // Keyed on the pack as well, so changing icon packs redraws the list.
        val icon = remember(app.packageName, iconPackKey) { viewModel.iconFor(app) }
        val modifier = Modifier.w10mStaggeredAnimation(
            { animationProgress.value },
            index,
            clickedItemKey == key
        )
        val onClick = { handleLaunch(key) { viewModel.launchApp(app.packageName) } }
        val onLongPress: (Float) -> Unit = { y ->
            menuAnchorY = y
            menuTarget = app
        }

        if (inGrid) {
            AppGridCell(
                app = app,
                icon = icon,
                isPinned = app.packageName in favoritePackages,
                onClick = onClick,
                onLongPress = onLongPress,
                modifier = modifier
            )
        } else {
            AppListRow(
                app = app,
                icon = icon,
                isPinned = app.packageName in favoritePackages,
                isHidden = app.packageName in hiddenApps,
                onClick = onClick,
                onLongPress = onLongPress,
                modifier = modifier
            )
        }
    }

    // ── Pages ──
    @Composable
    fun AllPage() {
        ZunePageTransition {
            if (isWideScreen) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(WIDE_COLUMNS),
                    contentPadding = PaddingValues(bottom = 24.dp, end = ZuneDimens.SpacingLg),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = flatList,
                        key = { _, item -> item.key },
                        span = { _, item ->
                            if (item is AppsListItem.Frequent) {
                                GridItemSpan(maxLineSpan)
                            } else {
                                GridItemSpan(1)
                            }
                        }
                    ) { index, item ->
                        when (item) {
                            is AppsListItem.Header -> AppLetterHeader(
                                letter = item.letter,
                                onClick = { jumpListOpen = true },
                                inline = true,
                                modifier = Modifier.w10mStaggeredAnimation({ animationProgress.value }, index)
                            )

                            is AppsListItem.Frequent -> AppSectionRule(
                                text = stringResource(R.string.apps_tab_frequent),
                                modifier = Modifier.w10mStaggeredAnimation({ animationProgress.value }, index)
                            )

                            is AppsListItem.Often -> AppEntry(item.appInfo, index, inGrid = true)

                            is AppsListItem.App -> AppEntry(item.appInfo, index, inGrid = true)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp, end = ZuneDimens.SpacingLg),
                    modifier = Modifier.fillMaxSize()
                ) {
                    flatList.forEachIndexed { index, item ->
                        when (item) {
                            // A letter holds the top of the screen while its own apps go past it,
                            // and is pushed off by the next letter rather than fading.
                            is AppsListItem.Header -> stickyHeader(key = item.key) {
                                AppLetterHeader(
                                    letter = item.letter,
                                    onClick = { jumpListOpen = true },
                                    pinned = index <= pinnedHeaderIndex,
                                    modifier = Modifier.w10mStaggeredAnimation(
                                        { animationProgress.value },
                                        index
                                    )
                                )
                            }

                            is AppsListItem.Frequent -> item(key = item.key) {
                                AppSectionRule(
                                    text = stringResource(R.string.apps_tab_frequent),
                                    modifier = Modifier.w10mStaggeredAnimation(
                                        { animationProgress.value },
                                        index
                                    )
                                )
                            }

                            is AppsListItem.Often ->
                                item(key = item.key) { AppEntry(item.appInfo, index, inGrid = false) }

                            is AppsListItem.App ->
                                item(key = item.key) { AppEntry(item.appInfo, index, inGrid = false) }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ShortListPage(apps: List<AppInfo>, emptyNotice: @Composable () -> Unit) {
        ZunePageTransition {
            if (apps.isEmpty()) {
                emptyNotice()
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, end = ZuneDimens.SpacingLg),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                        AppEntry(app, index, inGrid = false)
                    }
                }
            }
        }
    }

    @Composable
    fun SearchPage() {
        if (searchResults.isEmpty()) {
            AppsNotice(message = stringResource(R.string.apps_search_empty, searchQuery))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp, end = ZuneDimens.SpacingLg),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(searchResults, key = { _, app -> app.packageName }) { index, app ->
                    AppEntry(app, index, inGrid = false)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = if (isWideScreen) 72.dp else ZuneDimens.ScreenPaddingHorizontal,
                    top = 48.dp
                )
        ) {
            Text(
                text = stringResource(R.string.apps_hub),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                ),
                color = if (zuneColors.isDark) {
                    Color.White.copy(alpha = 0.9f)
                } else {
                    Color.Black.copy(alpha = 0.85f)
                },
                maxLines = 1,
                softWrap = false
            )

            // The Zune word-mark: the section's name, huge and lowercase, the next one bleeding
            // off the right edge so it is plain there is more than one way to read the list.
            // The column already carries the screen's margin, so the pivot adds none of its own.
            ZunePivotTabs(
                tabs = tabs,
                state = pager,
                startPadding = 0.dp,
                modifier = Modifier.padding(top = 2.dp)
            )

            AnimatedVisibility(visible = isSearchActive, enter = fadeIn(), exit = fadeOut()) {
                ZuneSearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    placeholder = stringResource(R.string.search_apps),
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp, end = ZuneDimens.SpacingLg)
                )
            }

            if (showingHidden) {
                AppSectionRule(
                    text = stringResource(R.string.apps_hidden_section),
                    modifier = Modifier.padding(end = ZuneDimens.SpacingLg)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isSearchActive && searchQuery.isNotBlank() -> SearchPage()

                    else -> ZuneLoopingPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                        when (page) {
                            PAGE_NEW -> ShortListPage(newApps) {
                                AppsNotice(message = stringResource(R.string.apps_new_empty))
                            }

                            else -> AllPage()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(bottomBarClearance))
        }

        WindowsPhoneBottomBar(
            actions = bottomBarActions,
            menuItems = bottomBarMenuItems,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── The alphabet, as a screen ──
        if (jumpListOpen) {
            AppsJumpList(
                availableLetters = availableLetters,
                onLetterSelected = { letter ->
                    jumpListOpen = false
                    letterIndexMap[letter]?.let { index ->
                        coroutineScope.launch {
                            if (isWideScreen) {
                                gridState.animateScrollToItem(index)
                            } else {
                                listState.animateScrollToItem(index)
                            }
                        }
                    }
                },
                onDismiss = { jumpListOpen = false }
            )
        }

        // ── What can be done with one app ──
        menuTarget?.let { app ->
            val pinned = app.packageName in favoritePackages
            val hidden = app.packageName in hiddenApps
            val chooserTitle = stringResource(R.string.apps_share_chooser)
            val favouritesFull = stringResource(R.string.apps_favorites_limit)

            AppContextMenu(
                title = app.label,
                anchorY = menuAnchorY,
                onDismiss = { menuTarget = null },
                items = buildList {
                    add(
                        AppMenuItem(
                            stringResource(if (pinned) R.string.apps_unpin else R.string.apps_pin)
                        ) {
                            val room = BuildConfig.IS_PREMIUM || pinned || favoritePackages.size < FREE_PIN_LIMIT
                            if (room) {
                                viewModel.toggleFavorite(app.packageName)
                            } else {
                                android.widget.Toast
                                    .makeText(context, favouritesFull, android.widget.Toast.LENGTH_SHORT)
                                    .show()
                            }
                            menuTarget = null
                        }
                    )
                    add(
                        AppMenuItem(
                            stringResource(if (hidden) R.string.apps_unhide else R.string.apps_hide)
                        ) {
                            viewModel.setHidden(app.packageName, !hidden)
                            menuTarget = null
                        }
                    )
                    add(AppMenuItem(stringResource(R.string.common_share)) {
                        viewModel.shareApp(app, chooserTitle)
                        menuTarget = null
                    })
                    add(AppMenuItem(stringResource(R.string.apps_app_info)) {
                        viewModel.openAppSettings(app.packageName)
                        menuTarget = null
                    })
                    if (!viewModel.isSystemApp(app.packageName)) {
                        add(AppMenuItem(stringResource(R.string.apps_uninstall), destructive = true) {
                            viewModel.uninstall(app.packageName)
                            menuTarget = null
                        })
                    }
                }
            )
        }
    }
}

/** A row of the "all" page: either a letter's heading or an app under it. */
private sealed class AppsListItem {
    abstract val key: String

    data class Header(val letter: Char) : AppsListItem() {
        override val key: String get() = "header_$letter"
    }

    /** The heading over the most-used apps. */
    data object Frequent : AppsListItem() {
        override val key: String get() = "rule_frequent"
    }

    /**
     * One of the most-used apps, at the top of the list. It is the same app as the one under its
     * own letter further down, so it needs a key of its own or the list has two of the same.
     */
    data class Often(val appInfo: AppInfo) : AppsListItem() {
        override val key: String get() = "often_${appInfo.packageName}"
    }

    data class App(val appInfo: AppInfo) : AppsListItem() {
        override val key: String get() = "app_${appInfo.packageName}"
    }
}

private const val PAGE_ALL = 0
private const val PAGE_NEW = 1
private const val WIDE_COLUMNS = 6
private const val ENTRANCE_MILLIS = 1200
private const val EXIT_MILLIS = 800

/** How many apps the free version may pin to Start. */
private const val FREE_PIN_LIMIT = 10
