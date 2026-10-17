package com.serkantkn.zunelauncher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.animation.ZUNE_ENTER_MILLIS
import com.serkantkn.zunelauncher.ui.animation.ZUNE_EXIT_MILLIS
import com.serkantkn.zunelauncher.ui.animation.zuneListMotion
import com.serkantkn.zunelauncher.ui.theme.LocalAnimationsEnabled
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The Zune HD's alphabet: touch a letter heading in a long list and the list gives way to the
 * letters themselves, large and lowercase on the dark, with nothing round them - the ones the
 * list has something under in full white, the rest faint and not to be pressed. The rows slide
 * in from the left as the Zune's lists did, and the letter chosen zooms through on the way back.
 *
 * Used by the Zune layout in place of Windows Phone's wall of accent squares, wherever a list
 * offers one: the app list, and the music and people hubs.
 *
 * @param letters the alphabet in order, "#" included where wanted
 * @param available the letters the list has something under
 */
@Composable
fun ZuneAlphabetJump(
    letters: List<String>,
    available: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val animationsOn = LocalAnimationsEnabled.current
    val progress = remember { Animatable(if (animationsOn) 0f else 1f) }
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (animationsOn) progress.animateTo(1f, tween(ZUNE_ENTER_MILLIS, easing = LinearEasing))
    }

    fun pick(letter: String) {
        if (picked != null) return
        picked = letter
        if (!animationsOn) {
            onPick(letter)
            return
        }
        scope.launch {
            launch { progress.animateTo(2f, tween(ZUNE_EXIT_MILLIS, easing = LinearEasing)) }
            // The list comes back while the letter is still on its way through.
            delay(PICK_AT_MILLIS)
            onPick(letter)
        }
    }

    val columns = if (LocalIsWideScreen.current) WIDE_COLUMNS else COLUMNS
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (zuneColors.isDark) Color.Black else Color.White)
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
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal, vertical = 24.dp)
        ) {
            letters.chunked(columns).forEachIndexed { row, inRow ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    inRow.forEach { letter ->
                        val enabled = letter in available
                        Box(modifier = Modifier.weight(1f)) {
                            Text(
                                text = letter.lowercase(Locale.getDefault()),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Light,
                                    fontSize = LETTER_SIZE,
                                    lineHeight = LETTER_SIZE
                                ),
                                color = if (enabled) ink else ink.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .zuneListMotion({ progress.value }, row, isClicked = picked == letter)
                                    .then(
                                        if (enabled) {
                                            Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { pick(letter) }
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    repeat(columns - inRow.size) { Spacer(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

private val LETTER_SIZE = 60.sp
private const val COLUMNS = 4
private const val WIDE_COLUMNS = 8
private const val PICK_AT_MILLIS = 260L
