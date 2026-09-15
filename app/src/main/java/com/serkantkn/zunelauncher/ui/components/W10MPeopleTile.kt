package com.serkantkn.zunelauncher.ui.components

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
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
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
    val columns = mosaicColumns(span)
    val rows = if (span == 4) columns / 2 else columns
    val cellCount = columns * rows
    val hasFaces = contacts.isNotEmpty()

    // Which contact each square is showing. A square moves on to the next unused contact in turn,
    // so the same face never sits in two squares at once.
    var assignments by remember(contacts, cellCount) {
        mutableStateOf(List(cellCount) { index -> if (contacts.isEmpty()) 0 else index % contacts.size })
    }
    LaunchedEffect(contacts, cellCount, isEditing) {
        if (isEditing || contacts.size <= cellCount) return@LaunchedEffect
        var cell = 0
        while (true) {
            delay(FACE_SWAP_MILLIS)
            val taken = assignments.toMutableList()
            val candidate = generateSequence(taken[cell]) { (it + 1) % contacts.size }
                .drop(1)
                .take(contacts.size)
                .firstOrNull { it !in taken }
            if (candidate != null) {
                taken[cell] = candidate
                assignments = taken
            }
            cell = (cell + 1) % cellCount
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

            Column(modifier = Modifier.fillMaxSize()) {
                repeat(rows) { row ->
                    Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        repeat(columns) { column ->
                            val cell = row * columns + column
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                PersonSquare(
                                    contact = contacts[assignments[cell] % contacts.size],
                                    // Squares alternate shade so the mosaic still reads as a grid
                                    // when the people in it have no photo.
                                    shade = if ((row + column) % 2 == 0) 0.10f else 0.24f
                                )
                            }
                        }
                    }
                }
            }

            if (span > 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                )
                TileLabel(title, span, gridColumns, Color.White)
            }
        }
    )
}

/** One square of the mosaic, fading from the face it had to the face it is given. */
@Composable
private fun PersonSquare(contact: ContactModel, shade: Float) {
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
            InitialSquare(person.name, shade)
        }
    }
}

/** Somebody with no photo: their initial on a slightly darkened patch of the tile colour. */
@Composable
private fun InitialSquare(name: String, shade: Float) {
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
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                text = initial,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Light),
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 1×1 shows one face, 2×2 shows four, the wide tile eight, the big square nine. */
private fun mosaicColumns(span: Int): Int = when {
    span <= 1 -> 1
    span == 2 -> 2
    span == 4 -> 4
    else -> 3
}

private const val FACE_SWAP_MILLIS = 3_500L
