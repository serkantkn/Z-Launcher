package com.serkantkn.zunelauncher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * The frame every page that opens over a hub shares.
 *
 * A Windows Phone sub-page never had a back arrow: it had a breadcrumb — the name of where you came
 * from, small, with the name of where you are underneath it in large light type — and the hardware
 * back key. That is what this draws, and it hands its content the bottom inset so a list inside it
 * can scroll clear of the navigation bar.
 */
@Composable
fun MetroSubScreen(
    breadcrumb: String,
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Headings are lower case like every other large word — except a file's name, which is
     * somebody's own spelling and, in Turkish, would lose its dots on the way down.
     */
    lowercaseTitle: Boolean = true,
    content: @Composable (contentPadding: PaddingValues) -> Unit
) {
    BackHandler { onClose() }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = 32.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal
                    )
            ) {
                Text(
                    text = breadcrumb,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
                    maxLines = 1
                )
                Text(
                    // Lower case, like every other large word in the launcher.
                    text = if (lowercaseTitle) title.lowercase(java.util.Locale.getDefault()) else title,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 40.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            ) {
                content(PaddingValues(bottom = bottomInset + 32.dp))
            }
        }
    }
}

/** A Windows Phone text box: a rectangle with a border, and the caret in the accent colour. */
@Composable
fun MetroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(2.dp, zuneColors.textMuted.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = zuneColors.textDim
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(zuneColors.accentColor),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
