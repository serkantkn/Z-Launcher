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
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
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
import kotlin.math.roundToInt

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
    onLongPress: (anchor: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    var rowOrigin by remember { mutableStateOf(Offset.Zero) }
    // Where the finger is, watched on the Initial pass and never consumed, so the row's own click
    // and long-click still work. The menu's line has to start from the touch, not from the row.
    var touch by remember { mutableStateOf(Offset.Zero) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowOrigin = it.positionInWindow() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.pressed }?.let { touch = it.position }
                    }
                }
            }
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress(rowOrigin + touch) }
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
    onLongPress: (anchor: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }
    var cellOrigin by remember { mutableStateOf(Offset.Zero) }
    var touch by remember { mutableStateOf(Offset.Zero) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { cellOrigin = it.positionInWindow() }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.firstOrNull { it.pressed }?.let { touch = it.position }
                    }
                }
            }
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = { onLongPress(cellOrigin + touch) }
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
 * What can be done with one app, opened from under the finger.
 *
 * Metro never drew a menu as a box that fades in: it drew a line and then let the line become the
 * thing. So this starts as a white hairline at the touch, runs out to both edges of the screen,
 * and only then opens downward into a white field with the choices set in thin black. The list
 * behind it does not black out — the other apps simply dim and step back, which is the same
 * depth the Start screen uses when a tile is held. Dismissing plays it backwards.
 */
@Composable
internal fun AppLineMenu(
    items: List<AppMenuItem>,
    anchor: Offset,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
            onDismiss()
        }
    }
    BackHandler(enabled = !closing) { closing = true }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val rowHeight = with(density) { MENU_LINE_HEIGHT.toPx() }
        val padding = with(density) { MENU_PADDING.toPx() }
        val hair = with(density) { MENU_HAIRLINE.toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val boxHeight = rowHeight * items.size + padding * 2

        // The line stands where the finger is, unless the field under it would not fit.
        val top = anchor.y.coerceIn(margin, (height - boxHeight - margin).coerceAtLeast(margin))

        val p = progress.value
        val spread = (p / SPREAD_SHARE).coerceIn(0f, 1f)
        val open = ((p - SPREAD_SHARE) / (1f - SPREAD_SHARE)).coerceIn(0f, 1f)
        val ink = ((p - INK_START) / (1f - INK_START)).coerceIn(0f, 1f)

        val left = anchor.x * (1f - spread)
        val right = anchor.x + (width - anchor.x) * spread

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { closing = true }
                )
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                .size(
                    width = with(density) { (right - left).coerceAtLeast(0f).toDp() },
                    height = with(density) { (hair + (boxHeight - hair) * open).toDp() }
                )
                .background(Color.White)
                .clipToBounds()
        ) {
            // Laid out at its full size from the start and revealed by the field growing over it,
            // so the words do not squash their way in.
            Column(
                modifier = Modifier
                    .width(with(density) { width.toDp() })
                    .padding(vertical = MENU_PADDING)
                    .graphicsLayer { alpha = ink }
            ) {
                items.forEach { item ->
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Light
                        ),
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MENU_LINE_HEIGHT)
                            .clickable(enabled = ink > 0.5f) { item.onClick() }
                            .padding(horizontal = 24.dp)
                            .wrapContentHeight()
                    )
                }
            }
        }
    }
}

/** How far into the animation the hairline is still running out to the edges. */
private const val SPREAD_SHARE = 0.42f

/** And where the words start to arrive. */
private const val INK_START = 0.74f

private val MENU_LINE_HEIGHT = 52.dp
private val MENU_PADDING = 10.dp
private val MENU_HAIRLINE = 2.dp

/** A quiet line of explanation where a list would be, with an optional thing to do about it. */
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
