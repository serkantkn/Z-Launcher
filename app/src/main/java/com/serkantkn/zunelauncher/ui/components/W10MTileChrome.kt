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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.TileAnimation
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
    /** Tiles that can be given a different picture get a third edit button for it. */
    onIconClick: (() -> Unit)? = null,
    back: (@Composable BoxScope.() -> Unit)? = null,
    front: @Composable BoxScope.() -> Unit
) {
    val style = LocalTileStyle.current
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }

    val animation = animationOverride ?: style.animation
    val fillOpacity = (opacity ?: style.opacity).coerceIn(0f, 1f)
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
    // The outline is what draws a see-through tile; it fades away as the fill takes over.
    val strokeAlpha = (1f - fillOpacity).coerceIn(0f, 1f)
    val strokeColor = when {
        highlighted -> Color.White
        isEditing -> zuneColors.accentColor
        zuneColors.isDark -> Color.White.copy(alpha = 0.20f + 0.25f * strokeAlpha)
        else -> Color.White.copy(alpha = 0.35f + 0.35f * strokeAlpha)
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
                .background((tileColor ?: zuneColors.accentColor).copy(alpha = fillOpacity))
                .border(if (highlighted) 2.dp else 0.5.dp, strokeColor, tileShape)
        ) {
            TileFaces(animation = animation, turn = turn, front = front, back = back)
        }

        if (isEditing) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onRemoveClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.notes_remove_cap),
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White, CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(onClick = onResizeClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.notes_resize_cap),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (onIconClick != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onIconClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Brush,
                        contentDescription = stringResource(R.string.tile_icon_title),
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
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
    val fill = tileColor ?: zuneColors.accentColor
    val dark = tileInkIsDark(
        ink = style.ink,
        isDarkTheme = zuneColors.isDark,
        opacity = style.opacity,
        fillLuminance = fill.luminance()
    )
    return if (dark) Color.Black else Color.White
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
