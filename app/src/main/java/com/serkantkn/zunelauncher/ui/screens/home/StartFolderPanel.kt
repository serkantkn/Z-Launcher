package com.serkantkn.zunelauncher.ui.screens.home

import com.serkantkn.zunelauncher.ui.components.swallowTaps
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.serkantkn.zunelauncher.ui.components.LocalTileTakeOut
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay

/**
 * An open folder, the way Windows 10 Mobile opened one: the board dims and the folder's own little
 * board slides down over it, with the folder's name at the top ready to be changed.
 *
 * The tiles inside behave like any others — tap to open, long-press to edit — and each carries a
 * button to put it back on the Start board.
 */
@Composable
fun StartFolderPanel(
    folder: StartTileUIModel.Folder?,
    isEditMode: Boolean,
    columns: Int,
    gap: androidx.compose.ui.unit.Dp,
    onEnterEditMode: () -> Unit,
    onRename: (String) -> Unit,
    onTakeOut: (childId: String) -> Unit,
    onMoveChild: (from: Int, to: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    tile: @Composable (
        model: StartTileUIModel,
        flatIndex: Int,
        isDragging: Boolean,
        isMergeTarget: Boolean,
        tileModifier: Modifier
    ) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    // The band is the theme's ground, not black: on the light theme a black band with white
    // writing stood out like a hole in the screen.
    val bandColor = if (zuneColors.isDark) Color(0xFF0B0B0B) else Color(0xFFF2F2F2)
    val ink = if (zuneColors.isDark) Color.White else Color.Black

    AnimatedVisibility(
        visible = folder != null,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(180)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                // Anywhere off the panel closes the folder, as tapping outside does on the phone.
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClose
                )
        ) {
            AnimatedVisibility(
                visible = folder != null,
                enter = slideInVertically(tween(260)) { -it / 3 } + fadeIn(tween(260)),
                exit = slideOutVertically(tween(200)) { -it / 3 } + fadeOut(tween(200))
            ) {
                val open = folder ?: return@AnimatedVisibility
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The open folder is a band of its own, not tiles floating over the board.
                        .background(bandColor)
                        .statusBarsPadding()
                        .padding(top = 20.dp, bottom = 16.dp)
                        // Taps inside the panel must not reach the scrim behind it.
                        .swallowTaps()
                ) {
                    FolderHeaderBar(
                        folderId = open.id,
                        name = open.name,
                        ink = ink,
                        onRename = onRename
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        MetroStartBoard(
                            tiles = open.children,
                            columns = columns,
                            gap = gap,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                start = ZuneDimens.ScreenPaddingHorizontal,
                                end = ZuneDimens.ScreenPaddingHorizontal,
                                bottom = 24.dp
                            ),
                            onEnterEditMode = onEnterEditMode,
                            onMoveTile = onMoveChild,
                            tile = { model, index, isDragging, isMergeTarget, tileModifier ->
                                // The take-out button is one of the tile's own edit buttons,
                                // so it stands with them instead of over the tile's corner.
                                CompositionLocalProvider(LocalTileTakeOut provides { onTakeOut(model.id) }) {
                                    tile(model, index, isDragging, isMergeTarget, tileModifier)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    BackHandler(enabled = folder != null) { onClose() }
}

/** How tall the folder's name row is, so the open band can be measured before it is drawn. */
internal val FolderHeaderHeight = 46.dp

/**
 * The folder's name, editable in place. Nothing else: the open folder's own tile is the way back
 * out, and emptying a folder is the same gesture as unpinning anything else — its remove button.
 */
@Composable
internal fun FolderHeaderBar(
    folderId: String,
    name: String,
    onRename: (String) -> Unit,
    /** The colour of the name: the panel's ink, or the theme's where the band shows the wallpaper. */
    ink: Color = Color.White
) {
    val zuneColors = LocalZuneColors.current
    // Keyed by the folder, not by its name: keying by the name would hand the stored value back
    // mid-word and eat the letters still being typed.
    var editing by remember(folderId) { mutableStateOf(name) }

    // The name is saved once the typing stops rather than on every letter.
    LaunchedEffect(editing) {
        if (editing == name) return@LaunchedEffect
        delay(RENAME_SETTLE_MILLIS)
        onRename(editing)
    }
    // And when the folder closes before the typing has stopped: the last letters typed were
    // lost with it, because the settle above never got its moment.
    val latestName by rememberUpdatedState(editing)
    val storedName by rememberUpdatedState(name)
    val currentOnRename by rememberUpdatedState(onRename)
    DisposableEffect(folderId) {
        onDispose { if (latestName != storedName) currentOnRename(latestName) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(FolderHeaderHeight)
            .padding(start = ZuneDimens.ScreenPaddingHorizontal, end = 8.dp)
    ) {
        BasicTextField(
            value = editing,
            onValueChange = { editing = it.take(40) },
            singleLine = true,
            textStyle = TextStyle(
                color = ink,
                fontSize = 30.sp,
                fontWeight = FontWeight.Light
            ),
            cursorBrush = SolidColor(zuneColors.accentColor),
            decorationBox = { field ->
                if (editing.isEmpty()) {
                    Text(
                        text = stringResource(R.string.start_folder_name_hint),
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Light
                        ),
                        color = ink.copy(alpha = 0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                field()
            },
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
    }
}

/** The strip along the bottom of the board while it is being edited. */
@Composable
fun StartEditBar(visible: Boolean, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val zuneColors = LocalZuneColors.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.82f))
                // The bar used to end under the navigation bar, with "done" half hidden by it.
                .navigationBarsPadding()
                .padding(start = ZuneDimens.ScreenPaddingHorizontal, end = 12.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.start_edit_hint),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Text(
                text = stringResource(R.string.start_edit_done),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = Color.White,
                modifier = Modifier
                    .background(zuneColors.accentColor)
                    .clickable(onClick = onDone)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

/** How long the typing has to stop before the folder's new name is saved. */
private const val RENAME_SETTLE_MILLIS = 400L
