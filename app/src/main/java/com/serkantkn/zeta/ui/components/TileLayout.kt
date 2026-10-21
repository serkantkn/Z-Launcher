package com.serkantkn.zeta.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.util.TileIconFace

/** The four sizes a Start tile comes in. */
enum class TileShape {
    /** 1×1: a glyph, a count, a number. */
    SMALL,

    /** 2×2: the everyday tile. */
    MEDIUM,

    /** 4×2: room beside the glyph for a few lines of what is new. */
    WIDE,

    /** 4×4: room for a list, a forecast, an agenda. */
    LARGE;

    companion object {
        fun of(span: Int): TileShape = when {
            span <= 1 -> SMALL
            span == 2 -> MEDIUM
            span == 4 -> WIDE
            else -> LARGE
        }
    }
}

/**
 * What a tile's face is drawn to: its size, and a [scale] that says how it compares with the size
 * its type was designed for — a tile of the same shape on the phone's four-column grid. The same
 * tile is half as wide on the eight-column grid and larger on a tablet, and type sized in fixed
 * points either spills over the first or rattles around in the second.
 */
@Immutable
class TileMetrics(val shape: TileShape, val width: Dp, val height: Dp) {

    val scale: Float = when (shape) {
        TileShape.SMALL -> width / 90.dp
        TileShape.MEDIUM -> width / 180.dp
        TileShape.WIDE -> height / 180.dp
        TileShape.LARGE -> width / 372.dp
    }.coerceIn(0.45f, 1.45f)

    /**
     * Words of [size] points on the tile the design was drawn for, at this tile's size - but
     * never below three quarters of it: on the phone's eight-column grid a tile is half the width
     * the design was drawn for, and names and lines of news set at half size could not be read,
     * while set at nearly full size they crowded the tile.
     */
    fun text(size: Float): TextUnit = (size * maxOf(scale, WORD_FLOOR)).sp

    /**
     * A figure - the time, the temperature, the day of the month - of [size] points on the tile
     * the design was drawn for, at exactly this tile's size: figures are sized to fill their tile,
     * and one that kept a floor would no longer fit in it.
     */
    fun figure(size: Float): TextUnit = (size * scale).sp

    /** A length of [size] dp on the tile the design was drawn for, at this tile's size. */
    fun dp(size: Float): Dp = (size * scale).dp

    /** The margin type keeps from the tile's edge. */
    val pad: Dp get() = dp(10f).coerceAtLeast(5.dp)

    val isSmall: Boolean get() = shape == TileShape.SMALL
    val isLarge: Boolean get() = shape == TileShape.LARGE

    /** Wide or large: there is room for a column of words beside, or under, the glyph. */
    val hasRoomForWords: Boolean get() = shape == TileShape.WIDE || shape == TileShape.LARGE
}

/** How far below the size they were designed at words on a smaller tile may go. */
private const val WORD_FLOOR = 0.75f

/** A face that lays itself out by what [TileMetrics] says about the tile it is on. */
@Composable
fun BoxScope.TileFace(span: Int, content: @Composable BoxScope.(TileMetrics) -> Unit) {
    BoxWithConstraints(modifier = Modifier.matchParentSize()) {
        val metrics = TileMetrics(TileShape.of(span), maxWidth, maxHeight)
        content(metrics)
    }
}

/**
 * One thing a live tile has to say: who or what (bold), what about, and when or where. The
 * messaging tile's are conversations, the e-mail tile's letters, the phone tile's missed calls.
 */
@Immutable
data class TileLine(val title: String, val body: String? = null, val meta: String? = null)

/**
 * Lines of a live tile, one under another until the room runs out: each a bold first line, its
 * text under it, and its time at the right of the first line. [maxItems] caps it before that.
 */
@Composable
internal fun TileLineList(
    lines: List<TileLine>,
    metrics: TileMetrics,
    ink: Color,
    maxItems: Int,
    bodyLines: Int = 1,
    modifier: Modifier = Modifier
) {
    // The large tile is read from further back, as a list rather than a glance: larger type.
    val big = metrics.isLarge
    // A tile drawn smaller than designed says less rather than squeezing the same in: fewer
    // things, a line or two of each, room between them - Windows Phone's small grid did the same.
    val narrow = metrics.scale < 0.75f
    val shownItems = if (narrow) minOf(maxItems, if (big) 3 else 2) else maxItems
    val shownBodyLines = if (narrow) minOf(bodyLines, 2) else bodyLines
    // One thing alone on a large tile is the whole tile's message, and is set like one.
    val alone = big && lines.size == 1
    // A list of names alone - missed calls, sums - has the room its bodies would have had.
    val bare = lines.none { !it.body.isNullOrBlank() }
    BoxWithConstraints(modifier = modifier) {
    // In a narrow list a time beside the name leaves the name no room: it goes under it instead.
    val metaBeside = metrics.hasRoomForWords && maxWidth >= 210.dp
    // Only what fits whole: a line cut in half by the tile's edge, or sitting on its name, reads
    // worse than one left out.
    FitColumn(spacing = metrics.dp(if (big) 14f else 9f).coerceAtLeast(6.dp)) {
        lines.take(shownItems).forEach { line ->
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = line.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = metrics.text(if (alone) 26f else if (bare && big) 23f else if (big) 19f else if (bare) 17f else 15f),
                            fontWeight = FontWeight.Normal
                        ),
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (metaBeside && !line.meta.isNullOrBlank()) {
                        Text(
                            text = line.meta,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = metrics.text(if (big) 13f else 12f)),
                            color = ink.copy(alpha = 0.7f),
                            maxLines = 1,
                            modifier = Modifier.padding(start = metrics.dp(8f), bottom = metrics.dp(1f))
                        )
                    }
                }
                val under = if (metaBeside) line.body
                    else listOfNotNull(line.meta, line.body).filter { it.isNotBlank() }.joinToString(" · ")
                if (!under.isNullOrBlank()) {
                    Text(
                        text = under,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = metrics.text(if (alone) 21f else if (big) 16f else 13f),
                            lineHeight = metrics.text(if (alone) 27f else if (big) 20f else 16f)
                        ),
                        color = ink.copy(alpha = 0.75f),
                        maxLines = shownBodyLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    }
}

/**
 * The glyph and the count side by side, the left half of Windows Phone's iconic wide tile, as the
 * messaging tile said "3" next to its smile.
 */
@Composable
internal fun IconicCount(
    icon: ImageVector?,
    face: TileIconFace?,
    count: Int,
    metrics: TileMetrics,
    ink: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    GlyphCount(count = count, iconSize = iconSize, ink = ink, maxWidth = metrics.width, modifier = modifier) { size, glyphModifier ->
        when {
            face != null -> TileIconImage(face = face, size = size, ink = ink, contentDescription = null, modifier = glyphModifier)
            icon != null -> Icon(imageVector = icon, contentDescription = null, tint = ink, modifier = glyphModifier)
        }
    }
}

/**
 * A glyph with its count just to its right, as Windows Phone wrote one on a tile: the figures
 * standing exactly as tall as the glyph, foot to foot with it, in the regular weight. The two
 * are one piece, to be centred together; when they would be wider than [maxWidth] - three
 * figures on a small tile - both shrink alike. With no count, or the tile told to keep its
 * counts to itself, it is the glyph alone.
 */
@Composable
internal fun GlyphCount(
    count: Int,
    iconSize: Dp,
    ink: Color,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    glyph: @Composable (size: Dp, modifier: Modifier) -> Unit
) {
    val shown = count > 0 && LocalTileLook.current.notifications
    val text = countText(count)
    val size = if (shown) fittedGlyphSize(iconSize, text.length, maxWidth) else iconSize
    Row(modifier = modifier) {
        // The foot of the drawing - not of its box, which keeps a margin round it - is the line
        // the figures stand on.
        glyph(size, Modifier.size(size).alignBy { (it.measuredHeight * (1f - GLYPH_MARGIN)).toInt() })
        if (shown) {
            Text(
                text = text,
                style = countStyle(size),
                color = ink,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = size * COUNT_GAP).alignByBaseline()
            )
        }
    }
}

internal fun countText(count: Int): String = if (count > 99) "99+" else count.toString()

/** The glyph's size once it and [characters] figures beside it fit in [maxWidth]. */
internal fun fittedGlyphSize(iconSize: Dp, characters: Int, maxWidth: Dp): Dp {
    val width = iconSize * (1f + COUNT_GAP + characters * FIGURE_ADVANCE)
    return if (width > maxWidth && width.value > 0f) iconSize * (maxWidth / width) else iconSize
}

/**
 * Type whose figures stand as tall as the drawing in a glyph of [glyph]: a figure is 0.71 of the
 * type's size, and a glyph is drawn inside a margin of a twelfth of its box all round.
 */
@Composable
internal fun countStyle(glyph: Dp): androidx.compose.ui.text.TextStyle {
    val drawn = glyph * (1f - 2 * GLYPH_MARGIN)
    val fontSize = with(androidx.compose.ui.platform.LocalDensity.current) { (drawn / FIGURE_HEIGHT).toSp() }
    return MaterialTheme.typography.displaySmall.copy(
        fontSize = fontSize,
        lineHeight = fontSize,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    )
}

/** A figure's height, as a share of the type's size. */
private const val FIGURE_HEIGHT = 0.71f
/** The margin a glyph is drawn inside, as a share of its box: two units of the icons' 24. */
private const val GLYPH_MARGIN = 2f / 24f
/** A figure's width, as a share of the glyph beside it (0.56 of the type's size). */
private const val FIGURE_ADVANCE = 0.56f * (1f - 2 * GLYPH_MARGIN) / FIGURE_HEIGHT
/** The room between the glyph and its count, as a share of the glyph. */
private const val COUNT_GAP = 0.2f

/** The small grey heading a text face starts with: the glyph and whose tile it is. */
@Composable
internal fun TileCaption(icon: ImageVector?, text: String, metrics: TileMetrics, ink: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(6f)),
        modifier = modifier
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = ink.copy(alpha = 0.75f), modifier = Modifier.size(metrics.dp(15f)))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = metrics.text(12f)),
            color = ink.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The room a face keeps clear along the bottom for the tile's name. */
internal fun TileMetrics.labelRoom(): Dp =
    // The name keeps its type size on small grids (see [TileMetrics.text]), so its strip does too.
    if (isSmall) 0.dp else maxOf(dp(26f), (13f * maxOf(scale, WORD_FLOOR) * 1.45f).dp + dp(8f))

/** Fills the face with [content], inside the margins, leaving the name its strip at the bottom. */
@Composable
internal fun BoxScope.TileBody(metrics: TileMetrics, keepLabelRoom: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = metrics.pad,
                end = metrics.pad,
                top = metrics.pad,
                bottom = if (keepLabelRoom) metrics.labelRoom() else metrics.pad
            ),
        content = content
    )
}

/** A face with nothing but words on it fills the width; this is the column it is written in. */
@Composable
internal fun TileColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) { content() }
}

/**
 * The face of a tile that is somebody or somewhere - a person, a conversation, a website, a note:
 * the glyph in the top corner and the name written large along the bottom, in lower case as
 * Windows Phone wrote the names of pinned things. A small tile has room for the glyph alone.
 */
@Composable
internal fun BoxScope.NamedFace(
    span: Int,
    name: String?,
    ink: Color,
    /** Draws the glyph at a size, where the modifier puts it, with [room] across for it and a count. */
    glyph: @Composable (size: Dp, modifier: Modifier, room: Dp) -> Unit
) {
    TileFace(span) { m ->
        if (m.isSmall) {
            glyph(m.width * 0.42f * tileIconScale(), Modifier.align(Alignment.Center), m.width * 0.84f)
            return@TileFace
        }
        val glyphSize = when (m.shape) {
            TileShape.MEDIUM -> m.dp(34f)
            TileShape.WIDE -> m.dp(40f)
            else -> m.dp(60f)
        } * tileIconScale()
        glyph(glyphSize, Modifier.align(Alignment.TopStart).padding(m.pad), m.width - m.pad * 2)
        if (name != null) {
            Text(
                text = name.lowercase(),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = m.text(when (m.shape) { TileShape.MEDIUM -> 19f; TileShape.WIDE -> 26f; else -> 36f }),
                    lineHeight = m.text(when (m.shape) { TileShape.MEDIUM -> 22f; TileShape.WIDE -> 30f; else -> 40f }),
                    fontWeight = FontWeight.Light
                ),
                color = ink,
                maxLines = if (m.isLarge) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = m.pad, end = m.pad, bottom = m.dp(8f))
            )
        }
    }
}

/**
 * A column that shows as many of its children as fit whole, from the top, and leaves out the rest
 * - for a live tile's lists, which have to look finished whatever size the tile is drawn at.
 */
@Composable
internal fun FitColumn(modifier: Modifier = Modifier, spacing: Dp = 0.dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = mutableListOf<Placeable>()
        var used = 0
        for (measurable in measurables) {
            val placeable = measurable.measure(loose)
            val needed = used + (if (placeables.isEmpty()) 0 else gap) + placeable.height
            // The first always stays, cut short by the height it is given: an empty list would say
            // there is nothing new when there is.
            if (placeables.isNotEmpty() && constraints.hasBoundedHeight && needed > constraints.maxHeight) break
            used = needed
            placeables += placeable
        }
        // As wide as its widest child, so it can stand beside something else in a row.
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceAtLeast(constraints.minWidth)
        layout(width, used.coerceAtLeast(constraints.minHeight)) {
            var y = 0
            placeables.forEachIndexed { index, placeable ->
                if (index > 0) y += gap
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}
