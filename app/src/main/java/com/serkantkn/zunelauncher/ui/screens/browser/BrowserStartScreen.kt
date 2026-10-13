package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.BrowserFavorite
import com.serkantkn.zunelauncher.data.model.BrowserHistory
import com.serkantkn.zunelauncher.data.model.FrequentSite
import com.serkantkn.zunelauncher.data.model.ReadingItem
import com.serkantkn.zunelauncher.data.model.frequentSites
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.net.URI


/** The start page's headings, in the order they stand. */
private enum class StartPage { FREQUENT, FAVORITES, READING, HISTORY }

/**
 * The page a new tab opens on, as a hub of its own on the hub standard: the most visited sites
 * as tiles, the favourites, the reading list and the history - a pivot on a phone, headings down
 * the left on a tablet.
 *
 * Favourites can be filed into folders - a folder is a tile of its own that the grid drops into -
 * and the history can be searched and thinned out a row at a time rather than only wiped whole.
 */
@Composable
fun BrowserStartScreen(
    favorites: List<BrowserFavorite>,
    history: List<BrowserHistory>,
    readingList: List<ReadingItem>,
    onUrlSelected: (String) -> Unit,
    onOpenReading: (ReadingItem) -> Unit,
    onRemoveReading: (String) -> Unit,
    onAddFavorite: (title: String, url: String, folder: String) -> Unit,
    onUpdateFavorite: (originalUrl: String, title: String, url: String, folder: String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    onMoveFavorite: (url: String, forward: Boolean) -> Unit,
    onRemoveHistoryEntry: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

    var favoriteToEdit by remember { mutableStateOf<BrowserFavorite?>(null) }
    var favoriteToAction by remember { mutableStateOf<BrowserFavorite?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    /** The folder being looked inside, or null while the whole shelf is on show. */
    var openFolder by remember { mutableStateOf<String?>(null) }
    var historyQuery by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    val folders = remember(favorites) {
        favorites.map { it.folder }.filter { it.isNotBlank() }.distinct()
    }
    val shownFavorites = remember(favorites, openFolder) {
        favorites.filter { it.folder == (openFolder ?: "") }
    }
    val frequent = remember(history) { frequentSites(history) }
    val shownHistory = remember(history, historyQuery) {
        if (historyQuery.isBlank()) {
            history
        } else {
            val needle = historyQuery.trim().lowercase()
            history.filter {
                it.title.lowercase().contains(needle) || it.url.lowercase().contains(needle)
            }
        }
    }

    // Grouped by the day they were visited, worked out once rather than row by row.
    val todayLabel = stringResource(R.string.common_today)
    val yesterdayLabel = stringResource(R.string.common_yesterday)
    val historyDays = remember(shownHistory, todayLabel, yesterdayLabel) {
        groupHistoryByDay(shownHistory, todayLabel, yesterdayLabel)
    }

    fun requirePremium(messageRes: Int, block: () -> Unit) {
        if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
            block()
        } else {
            android.widget.Toast
                .makeText(context, context.getString(messageRes), android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    }

    val pages = StartPage.entries
    val tabs = pages.map {
        stringResource(
            when (it) {
                StartPage.FREQUENT -> R.string.browser_frequent
                StartPage.FAVORITES -> R.string.browser_favorites
                StartPage.READING -> R.string.browser_reading_list
                StartPage.HISTORY -> R.string.common_history
            }
        )
    }
    val pager = com.serkantkn.zunelauncher.ui.components.rememberLoopingPagerState(pageCount = pages.size)
    val bottomPadding = if (isWideScreen) 40.dp else 32.dp

    Box(modifier = modifier.fillMaxSize()) {
        com.serkantkn.zunelauncher.ui.components.ZuneHub(
            title = stringResource(R.string.hub_internet),
            tabs = tabs,
            pager = pager
        ) { page ->
            when (pages[page]) {
                StartPage.FREQUENT -> FrequentPage(frequent, onUrlSelected, bottomPadding)
                StartPage.READING -> ReadingPage(readingList, onOpenReading, onRemoveReading, bottomPadding)
                StartPage.FAVORITES -> LazyVerticalGrid(
                    columns = if (isWideScreen) GridCells.Adaptive(120.dp) else GridCells.Fixed(4),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (openFolder != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = openFolder.orEmpty(),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                                color = zuneColors.accentColor,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        // Inside a folder, the first tile is the way back out of it.
                        item {
                            FavoriteActionTile(
                                title = stringResource(R.string.browser_folder_back),
                                onClick = { openFolder = null },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        items(folders, key = { "folder:$it" }) { folder ->
                            BrowserFolderTile(
                                name = folder,
                                count = favorites.count { it.folder == folder },
                                onClick = { openFolder = folder },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    itemsIndexed(shownFavorites, key = { _, fav -> fav.url }) { index, fav ->
                        WpFavoriteSiteTile(
                            title = fav.title,
                            url = fav.url,
                            index = index,
                            onClick = { onUrlSelected(fav.url) },
                            onLongClick = {
                                requirePremium(R.string.browser_fav_edit_pro) { favoriteToAction = fav }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        FavoriteActionTile(
                            title = stringResource(R.string.common_add),
                            onClick = { requirePremium(R.string.browser_fav_add_pro) { showAddDialog = true } },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                StartPage.HISTORY -> androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomPadding)
                ) {
                    // Searching the history only earns its place once there is a history to search.
                    if (history.isNotEmpty()) {
                        item {
                            HistorySearchField(
                                query = historyQuery,
                                onQueryChanged = { historyQuery = it },
                                modifier = Modifier.padding(bottom = ZuneDimens.SpacingSm)
                            )
                        }
                    }
                    if (shownHistory.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    if (history.isEmpty()) R.string.browser_history_empty
                                    else R.string.browser_history_no_match
                                ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = zuneColors.textMuted
                            )
                        }
                    } else {
                        historyDays.forEach { (day, entries) ->
                            item(key = "day:$day") {
                                Text(
                                    text = day,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = zuneColors.textMuted,
                                    modifier = Modifier.padding(top = ZuneDimens.SpacingSm, bottom = 2.dp)
                                )
                            }
                            items(entries.size, key = { "h:" + entries[it].url }) { i ->
                                val entry = entries[i]
                                BrowserHistoryItem(
                                    title = entry.title,
                                    url = entry.url,
                                    onClick = { onUrlSelected(entry.url) },
                                    onRemove = { onRemoveHistoryEntry(entry.url) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── What to do with a favourite ──
        favoriteToAction?.let { fav ->
            FavoriteActionSheet(
                favorite = fav,
                onEdit = {
                    favoriteToAction = null
                    favoriteToEdit = fav
                },
                onMoveEarlier = {
                    onMoveFavorite(fav.url, false)
                    favoriteToAction = null
                },
                onMoveLater = {
                    onMoveFavorite(fav.url, true)
                    favoriteToAction = null
                },
                onRemove = {
                    onRemoveFavorite(fav.url)
                    favoriteToAction = null
                },
                onDismiss = { favoriteToAction = null }
            )
        }

        if (showAddDialog || favoriteToEdit != null) {
            val editing = favoriteToEdit
            var editTitle by remember(editing) { mutableStateOf(editing?.title ?: "") }
            var editUrl by remember(editing) { mutableStateOf(editing?.url ?: "") }
            var editFolder by remember(editing) { mutableStateOf(editing?.folder ?: openFolder.orEmpty()) }

            com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
                onDismissRequest = {
                    showAddDialog = false
                    favoriteToEdit = null
                },
                title = stringResource(
                    if (editing != null) R.string.browser_edit_favorite else R.string.browser_add_favorite
                ),
                confirmButton = {
                    com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                        text = stringResource(R.string.common_save),
                        onClick = {
                            if (editUrl.isNotBlank()) {
                                if (editing != null) {
                                    onUpdateFavorite(editing.url, editTitle, editUrl, editFolder)
                                } else {
                                    onAddFavorite(editTitle, editUrl, editFolder)
                                }
                            }
                            showAddDialog = false
                            favoriteToEdit = null
                        },
                        borderColor = zuneColors.accentColor
                    )
                },
                dismissButton = {
                    com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = {
                            showAddDialog = false
                            favoriteToEdit = null
                        },
                        borderColor = zuneColors.textMuted
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text(stringResource(R.string.browser_title_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text(stringResource(R.string.browser_url_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editFolder,
                        onValueChange = { editFolder = it },
                        label = { Text(stringResource(R.string.browser_folder_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (folders.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.browser_folder_existing, folders.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = zuneColors.textMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * The most visited sites as tiles in the accent, the way Internet Explorer's start page set out
 * its frequent sites: the site's icon in the corner, its name along the bottom.
 */
@Composable
private fun FrequentPage(sites: List<FrequentSite>, onOpen: (String) -> Unit, bottomPadding: androidx.compose.ui.unit.Dp) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    if (sites.isEmpty()) {
        Text(
            text = stringResource(R.string.browser_frequent_empty),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textMuted,
            modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
        )
        return
    }
    LazyVerticalGrid(
        columns = if (isWideScreen) GridCells.Adaptive(220.dp) else GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(sites, key = { _, site -> site.host }) { index, site ->
            val interaction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .wpTilt(interaction)
                    .background(zuneColors.accentColor.copy(alpha = if (index % 3 == 1) 0.82f else 1f))
                    .clickable(interactionSource = interaction, indication = null) { onOpen(site.url) }
                    .padding(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = "https://www.google.com/s2/favicons?domain=${site.host}&sz=128",
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.align(Alignment.BottomStart)) {
                    Text(
                        text = site.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal, fontSize = 16.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = site.host,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The reading list: newest kept first, a picture where there is one, the ones read dimmed. */
@Composable
private fun ReadingPage(
    items: List<ReadingItem>,
    onOpen: (ReadingItem) -> Unit,
    onRemove: (String) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    val zuneColors = LocalZuneColors.current
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.browser_reading_list_empty),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textMuted,
            modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
        )
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        items(items.size, key = { "r:" + items[it].url }) { i ->
            val item = items[i]
            val interaction = remember { MutableInteractionSource() }
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .wpTilt(interaction)
                    .clickable(interactionSource = interaction, indication = null) { onOpen(item) }
                    .padding(vertical = 10.dp)
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .background(zuneColors.accentColor.copy(alpha = 0.25f))
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title.ifBlank { item.url },
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light, fontSize = 18.sp, lineHeight = 23.sp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (item.read) 0.55f else 1f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            item.source.takeIf { it.isNotBlank() },
                            if (item.offline) stringResource(R.string.browser_reading_offline) else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = zuneColors.accentColor,
                        maxLines = 1
                    )
                }
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.common_remove),
                    tint = zuneColors.textMuted,
                    modifier = Modifier
                        .clickable { onRemove(item.url) }
                        .padding(10.dp)
                        .size(18.dp)
                )
            }
        }
    }
}


/** A folder of favourites, standing on the shelf among them. */
@Composable
private fun BrowserFolderTile(
    name: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(zuneColors.accentColor.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = null,
            tint = zuneColors.accentColor,
            modifier = Modifier.align(Alignment.TopStart).size(22.dp)
        )
        Column(modifier = Modifier.align(Alignment.BottomStart)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = zuneColors.textMuted
            )
        }
    }
}

/** The box that thins a long history down to what is being looked for. */
@Composable
private fun HistorySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        label = { Text(stringResource(R.string.browser_history_search)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = zuneColors.textMuted,
                    modifier = Modifier.clickable { onQueryChanged("") }
                )
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}

/** The four things worth doing to a favourite, as a Windows Phone list rather than two buttons. */
@Composable
private fun FavoriteActionSheet(
    favorite: BrowserFavorite,
    onEdit: () -> Unit,
    onMoveEarlier: () -> Unit,
    onMoveLater: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 16.dp)
        ) {
            Text(
                text = favorite.title.ifBlank { favorite.url },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 8.dp)
            )
            SheetAction(stringResource(R.string.common_edit), onEdit)
            SheetAction(stringResource(R.string.browser_move_earlier), onMoveEarlier)
            SheetAction(stringResource(R.string.browser_move_later), onMoveLater)
            SheetAction(stringResource(R.string.common_remove), onRemove, MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun SheetAction(label: String, onClick: () -> Unit, color: Color? = null) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = color ?: MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 12.dp)
    )
}




@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WpFavoriteSiteTile(
    title: String,
    url: String,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val tileColor = if (zuneColors.isDark) Color(0xFF242424) else Color(0xFFE2E2E2)
    val host = remember(url) {
        try {
            URI(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }
    val iconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=256"
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(url) {
        val initialDelay = (index * 500L) % 2500L
        delay(initialDelay)

        while (true) {
            // Full icon resting state
            delay(3000L)

            // Lift icon & slide up title banner
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )

            // Title visible state
            delay(2200L)

            // Collapse banner & slide icon back to full
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(0.dp))
            .background(tileColor)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tileHeightPx = constraints.maxHeight.toFloat()
            val bannerHeightPx = tileHeightPx * 0.30f
            val bannerHeightDp = with(LocalDensity.current) { bannerHeightPx.toDp() }

            val p = animProgress.value
            val photoOffsetYPx = -bannerHeightPx * p

            // 1. Favicon / Site Icon Layer (slides up by photoOffsetYPx)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = photoOffsetYPx
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = title,
                    modifier = Modifier.size(36.dp)
                )
            }

            // 2. Title Banner Layer (slides up from bottom edge with theme accent color)
            val currentBannerHeightDp = bannerHeightDp * p

            if (p > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(currentBannerHeightDp)
                        .background(zuneColors.accentColor)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = title.lowercase(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteActionTile(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val tileColor = if (zuneColors.isDark) Color(0xFF242424) else Color(0xFFE2E2E2)

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(0.dp))
            .background(tileColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = title,
                tint = zuneColors.accentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title.lowercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                ),
                color = zuneColors.accentColor
            )
        }
    }
}

@Composable
private fun BrowserHistoryItem(
    title: String,
    url: String,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

    val itemBg = if (isWideScreen) {
        if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF0F0F0)
    } else Color.Transparent

    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBg, RoundedCornerShape(2.dp))
            .padding(
                horizontal = if (isWideScreen) 14.dp else 0.dp,
                vertical = if (isWideScreen) 10.dp else ZuneDimens.SpacingSm
            )
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
        ) {
            Text(
                text = title.lowercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (isWideScreen) 16.sp else 18.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = url,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = if (isWideScreen) 13.sp else 14.sp
                ),
                color = zuneColors.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // One page at a time, rather than the whole history or nothing.
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.common_remove),
            tint = zuneColors.textMuted,
            modifier = Modifier
                .clickable(onClick = onRemove)
                .padding(10.dp)
                .size(18.dp)
        )
    }
}
