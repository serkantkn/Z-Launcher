package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import coil.compose.AsyncImage
import com.serkantkn.zunelauncher.data.model.TileAnimation
import com.serkantkn.zunelauncher.data.model.TileIcon
import com.serkantkn.zunelauncher.data.model.TileLook
import com.serkantkn.zunelauncher.util.TileIconFace
import java.io.File
import com.serkantkn.zunelauncher.data.model.TileInk
import com.serkantkn.zunelauncher.data.model.tileInkIsDark
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.components.rememberPressPoint
import com.serkantkn.zunelauncher.ui.components.rememberWpTiltAngles
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalScreenAwake
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlin.math.abs

// ════════════════════════════════════════════════════════════
// STYLE
// ════════════════════════════════════════════════════════════

/**
 * What the Start board tells its tiles about their look: how solid they are and how their live
 * faces move. Set once around the board, read by every tile.
 */
data class TileStyle(
    /** 0f = only the outline is left and the wallpaper shows through, 1f = a solid colour block. */
    val opacity: Float = 0.45f,
    val animation: TileAnimation = TileAnimation.SLIDE,
    /** Black, white, or whatever suits the theme. See [TileInk]. */
    val ink: TileInk = TileInk.AUTO
)

val LocalTileStyle = staticCompositionLocalOf { TileStyle() }

// ════════════════════════════════════════════════════════════
// ONE TILE'S OWN LOOK
// ════════════════════════════════════════════════════════════

/**
 * What this one tile has been told to do differently: its colour, name, icon, picture and the
 * rest of [TileLook]. Provided around each tile by the board; the tile pieces below read it, so
 * a tile composable does not have to be told about any of it.
 */
val LocalTileLook = compositionLocalOf { TileLook.DEFAULT }

/**
 * Opens the tile's own settings page. Provided per tile; null where a tile cannot be customised
 * (the settings preview), and the edit button is simply not drawn.
 */
val LocalTileCustomize = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Turns a [TileIcon] a tile was given into something drawable; null when it cannot be. */
val LocalTileIconResolver = staticCompositionLocalOf<(TileIcon) -> TileIconFace?> { { null } }

/**
 * Puts the tile back on the board from the folder it is in. Provided around each tile of an open
 * folder, null everywhere else; the tile draws the button with its other edit buttons, so the
 * folder does not have to lay one over the tile and over whatever the tile put in that corner.
 */
val LocalTileTakeOut = staticCompositionLocalOf<(() -> Unit)?> { null }

/**
 * True inside the customise page's preview: the tile is drawn with its look but without its edit
 * buttons, its press handling, or the dimming a board in edit mode puts over it.
 */
val LocalTileIsPreview = staticCompositionLocalOf { false }

/** True on the tile of a hub that was left running, which then carries the running mark. */
val LocalTileRunning = compositionLocalOf { false }

/** The icon a tile was given, ready to draw, or null when it should draw its own. */
@Composable
fun customTileFace(): TileIconFace? {
    val icon = LocalTileLook.current.icon
    if (icon == TileIcon.Default) return null
    return LocalTileIconResolver.current(icon)
}

/** How much larger or smaller than usual this tile's icon is drawn. */
@Composable
fun tileIconScale(): Float = LocalTileLook.current.iconScale

/** True when the tile shows a picture instead of its icon, so the icon should step aside. */
@Composable
fun tileHasPicture(): Boolean = LocalTileLook.current.picture != null

/** The name this tile's corner should carry, or null when the corner was told to stay empty. */
@Composable
fun tileLabelText(default: String): String? {
    val look = LocalTileLook.current
    if (!look.showLabel) return null
    return look.name?.takeIf { it.isNotBlank() } ?: default
}

// ════════════════════════════════════════════════════════════
// LIVE HEARTBEAT
// ════════════════════════════════════════════════════════════

/** The back face stays up about as long as it took Windows Phone to read a tile out. */
private const val BACK_FACE_MILLIS = 6_000L
private const val FRONT_FACE_MILLIS = 14_000L

/**
 * Tiles do not turn at the same moment: each one waits `slot × STAGGER` before its first turn and
 * then keeps the same period, so the board ripples the way a Start screen full of live tiles did
 * instead of twitching all at once.
 */
private const val FACE_STAGGER_MILLIS = 900L
private const val FACE_SLOTS = 8

private const val FACE_TURN_MILLIS = 620

/** True while the tile should be showing its back face. */
@Composable
private fun rememberShowBack(
    liveKey: Any,
    hasBack: Boolean,
    isEditing: Boolean,
    animation: TileAnimation
): Boolean {
    var showBack by remember(liveKey) { mutableStateOf(false) }
    val slot = remember(liveKey) { abs(liveKey.hashCode()) % FACE_SLOTS }
    // A Start screen that keeps turning over is the most conspicuous motion the launcher has, so
    // it is the first thing that should stop when motion is switched off.
    val animationsEnabled = LocalAnimationsEnabled.current
    // A board behind an opaque hub is still composed, and turning tiles nobody can see is the
    // most expensive nothing the launcher could do. It keeps the face it was showing and simply
    // stops scheduling turns, so coming back does not snap every tile round at once.
    val awake = LocalScreenAwake.current

    LaunchedEffect(liveKey, hasBack, isEditing, animation, animationsEnabled, awake) {
        if (!hasBack || isEditing || animation == TileAnimation.NONE || !animationsEnabled) {
            showBack = false
            return@LaunchedEffect
        }
        if (!awake) return@LaunchedEffect
        delay(slot * FACE_STAGGER_MILLIS)
        while (true) {
            showBack = true
            delay(BACK_FACE_MILLIS)
            showBack = false
            delay(FRONT_FACE_MILLIS)
        }
    }
    return showBack
}

// ════════════════════════════════════════════════════════════
// SURFACE
// ════════════════════════════════════════════════════════════

/**
 * The body every Start tile is built on: the grid-aligned size, the Windows Phone press tilt, the
 * translucent accent fill, the live turn between [front] and [back], and the edit-mode buttons.
 *
 * [liveKey] should be the tile's id — it decides where the tile sits in the turning ripple, so a
 * tile keeps its place in the wave across recompositions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun W10MTileSurface(
    liveKey: Any,
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    cornerStyle: TileCornerStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier,
    tileColor: Color? = null,
    opacity: Float? = null,
    animationOverride: TileAnimation? = null,
    /** Lit up because a dragged tile is hovering over its middle, ready to make a folder. */
    highlighted: Boolean = false,
    /** Folders answer a tap while the board is being edited; everything else waits its turn. */
    clickableWhileEditing: Boolean = false,
    back: (@Composable BoxScope.() -> Unit)? = null,
    front: @Composable BoxScope.() -> Unit
) {
    val style = LocalTileStyle.current
    val look = LocalTileLook.current
    val preview = LocalTileIsPreview.current
    val customize = LocalTileCustomize.current
    val takeOut = LocalTileTakeOut.current
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    // The preview on the customise page is a tile with none of the board's business: no edit
    // buttons, no dimming, and nothing happens when it is touched.
    val isEditing = isEditing && !preview

    val animation = animationOverride ?: look.animation ?: style.animation
    // A picture fills the tile, so it is drawn solid whatever the board's transparency says.
    val fillOpacity = (
        opacity
            ?: look.picture?.let { 1f }
            ?: look.opacity?.let { it / 100f }
            ?: style.opacity
        ).coerceIn(0f, 1f)
    val fillColor = look.color?.let { Color(it) } ?: tileColor ?: zuneColors.accentColor
    val showBack = rememberShowBack(liveKey, back != null, isEditing, animation)
    val turn by animateFloatAsState(
        targetValue = if (showBack) 1f else 0f,
        animationSpec = tween(durationMillis = FACE_TURN_MILLIS, easing = FastOutSlowInEasing),
        label = "w10m_tile_turn"
    )

    // ── Press tilt: the tile sinks in on the side the finger landed, as on Windows Phone ──
    // The lean is the launcher's shared one; only the scale below is the tile's own, because it
    // has to answer to dragging and editing as well as to a finger.
    var tileSize by remember { mutableStateOf(IntSize.Zero) }
    val point = rememberPressPoint(interactionSource)
    val lean = rememberWpTiltAngles(point, tileSize, maxDegrees = TILE_TILT_DEGREES)
    val pressScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.05f
            // A folder target pulls back a little, the way a pocket opens.
            highlighted -> 0.90f
            point != null -> 0.975f
            isEditing -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "w10m_tile_scale"
    )

    val tileShape: Shape = remember(cornerStyle) {
        when (cornerStyle) {
            TileCornerStyle.SHARP -> RoundedCornerShape(0.dp)
            TileCornerStyle.ROUNDED -> RoundedCornerShape(8.dp)
        }
    }
    // The outline is what draws a see-through tile; it fades away as the fill takes over. It is
    // the theme's ink: a white outline on the light theme's white ground drew nothing.
    val strokeAlpha = (1f - fillOpacity).coerceIn(0f, 1f)
    val strokeColor = when {
        highlighted -> if (zuneColors.isDark) Color.White else Color.Black
        isEditing -> zuneColors.accentColor
        zuneColors.isDark -> Color.White.copy(alpha = 0.20f + 0.25f * strokeAlpha)
        else -> Color.Black.copy(alpha = 0.22f + 0.30f * strokeAlpha)
    }
    val flipRotation = if (animation == TileAnimation.FLIP) -180f * turn else 0f

    Box(
        modifier = modifier
            .w10mTileSize(span = span, gridColumns = gridColumns, spacing = spacing)
            .onSizeChanged { tileSize = it }
            .alpha(if (isDragging) 0.8f else 1f)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                rotationX = flipRotation + lean.rotationX
                rotationY = lean.rotationY
                cameraDistance = 12f * density.density
            }
            .combinedClickable(
                enabled = !preview,
                interactionSource = interactionSource,
                indication = null,
                onClick = { if (!isEditing || clickableWhileEditing) onClick() },
                onLongClick = if (isEditing) null else onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(tileShape)
                .background(fillColor.copy(alpha = fillOpacity))
                .border(if (highlighted) 2.dp else 0.5.dp, strokeColor, tileShape)
        ) {
            val picture = look.picture
            if (picture != null) {
                TilePicture(path = picture, shadeBottom = look.showLabel)
            }
            TileFaces(animation = animation, turn = turn, front = front, back = back)
            RunningMark(visible = LocalTileRunning.current && !isEditing && !preview)
        }

        if (isEditing) {
            TileEditButtons(
                span = span,
                gridColumns = gridColumns,
                onRemove = onRemoveClick,
                onResize = onResizeClick,
                onCustomize = customize,
                onTakeOut = takeOut
            )
        }
    }
}

/**
 * The mark of a hub left running: a short dash in the bottom-right corner, in the tile's own ink.
 * The name keeps the bottom-left and a count the top-right, so the corner is free; and a flat
 * dash is what Windows put under a running program on the taskbar, where a dot is someone
 * else's idiom.
 */
@Composable
private fun BoxScope.RunningMark(visible: Boolean) {
    val shown by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "tile_running_mark"
    )
    if (shown <= 0f) return
    val ink = tileForegroundColor()
    // A thin halo in the opposite tone keeps the dash readable over a photo, which the tile's
    // ink alone is not: the pictures tile writes its name in white over a dark-shaded picture.
    val halo = if (ink.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 7.dp, bottom = 8.dp)
            .size(width = 16.dp, height = 5.dp)
            .graphicsLayer { alpha = shown }
            .background(halo.copy(alpha = 0.35f))
            .padding(1.dp)
            .background(ink)
    )
}

/**
 * The buttons a tile carries while the board is edited, laid out the same on the phone grid, the
 * Windows 8 board and inside a folder.
 *
 * A small tile has room for two: the cross, and a "…" that opens the rest as a menu — one above
 * the other, since a small tile on the phone's eight-cell grid is only forty dp across. Anything
 * bigger keeps its buttons on the top row — three of them used to stand in three corners and the
 * one at the bottom-left sat on the tile's name. Taking a tile out of its folder goes bottom-right,
 * where the name leaves room for the count.
 */
@Composable
private fun BoxScope.TileEditButtons(
    span: Int,
    gridColumns: Int,
    onRemove: () -> Unit,
    onResize: () -> Unit,
    onCustomize: (() -> Unit)?,
    onTakeOut: (() -> Unit)?
) {
    val compact = isCompactTile(span, gridColumns)
    val tiny = span == 1 && gridColumns >= 8
    val size = when {
        tiny -> 16.dp
        compact -> 22.dp
        else -> 24.dp
    }
    val inset = when {
        tiny -> 2.dp
        compact -> 3.dp
        else -> 4.dp
    }

    TileEditButton(
        icon = Icons.Default.Close,
        label = stringResource(R.string.notes_remove_cap),
        size = size,
        solid = true,
        onClick = onRemove,
        modifier = Modifier.align(Alignment.TopEnd).padding(inset)
    )

    if (span == 1) {
        var menuOpen by remember { mutableStateOf(false) }
        Box(modifier = Modifier.align(Alignment.BottomEnd).padding(inset)) {
            TileEditButton(
                icon = Icons.Default.MoreHoriz,
                label = stringResource(R.string.tile_more_actions),
                size = size,
                onClick = { menuOpen = true }
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = Color(0xFF1F1F1F)
            ) {
                TileMenuItem(stringResource(R.string.notes_resize_cap)) { menuOpen = false; onResize() }
                if (onCustomize != null) {
                    TileMenuItem(stringResource(R.string.tile_look_title)) { menuOpen = false; onCustomize() }
                }
                if (onTakeOut != null) {
                    TileMenuItem(stringResource(R.string.start_folder_take_out)) { menuOpen = false; onTakeOut() }
                }
            }
        }
        return
    }

    TileEditButton(
        icon = Icons.Default.KeyboardArrowRight,
        label = stringResource(R.string.notes_resize_cap),
        size = size,
        onClick = onResize,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = inset, end = inset + size + inset)
    )
    if (onCustomize != null) {
        TileEditButton(
            icon = Icons.Default.Brush,
            label = stringResource(R.string.tile_look_title),
            size = size,
            onClick = onCustomize,
            modifier = Modifier.align(Alignment.TopStart).padding(inset)
        )
    }
    if (onTakeOut != null) {
        TileEditButton(
            icon = Icons.Default.DriveFileMove,
            label = stringResource(R.string.start_folder_take_out),
            size = size,
            onClick = onTakeOut,
            modifier = Modifier.align(Alignment.BottomEnd).padding(inset)
        )
    }
}

@Composable
private fun TileEditButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The cross is solid white; the rest are rings, so the cross is the one found first. */
    solid: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(if (solid) Modifier.background(Color.White) else Modifier.border(1.dp, Color.White, CircleShape).background(Color.Black.copy(alpha = 0.5f)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (solid) Color.Black else Color.White,
            modifier = Modifier.size(size * 0.62f)
        )
    }
}

@Composable
private fun TileMenuItem(text: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                color = Color.White
            )
        },
        onClick = onClick
    )
}

/**
 * Ink for whatever sits on a tile. While the tile is see-through the wallpaper decides what reads
 * best, so the theme wins; once the fill is solid enough to be the background, the fill's own
 * brightness does.
 */
@Composable
fun tileForegroundColor(tileColor: Color? = null): Color {
    val zuneColors = LocalZuneColors.current
    val style = LocalTileStyle.current
    val look = LocalTileLook.current
    val fill = look.color?.let { Color(it) } ?: tileColor ?: zuneColors.accentColor
    // Over a picture the name sits on a dark shade along the bottom, so white reads — unless the
    // tile was told in so many words to write in black.
    if (look.picture != null && look.ink != TileInk.DARK) return Color.White
    val dark = tileInkIsDark(
        ink = look.ink ?: style.ink,
        isDarkTheme = zuneColors.isDark,
        opacity = look.opacity?.let { it / 100f } ?: style.opacity,
        fillLuminance = fill.luminance()
    )
    return if (dark) Color.Black else Color.White
}

/**
 * A picture the user gave the tile, filling it edge to edge, with the same shade along the bottom
 * the photo tiles carry so a name can still be read across it.
 */
@Composable
private fun BoxScope.TilePicture(path: String, shadeBottom: Boolean) {
    AsyncImage(
        model = remember(path) { File(path) },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.matchParentSize()
    )
    if (shadeBottom) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
        )
    }
}

/**
 * A tile leans further than a list row does — nine degrees at its corner against a row's five.
 * The shared helper measures from the centre outwards, so the number here is twice the angle.
 */
private const val TILE_TILT_DEGREES = 18f

/**
 * Puts the two faces on screen for the motion in use: [TileAnimation.SLIDE] pushes the front up
 * and brings the back in underneath it, [TileAnimation.FLIP] hands the turn to the surface's own
 * rotation and only picks the visible face, and [TileAnimation.CYCLE] cross-fades.
 */
@Composable
private fun TileFaces(
    animation: TileAnimation,
    turn: Float,
    front: @Composable BoxScope.() -> Unit,
    back: (@Composable BoxScope.() -> Unit)?
) {
    if (back == null || animation == TileAnimation.NONE) {
        Box(modifier = Modifier.fillMaxSize()) { front() }
        return
    }
    when (animation) {
        TileAnimation.FLIP -> {
            if (abs(turn) <= 0.5f) {
                Box(modifier = Modifier.fillMaxSize()) { front() }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationX = -180f }
                ) { back() }
            }
        }

        TileAnimation.CYCLE -> {
            if (turn < 1f) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - turn }) { front() }
            }
            if (turn > 0f) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = turn }) { back() }
            }
        }

        else -> {
            if (turn < 1f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = -size.height * turn }
                ) { front() }
            }
            if (turn > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationY = size.height * (1f - turn) }
                ) { back() }
            }
        }
    }
}
