package com.serkantkn.zeta.ui.screens.clock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen

/**
 * The shape every page of the clock has: one big thing to read — the time, the next alarm, the
 * running count — and a list that belongs to it.
 *
 * On a phone the list runs on under the big thing, in one column. On a wide screen that column
 * would leave the right half of the tablet empty and the list a long reach down, so the two
 * stand side by side instead: the reading on the left, where the eye lands, and its list on the
 * right with the whole height to itself.
 */
@Composable
internal fun ClockSplit(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    head: @Composable () -> Unit,
    list: LazyListScope.() -> Unit
) {
    if (LocalIsWideScreen.current) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(HEAD_SHARE)
                    .fillMaxHeight()
                    .padding(top = contentPadding.calculateTopPadding())
            ) { head() }
            Spacer(modifier = Modifier.width(48.dp))
            LazyColumn(
                modifier = Modifier.weight(1f - HEAD_SHARE).fillMaxHeight(),
                contentPadding = contentPadding,
                content = list
            )
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
            item { head() }
            list()
        }
    }
}

private const val HEAD_SHARE = 0.42f
