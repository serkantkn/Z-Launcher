package com.serkantkn.zunelauncher.ui.screens.browser

import com.serkantkn.zunelauncher.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * Internet Hub Address & Application Bar styled according to standard Windows Phone Metro AppBar guidelines.
 * Features address input box, circular tab counter action, 3-dot (...) expansion button on far right, and expandable Metro menu items.
 */
@Composable
fun WP8AddressBar(
    url: String,
    isLoading: Boolean,
    progress: Float,
    canGoBack: Boolean,
    canGoForward: Boolean,
    suggestions: List<BrowserSuggestion>,
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    onInputChanged: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    onLuckyUrlSubmitted: (String) -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    isFavorited: Boolean,
    onToggleFavorite: () -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: (String) -> Unit,
    onSwitchTab: (Int) -> Unit,
    onOpenDownloads: () -> Unit,
    downloadsCount: Int = 0,
    onOpenSettings: (() -> Unit)? = null,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val wp8MenuBarColor = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE0E0E0)
    var inputText by remember(url) { mutableStateOf(url) }
    var menuExpanded by remember { mutableStateOf(false) }
    var tabsExpanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(wp8MenuBarColor)
    ) {
        // Top Loading Bar
        if (isLoading && progress > 0f && progress < 1f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = zuneColors.accentColor,
                trackColor = Color.Transparent
            )
        }

        // Suggestions Dropdown
        AnimatedVisibility(
            visible = suggestions.isNotEmpty() && inputText.isNotEmpty(),
            enter = expandVertically(expandFrom = Alignment.Bottom),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 250.dp)
                    .background(wp8MenuBarColor),
                reverseLayout = true
            ) {
                items(suggestions) { suggestion ->
                    WP8MenuItem(text = suggestion.displayText, subtitle = suggestion.subtitle, enabled = true) {
                        inputText = suggestion.query
                        menuExpanded = false
                        if (suggestion.isLucky) {
                            onLuckyUrlSubmitted(suggestion.query)
                        } else {
                            onUrlSubmitted(suggestion.query)
                        }
                    }
                }
            }
        }

        // Main Bar Content Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(wp8MenuBarColor)
                .pointerInput(tabs.size, activeTabIndex) {
                    detectHorizontalDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount)
                        }
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Tab Counter Button (Left side, standard circular WP action)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { tabsExpanded = !tabsExpanded; menuExpanded = false }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(
                        1.5.dp,
                        if (zuneColors.isDark) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = tabs.size.toString(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (zuneColors.isDark) Color.White else Color.Black
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Address Input Box
            val inputBg = if (zuneColors.isDark) Color(0xFF333333) else Color(0xFFCCCCCC)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .background(inputBg, RoundedCornerShape(2.dp))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                onInputChanged(it)
                            },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = if (zuneColors.isDark) Color.White else Color.Black,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(zuneColors.accentColor),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    onUrlSubmitted(inputText)
                                    menuExpanded = false
                                    tabsExpanded = false
                                    isFocused = false
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                        )
                        if (inputText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.browser_address_hint),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                                color = zuneColors.textMuted
                            )
                        }
                    }

                    // Inside Right Action Button (Clear or Refresh/Stop)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable {
                                if (isFocused || inputText != url) {
                                    inputText = ""
                                    onInputChanged("")
                                } else {
                                    if (isLoading) onStop() else onRefresh()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = if (isFocused || inputText != url) Icons.Default.Close else (if (isLoading) Icons.Default.Close else Icons.Default.Refresh)
                        val tint = if (isFocused || inputText != url) zuneColors.textMuted else (if (zuneColors.isDark) Color.White else Color.Black)
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(R.string.browser_action),
                            tint = tint,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 3-Dots (...) Menu Expansion Button on Far Right
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { menuExpanded = !menuExpanded; tabsExpanded = false }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = stringResource(R.string.browser_more),
                    tint = if (zuneColors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Expanded Metro Menu Options (Revealed when 3-dots ... button is tapped)
        AnimatedVisibility(
            visible = menuExpanded,
            enter = fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut(tween(150)) + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(wp8MenuBarColor)
                    .padding(vertical = 8.dp)
            ) {
                WP8MenuItem(text = stringResource(R.string.browser_home), subtitle = null, enabled = true) {
                    menuExpanded = false
                    onGoHome()
                }
                val favText = if (isFavorited) stringResource(R.string.browser_remove_favorite) else stringResource(R.string.browser_add_to_favorites)
                WP8MenuItem(text = favText, subtitle = null, enabled = true) {
                    menuExpanded = false
                    onToggleFavorite()
                }
                WP8MenuItem(text = stringResource(R.string.browser_tabs_count, tabs.size), subtitle = null, enabled = true) {
                    menuExpanded = false
                    tabsExpanded = true
                }
                WP8MenuItem(text = stringResource(R.string.browser_downloads_count, downloadsCount), subtitle = null, enabled = true) {
                    menuExpanded = false
                    onOpenDownloads()
                }
                WP8MenuItem(text = stringResource(R.string.common_settings), subtitle = null, enabled = true) {
                    menuExpanded = false
                    onOpenSettings?.invoke()
                }
                WP8MenuItem(text = stringResource(R.string.common_back), subtitle = null, enabled = canGoBack) {
                    if (canGoBack) {
                        menuExpanded = false
                        onGoBack()
                    }
                }
                WP8MenuItem(text = stringResource(R.string.browser_forward), subtitle = null, enabled = canGoForward) {
                    if (canGoForward) {
                        menuExpanded = false
                        onGoForward()
                    }
                }
            }
        }

        // Tabs Switcher Menu
        AnimatedVisibility(
            visible = tabsExpanded,
            enter = fadeIn(tween(180)) + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut(tween(150)) + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(wp8MenuBarColor)
                    .padding(bottom = ZuneDimens.SpacingLg)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZuneDimens.SpacingLg, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.browser_tabs),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onNewTab() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.browser_new_tab),
                            tint = if (zuneColors.isDark) Color.White else Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .heightIn(max = 300.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(tabs) { index, tab ->
                        val isTabActive = index == activeTabIndex
                        val borderColor = if (isTabActive) zuneColors.accentColor else zuneColors.textMuted.copy(alpha = 0.5f)

                        Box(
                            modifier = Modifier
                                .padding(6.dp)
                                .height(95.dp)
                                .border(1.5.dp, borderColor)
                                .clickable {
                                    tabsExpanded = false
                                    onSwitchTab(index)
                                }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = tab.title.ifBlank { stringResource(R.string.browser_new_tab) },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = if (zuneColors.isDark) Color.White else Color.Black,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = tab.url.ifBlank { "" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = zuneColors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Close button for tab
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_close_cap),
                                tint = if (zuneColors.isDark) Color.White else Color.Black,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(20.dp)
                                    .clickable {
                                        onCloseTab(tab.id)
                                        if (tabs.size == 1) {
                                            tabsExpanded = false
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WP8MenuItem(
    text: String,
    subtitle: String?,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val textColor = if (enabled) (if (zuneColors.isDark) Color.White else Color.Black) else zuneColors.textMuted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text.lowercase(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                color = textColor
            )
        )
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = zuneColors.accentColor)
            )
        }
    }
}
