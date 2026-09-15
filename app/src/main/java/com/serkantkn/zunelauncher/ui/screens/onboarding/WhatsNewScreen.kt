package com.serkantkn.zunelauncher.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.util.ReleaseNote

/**
 * What changed since somebody last opened the launcher.
 *
 * Shown once per version and never again. It is deliberately short and deliberately not a tour:
 * an update note that has to be read is an update note nobody reads. If several versions were
 * missed at once - somebody who has not opened it in a while - they all appear, newest at the top,
 * under their own headings.
 */
@Composable
fun WhatsNewScreen(
    notes: List<ReleaseNote>,
    onDismiss: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val isWide = com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen.current
    val sidePadding = if (isWide) {
        com.serkantkn.zunelauncher.ui.components.ZuneWideHubStartPadding
    } else {
        ZuneDimens.ScreenPaddingHorizontal
    }

    BackHandler { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (zuneColors.isDark) Color(0xFF0A0A0A) else Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = sidePadding)
        ) {
            Spacer(modifier = Modifier.height(36.dp))
            Text(
                text = stringResource(R.string.whats_new_title),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 44.sp,
                    letterSpacing = (-1).sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Kept to a readable column on a tablet rather than running the full width.
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .then(if (isWide) Modifier.widthIn(max = 620.dp) else Modifier.fillMaxWidth())
                    .padding(top = 28.dp)
            ) {
                notes.forEach { note ->
                    item(key = "title_${note.version}") {
                        Text(
                            text = stringResource(note.titleRes),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                            color = zuneColors.accentColor,
                            modifier = Modifier.padding(top = 12.dp, bottom = 10.dp)
                        )
                    }
                    items(note.lineRes.size, key = { index -> "line_${note.version}_$index" }) { index ->
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            // A small square instead of a bullet: the launcher has no round things.
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp, end = 12.dp)
                                    .size(6.dp)
                                    .background(zuneColors.textDim)
                            )
                            Text(
                                text = stringResource(note.lineRes[index]),
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Light),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.whats_new_close),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 26.sp
                    ),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }
    }
}
