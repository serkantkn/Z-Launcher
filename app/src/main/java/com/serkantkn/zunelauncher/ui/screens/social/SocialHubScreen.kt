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
import com.serkantkn.zunelauncher.ui.components.WindowsPhoneBottomBar
import com.serkantkn.zunelauncher.ui.components.WpBarAction
import com.serkantkn.zunelauncher.ui.components.WpBarMenuItem
import com.serkantkn.zunelauncher.ui.components.ZuneEmptyState
import com.serkantkn.zunelauncher.ui.components.ZunePermissionRequest
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

@Composable
fun SocialHubScreen(
    modifier: Modifier = Modifier,
    isCurrentPage: Boolean = true,
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

            com.serkantkn.zunelauncher.ui.screens.notes.TodayNotesGroup()

            if (!com.serkantkn.zunelauncher.BuildConfig.IS_PREMIUM) {
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
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else if (!hasPermission) {
                ZunePermissionRequest(
                    title = stringResource(R.string.notification_permission_title).lowercase(),
                    message = stringResource(R.string.notification_permission_message),
                    buttonLabel = stringResource(R.string.open_settings).lowercase(),
                    onRequest = { viewModel.openNotificationSettings() }
                )
            } else if (messages.isEmpty()) {
                ZuneEmptyState(stringResource(R.string.no_notifications))
            } else {
                when (layout) {
                    SocialHubLayout.TIMELINE -> TimelineLayout(
                        messages = messages,
                        onMessageClick = { viewModel.selectMessage(it) },
                        onMessageDismiss = { viewModel.dismissMessage(it) }
                    )
                    SocialHubLayout.GROUPED -> GroupedLayout(
                        messages = messages,
                        onMessageClick = { viewModel.selectMessage(it) },
                        onMessageDismiss = { viewModel.dismissMessage(it) }
                    )
                }
            }
        }

        // Standard Windows Phone Metro Bottom Application Bar
        AnimatedVisibility(
            visible = isCurrentPage && selectedMessage == null,
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
                    WpBarMenuItem(stringResource(R.string.common_clear_all)) { viewModel.clearAll() },
                    WpBarMenuItem(stringResource(R.string.social_notification_settings)) { viewModel.openNotificationSettings() }
                )
            )
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
private fun TimelineLayout(
    messages: List<SocialMessageModel>,
    onMessageClick: (SocialMessageModel) -> Unit,
    onMessageDismiss: (SocialMessageModel) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            SwipeableMessageListItem(
                message = message,
                onClick = { onMessageClick(message) },
                onDismiss = { onMessageDismiss(message) }
            )
        }
    }
}

@Composable
private fun GroupedLayout(
    messages: List<SocialMessageModel>,
    onMessageClick: (SocialMessageModel) -> Unit,
    onMessageDismiss: (SocialMessageModel) -> Unit
) {
    val grouped = messages.groupBy { it.appName }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp)
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
                SwipeableMessageListItem(
                    message = message,
                    onClick = { onMessageClick(message) },
                    onDismiss = { onMessageDismiss(message) }
                )
            }
        }
    }
}

@Composable
private fun SwipeableMessageListItem(
    message: SocialMessageModel,
    onClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val offsetX = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val gapPx = with(density) { 12.dp.toPx() }
    val revealWidthPx = with(density) { 92.dp.toPx() }
    val dismissThresholdPx = with(density) { 170.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Background Delete Button (Revealed on Left side as item drags Right)
        if (offsetX.value > 0f) {
            val bgWidthDp = with(density) { (offsetX.value - gapPx).coerceAtLeast(0f).toDp() }
            
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(bgWidthDp)
                    .matchParentSize()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE51C23))
                    .clickable {
                        coroutineScope.launch {
                            offsetX.animateTo(1000f, tween(200))
                            onDismiss()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.files_delete_cap),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    if (offsetX.value > revealWidthPx - 15f) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.common_delete),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // Foreground Message Item Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                if (offsetX.value > dismissThresholdPx) {
                                    // Over-swiped past threshold -> dismiss directly without tapping button!
                                    offsetX.animateTo(1000f, tween(200))
                                    onDismiss()
                                } else if (offsetX.value > revealWidthPx / 2f) {
                                    // Partial swipe -> snap open to reveal delete button
                                    offsetX.animateTo(revealWidthPx, tween(180))
                                } else {
                                    // Minor drag -> collapse back
                                    offsetX.animateTo(0f, tween(180))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                offsetX.animateTo(0f, tween(180))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                val newOffset = (offsetX.value + dragAmount).coerceAtLeast(0f)
                                offsetX.snapTo(newOffset)
                            }
                        }
                    )
                }
        ) {
            MessageListItem(
                message = message,
                onClick = {
                    if (offsetX.value > 10f) {
                        coroutineScope.launch { offsetX.animateTo(0f, tween(180)) }
                    } else {
                        onClick()
                    }
                }
            )
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
