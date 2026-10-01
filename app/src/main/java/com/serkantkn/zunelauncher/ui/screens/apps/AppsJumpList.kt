package com.serkantkn.zunelauncher.ui.screens.apps

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import java.text.Collator
import java.util.Locale

/**
 * The alphabet, as a screen.
 *
 * This is the one interaction everybody remembers about the Windows Phone app list: press a letter
 * heading and the list folds away into a wall of letters, where the ones you have apps under are
 * lit in the accent colour and the rest are not there to be pressed at all. One tap and the list
 * comes back at that letter.
 *
 * The letters turn in one after another, top-left first, which is the platform's own entrance.
 */
@Composable
internal fun AppsJumpList(
    availableLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val locale = Locale.getDefault()
    var appeared by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { appeared = true }

    val letters = remember(availableLetters, locale) { jumpAlphabet(availableLetters, locale) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (zuneColors.isDark) Color(0xFF0A0A0A) else Color(0xFFFAFAFA))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.apps_jump_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp
                ),
                color = zuneColors.textMuted,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Four squares across fill a phone; on a tablet the same four would be the size of
            // a hand, so the alphabet is set the way Windows 8's semantic zoom set it: wide.
            val columns = if (LocalIsWideScreen.current) WIDE_COLUMNS else COLUMNS
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(letters, key = { _, letter -> letter }) { index, letter ->
                    val available = letter in availableLetters
                    // The wave runs diagonally, as the platform's own tile entrance does.
                    val step = (index % columns) + (index / columns)
                    val turn by animateFloatAsState(
                        targetValue = if (appeared) 0f else -90f,
                        animationSpec = tween(
                            durationMillis = 260,
                            delayMillis = step * 28,
                            easing = FastOutSlowInEasing
                        ),
                        label = "jump_letter"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .graphicsLayer {
                                rotationY = turn
                                transformOrigin = TransformOrigin(0f, 0.5f)
                                cameraDistance = 14f * density.density
                            }
                            .then(if (available) Modifier.wpTilt(interactionSource) else Modifier)
                            .background(
                                if (available) {
                                    zuneColors.accentColor
                                } else if (zuneColors.isDark) {
                                    Color.White.copy(alpha = 0.06f)
                                } else {
                                    Color.Black.copy(alpha = 0.06f)
                                }
                            )
                            .then(
                                if (available) {
                                    Modifier.clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { onLetterSelected(letter) }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            text = letter.toString().lowercase(locale),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Light,
                                fontSize = 26.sp
                            ),
                            color = if (available) Color.White else zuneColors.textDim,
                            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Which letters the wall shows: the reader's own alphabet, plus any letter an app actually starts
 * with that the alphabet does not have, and "#" at the end for everything that starts with a
 * number or a symbol.
 */
internal fun jumpAlphabet(availableLetters: Set<Char>, locale: Locale): List<Char> {
    val base = if (locale.language == "tr") TURKISH_ALPHABET else LATIN_ALPHABET
    val extra = availableLetters.filter { it != '#' && it !in base }
    val collator = Collator.getInstance(locale)
    return (base + extra)
        .distinct()
        .sortedWith { a, b -> collator.compare(a.toString(), b.toString()) }
        .plus('#')
}

private val LATIN_ALPHABET = ('A'..'Z').toList()

/** Turkish has its own letters and its own order; Ç follows C, Ş follows S. */
private val TURKISH_ALPHABET =
    listOf('A', 'B', 'C', 'Ç', 'D', 'E', 'F', 'G', 'Ğ', 'H', 'I', 'İ', 'J', 'K', 'L', 'M', 'N',
        'O', 'Ö', 'P', 'R', 'S', 'Ş', 'T', 'U', 'Ü', 'V', 'Y', 'Z')

private const val COLUMNS = 4
private const val WIDE_COLUMNS = 9
