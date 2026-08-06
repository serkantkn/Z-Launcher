package com.serkantkn.zunelauncher.ui.screens.social

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.SocialHubLayout
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

@Composable
fun SocialHubScreen(
    modifier: Modifier = Modifier,
    viewModel: SocialHubViewModel = viewModel()
) {
    val zuneColors = LocalZuneColors.current
    val hasPermission by viewModel.hasPermission.collectAsState()
    val layout by viewModel.layout.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val selectedMessage by viewModel.selectedMessage.collectAsState()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.checkPermissionAgain()
    }

    BackHandler(enabled = selectedMessage != null) {
        viewModel.selectMessage(null)
    }

    Box(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val overflowYPx = with(density) { (-24).dp.toPx() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = ZuneDimens.ScreenPaddingHorizontal,
                    end = ZuneDimens.ScreenPaddingHorizontal,
                    top = 48.dp
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
                    .graphicsLayer {
                        translationY = overflowYPx
                    }
                    .padding(bottom = ZuneDimens.SpacingLg)
            )

            if (!com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Lock",
                        tint = zuneColors.textMuted,
                        modifier = Modifier.size(64.dp).padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Social Hub sadece Z Launcher Pro'da geçerlidir.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (!hasPermission) {
                PermissionRequestView(onOpenSettings = { viewModel.openNotificationSettings() })
            } else if (messages.isEmpty()) {
                EmptyStateView()
            } else {
                when (layout) {
                    SocialHubLayout.TIMELINE -> TimelineLayout(messages, onMessageClick = { viewModel.selectMessage(it) })
                    SocialHubLayout.GROUPED -> GroupedLayout(messages, onMessageClick = { viewModel.selectMessage(it) })
                }
            }
        }

        // Detail Overlay
        SocialDetailScreen(
            message = selectedMessage,
            onBack = { viewModel.selectMessage(null) },
            onReply = { msg, text -> viewModel.sendReply(msg, text) },
            onOpen = { msg -> viewModel.openMessage(msg) }
        )
    }
}

@Composable
private fun TimelineLayout(messages: List<SocialMessageModel>, onMessageClick: (SocialMessageModel) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageListItem(message = message, onClick = { onMessageClick(message) })
        }
    }
}

@Composable
private fun GroupedLayout(messages: List<SocialMessageModel>, onMessageClick: (SocialMessageModel) -> Unit) {
    val grouped = messages.groupBy { it.appName }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp)
    ) {
        grouped.forEach { (appName, appMessages) ->
            item(key = "header_$appName") {
                Text(
                    text = appName.lowercase(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = LocalZuneColors.current.accentColor,
                    modifier = Modifier.padding(top = ZuneDimens.SpacingLg, bottom = 8.dp)
                )
            }
            items(appMessages, key = { it.id }) { message ->
                MessageListItem(message = message, onClick = { onMessageClick(message) })
            }
        }
    }
}

@Composable
private fun MessageListItem(message: SocialMessageModel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (message.icon != null) {
            Icon(
                bitmap = message.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                tint = Color.Unspecified
            )
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF555555)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.appName.take(1).uppercase(),
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = DateUtils.getRelativeTimeSpanString(message.timestamp).toString().lowercase(),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalZuneColors.current.textDim
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalZuneColors.current.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PermissionRequestView(onOpenSettings: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Column(modifier = Modifier.padding(top = ZuneDimens.SpacingHuge)) {
        Text(
            text = stringResource(R.string.notification_permission_title).lowercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.notification_permission_message),
            style = MaterialTheme.typography.bodyMedium,
            color = zuneColors.textMuted
        )
        Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                containerColor = zuneColors.accentColor,
                contentColor = Color.White
            )
        ) {
            Text(text = stringResource(R.string.open_settings).lowercase())
        }
    }
}

@Composable
private fun EmptyStateView() {
    Text(
        text = stringResource(R.string.no_notifications).lowercase(),
        style = MaterialTheme.typography.bodyMedium,
        color = LocalZuneColors.current.textDim,
        modifier = Modifier.padding(top = ZuneDimens.SpacingLg)
    )
}
