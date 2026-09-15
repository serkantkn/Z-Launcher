package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.ui.components.W10MHubTile
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The stage the lessons are played on: the start screen, full size.
 *
 * A gesture practised in a little box in the middle of a page is a gesture practised somewhere
 * else. Holding a tile, swiping between the screens and turning a pivot all feel like what they
 * are only at the size they really happen at, so the lessons take over the whole screen and the
 * tour's own words move to a card floating at the bottom of it.
 *
 * Nothing here is wired to anything. The tiles are the real tiles and they lean and turn like the
 * real ones, but tapping one opens nothing: this is a rehearsal, and a rehearsal that launches the
 * camera is a rehearsal nobody finishes.
 */

/** The tiles the clone is laid out with - the ones anybody would recognise. */
private val CLONE_TILES = listOf(
    HubType.PHONE,
    HubType.MESSAGING,
    HubType.PEOPLE,
    HubType.INTERNET,
    HubType.PICTURES,
    HubType.MUSIC,
    HubType.SETTINGS,
    HubType.CAMERA
)

/**
 * A copy of the start screen: the clock, the date, and a board of tiles.
 *
 * [editingTile] is the tile currently being held, if any. It is passed in rather than kept here so
 * the lesson above can tell when it happened and mark itself done.
 */
@Composable
fun StartScreenClone(
    editingTile: HubType?,
    onTileLongPress: (HubType) -> Unit,
    modifier: Modifier = Modifier
) {
    val now = remember { Date() }
    val time = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(now) }
    val date = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 16.dp)
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Light,
                fontSize = 56.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = date.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalZuneColors.current.textMuted,
            modifier = Modifier.padding(bottom = 18.dp)
        )

        CLONE_TILES.chunked(2).forEach { pair ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                pair.forEach { hub ->
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                        W10MHubTile(
                            hubType = hub,
                            span = 2,
                            gridColumns = 4,
                            spacing = 8.dp,
                            isEditing = editingTile == hub,
                            isDragging = false,
                            cornerStyle = TileCornerStyle.ROUNDED,
                            onClick = { },
                            onLongClick = { onTileLongPress(hub) },
                            onRemoveClick = { },
                            onResizeClick = { }
                        )
                    }
                }
                // An odd row keeps its gap rather than stretching the last tile across it.
                if (pair.size == 1) Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * What the lesson is asking for, over the stage.
 *
 * It sits at the bottom, over the clone rather than beside it, so the thing being taught stays
 * the largest thing on the screen.
 */
@Composable
fun LessonInstruction(
    title: String,
    line: String,
    ticks: List<Pair<String, Boolean>>,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val accentRule = zuneColors.accentColor
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Opaque, with a rule along the top: a see-through card over a board of tiles is a
            // card nobody can read.
            .background(if (zuneColors.isDark) Color(0xFF0A0A0A) else Color.White)
            .drawBehind {
                drawRect(
                    color = accentRule,
                    size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx())
                )
            }
            // The tour's frame already stands clear of the system bars; padding again here would
            // leave a white band under the card.
            .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 18.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Light,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = zuneColors.textMuted,
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ticks.forEach { (label, done) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                if (done) zuneColors.accentColor else Color.Transparent
                            )
                            .then(
                                if (done) Modifier else Modifier.border(2.dp, zuneColors.textDim)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (done) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (done) zuneColors.accentColor else zuneColors.textMuted,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
