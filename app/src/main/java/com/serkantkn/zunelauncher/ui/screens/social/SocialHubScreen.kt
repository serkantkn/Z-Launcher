package com.serkantkn.zunelauncher.ui.screens.social

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.BuildConfig
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.data.repository.SettingsBridge
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubStartPadding
import com.serkantkn.zunelauncher.ui.components.ZuneWideHubEndPadding
import com.serkantkn.zunelauncher.ui.theme.LocalIsWideScreen

/**
 * The Social hub.
 *
 * Not a second notification shade: a place for what people say to each other. Only the apps named
 * as sources in Settings reach it, and inside those apps only what reads as a message — a backup
 * running, an upload in progress and "you have 12 new updates" are all turned away before they get
 * here.
 *
 * One page, one big Zune word across the top, and nothing to configure: every setting the hub has
 * lives in Settings, under the hubs tab.
 */
@Composable
fun SocialHubScreen(
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
    viewModel: SocialHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current
    val isWideScreen = LocalIsWideScreen.current

    val hasPermission by viewModel.hasPermission.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val byApp by viewModel.byApp.collectAsState()
    val hasSources by viewModel.hasSources.collectAsState()
    val selectedMessage by viewModel.selectedMessage.collectAsState()

    var menuTarget by remember { mutableStateOf<SocialMessageModel?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkPermissionAgain()
    }

    BackHandler(enabled = selectedMessage != null || menuTarget != null) {
        when {
            menuTarget != null -> menuTarget = null
            else -> viewModel.selectMessage(null)
        }
    }

    val listBottomPadding =
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

    Box(modifier = modifier.fillMaxSize()) {
        val overflowYPx = with(density) { (-24).dp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    // A tablet gives the hub the same margins every other wide hub uses.
                    start = if (isWideScreen) ZuneWideHubStartPadding else ZuneDimens.ScreenPaddingHorizontal,
                    end = if (isWideScreen) ZuneWideHubEndPadding else ZuneDimens.ScreenPaddingHorizontal,
                    top = if (isWideScreen) 16.dp else 48.dp
                )
        ) {
            // Header — Zune large typography
            Text(
                text = stringResource(R.string.social_hub),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    fontSize = 96.sp,
                    letterSpacing = (-4).sp,
                    lineHeight = 96.sp
                ),
                color = if (zuneColors.isDark) Color.White else Color.Black,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .graphicsLayer { translationY = overflowYPx }
                    .padding(bottom = ZuneDimens.SpacingLg)
            )

            com.serkantkn.zunelauncher.ui.screens.notes.TodayNotesGroup()

            when {
                !BuildConfig.IS_PREMIUM -> ProOnly()

                !hasPermission -> ZunePermissionRequest(
                    title = stringResource(R.string.notification_permission_title).lowercase(),
                    message = stringResource(R.string.notification_permission_message),
                    buttonLabel = stringResource(R.string.open_settings).lowercase(),
                    onRequest = { viewModel.openNotificationSettings() }
                )

                messages.isEmpty() -> SocialEmpty(hasSources = hasSources)

                layout == SocialHubLayout.GROUPED -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = listBottomPadding)
                ) {
                    byApp.forEach { (appName, appMessages) ->
                        val packageName = appMessages.first().packageName
                        item(key = "header_$packageName") {
                            SocialAppRule(
                                appName = appName,
                                count = appMessages.size,
                                onClear = { viewModel.clearApp(packageName) }
                            )
                        }
                        items(appMessages, key = { it.id }) { message ->
                            SwipeableSocialRow(
                                message = message,
                                showAppName = false,
                                onClick = { viewModel.selectMessage(message) },
                                onLongClick = { menuTarget = message },
                                onDismiss = { viewModel.dismissMessage(message) }
                            )
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = listBottomPadding)
                ) {
                    items(messages, key = { it.id }) { message ->
                        SwipeableSocialRow(
                            message = message,
                            showAppName = true,
                            onClick = { viewModel.selectMessage(message) },
                            onLongClick = { menuTarget = message },
                            onDismiss = { viewModel.dismissMessage(message) }
                        )
                    }
                }
            }
        }

        // ── The bar ──
        AnimatedVisibility(
            visible = isCurrentPage && selectedMessage == null && BuildConfig.IS_PREMIUM,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            WindowsPhoneBottomBar(
                actions = listOf(
                    WpBarAction(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.common_clear),
                        onClick = { viewModel.clearAll() }
                    )
                ),
                menuItems = listOf(
                    WpBarMenuItem(stringResource(R.string.settings_social_sources)) {
                        SettingsBridge.open(SETTINGS_TAB_HUBS)
                    },
                    WpBarMenuItem(stringResource(R.string.social_notification_settings)) {
                        viewModel.openNotificationSettings()
                    }
                )
            )
        }

        // ── Long press on one message ──
        menuTarget?.let { message ->
            com.serkantkn.zunelauncher.ui.screens.messaging.MessagingSheet(
                title = message.appName,
                onDismiss = { menuTarget = null }
            ) {
                com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction(
                    label = stringResource(R.string.social_clear_app, message.appName)
                ) {
                    viewModel.clearApp(message.packageName)
                    menuTarget = null
                }
                com.serkantkn.zunelauncher.ui.screens.messaging.SheetAction(
                    label = stringResource(R.string.social_mute_app, message.appName),
                    color = MaterialTheme.colorScheme.error
                ) {
                    viewModel.muteSource(message.packageName)
                    menuTarget = null
                }
            }
        }

        // ── The message itself ──
        SocialDetailScreen(
            message = selectedMessage,
            onBack = { viewModel.selectMessage(null) },
            onReply = { msg, text -> viewModel.sendReply(msg, text) },
            onOpen = { msg -> viewModel.openMessage(msg) },
            onOpenApp = { msg -> viewModel.openApp(msg) }
        )
    }
}

// ── Small pieces ────────────────────────────────────────────────────────────

@Composable
private fun ProOnly() {
    val zuneColors = LocalZuneColors.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = stringResource(R.string.social_lock),
            tint = zuneColors.textMuted,
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.social_pro_only),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Nothing to show — but for one of two quite different reasons, and saying which is the whole
 * point. A hub with no sources is not quiet, it is switched off.
 */
@Composable
private fun SocialEmpty(hasSources: Boolean) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = ZuneDimens.SpacingLg)) {
        Text(
            text = stringResource(
                if (hasSources) R.string.social_nothing_new else R.string.social_no_sources_yet
            ),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
            color = zuneColors.textMuted
        )
        if (!hasSources) {
            Text(
                text = stringResource(R.string.social_choose_sources),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                color = zuneColors.accentColor,
                modifier = Modifier
                    .padding(top = 10.dp)
                    .clickable { SettingsBridge.open(SETTINGS_TAB_HUBS) }
            )
        }
    }
}

/** The settings tab the hub's own settings live on. */
private const val SETTINGS_TAB_HUBS = "HUBS"
