package com.serkantkn.zunelauncher.ui.screens.apps

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.toSquareImageBitmap
import java.util.Locale

/**
 * The pieces the app list is drawn from.
 *
 * The shapes are Windows Phone's — square icons, an accent square for each letter, the lean on
 * touch — and the type is Zune's: light, lowercase, roomy, with a hairline running off the edge of
 * the screen rather than a box drawn around anything.
 */

// ── One app ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppListRow(
    app: AppInfo,
    icon: Drawable?,
    isPinned: Boolean,
    isHidden: Boolean,
    onClick: () -> Unit,
    onLongPress: (anchorY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    var rowTop by remember { mutableFloatStateOf(0f) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowTop = it.positionInWindow().y }
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress(rowTop) }
            )
            .padding(vertical = 7.dp)
    ) {
        AppIcon(app = app, icon = icon, size = ZuneDimens.AppIconSize)

        Spacer(modifier = Modifier.width(ZuneDimens.SpacingMd))

        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 19.sp,
                // A light face over a photo wallpaper needs a lift on a pale background.
                shadow = if (!zuneColors.isDark) {
                    Shadow(Color.White.copy(alpha = 0.9f), Offset(1f, 1f), 4f)
                } else {
                    null
                }
            ),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isHidden) 0.45f else 1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        if (isPinned || isHidden) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.PushPin,
                contentDescription = null,
                tint = if (isHidden) zuneColors.textDim else zuneColors.accentColor,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/** The same app as a square on a wide screen, where a single column would waste the width. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppGridCell(
    app: AppInfo,
    icon: Drawable?,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongPress: (anchorY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    var cellTop by remember { mutableFloatStateOf(0f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { cellTop = it.positionInWindow().y }
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress(cellTop) }
            )
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        AppIcon(app = app, icon = icon, size = ZuneDimens.AppIconSizeLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Light,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                shadow = if (!zuneColors.isDark) {
                    Shadow(Color.White.copy(alpha = 0.9f), Offset(1f, 1f), 4f)
                } else {
                    null
                }
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (isPinned) {
            Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                tint = zuneColors.accentColor,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * An app's picture: square and unrounded, the way Windows Phone drew them. Where there is no
 * picture at all the first letter stands in on an accent square, as a tile without a glyph did.
 */
@Composable
private fun AppIcon(app: AppInfo, icon: Drawable?, size: Dp) {
    val zuneColors = LocalZuneColors.current
    if (icon != null) {
        val bitmap = remember(icon) { icon.toSquareImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = app.label,
            modifier = Modifier.size(size)
        )
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

// ── A letter ────────────────────────────────────────────────────────────────

/**
 * The heading above each letter's apps, and the way into the jump list.
 *
 * Windows Phone's accent square, with a hairline carried off the right edge of the screen — the
 * rule Zune ran under every section title. Tapping it opens the alphabet.
 *
 * On a wide screen the rule is dropped and the square stands inline among the icons, so a letter
 * with two apps under it does not cost a whole row of a six-column grid.
 */
@Composable
internal fun AppLetterHeader(
    letter: Char,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    inline: Boolean = false
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }

    if (inline) {
        Box(
            modifier = modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            LetterSquare(letter, interactionSource, onClick, ZuneDimens.AppIconSizeLarge)
        }
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ZuneDimens.SpacingLg, bottom = ZuneDimens.SpacingSm)
    ) {
        LetterSquare(letter, interactionSource, onClick, LETTER_SQUARE)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(zuneColors.accentColor.copy(alpha = 0.35f))
        )
    }
}

/** The accent square itself, which is the whole heading on a wide screen. */
@Composable
private fun LetterSquare(
    letter: Char,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    size: Dp
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = Modifier
            .wpTilt(interactionSource)
            .size(size)
            .background(zuneColors.accentColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = letter.toString().lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Light,
                fontSize = 22.sp
            ),
            color = Color.White
        )
    }
}

/** A plain Zune section rule with a word on it, for pages that have no letters. */
@Composable
internal fun AppSectionRule(text: String, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ZuneDimens.SpacingMd, bottom = ZuneDimens.SpacingSm)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
            color = zuneColors.accentColor
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(zuneColors.accentColor.copy(alpha = 0.35f))
        )
    }
}

// ── The context menu ────────────────────────────────────────────────────────

internal data class AppMenuItem(
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * What can be done with one app.
 *
 * Windows Phone dimmed the whole list and stood the menu where the app was, each line turning in
 * a moment after the one above it. That stagger is the whole character of the thing, so it is kept
 * rather than replaced by a sheet sliding up from the bottom.
 */
@Composable
internal fun AppContextMenu(
    title: String,
    items: List<AppMenuItem>,
    anchorY: Float,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appeared = true }

    // The menu stands where the app is, but never so low that it runs off the screen.
    val menuHeight = MENU_TITLE_HEIGHT + MENU_ITEM_HEIGHT * items.size + 16.dp
    val anchorDp = with(density) { anchorY.toDp() }
    val topDp = anchorDp.coerceIn(0.dp, (screenHeightDp - menuHeight).coerceAtLeast(0.dp))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .padding(top = topDp)
                .fillMaxWidth()
                .background(if (zuneColors.isDark) Color(0xFF1B1B1B) else Color(0xFFF2F2F2))
                .padding(vertical = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal),
                color = zuneColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            items.forEachIndexed { index, item ->
                MenuLine(item = item, index = index, visible = appeared)
            }
        }
    }
}

@Composable
private fun MenuLine(item: AppMenuItem, index: Int, visible: Boolean) {
    val density = LocalDensity.current
    val turn by animateFloatAsState(
        targetValue = if (visible) 0f else -80f,
        animationSpec = tween(
            durationMillis = 220,
            delayMillis = index * 45,
            easing = FastOutSlowInEasing
        ),
        label = "apps_menu_line"
    )
    Text(
        text = item.label,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
        color = if (item.destructive) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationX = turn
                transformOrigin = TransformOrigin(0f, 0f)
                cameraDistance = 14f * density.density
            }
            .clickable(onClick = item.onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp)
    )
}

// ── Notices ─────────────────────────────────────────────────────────────────

/** Said once, on the page that needs it, rather than as a dialog nobody asked for. */
@Composable
internal fun AppsNotice(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val zuneColors = LocalZuneColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ZuneDimens.SpacingXl, end = ZuneDimens.SpacingLg)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textMuted
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .clickable(onClick = onAction)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

private val LETTER_SQUARE = 44.dp
private val MENU_TITLE_HEIGHT = 34.dp
private val MENU_ITEM_HEIGHT = 46.dp
