package com.serkantkn.zeta.ui.components

import androidx.compose.ui.res.stringResource
import com.serkantkn.zeta.R
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
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors

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
 * How far a bar along the bottom stands off the screen's edge: the system's navigation bar while
 * that is what is down there, and nothing once the keyboard is up. A screen with the keyboard open
 * has already been lifted clear of it - the keyboard's height takes in the navigation bar - so
 * adding the navigation bar again left a band of empty space between the keys and the bar.
 */
@Composable
fun barBottomInset(): androidx.compose.ui.unit.Dp =
    // A pane of a split that does not reach the bottom of the screen has no navigation bar under it.
    if (!LocalPaneEdges.current.bottom) 0.dp
    else WindowInsets.navigationBars.exclude(WindowInsets.ime).asPaddingValues().calculateBottomPadding()

/**
 * How much of the bottom of the screen the bar covers, edge and all: Windows Phone's 56dp bar on
 * a phone, Windows 8's taller one with its names under the commands on a tablet. What has to sit
 * just clear of the bar - a toolbar docked over it, a mini player, the end of a list - asks this
 * rather than assuming either.
 */
@Composable
fun bottomBarHeight(): androidx.compose.ui.unit.Dp =
    (if (LocalIsWideScreen.current) WIN8_BAR_HEIGHT else 56.dp) + barBottomInset()

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
    menuItems: List<WpBarMenuItem> = emptyList(),
    wideCommandsAtStart: Boolean = false
) {
    // A television is Media Center: the same commands, on its strip along the foot.
    if (com.serkantkn.zeta.ui.theme.LocalIsTv.current) {
        com.serkantkn.zeta.ui.tv.MediaCenterCommandStrip(actions, menuItems, bottomBarHeight(), modifier)
        return
    }
    // A tablet is Windows 8, not Windows Phone: the same commands, on Windows 8's bar.
    if (LocalIsWideScreen.current) {
        Win8BarForPhoneCommands(actions, menuItems, wideCommandsAtStart, modifier)
        return
    }
    val zuneColors = LocalZuneColors.current
    val navBarBottomPadding = barBottomInset()
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
                        contentDescription = stringResource(R.string.bottom_bar_more),
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

/**
 * The bar's ground with nothing on it: no buttons and no "…", which here would open onto an empty
 * menu. A tablet hub lays it under its pane while nothing is open there, so the bar along the
 * bottom still runs the whole width.
 */
@Composable
fun WindowsPhoneBottomBarStrip(modifier: Modifier = Modifier) {
    if (com.serkantkn.zeta.ui.theme.LocalIsTv.current) {
        com.serkantkn.zeta.ui.tv.MediaCenterCommandStrip(emptyList(), emptyList(), bottomBarHeight(), modifier)
        return
    }
    if (LocalIsWideScreen.current) {
        Win8CommandBar(left = emptyList(), right = emptyList(), modifier = modifier)
        return
    }
    val zuneColors = LocalZuneColors.current
    val navBarBottomPadding = barBottomInset()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp + navBarBottomPadding),
        color = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE0E0E0),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {}
}

/**
 * A Windows Phone bar's commands laid out as Windows 8 had them: every command with its name
 * under it all the time, and what the phone kept behind its "…" in a flyout from a "…" command
 * of its own. The commands stand at the bar's right end, where Windows 8 put the page's own; a
 * tablet hub's list asks for the left end, so that with whatever is open beside it the band
 * across the bottom reads as one bar - the list's commands on the left, the open thing's on the
 * right.
 */
@Composable
private fun Win8BarForPhoneCommands(
    actions: List<WpBarAction>,
    menuItems: List<WpBarMenuItem>,
    atStart: Boolean,
    modifier: Modifier
) {
    val commands = buildList {
        actions.forEach { action ->
            add(Win8Command(icon = action.icon, label = action.label.lowercase(), onClick = action.onClick))
        }
        if (menuItems.isNotEmpty()) {
            add(
                Win8Command(
                    icon = Icons.Default.MoreHoriz,
                    label = stringResource(R.string.bottom_bar_more).lowercase(),
                    menu = { close ->
                        menuItems.forEach { item ->
                            Win8MenuItem(text = item.text.lowercase()) {
                                close()
                                item.onClick()
                            }
                        }
                    }
                )
            )
        }
    }
    Win8CommandBar(
        left = if (atStart) commands else emptyList(),
        right = if (atStart) emptyList() else commands,
        modifier = modifier
    )
}
