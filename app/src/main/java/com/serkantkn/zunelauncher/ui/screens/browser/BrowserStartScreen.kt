package com.serkantkn.zunelauncher.ui.screens.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.RectangleShape
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

    val gridColumns = if (isWideScreen) GridCells.Adaptive(minSize = 140.dp) else GridCells.Fixed(3)
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
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(ZuneDimens.SpacingLg)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "sık kullanılanlar",
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
                    FavoriteActionTile(title = "ekle", onClick = { 
                        if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                            showAddDialog = true 
                        } else {
                            android.widget.Toast.makeText(context, "Sık kullanılanlara site ekleme özelliği sadece Z Launcher Pro'da geçerlidir.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            } else {
                items(favorites) { fav ->
                    FavoriteTile(
                        title = fav.title,
                        url = fav.url,
                        onClick = { onUrlSelected(fav.url) },
                        onLongClick = { 
                            if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                                favoriteToAction = fav 
                            } else {
                                android.widget.Toast.makeText(context, "Sık kullanılanları düzenleme özelliği sadece Z Launcher Pro'da geçerlidir.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                item {
                    FavoriteActionTile(title = "ekle", onClick = { 
                        if (com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                            showAddDialog = true 
                        } else {
                            android.widget.Toast.makeText(context, "Sık kullanılanlara site ekleme özelliği sadece Z Launcher Pro'da geçerlidir.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "geçmiş",
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
                        text = "geçmişiniz boş",
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
                        text = "düzenle",
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
                        text = "kaldır",
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
                    text = "ne yapmak istersiniz?",
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
                title = if (favoriteToEdit != null) "sık kullanılanı düzenle" else "sık kullanılan ekle",
                confirmButton = {
                    com.serkantkn.zunelauncher.ui.components.ZuneDialogButton(
                        text = "kaydet",
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
                        text = "iptal",
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
                        label = { Text("başlık") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        label = { Text("url") },
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
private fun FavoriteTile(
    title: String,
    url: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val host = remember(url) {
        try {
            URI(url).host ?: url
        } catch (e: Exception) {
            url
        }
    }
    val iconUrl = "https://www.google.com/s2/favicons?domain=$host&sz=128"
    val tileColor = if (zuneColors.isDark) Color(0xFF242424) else Color(0xFFE2E2E2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(tileColor, RoundedCornerShape(2.dp))
                .padding(if (isWideScreen) 20.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = iconUrl,
                contentDescription = title,
                modifier = Modifier.size(if (isWideScreen) 52.dp else 48.dp)
            )
        }
        Text(
            text = title.lowercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isWideScreen) 13.sp else 12.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun FavoriteActionTile(
    title: String,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val tileColor = if (zuneColors.isDark) Color(0xFF242424) else Color(0xFFE2E2E2)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(tileColor, RoundedCornerShape(2.dp))
                .padding(if (isWideScreen) 20.dp else 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(if (isWideScreen) 52.dp else 48.dp)
            )
        }
        Text(
            text = title.lowercase(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = if (isWideScreen) 13.sp else 12.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
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
