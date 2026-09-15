package com.serkantkn.zunelauncher.ui.screens.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors

/**
 * Find on page: a strip above the address bar with the box, the tally and the two arrows, the way
 * Windows Phone's Internet Explorer put it there.
 */
@Composable
internal fun BrowserFindBar(
    query: String,
    matches: Pair<Int, Int>,
    onQueryChanged: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zuneColors = LocalZuneColors.current
    val barColor = if (zuneColors.isDark) Color(0xFF1F1F1F) else Color(0xFFE0E0E0)
    val inputBg = if (zuneColors.isDark) Color(0xFF333333) else Color(0xFFCCCCCC)
    val ink = if (zuneColors.isDark) Color.White else Color.Black
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(barColor)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .background(inputBg, RoundedCornerShape(2.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink, fontSize = 14.sp),
                cursorBrush = SolidColor(zuneColors.accentColor),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.browser_find_hint),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp),
                    color = zuneColors.textMuted
                )
            }
        }

        // The tally only means something once something has been looked for.
        if (query.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.browser_find_count, matches.first, matches.second),
                style = MaterialTheme.typography.bodyMedium,
                color = if (matches.second == 0) zuneColors.textMuted else zuneColors.accentColor
            )
        }

        FindBarButton(Icons.Default.KeyboardArrowUp, ink, onPrevious)
        FindBarButton(Icons.Default.KeyboardArrowDown, ink, onNext)
        FindBarButton(Icons.Default.Close, ink, onClose)
    }
}

@Composable
private fun FindBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(38.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
