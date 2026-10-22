package com.serkantkn.zeta.ui.screens.external

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.ZuneHub
import com.serkantkn.zeta.ui.components.hubTitle
import com.serkantkn.zeta.ui.components.rememberLoopingPagerState
import com.serkantkn.zeta.ui.theme.ZuneDimens

/**
 * A hub another app supplies through ZetaHubKit, drawn by Zeta from what the app describes
 * (PLATFORM.md). Laid out by [ZuneHub] like every hub of Zeta's own, so it takes the phone's
 * pivot, the tablet's headings and pane and Aura's big title without asking.
 *
 * Until the connection to the app is made (PLATFORM.md, stage 1) it says it cannot open yet.
 */
@Composable
fun ExternalHubScreen(
    hub: ExternalHub,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onBack() }
    val pager = rememberLoopingPagerState(pageCount = 1)
    ZuneHub(
        title = hubTitle(hub),
        tabs = emptyList(),
        pager = pager,
        modifier = modifier,
        showTabs = false,
        zuneTitleKey = hub.key,
        takeover = {
            MetroEmpty(
                message = stringResource(R.string.external_hub_unavailable),
                modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
            )
        }
    ) { }
}
