package com.serkantkn.zunelauncher.ui.screens.apps

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.BuildConfig
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.components.ZuneSearchBar
import com.serkantkn.zunelauncher.ui.screens.home.Win8CircleButton
import com.serkantkn.zunelauncher.ui.screens.home.win8PinchZoom
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.toSquareImageBitmap
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * The Windows 8.1 Apps view, which rises from under the Start screen on a tablet.
 *
 * The same shape as the Start screen it replaces — the title top-left where "start" was, the
 * same margins — with every installed app set out under a letter in columns that read down and
 * then across, the whole thing scrolling sideways. Beside the title sits the little menu that
 * re-orders the list: by name, by when it was installed, by how much it is used. A tap opens an
 * app; a hold marks it and brings the app bar up from the bottom with what can be done to it.
 * Pinching, or tapping a letter, drops into the alphabet to jump. The arrow at the bottom-left,
 * a pull downwards, and the Back key all go back up to Start.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Windows8AppsScreen(
    /** Bumped by Start's search button, so the view arrives with the search box open. */
    searchRequests: Int,
    onBackToStart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppsHubViewModel = viewModel()
) {
    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val fg = if (zuneColors.isDark) Color.White else Color.Black
    val scope = rememberCoroutineScope()

    val groupedApps by viewModel.groupedApps.collectAsState()
    val availableLetters by viewModel.availableLetters.collectAsState()
    val favoritePackages by viewModel.favoritePackages.collectAsState()
    val hiddenApps by viewModel.hiddenApps.collectAsState()
    val showingHidden by viewModel.showingHidden.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val usage by viewModel.usage.collectAsState()
    val usageGranted by viewModel.usageGranted.collectAsState()
    val iconPackKey by viewModel.iconPackKey.collectAsState()

    var sort by rememberSaveable { mutableStateOf(Win8AppsSort.BY_NAME) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var jumpListOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<AppInfo?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(searchRequests) { if (searchRequests > 0) isSearchActive = true }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshUsage() }

    // The sections, cut afresh when the order or the apps change. "Today" is dated from the
    // moment the view is composed, which is close enough for a list of installs.
    val sections = remember(sort, groupedApps, usage) {
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        buildWin8AppSections(sort, groupedApps, usage, now, startOfToday)
    }
    val searching = isSearchActive && searchQuery.isNotBlank()
    val shownSections = if (searching) {
        if (searchResults.isEmpty()) emptyList() else listOf(Win8AppSection(id = "search", apps = searchResults))
    } else {
        sections
    }

    fun closeSearch() {
        isSearchActive = false
        viewModel.clearSearch()
    }

    BackHandler {
        when {
            selected != null -> selected = null
            jumpListOpen -> jumpListOpen = false
            sortMenuOpen -> sortMenuOpen = false
            isSearchActive -> closeSearch()
            else -> onBackToStart()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            // A tap on nothing in particular lets a marked app go.
            .clickable(
                enabled = selected != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { selected = null }
    ) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val searchHeight = if (isSearchActive) SEARCH_HEIGHT else 0.dp
        val area = maxHeight - statusTop - navBottom - HEADER_HEIGHT - FOOTER_HEIGHT - searchHeight
        val rows = ((area - SECTION_HEADING) / ROW_HEIGHT).toInt().coerceIn(3, 24)

        Column(modifier = Modifier.fillMaxSize().padding(top = statusTop, bottom = navBottom)) {
            // ── Header: the title, the order menu, search ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT)
                    .padding(start = SIDE_PADDING, end = SIDE_PADDING, top = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.apps_hub),
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Light, fontSize = 42.sp),
                    color = fg,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(22.dp))
                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { sortMenuOpen = !sortMenuOpen }
                            .padding(top = 10.dp)
                    ) {
                        Text(
                            text = stringResource(sortLabelOf(sort)),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 17.sp),
                            color = fg.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = fg.copy(alpha = 0.85f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (sortMenuOpen) {
                        Win8SortMenu(
                            current = sort,
                            fg = fg,
                            onPick = { picked ->
                                sortMenuOpen = false
                                if (picked == Win8AppsSort.BY_USAGE && !usageGranted) {
                                    // The phone will not say what is used until it is allowed to.
                                    Toast.makeText(context, R.string.apps_usage_access, Toast.LENGTH_SHORT).show()
                                    viewModel.openUsageAccessSettings(context)
                                } else {
                                    sort = picked
                                    scope.launch { listState.scrollToItem(0) }
                                }
                            },
                            onDismiss = { sortMenuOpen = false }
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Win8CircleButton(Icons.Default.Search, stringResource(R.string.common_search), fg) {
                    if (isSearchActive) closeSearch() else isSearchActive = true
                }
            }

            if (isSearchActive) {
                ZuneSearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::updateSearchQuery,
                    placeholder = stringResource(R.string.search_apps),
                    modifier = Modifier
                        .padding(start = SIDE_PADDING, bottom = 8.dp)
                        .width(SEARCH_WIDTH)
                        .height(SEARCH_HEIGHT - 8.dp)
                )
            }

            // ── The columns ──
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(start = SIDE_PADDING, end = SIDE_PADDING),
                    horizontalArrangement = Arrangement.spacedBy(GROUP_GAP),
                    modifier = Modifier
                        .fillMaxSize()
                        .win8PinchZoom(
                            onZoomOut = { if (sort == Win8AppsSort.BY_NAME && !searching) jumpListOpen = true },
                            onZoomIn = { }
                        )
                ) {
                    itemsIndexed(shownSections, key = { _, section -> section.id }) { _, section ->
                        Win8Section(
                            section = section,
                            rows = rows,
                            fg = fg,
                            onHeading = { if (section.letter != null) jumpListOpen = true }
                        ) { app ->
                            val icon = remember(app.packageName, iconPackKey) { viewModel.iconFor(app) }
                            Win8AppRow(
                                app = app,
                                icon = icon,
                                selected = selected?.packageName == app.packageName,
                                dimmed = app.packageName in hiddenApps && !showingHidden,
                                fg = fg,
                                onClick = {
                                    if (selected != null) selected = null else viewModel.launchApp(app.packageName)
                                },
                                onLongClick = { selected = app }
                            )
                        }
                    }
                }
                if (searching && shownSections.isEmpty()) {
                    Text(
                        text = stringResource(R.string.apps_search_empty, searchQuery),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 20.sp),
                        color = fg.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = SIDE_PADDING, top = 12.dp)
                    )
                }
            }

            // ── Footer: the way back up ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FOOTER_HEIGHT)
                    .padding(horizontal = SIDE_PADDING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Win8CircleButton(Icons.Default.KeyboardArrowUp, stringResource(R.string.win8_back_to_start), fg, onBackToStart)
            }
        }

        // ── The app bar, for the app that is marked ──
        val marked = selected
        val pinned = marked != null && marked.packageName in favoritePackages
        val hidden = marked != null && marked.packageName in hiddenApps
        val chooserTitle = stringResource(R.string.apps_share_chooser)
        val favouritesFull = stringResource(R.string.apps_favorites_limit)
        val actions = if (marked == null) emptyList() else buildList {
            add(Win8BarAction(Icons.Default.PushPin, stringResource(if (pinned) R.string.apps_unpin else R.string.apps_pin)) {
                val room = BuildConfig.IS_PREMIUM || pinned || favoritePackages.size < FREE_PIN_LIMIT
                if (room) viewModel.toggleFavorite(marked.packageName)
                else Toast.makeText(context, favouritesFull, Toast.LENGTH_SHORT).show()
            })
            add(Win8BarAction(
                if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                stringResource(if (hidden) R.string.apps_unhide else R.string.apps_hide)
            ) { viewModel.setHidden(marked.packageName, !hidden) })
            add(Win8BarAction(Icons.Default.Share, stringResource(R.string.common_share)) {
                viewModel.shareApp(marked, chooserTitle)
            })
            add(Win8BarAction(Icons.Default.Info, stringResource(R.string.apps_app_info)) {
                viewModel.openAppSettings(marked.packageName)
            })
            if (!viewModel.isSystemApp(marked.packageName)) {
                add(Win8BarAction(Icons.Default.Delete, stringResource(R.string.apps_uninstall)) {
                    viewModel.uninstall(marked.packageName)
                })
            }
        }
        Win8AppBar(
            visible = marked != null,
            actions = actions,
            fg = fg,
            onAction = { selected = null },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // ── The alphabet ──
        if (jumpListOpen) {
            AppsJumpList(
                availableLetters = availableLetters,
                onLetterSelected = { letter ->
                    jumpListOpen = false
                    val index = sections.indexOfFirst { it.letter == letter }
                    if (index >= 0) scope.launch { listState.animateScrollToItem(index) }
                },
                onDismiss = { jumpListOpen = false }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
// PARTS
// ════════════════════════════════════════════════════════════

/** One heading and the columns under it. */
@Composable
private fun Win8Section(
    section: Win8AppSection,
    rows: Int,
    fg: Color,
    onHeading: () -> Unit,
    row: @Composable (AppInfo) -> Unit
) {
    val title = when {
        section.letter != null -> section.letter.toString().uppercase(Locale.getDefault())
        section.bucket != null -> stringResource(bucketLabelOf(section.bucket))
        section.usage == true -> stringResource(R.string.win8_apps_most_used)
        section.usage == false -> stringResource(R.string.win8_apps_others)
        else -> stringResource(R.string.win8_search_results)
    }
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
            color = fg,
            maxLines = 1,
            modifier = Modifier
                .height(SECTION_HEADING)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onHeading
                )
                .padding(start = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP)) {
            columnsOf(section.apps, rows).forEach { column ->
                Column(modifier = Modifier.width(COLUMN_WIDTH)) {
                    column.forEach { app -> row(app) }
                }
            }
        }
    }
}

/** An app: its picture and its name on one line, marked with the accent when it is held. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Win8AppRow(
    app: AppInfo,
    icon: Drawable?,
    selected: Boolean,
    dimmed: Boolean,
    fg: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val accent = zuneColors.accentColor
    Row(
        modifier = Modifier
            .width(COLUMN_WIDTH)
            .height(ROW_HEIGHT)
            .then(if (selected) Modifier.border(2.dp, accent) else Modifier)
            .combinedClickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Win8AppIcon(app, icon, ICON_SIZE)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = fg.copy(alpha = if (dimmed) 0.45f else 0.95f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Box(
                modifier = Modifier.size(16.dp).background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✓", color = Color.White, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun Win8AppIcon(app: AppInfo, icon: Drawable?, size: Dp) {
    val zuneColors = LocalZuneColors.current
    if (icon != null) {
        val bitmap = remember(icon) { icon.toSquareImageBitmap() }
        Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            modifier = Modifier.size(size).background(zuneColors.accentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.label.take(1).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = Color.White
            )
        }
    }
}

/** The order menu, a flat box under its control the way Windows 8 drew its dropdowns. */
@Composable
private fun Win8SortMenu(current: Win8AppsSort, fg: Color, onPick: (Win8AppsSort) -> Unit, onDismiss: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    val paper = if (zuneColors.isDark) Color(0xFF2B2B2B) else Color.White
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .padding(top = 44.dp)
                .width(260.dp)
                .background(paper)
                .border(1.dp, fg.copy(alpha = 0.25f))
        ) {
            Win8AppsSort.entries.forEach { option ->
                val chosen = option == current
                Text(
                    text = stringResource(sortLabelOf(option)),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = if (chosen) Color.White else fg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (chosen) zuneColors.accentColor else Color.Transparent)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onPick(option) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private data class Win8BarAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * The Windows 8 app bar: a dark band across the bottom with the commands set to the right, each
 * a ringed glyph with its name under it. It comes up when an app is marked and goes when the
 * mark is lifted or a command is taken.
 */
@Composable
private fun Win8AppBar(
    visible: Boolean,
    actions: List<Win8BarAction>,
    fg: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFEDEDED))
                // The bar takes its own taps so they do not fall through and lift the mark.
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { }
                .padding(bottom = navBottom)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(horizontal = SIDE_PADDING, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                actions.forEach { action ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            action.onClick()
                            onAction()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .border(2.dp, fg.copy(alpha = 0.9f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = action.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = fg.copy(alpha = 0.9f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun sortLabelOf(sort: Win8AppsSort): Int = when (sort) {
    Win8AppsSort.BY_NAME -> R.string.win8_apps_by_name
    Win8AppsSort.BY_DATE -> R.string.win8_apps_by_date
    Win8AppsSort.BY_USAGE -> R.string.win8_apps_by_usage
}

private fun bucketLabelOf(bucket: Win8DateBucket): Int = when (bucket) {
    Win8DateBucket.TODAY -> R.string.win8_apps_today
    Win8DateBucket.THIS_WEEK -> R.string.win8_apps_this_week
    Win8DateBucket.THIS_MONTH -> R.string.win8_apps_this_month
    Win8DateBucket.EARLIER -> R.string.win8_apps_earlier
}

private val HEADER_HEIGHT = 92.dp
private val FOOTER_HEIGHT = 56.dp
private val SIDE_PADDING = 40.dp
private val SECTION_HEADING = 48.dp
private val ROW_HEIGHT = 44.dp
private val COLUMN_WIDTH = 236.dp
private val COLUMN_GAP = 12.dp
private val GROUP_GAP = 56.dp
private val ICON_SIZE = 30.dp
private val SEARCH_HEIGHT = 56.dp
private val SEARCH_WIDTH = 420.dp

/** How many apps the free version may pin to Start. */
private const val FREE_PIN_LIMIT = 10
