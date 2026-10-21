package com.serkantkn.zeta.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.R

/**
 * The little cross at the end of a search box that empties it, as Windows Phone's had. Only there
 * while something is typed: an empty box has nothing to take away.
 */
@Composable
fun SearchClearButton(
    query: String,
    onClear: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier
) {
    if (query.isEmpty()) return
    Box(
        modifier = modifier
            .size(36.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClear
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.common_clear),
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}
