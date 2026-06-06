package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * Vertical alphabet index for fast scrolling in the apps list.
 * Supports drag gesture to quickly jump to letter sections.
 * Letters without matching apps are dimmed.
 */
@Composable
fun ZuneAlphabetIndex(
    availableLetters: Set<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val letters = ('A'..'Z').toList() + '#'
    var totalHeight by remember { mutableIntStateOf(0) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier
            .fillMaxHeight()
            .width(24.dp)
            .onGloballyPositioned { totalHeight = it.size.height }
            .pointerInput(availableLetters) {
                detectVerticalDragGestures { change, _ ->
                    if (totalHeight <= 0) return@detectVerticalDragGestures
                    val yPosition = change.position.y
                    val letterIndex = ((yPosition / totalHeight) * letters.size)
                        .toInt()
                        .coerceIn(0, letters.lastIndex)
                    val letter = letters[letterIndex]
                    if (letter != '#' && letter in availableLetters) {
                        onLetterSelected(letter)
                    }
                }
            }
    ) {
        letters.forEach { letter ->
            val isAvailable = letter == '#' || letter in availableLetters
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = ZuneDimens.AlphabetLetterSize,
                    fontWeight = if (isAvailable) FontWeight.Medium else FontWeight.Thin
                ),
                color = if (isAvailable) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    zuneColors.textDim.copy(alpha = 0.3f)
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
