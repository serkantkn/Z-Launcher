package com.serkantkn.zunelauncher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

/**
 * The head of whatever is open in the pane beside a tablet hub's list: where it is, in the hub's
 * light type, and a ringed button to close it. The hub's own name is already at the top left,
 * so this does not say it again.
 */
@Composable
fun ZuneWidePaneHeader(
    section: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = ZuneDimens.ScreenPaddingHorizontal, end = 32.dp, top = 40.dp, bottom = 4.dp)
    ) {
        Text(
            text = section,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 30.sp),
            color = LocalZuneColors.current.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke(this)
        com.serkantkn.zunelauncher.ui.screens.messaging.RingButton(
            icon = Icons.Default.Close,
            label = stringResource(R.string.common_close_cap),
            enabled = true,
            onClick = onClose
        )
    }
}

/**
 * A tablet hub's pane with nothing open in it: what to do, in the hub's light type at the top
 * left, and the way to start something new as a word in the accent; with an empty bar under it,
 * so the bar along the bottom runs the whole width as it does when something open has its own.
 */
@Composable
fun ZuneWideEmptyPane(title: String, action: String?, onAction: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(start = ZuneDimens.ScreenPaddingHorizontal, top = 40.dp, end = 32.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Light, fontSize = 34.sp),
                color = zuneColors.textMuted
            )
            if (action != null) {
                Text(
                    text = action,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    color = zuneColors.accentColor,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(onClick = onAction)
                        .padding(vertical = 4.dp)
                )
            }
        }
        WindowsPhoneBottomBarStrip(modifier = Modifier.align(Alignment.BottomCenter))
    }
}
