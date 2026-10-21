package com.serkantkn.zeta.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zeta.data.model.ContactModel
import com.serkantkn.zeta.data.model.HubType
import com.serkantkn.zeta.data.model.TileCornerStyle
import com.serkantkn.zeta.ui.theme.LocalScreenAwake
import kotlinx.coroutines.delay

/**
 * The People tile: a mosaic of the people you talk to, one square each, with a single square
 * turning over to somebody new every few seconds — the tile Windows Phone was known for.
 *
 * With no contacts to show (the permission is not granted, or the phone book is empty) it falls
 * back to the plain glyph face so the tile still looks like every other one.
 */
@Composable
fun W10MPeopleTile(
    span: Int,
    gridColumns: Int,
    spacing: Dp,
    isEditing: Boolean,
    isDragging: Boolean,
    isMergeTarget: Boolean = false,
    cornerStyle: TileCornerStyle,
    contacts: List<ContactModel>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemoveClick: () -> Unit,
    onResizeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val title = stringResource(HubType.PEOPLE.titleRes)
    val mosaic = remember(span) { mosaicOf(TileShape.of(span)) }
    val cellCount = mosaic.cells.size
    // Faces before initials: a mosaic of letters says little, and the alphabet put four people
    // starting with the same letter side by side. With photos enough to fill every square only
    // they take turns; with fewer, the photos stay where they are and the initials around them
    // take turns - turning in order used to swap every photo out for a letter within a minute.
    val withPhoto = remember(contacts) { contacts.filter { it.photoUri != null } }
    val faces = remember(contacts, withPhoto, cellCount) {
        if (withPhoto.size >= cellCount) withPhoto else withPhoto + contacts.filter { it.photoUri == null }
    }
    val settled = if (withPhoto.size >= cellCount) 0 else withPhoto.size
    val hasFaces = faces.isNotEmpty()

    // Which contact each square is showing. A square moves on to the next unused contact in turn,
    // so the same face never sits in two squares at once.
    var assignments by remember(faces, cellCount) {
        mutableStateOf(List(cellCount) { index -> if (faces.isEmpty()) 0 else index % faces.size })
    }
    // Behind an opaque hub the faces stop changing: nobody can see them change.
    val awake = LocalScreenAwake.current
    LaunchedEffect(faces, cellCount, settled, isEditing, awake) {
        if (isEditing || !awake || faces.size <= cellCount || settled >= cellCount) return@LaunchedEffect
        var cell = settled
        while (true) {
            delay(FACE_SWAP_MILLIS)
            val taken = assignments.toMutableList()
            val candidate = generateSequence(taken[cell]) { (it + 1) % faces.size }
                .drop(1)
                .take(faces.size)
                .firstOrNull { it !in taken && it >= settled }
            if (candidate != null) {
                taken[cell] = candidate
                assignments = taken
            }
            cell = if (cell + 1 >= cellCount) settled else cell + 1
        }
    }

    W10MTileSurface(
        liveKey = "hub:${HubType.PEOPLE.name}",
        span = span,
        gridColumns = gridColumns,
        spacing = spacing,
        isEditing = isEditing,
        isDragging = isDragging,
        highlighted = isMergeTarget,
        cornerStyle = cornerStyle,
        onClick = onClick,
        onLongClick = onLongClick,
        onRemoveClick = onRemoveClick,
        onResizeClick = onResizeClick,
        modifier = modifier,
        front = {
            if (!hasFaces) {
                HubGlyphFace(
                    icon = Icons.Default.People,
                    title = title,
                    badgeCount = 0,
                    span = span,
                    gridColumns = gridColumns
                )
                return@W10MTileSurface
            }

            TileFace(span) { m ->
                val cellWidth = m.width / mosaic.columns
                val cellHeight = m.height / mosaic.rows
                mosaic.cells.forEachIndexed { index, cell ->
                    val person = faces[assignments[index] % faces.size]
                    Box(
                        modifier = Modifier
                            .offset(x = cellWidth * cell.column, y = cellHeight * cell.row)
                            .size(cellWidth * cell.width, cellHeight * cell.height)
                    ) {
                        PersonSquare(
                            contact = person,
                            // Squares alternate shade so the mosaic still reads as a grid when the
                            // people in it have no photo.
                            shade = if ((cell.row + cell.column) % 2 == 0) 0.10f else 0.24f,
                            initialSize = m.dp(22f) * cell.width
                        )
                        if (cell.width > 1) {
                            // The large tile's big square is somebody in particular: it says who.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(m.dp(40f))
                                    .align(Alignment.BottomCenter)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                            )
                            Text(
                                text = person.name,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = m.text(15f), fontWeight = FontWeight.Normal),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.align(Alignment.BottomStart).padding(start = m.dp(8f), end = m.dp(8f), bottom = m.dp(6f))
                            )
                        }
                    }
                }
                if (!m.isSmall) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(m.dp(34f))
                            .align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    )
                }
            }
            TileLabel(title, span, gridColumns, Color.White)
        }
    )
}

/** One square, or block of squares, of the mosaic, in grid cells. */
private data class MosaicCell(val column: Int, val row: Int, val width: Int = 1, val height: Int = 1)

private class Mosaic(val columns: Int, val rows: Int, val cells: List<MosaicCell>)

/**
 * How the faces are laid out: one on the small tile, three by three on the medium one as on
 * Windows Phone, three rows of six on the wide one, and on the large one a big face at the top
 * left among twelve small ones.
 */
private fun mosaicOf(shape: TileShape): Mosaic = when (shape) {
    TileShape.SMALL -> Mosaic(1, 1, listOf(MosaicCell(0, 0)))
    TileShape.MEDIUM -> grid(3, 3)
    TileShape.WIDE -> grid(6, 3)
    TileShape.LARGE -> Mosaic(
        4, 4,
        listOf(MosaicCell(0, 0, 2, 2)) + (0 until 4).flatMap { row ->
            (0 until 4).mapNotNull { column -> MosaicCell(column, row).takeUnless { row < 2 && column < 2 } }
        }
    )
}

private fun grid(columns: Int, rows: Int) =
    Mosaic(columns, rows, (0 until rows).flatMap { row -> (0 until columns).map { column -> MosaicCell(column, row) } })

/** One square of the mosaic, fading from the face it had to the face it is given. */
@Composable
private fun PersonSquare(contact: ContactModel, shade: Float, initialSize: Dp) {
    Crossfade(
        targetState = contact,
        animationSpec = tween(durationMillis = 600),
        label = "w10m_people_face",
        modifier = Modifier.fillMaxSize()
    ) { person ->
        val photo = person.photoUri
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            InitialSquare(person.name, shade, initialSize)
        }
    }
}

/** Somebody with no photo: their initial on a slightly darkened patch of the tile colour. */
@Composable
private fun InitialSquare(name: String, shade: Float, size: Dp) {
    val fg = tileForegroundColor()
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = shade)),
        contentAlignment = Alignment.Center
    ) {
        if (initial.isEmpty()) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = fg.copy(alpha = 0.8f),
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = (size.value * 0.9f).sp, fontWeight = FontWeight.Light),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val FACE_SWAP_MILLIS = 3_500L
