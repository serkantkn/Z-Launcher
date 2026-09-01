package com.serkantkn.zunelauncher.ui.screens.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * Desktop / Tablet style browser header with horizontal tab strip and desktop toolbar.
 */
@Composable
fun DesktopBrowserHeader(
    url: String,
    isLoading: Boolean,
    progress: Float,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabs: List<BrowserTab>,
    activeTabIndex: Int,
    isFavorited: Boolean,
    onInputChanged: (String) -> Unit,
    onUrlSubmitted: (String) -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
    onGoHome: () -> Unit,
    onToggleFavorite: () -> Unit,
    onNewTab: () -> Unit,
    onCloseTab: (String) -> Unit,
    onSwitchTab: (Int) -> Unit,
    onOpenDownloads: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    var inputText by remember(url) { mutableStateOf(url) }
    var isFocused by remember { mutableStateOf(false) }

    val headerBg = if (zuneColors.isDark) Color(0xFF1E1E1E) else Color(0xFFE6E6E6)
    val tabStripBg = if (zuneColors.isDark) Color(0xFF121212) else Color(0xFFD0D0D0)
    val activeTabBg = if (zuneColors.isDark) Color(0xFF262626) else Color(0xFFFFFFFF)
    val inactiveTabBg = Color.Transparent
    val inputBg = if (zuneColors.isDark) Color(0xFF2E2E2E) else Color(0xFFCCCCCC)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(headerBg)
    ) {
        // ── TOP TAB STRIP (Desktop horizontal browser tabs) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(tabStripBg)
                .padding(start = 8.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isActive = index == activeTabIndex

                    Surface(
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp),
                        color = if (isActive) activeTabBg else inactiveTabBg,
                        border = if (isActive) BorderStroke(1.dp, zuneColors.accentColor) else null,
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 220.dp)
                            .height(36.dp)
                            .clickable { onSwitchTab(index) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tab.title.ifBlank { "Yeni Sekme" },
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                                ),
                                color = if (zuneColors.isDark) Color.White else Color.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .clickable { onCloseTab(tab.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Kapat",
                                    tint = if (zuneColors.isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }

            // New Tab (+) button on tab strip
            Box(
                modifier = Modifier
                    .padding(start = 8.dp, bottom = 4.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(headerBg)
                    .clickable { onNewTab() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Yeni Sekme",
                    tint = if (zuneColors.isDark) Color.White else Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // ── TOOLBAR (Desktop navigation & address bar row) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Navigation Controls: Back, Forward, Refresh/Stop
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Back Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .alpha(if (canGoBack) 1f else 0.4f)
                        .clickable(enabled = canGoBack) { onGoBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Geri",
                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Forward Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .alpha(if (canGoForward) 1f else 0.4f)
                        .clickable(enabled = canGoForward) { onGoForward() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "İleri",
                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Refresh / Stop Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { if (isLoading) onStop() else onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = "Yenile",
                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Address Input Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(inputBg, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp),
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
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
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
                                    isFocused = false
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isFocused = it.isFocused }
                        )
                        if (inputText.isEmpty()) {
                            Text(
                                text = "arama yapın veya url girin",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = zuneColors.textMuted
                            )
                        }
                    }

                    if (inputText.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clickable {
                                    inputText = ""
                                    onInputChanged("")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Temizle",
                                tint = zuneColors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Quick Desktop Action Icons: Home, Favorite
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Home Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onGoHome() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Ana Sayfa",
                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Favorite Star Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorited) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorited) zuneColors.accentColor else (if (zuneColors.isDark) Color.White else Color.Black),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Downloads Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onOpenDownloads?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "İndirmeler",
                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings Button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable { onOpenSettings?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ayarlar",
                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Progress Bar
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
    }
}
