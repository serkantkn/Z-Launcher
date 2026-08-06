package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

data class WpBarAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

data class WpBarMenuItem(
    val text: String,
    val onClick: () -> Unit
)

/**
 * Standard Windows Phone Metro style Bottom Application Bar.
 * Action buttons are centered horizontally and stay above the system navigation bar.
 * Collapsed state (56.dp + insets): Circular outlined icons centered, 3-dot (...) button flush right. Button labels hidden.
 * Expanded state (~100.dp + insets): Tapping (...) expands the bar upward, revealing centered icon labels and optional text menu items.
 */
@Composable
fun WindowsPhoneBottomBar(
    actions: List<WpBarAction>,
    modifier: Modifier = Modifier,
    menuItems: List<WpBarMenuItem> = emptyList()
) {
    val zuneColors = LocalZuneColors.current
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var isExpanded by remember { mutableStateOf(false) }

    val baseHeight = 56.dp + navBarBottomPadding
    val expandedHeight = if (menuItems.isNotEmpty()) (100 + menuItems.size * 32).dp + navBarBottomPadding else 90.dp + navBarBottomPadding
    val barHeight by animateDpAsState(
        targetValue = if (isExpanded) expandedHeight else baseHeight,
        animationSpec = tween(durationMillis = 260),
        label = "app_bar_height"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
        color = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE0E0E0),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isExpanded) isExpanded = true
                    }
                )
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 8.dp + navBarBottomPadding
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                // Centered circular action buttons
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions.forEach { action ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (isExpanded) isExpanded = false
                                        action.onClick()
                                    }
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
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = action.label,
                                        tint = if (zuneColors.isDark) Color.White else Color.Black,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3-dots (...) button on far right to expand/collapse
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .align(Alignment.CenterEnd)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isExpanded = !isExpanded }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreHoriz,
                        contentDescription = "Daha fazla",
                        tint = if (zuneColors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Expanded content: Labels under centered circular buttons + optional menu items
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Labels row corresponding to centered action icons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        actions.forEach { action ->
                            Box(
                                modifier = Modifier.width(38.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = action.label.lowercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = if (zuneColors.isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Optional extra text menu items
                    if (menuItems.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        menuItems.forEach { item ->
                            Text(
                                text = item.text.lowercase(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = if (zuneColors.isDark) Color.White else Color.Black,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isExpanded = false
                                        item.onClick()
                                    }
                                    .padding(vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
