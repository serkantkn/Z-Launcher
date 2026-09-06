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
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.BrowserFavorite
import com.serkantkn.zunelauncher.data.model.BrowserHistory
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.net.URI

@Composable
fun BrowserStartScreen(
    favorites: List<BrowserFavorite>,
    history: List<BrowserHistory>,
    onUrlSelected: (String) -> Unit,
    onAddFavorite: (String, String) -> Unit,
    onRemoveFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    
    var favoriteToEdit by remember { mutableStateOf<BrowserFavorite?>(null) }
    var favoriteToAction by remember { mutableStateOf<BrowserFavorite?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current

    val gridColumns = if (isWideScreen) GridCells.Fixed(6) else GridCells.Fixed(4)
    val topPadding = if (isWideScreen) 28.dp else 80.dp
    val bottomPadding = if (isWideScreen) 40.dp else 120.dp

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = gridColumns,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = ZuneDimens.ScreenPaddingHorizontal,
                end = ZuneDimens.ScreenPaddingHorizontal,
                top = topPadding,
                bottom = bottomPadding
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.browser_favorites),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = if (isWideScreen) 36.sp else 32.sp
                    ),
                    color = zuneColors.accentColor,
                    modifier = Modifier.padding(bottom = ZuneDimens.SpacingSm)
                )
            }

            if (favorites.isEmpty()) {
                item {
                    FavoriteActionTile(
                        title = stringResource(R.string.common_add),
                        onClick = { 
                            if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                showAddDialog = true 
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.browser_fav_add_pro), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                itemsIndexed(favorites, key = { _, fav -> fav.url }) { index, fav ->
                    WpFavoriteSiteTile(
                        title = fav.title,
                        url = fav.url,
                        index = index,
                        onClick = { onUrlSelected(fav.url) },
                        onLongClick = { 
                            if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                favoriteToAction = fav 
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.browser_fav_edit_pro), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    FavoriteActionTile(
                        title = stringResource(R.string.common_add),
                        onClick = { 
                            if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                showAddDialog = true 
                            } else {
                                android.widget.Toast.makeText(context, context.getString(R.string.browser_fav_add_pro), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.common_history),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = if (isWideScreen) 36.sp else 32.sp
                    ),
                    color = zuneColors.accentColor,
                    modifier = Modifier.padding(top = ZuneDimens.SpacingXl, bottom = ZuneDimens.SpacingSm)
                )
            }

            if (history.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.browser_history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textMuted
                    )
                }
            } else {
                items(
                    history,
                    span = { GridItemSpan(if (isWideScreen) 1 else maxLineSpan) }
                ) { hist ->
                    BrowserHistoryItem(
                        title = hist.title,
                        url = hist.url,
                        onClick = { onUrlSelected(hist.url) }
                    )
                }
            }
        }

        // Overlays & Dialogs
        if (favoriteToAction != null) {
            val fav = favoriteToAction!!
            com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
                onDismissRequest = { favoriteToAction = null },
                title = fav.title,
                confirmButton = {
                    com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                        text = stringResource(R.string.common_edit),
                        onClick = {
                            dismissWithAnim {
                                favoriteToAction = null
                                favoriteToEdit = fav
                            }
                        },
                        borderColor = zuneColors.accentColor
                    )
                },
                dismissButton = {
                    com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                        text = stringResource(R.string.common_remove),
                        onClick = {
                            dismissWithAnim {
                                onRemoveFavorite(fav.url)
                                favoriteToAction = null
                            }
                        },
                        borderColor = MaterialTheme.colorScheme.error
                    )
                }
            ) {
                Text(
                    text = stringResource(R.string.browser_what_to_do),
                    style = MaterialTheme.typography.bodyLarge,
                    color = zuneColors.textMuted
                )
            }
        }

        if (showAddDialog || favoriteToEdit != null) {
            var editTitle by remember(favoriteToEdit) { mutableStateOf(favoriteToEdit?.title ?: "") }
            var editUrl by remember(favoriteToEdit) { mutableStateOf(favoriteToEdit?.url ?: "") }

            com.serkantkn.zunelauncher.ui.components.ZuneFlipDialog(
                onDismissRequest = {
                    showAddDialog = false
                    favoriteToEdit = null
                },
                title = if (favoriteToEdit != null) stringResource(R.string.browser_edit_favorite) else stringResource(R.string.browser_add_favorite),
                confirmButton = {
                    com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                        text = stringResource(R.string.common_save),
                        onClick = {
                            dismissWithAnim {
                                if (favoriteToEdit != null) {
                                    onRemoveFavorite(favoriteToEdit!!.url)
                                }
                                onAddFavorite(editTitle.ifBlank { editUrl }, editUrl)
                                showAddDialog = false
                                favoriteToEdit = null
                            }
                        },
                        borderColor = zuneColors.accentColor
                    )
                },
                dismissButton = {
                    com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = {
                            dismissWithAnim {
                                showAddDialog = false
                                favoriteToEdit = null
                            }
                        },
                        borderColor = zuneColors.textMuted
                    )
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                }
            }
        }
    }
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
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current

    val itemBg = if (isWideScreen) {
        if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFF0F0F0)
    } else Color.Transparent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(itemBg, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (isWideScreen) 14.dp else 0.dp,
                vertical = if (isWideScreen) 10.dp else ZuneDimens.SpacingSm
            )
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
}
