package com.serkantkn.zunelauncher.ui.components

import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.serkantkn.zunelauncher.ui.tv.tvTake
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
import androidx.compose.ui.graphics.takeOrElse
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
    // A television sets it as Media Center did a page inside a library: its margins, the path
    // small and pale blue, the page's name under it.
    val tv = com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current
    val side = if (tv) com.serkantkn.zunelauncher.ui.tv.MEDIA_CENTER_SIDE else ZuneDimens.ScreenPaddingHorizontal

    // Vista set a page inside a library or its settings as a menu: where you came from huge and
    // faint at the left ("settings"), the page's name in small white capitals, the page under it.
    if (com.serkantkn.zunelauncher.ui.tv.LocalVista.current) {
        HubGround(modifier = modifier) {
            androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val pageStart = maxWidth * VISTA_PAGE_START
                Text(
                    text = breadcrumb.lowercase(java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = com.serkantkn.zunelauncher.ui.tv.SelawikFamily,
                        fontWeight = FontWeight.Light,
                        fontSize = 28.sp
                    ),
                    color = com.serkantkn.zunelauncher.ui.tv.Vista.Faint,
                    maxLines = 1,
                    modifier = Modifier.padding(start = maxWidth * VISTA_PATH_START, top = 30.dp)
                )
                Column(modifier = Modifier.fillMaxSize().padding(start = pageStart, end = 48.dp, top = 26.dp)) {
                    Text(
                        text = if (lowercaseTitle) title.uppercase(java.util.Locale.getDefault()) else title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = com.serkantkn.zunelauncher.ui.tv.SelawikFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 20.sp
                        ),
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 30.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        content(PaddingValues(bottom = bottomInset + 32.dp))
                    }
                }
            }
        }
        return
    }

    HubGround(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (tv) 28.dp else 32.dp,
                        start = side,
                        end = side
                    )
            ) {
                Text(
                    text = breadcrumb,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    ),
                    color = if (tv) com.serkantkn.zunelauncher.ui.tv.MediaCenter.Dim
                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f),
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
                    .padding(horizontal = side)
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
    modifier: Modifier = Modifier,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
    /** A search box: on a television it takes the remote as it appears, and the keyboard opens. */
    search: Boolean = false,
    /** The words typed: the page's own colour, or white inside a dialog, which is always dark. */
    textColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
) {
    val zuneColors = LocalZuneColors.current
    val takeFocus = search && com.serkantkn.zunelauncher.ui.theme.LocalIsTv.current
    val requester = remember { FocusRequester() }
    if (takeFocus) LaunchedEffect(Unit) { requester.tvTake() }
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
                color = textColor.takeOrElse { MaterialTheme.colorScheme.onBackground }
            ),
            cursorBrush = SolidColor(zuneColors.accentColor),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (takeFocus) Modifier.focusRequester(requester) else Modifier)
        )
    }
}

/** Where Vista's pages set the faint path and the page itself across the screen. */
private const val VISTA_PATH_START = 0.12f
private const val VISTA_PAGE_START = 0.30f
