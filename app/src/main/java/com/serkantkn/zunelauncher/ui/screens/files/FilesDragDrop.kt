package com.serkantkn.zunelauncher.ui.screens.files

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.FileItemModel
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.util.DropAction
import com.serkantkn.zunelauncher.util.category
import java.io.File
import kotlin.math.roundToInt

/*
 * Dragging files onto folders, on the phone and the tablet alike.
 *
 * Hold a file and move the finger, and a ghost of it follows — a smaller copy of its picture, a
 * count when more than one is going, and over it in words what letting go will do ("move to
 * Pictures", "copy to SD card"). Folders it passes over light up the way Windows 8 lit them, an
 * accent frame and a slight lift; the places down the tablet's left turn accent. Letting go over
 * one hands the files to the view model's paste; letting go anywhere else sends the ghost back to
 * where it came from.
 *
 * A hold without a move keeps meaning what it meant before — the tablet chooses the thing, the
 * phone opens its menu — so it is only decided when the finger lifts or moves, not when the hold
 * is first felt. Everything is measured in the window's own coordinates, so a tile in the grid
 * and a place in the side pane can be found under the same finger.
 */

/** The drag in progress for everything under [FilesDragArea]; null outside the Files hub. */
internal val LocalFilesDragState = staticCompositionLocalOf<FilesDragState?> { null }

/** How a folder shows that it would take the drop. */
internal enum class DropHighlight(val scale: Float) {
    /** A tile in the thumbnails view: an accent frame, and it rises a little. */
    TILE(1.05f),
    /** A row of a list: the same frame, and barely any rise, or it would overrun the screen. */
    ROW(1.015f),
    /** A place in the tablet's side pane: the whole entry turns accent, its words white. */
    PANE(1.03f)
}

/**
 * What is being dragged, where the finger is, and what it is over.
 *
 * One per hub, hoisted above both layouts; the tiles and rows find it through
 * [LocalFilesDragState]. The folders that can take a drop tell it where they are as they are
 * laid out, and are asked for their bounds again only while a drag is going.
 */
@Stable
internal class FilesDragState(
    private val selection: () -> Set<String>,
    private val actionOf: (List<String>, File) -> DropAction,
    private val onDrop: (paths: List<String>, destination: File, copy: Boolean) -> Unit
) {
    /** The files on their way, the one under the finger (the ghost wears its picture), and where it was. */
    class Drag(val paths: List<String>, val item: FileItemModel, val home: Rect) {
        internal val pathSet: Set<String> = paths.toHashSet()
    }

    /** A folder under the finger that would take the drop, and how. */
    class Hover(internal val target: DropTarget, val folder: File, val label: String, val isPlace: Boolean, val action: DropAction)

    /** A ghost on its way out: into the folder it was dropped on, or back home. */
    class Leaving(val drag: Drag, val from: Offset, val to: Rect, val dropped: Boolean)

    /** A folder that can take a drop, as it stands this frame. */
    internal class DropTarget {
        var folder: File = File("/")
        var label: String = ""
        var isPlace: Boolean = false
        var coordinates: LayoutCoordinates? = null

        fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()
    }

    var drag: Drag? by mutableStateOf(null)
        private set

    /** Where the finger is, in the window's coordinates. */
    var finger: Offset by mutableStateOf(Offset.Zero)
        private set

    var hover: Hover? by mutableStateOf(null)
        private set

    var leaving: Leaving? by mutableStateOf(null)
        private set

    private val targets = LinkedHashSet<DropTarget>()

    // The target last looked at, refused or not, so a finger resting on one folder does not ask
    // the rules again with every movement.
    private var probed: DropTarget? = null

    internal fun register(target: DropTarget) {
        targets += target
    }

    internal fun unregister(target: DropTarget) {
        targets -= target
        if (probed === target) probed = null
        if (hover?.target === target) hover = null
    }

    fun isDragged(path: String): Boolean = drag?.pathSet?.contains(path) == true

    /** The selection when the held thing is part of it — Windows drags all that is chosen — else the thing alone. */
    internal fun pathsFor(item: FileItemModel): List<String> {
        val path = item.file.absolutePath
        val chosen = selection()
        return if (path in chosen) chosen.toList() else listOf(path)
    }

    internal fun begin(item: FileItemModel, home: Rect, at: Offset) {
        leaving = null
        probed = null
        hover = null
        drag = Drag(pathsFor(item), item, home)
        moveTo(at)
    }

    internal fun moveTo(at: Offset) {
        val current = drag ?: return
        finger = at
        // The smallest target under the finger, should one ever sit inside another.
        val hit = targets
            .mapNotNull { target -> target.bounds()?.takeIf { it.contains(at) }?.let { target to it } }
            .minByOrNull { (_, bounds) -> bounds.width * bounds.height }
            ?.first
        if (hit === probed) return
        probed = hit
        hover = hit?.let { target ->
            val action = actionOf(current.paths, target.folder)
            if (action == DropAction.REFUSED) null
            else Hover(target, target.folder, target.label, target.isPlace, action)
        }
    }

    /** The finger lifted: into the folder under it, or back home if there is none. */
    internal fun release() {
        val current = drag ?: return
        val target = hover
        clear()
        if (target == null) {
            leaving = Leaving(current, finger, current.home, dropped = false)
            return
        }
        leaving = Leaving(current, finger, target.target.bounds() ?: current.home, dropped = true)
        onDrop(current.paths, target.folder, target.action == DropAction.COPY)
    }

    /** The gesture was taken away, or its tile went: nothing is dropped, the ghost goes home. */
    internal fun cancel() {
        val current = drag ?: return
        clear()
        leaving = Leaving(current, finger, current.home, dropped = false)
    }

    internal fun finishLeaving(gone: Leaving) {
        if (leaving === gone) leaving = null
    }

    private fun clear() {
        drag = null
        hover = null
        probed = null
    }
}

@Composable
internal fun rememberFilesDragState(viewModel: FilesHubViewModel): FilesDragState = remember(viewModel) {
    FilesDragState(
        selection = { viewModel.selection.value },
        actionOf = viewModel::dropActionOnto,
        onDrop = viewModel::dropOnto
    )
}

/**
 * The hub's own box, which lets every tile, row and place inside it take part in a drag, and
 * draws the ghost over all of it — the bars included, as Windows drew a drag over everything.
 */
@Composable
internal fun FilesDragArea(
    state: FilesDragState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    CompositionLocalProvider(LocalFilesDragState provides state) {
        Box(modifier = modifier) {
            content()
            FilesDragGhost(state, Modifier.matchParentSize())
        }
    }
}

// ════════════════════════════════════════════════════════════
// PICKING UP
// ════════════════════════════════════════════════════════════

/**
 * What a file's tile or row needs to be picked up: a [modifier] to put outside its clickable,
 * and [onHold] to give that clickable as its long click in place of the real one.
 *
 * The clickable still feels the hold — with its buzz, and taking the rest of the gesture so the
 * list under it does not scroll — but only notes it here. A move past the touch slop after that
 * picks the thing up; a lift without one does what the long click always did.
 */
internal class FileDragSource(private val state: FilesDragState) {
    internal var item: FileItemModel? = null
    internal var onLongClick: () -> Unit = {}
    private var coordinates: LayoutCoordinates? = null
    private var inGesture = false
    private var held = false

    /**
     * The long click the clickable is given. From a finger it only arms the drag; from
     * TalkBack's actions, which have no finger behind them, it is the long click itself.
     */
    val onHold: () -> Unit = { if (inGesture) held = true else onLongClick() }

    val modifier: Modifier = Modifier
        .onGloballyPositioned { coordinates = it }
        // What is being dragged stays where it was, faded, as Windows left it.
        .graphicsLayer { alpha = if (item?.let { state.isDragged(it.file.absolutePath) } == true) DRAGGED_ALPHA else 1f }
        .pointerInput(Unit) {
            awaitEachGesture {
                // The Final pass: this only watches, after the clickable and the list have had
                // their say, and whatever they consumed is still seen.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                inGesture = true
                held = false
                var heldAt: Offset? = null
                var dragging = false
                var released = false
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Final).changes
                            .firstOrNull { it.id == down.id } ?: break
                        if (held && heldAt == null) heldAt = change.previousPosition
                        if (!change.pressed) {
                            if (dragging) {
                                released = true
                                state.release()
                            } else if (held) {
                                onLongClick()
                            }
                            break
                        }
                        val from = heldAt ?: continue
                        val layout = coordinates?.takeIf { it.isAttached } ?: continue
                        if (dragging) {
                            state.moveTo(layout.localToRoot(change.position))
                        } else if ((change.position - from).getDistance() > viewConfiguration.touchSlop) {
                            val picked = item ?: continue
                            state.begin(picked, layout.boundsInRoot(), layout.localToRoot(change.position))
                            dragging = true
                        }
                    }
                } finally {
                    if (dragging && !released) state.cancel()
                    inGesture = false
                    held = false
                }
            }
        }
}

/**
 * The drag source for [item], or null outside the Files hub — then the caller keeps its own long
 * click. A folder is also a drop target, styled by [highlight].
 */
@Composable
internal fun rememberFileDragSource(
    item: FileItemModel,
    onLongClick: () -> Unit,
    highlight: DropHighlight
): FileDragHandle? {
    val state = LocalFilesDragState.current ?: return null
    val source = remember(state) { FileDragSource(state) }
    SideEffect {
        source.item = item
        source.onLongClick = onLongClick
    }
    val target = if (item.isDirectory) Modifier.fileDropTarget(item.file, item.name, highlight) else Modifier
    return FileDragHandle(source.modifier.then(target), source.onHold)
}

/** What a tile or row puts on: [modifier] outside its clickable, [onHold] as that clickable's long click. */
internal class FileDragHandle(val modifier: Modifier, val onHold: () -> Unit)

// ════════════════════════════════════════════════════════════
// LETTING GO
// ════════════════════════════════════════════════════════════

/**
 * Makes this a place files can be dropped into [folder], called [label] in the ghost's words.
 * [isPlace] words it as a place ("SD card konumuna") rather than a folder ("Pictures klasörüne").
 * Outside the Files hub it does nothing.
 */
internal fun Modifier.fileDropTarget(
    folder: File,
    label: String,
    highlight: DropHighlight,
    isPlace: Boolean = false
): Modifier = composed {
    val state = LocalFilesDragState.current ?: return@composed Modifier
    val target = remember(state) { FilesDragState.DropTarget() }
    SideEffect {
        target.folder = folder
        target.label = label
        target.isPlace = isPlace
    }
    DisposableEffect(state, target) {
        onDispose { state.unregister(target) }
    }
    val hovered = state.hover?.target === target
    val lift by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(HOVER_MILLIS),
        label = "files_drop_target"
    )
    val accent = LocalZuneColors.current.accentColor
    Modifier
        .onGloballyPositioned {
            target.coordinates = it
            state.register(target)
        }
        .graphicsLayer {
            val scale = 1f + (highlight.scale - 1f) * lift
            scaleX = scale
            scaleY = scale
            // The white-on-accent turn below has to be worked out in a layer of its own.
            if (highlight == DropHighlight.PANE) {
                compositingStrategy = if (hovered) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
        }
        .drawWithContent {
            drawContent()
            if (highlight == DropHighlight.PANE) {
                if (!hovered) return@drawWithContent
                // Whatever the entry drew turns white, and the accent goes in behind it.
                drawRect(Color.White, blendMode = BlendMode.SrcIn)
                drawRect(accent, blendMode = BlendMode.DstOver)
            } else if (lift > 0f) {
                val stroke = FRAME_WIDTH.toPx()
                drawRect(
                    color = accent,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    alpha = lift,
                    style = Stroke(stroke)
                )
            }
        }
}

// ════════════════════════════════════════════════════════════
// THE GHOST
// ════════════════════════════════════════════════════════════

/**
 * The ghost under the finger: the dragged thing's picture at about two-thirds of its size, held
 * a little above the finger so the finger does not hide it, a count when there are several, and
 * over it what letting go will do.
 */
@Composable
private fun FilesDragGhost(state: FilesDragState, modifier: Modifier) {
    val density = LocalDensity.current
    val animationsOn = LocalAnimationsEnabled.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    Box(modifier = modifier.onGloballyPositioned { origin = it.positionInRoot() }) {
        val drag = state.drag
        val leaving = state.leaving
        if (drag != null) {
            val side = ghostSide(drag, density.density)
            val lift = side / 2f + with(density) { GHOST_LIFT.toPx() }
            val centre = { state.finger - origin - Offset(0f, lift) }
            Ghost(drag, side, centre, alpha = { 1f }, scale = { 1f })
            state.hover?.let { hover ->
                Caption(hover, Modifier.placedAt(0.5f, 1f) {
                    centre() - Offset(0f, side / 2f + with(density) { CAPTION_GAP.toPx() })
                })
            }
        } else if (leaving != null) {
            val side = ghostSide(leaving.drag, density.density)
            val lift = side / 2f + with(density) { GHOST_LIFT.toPx() }
            val progress = remember(leaving) { Animatable(0f) }
            LaunchedEffect(leaving) {
                if (animationsOn) {
                    progress.animateTo(
                        1f,
                        tween(if (leaving.dropped) DROP_MILLIS else RETURN_MILLIS, easing = FastOutSlowInEasing)
                    )
                }
                state.finishLeaving(leaving)
            }
            val start = leaving.from - Offset(0f, lift)
            Ghost(
                drag = leaving.drag,
                side = side,
                centre = { lerp(start, leaving.to.center, progress.value) - origin },
                alpha = { 1f - progress.value },
                // Into a folder it shrinks away; going home it keeps its size, as it came.
                scale = { if (leaving.dropped) 1f - 0.6f * progress.value else 1f }
            )
        }
    }
}

@Composable
private fun Ghost(
    drag: FilesDragState.Drag,
    side: Float,
    centre: () -> Offset,
    alpha: () -> Float,
    scale: () -> Float
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val sideDp = with(density) { side.toDp() }
    val count = drag.paths.size
    Box(
        modifier = Modifier
            .placedAt(0.5f, 0.5f, centre)
            .graphicsLayer {
                this.alpha = alpha()
                scaleX = scale()
                scaleY = scale()
            }
            .size(sideDp)
    ) {
        if (count > 1) {
            // A second card behind the first: more than one thing is going.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(STACK_OFFSET, STACK_OFFSET)
                    .graphicsLayer { this.alpha = GHOST_ALPHA * 0.6f }
                    .background(categoryColor(drag.item.category(), zuneColors.accentColor))
            )
        }
        FileThumb(
            item = drag.item,
            modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = GHOST_ALPHA },
            glyphSize = sideDp * 0.35f
        )
        if (count > 1) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(BADGE_SIZE / 3, -BADGE_SIZE / 3)
                    .size(BADGE_SIZE)
                    .background(zuneColors.accentColor)
                    .border(2.dp, Color.White)
            ) {
                Text(
                    text = count.toString(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

/** "Pictures klasörüne taşı": plain words on a plain box, as Windows 8 put them by a drag. */
@Composable
private fun Caption(hover: FilesDragState.Hover, modifier: Modifier) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val text = stringResource(
        when {
            hover.isPlace && hover.action == DropAction.COPY -> R.string.files_drop_copy_place
            hover.isPlace -> R.string.files_drop_move_place
            hover.action == DropAction.COPY -> R.string.files_drop_copy_folder
            else -> R.string.files_drop_move_folder
        },
        hover.label
    )
    Box(
        modifier = modifier
            .background(if (zuneColors.isDark) Color(0xFF1F1F1F) else Color.White)
            .border(1.dp, ink.copy(alpha = 0.25f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text = text, color = ink, fontSize = 14.sp, maxLines = 1)
    }
}

/**
 * Places this at a point given in the parent's coordinates, read only while placing — so the
 * ghost follows the finger frame by frame without anything being composed again. [alignX] and
 * [alignY] say which point of it sits there: 0.5 and 0.5 its centre, 0.5 and 1 the middle of
 * its foot.
 */
private fun Modifier.placedAt(alignX: Float, alignY: Float, point: () -> Offset): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        layout(placeable.width, placeable.height) {
            val at = point()
            placeable.place(
                (at.x - placeable.width * alignX).roundToInt(),
                (at.y - placeable.height * alignY).roundToInt()
            )
        }
    }

/** About two-thirds of the thing picked up, within reason: a list row is short, a tablet tile big. */
private fun ghostSide(drag: FilesDragState.Drag, density: Float): Float {
    val picked = minOf(drag.home.width, drag.home.height) * GHOST_SCALE
    return picked.coerceIn(GHOST_MIN_DP * density, GHOST_MAX_DP * density)
}

private const val GHOST_SCALE = 0.7f
private const val GHOST_MIN_DP = 56f
private const val GHOST_MAX_DP = 120f
private const val GHOST_ALPHA = 0.85f
private const val DRAGGED_ALPHA = 0.4f
private const val HOVER_MILLIS = 120
private const val DROP_MILLIS = 180
private const val RETURN_MILLIS = 260
private val GHOST_LIFT = 24.dp
private val CAPTION_GAP = 10.dp
private val STACK_OFFSET = 6.dp
private val BADGE_SIZE = 26.dp
private val FRAME_WIDTH = 3.dp
