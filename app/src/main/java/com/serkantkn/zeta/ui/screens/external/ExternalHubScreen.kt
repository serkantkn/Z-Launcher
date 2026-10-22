package com.serkantkn.zeta.ui.screens.external

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.hubs.HubConnection
import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.hubkit.protocol.HubNode
import com.serkantkn.zeta.hubkit.protocol.HubProtocol
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Command
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Event
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Node
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Prop
import com.serkantkn.zeta.ui.animation.LocalZuneMotion
import com.serkantkn.zeta.ui.animation.pageHingeAway
import com.serkantkn.zeta.ui.animation.pageHingeIn
import com.serkantkn.zeta.ui.animation.rememberHingeSpec
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.MetroSubScreen
import com.serkantkn.zeta.ui.components.WpBarAction
import com.serkantkn.zeta.ui.components.WpBarMenuItem
import com.serkantkn.zeta.ui.components.ZuneHub
import com.serkantkn.zeta.ui.components.ZuneHubBar
import com.serkantkn.zeta.ui.components.ZuneHubPane
import com.serkantkn.zeta.ui.components.ZuneWidePaneHeader
import com.serkantkn.zeta.ui.components.bottomBarHeight
import com.serkantkn.zeta.ui.components.hubTitle
import com.serkantkn.zeta.ui.components.rememberLoopingPagerState
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import com.serkantkn.zeta.ui.theme.ZuneDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

/**
 * A hub another app supplies through ZetaHubKit (PLATFORM.md): the app describes it, Zeta draws
 * it. Laid out by [ZuneHub] like every hub of Zeta's own, so it takes the phone's pivot, the
 * tablet's headings and pane, and Aura's big title. A page the app opens over the hub hinges in
 * on a phone and stands in the pane on a tablet.
 */
@Composable
fun ExternalHubScreen(
    hub: ExternalHub,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val wide = LocalIsWideScreen.current
    val zuneColors = LocalZuneColors.current
    val aura = LocalZuneMotion.current
    val connection = remember(hub) { HubConnection(context.applicationContext, hub) }
    val host = Bundle().apply {
        putInt(HubProtocol.Host.PROTOCOL, HubProtocol.VERSION)
        putBundle(HubProtocol.Host.COMPONENTS, Bundle().apply { HubProtocol.Node.V1.forEach { (k, v) -> putInt(k, v) } })
        putString(HubProtocol.Host.FORM_FACTOR, if (wide) "tablet" else "phone")
        putString(HubProtocol.Host.LAYOUT, when {
            aura -> "aura"
            wide -> "panorama"
            else -> "mosaic"
        })
        putString(HubProtocol.Host.LOCALE, Locale.getDefault().toLanguageTag())
        putBoolean(HubProtocol.Host.DARK, zuneColors.isDark)
        putInt(HubProtocol.Host.ACCENT, zuneColors.accentColor.toArgb())
    }
    DisposableEffect(connection) {
        connection.connect(host)
        onDispose { connection.close() }
    }
    LaunchedEffect(connection) {
        delay(CONNECT_TIMEOUT_MILLIS)
        connection.timeOut()
    }

    val spec by connection.spec.collectAsState()
    val status by connection.status.collectAsState()
    val latestBack by rememberUpdatedState(onBack)

    // What the app asks of Zeta.
    LaunchedEffect(connection) {
        connection.commands.collect { command ->
            when (command.getString(Command.KIND)) {
                Command.TOAST -> command.getString(Command.TEXT)?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }
                Command.OPEN_URL -> command.getString(Command.URL)
                    ?.takeIf { it.startsWith("https://") }
                    ?.let { url ->
                        com.serkantkn.zeta.data.repository.BrowserBridge.open(url)
                        com.serkantkn.zeta.data.repository.HubBridge.open(com.serkantkn.zeta.data.model.HubType.INTERNET)
                    }
                Command.SHARE -> command.getString(Command.TEXT)?.let { text ->
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    runCatching {
                        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                Command.CLOSE_HUB -> latestBack()
            }
        }
    }

    val send: HubEventSink = { kind, key, extra -> connection.send(kind, key, extra) }
    val root = spec
    val pages = root?.childrenOf(Node.PAGE).orEmpty()
    val subPages = root?.childrenOf(Node.SUB_PAGE).orEmpty()
    val top = subPages.lastOrNull()
    val title = root?.string(Prop.TITLE)?.lowercase(Locale.getDefault()) ?: hubTitle(hub)

    // ── The phone's sub-page, hinging in over the hub as the hub swings away ──
    val hingeSpec = rememberHingeSpec()
    val hinge = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<HubNode?>(null) }
    LaunchedEffect(top?.key, wide) {
        if (wide) {
            hinge.snapTo(0f)
            shown = null
            return@LaunchedEffect
        }
        if (top != null) {
            if (shown == null) shown = top
            hinge.animateTo(1f, hingeSpec)
        } else {
            hinge.animateTo(0f, hingeSpec)
            shown = null
        }
    }
    // The page shown follows the app's description of it, once it is open.
    if (top != null && shown?.key == top.key) shown = top

    BackHandler {
        if (top != null) send(Event.BACK, top.key) {} else onBack()
    }

    val bar = root?.childOf(Node.BAR)
    val barActions = bar?.childrenOf(Node.ACTION).orEmpty().map { action ->
        WpBarAction(hubIcon(action.string(Prop.ICON)), action.string(Prop.LABEL).orEmpty()) {
            send(Event.CLICK, action.key) {}
        }
    }
    val barMenu = bar?.childrenOf(Node.MENU_ITEM).orEmpty().map { item ->
        WpBarMenuItem(item.string(Prop.LABEL).orEmpty()) { send(Event.CLICK, item.key) {} }
    }
    val takeover = root?.childOf(Node.TAKEOVER)

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().pageHingeAway { hinge.value }) {
            // A new set of headings is a new pager: its looping start depends on how many there are.
            key(pages.size) {
                val pager = rememberLoopingPagerState(pageCount = pages.size.coerceAtLeast(1))
                LaunchedEffect(pager) {
                    snapshotFlow { pager.pagerState.currentPage % pager.pageCount }
                        .distinctUntilChanged()
                        .collect { index -> send(Event.PAGE, "hub") { putInt(Event.INDEX, index) } }
                }
                ZuneHub(
                    title = title,
                    tabs = pages.map { it.string(Prop.TITLE).orEmpty() },
                    pager = pager,
                    zuneTitleKey = hub.key,
                    showTabs = pages.size > 1 || pages.isNotEmpty() && status == HubConnection.Status.READY,
                    bar = if (barActions.isNotEmpty() || barMenu.isNotEmpty()) ZuneHubBar(barActions, barMenu) else null,
                    takeover = when {
                        status == HubConnection.Status.FAILED && root == null -> {
                            {
                                MetroEmpty(
                                    message = stringResource(R.string.external_hub_unavailable),
                                    actionLabel = stringResource(R.string.external_hub_retry),
                                    onAction = { connection.retry() },
                                    modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                                )
                            }
                        }
                        root == null -> {
                            { HubLoading(modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)) }
                        }
                        takeover != null -> {
                            {
                                HubNodeColumn(
                                    nodes = takeover.children,
                                    send = send,
                                    modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                                )
                            }
                        }
                        pages.isEmpty() -> {
                            {
                                MetroEmpty(
                                    message = stringResource(R.string.external_hub_empty),
                                    modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                                )
                            }
                        }
                        else -> null
                    },
                    // A tablet shows the page the app opened beside the list.
                    widePane = if (wide && top != null) ZuneHubPane(key = top.key) { _, _ ->
                        Column(modifier = Modifier.fillMaxSize()) {
                            ZuneWidePaneHeader(
                                section = top.string(Prop.TITLE).orEmpty(),
                                onClose = { send(Event.BACK, top.key) {} }
                            )
                            HubNodeColumn(
                                nodes = top.children,
                                send = send,
                                modifier = Modifier.padding(horizontal = ZuneDimens.ScreenPaddingHorizontal)
                            )
                        }
                    } else null
                ) { index ->
                    val page = pages.getOrNull(index) ?: return@ZuneHub
                    HubNodeColumn(nodes = page.children, send = send)
                }
            }
        }

        val page = shown
        if (!wide && page != null) {
            Box(modifier = Modifier.fillMaxSize().pageHingeIn { hinge.value }) {
                MetroSubScreen(
                    breadcrumb = title,
                    title = page.string(Prop.TITLE).orEmpty(),
                    onClose = { send(Event.BACK, page.key) {} }
                ) { padding ->
                    // The sub-screen keeps the screen's margin itself.
                    HubNodeColumn(
                        nodes = page.children,
                        send = send,
                        contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + bottomBarHeight())
                    )
                }
            }
        }
    }
}

/** How long a hub app has to describe its hub before Zeta says it cannot open it. */
private const val CONNECT_TIMEOUT_MILLIS = 5000L
