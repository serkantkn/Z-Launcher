package com.serkantkn.zeta.ui.components

import com.serkantkn.zeta.R
import androidx.compose.ui.res.stringResource
import com.serkantkn.zeta.util.ZuneLog
import com.serkantkn.zeta.util.sendAllowingBackgroundStart
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.data.model.SocialMessageModel
import com.serkantkn.zeta.data.repository.SocialRepository
import com.serkantkn.zeta.ui.theme.LocalIsWideScreen
import com.serkantkn.zeta.ui.theme.LocalZuneColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Windows Phone & Windows 8 Desktop style Toast Notification.
 * Phone: Flush attached top banner with 3D horizontal center flip animation.
 * Tablet: Windows 8 top-right desktop notification toast card.
 * Drag-down expands full text and inline quick reply input.
 */
@Composable
fun WpToastNotification(
    message: SocialMessageModel?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fires when the quick-reply section opens/closes; the overlay host toggles window focus with it. */
    onExpandedChange: (Boolean) -> Unit = {},
    /** False when hosted in a WRAP_CONTENT overlay window so the window is only as tall as the banner. */
    fillHeight: Boolean = true,
    /** In a window of its own, which is placed and sized to the card: the card fills it. */
    ownWindow: Boolean = false,
    /** How much of the card shows, 0-1, as it flips in and out and is swiped away: its window's blur follows it. */
    onShown: (Float) -> Unit = {}
) {
    if (message == null) return

    val context = LocalContext.current
    val zuneColors = LocalZuneColors.current
    val isWideScreen = LocalIsWideScreen.current
    val density = LocalDensity.current.density
    val coroutineScope = rememberCoroutineScope()

    val flipAnim = remember(message.id) { Animatable(0f) }
    var isClosing by remember(message.id) { mutableStateOf(false) }

    var isExpanded by remember(message.id) { mutableStateOf(false) }
    var replyText by remember(message.id) { mutableStateOf("") }
    var isReplying by remember(message.id) { mutableStateOf(false) }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // 3D Flip entrance
    LaunchedEffect(message.id) {
        flipAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
        )
    }

    // Dismiss with 3D flip close animation
    val dismissWithFlip: () -> Unit = remember(message.id) {
        {
            if (!isClosing) {
                isClosing = true
                coroutineScope.launch {
                    flipAnim.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    )
                    onDismiss()
                }
            }
        }
    }

    LaunchedEffect(message.id, isExpanded) {
        onExpandedChange(isExpanded)
    }

    // Auto dismiss timer (6s) if user hasn't interacted or expanded
    LaunchedEffect(message.id, isExpanded, isReplying, isClosing) {
        if (!isExpanded && !isReplying && !isClosing) {
            delay(6000L)
            dismissWithFlip()
        }
    }

    // Frosted as the tiles are, when they are (LocalBannerAlpha); solid otherwise.
    val cardAlpha = LocalBannerAlpha.current
    val bannerBgColor = zuneColors.accentColor.copy(alpha = cardAlpha)
    val swipe = rememberSwipeAwayState(message.id)
    LaunchedEffect(message.id) {
        snapshotFlow { flipAnim.value * swipe.blurShare }.collect { onShown(it) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
        contentAlignment = if (isWideScreen) Alignment.TopEnd else Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .then(
                    if (isWideScreen && !ownWindow) {
                        Modifier
                            .width(380.dp)
                            .padding(top = statusBarTop + 12.dp, end = 16.dp)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 0.dp)
                    }
                )
                // Up and away to put it aside; down to open the quick reply.
                .swipeAway(
                    state = swipe,
                    enabled = !isClosing,
                    onPulledDown = { if (!isExpanded) isExpanded = true },
                    onSwipedAway = {
                        isClosing = true
                        onDismiss()
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isExpanded && message.replyAction != null) {
                            isExpanded = true
                        } else {
                            // The launcher is in front, so it may lend the app its right to open
                            // a screen; a plain send is blocked and nothing would appear.
                            message.openIntent?.sendAllowingBackgroundStart(context)
                            dismissWithFlip()
                        }
                    }
                )
                .graphicsLayer {
                    // 3D Flip animation around X-axis (horizontal center)
                    val rotation = (1f - flipAnim.value) * -90f
                    rotationX = rotation
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    cameraDistance = 12f * density
                    alpha = flipAnim.value.coerceIn(0f, 1f)
                }
                // In the launcher, the wallpaper behind it frosted as behind the tiles; over another
                // app its window blurs what is there instead.
                .then(if (cardAlpha < 1f) Modifier.frostedBackdrop() else Modifier),
            shape = RoundedCornerShape(if (isWideScreen) 2.dp else 0.dp),
            color = bannerBgColor,
            // A shadow under glass shows through it.
            shadowElevation = if (cardAlpha < 1f) 0.dp else 12.dp,
            tonalElevation = if (cardAlpha < 1f) 0.dp else 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (isWideScreen) 14.dp else statusBarTop + 10.dp,
                        bottom = 14.dp
                    )
            ) {
                // Header Row: App Name & Close Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        message.icon?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = message.appName,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = message.appName.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            ),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { dismissWithFlip() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close_cap),
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Title / Sender Name
                if (message.title.isNotBlank()) {
                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = Color.White,
                        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Notification Content Text
                if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Drag indicator / Expand Hint
                if (!isExpanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                    )
                }

                // Expanded Section: Inline Reply Field
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(120))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        if (message.replyAction != null) {
                            Text(
                                text = stringResource(R.string.toast_quick_reply),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    BasicTextField(
                                        value = replyText,
                                        onValueChange = {
                                            replyText = it
                                            isReplying = true
                                        },
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                                            color = Color.White,
                                            fontSize = 14.sp
                                        ),
                                        cursorBrush = SolidColor(Color.White),
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                        keyboardActions = KeyboardActions(
                                            onSend = {
                                                if (replyText.isNotBlank()) {
                                                    val success = SocialRepository.sendReply(context, message.replyAction, replyText)
                                                    if (success) {
                                                        android.widget.Toast.makeText(context, context.getString(R.string.toast_reply_sent), android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                    dismissWithFlip()
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    if (replyText.isEmpty()) {
                                        Text(
                                            text = stringResource(R.string.toast_reply_hint),
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                            color = Color.White.copy(alpha = 0.55f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Send Button
                                Surface(
                                    shape = RoundedCornerShape(2.dp),
                                    color = Color.White,
                                    modifier = Modifier
                                        .height(38.dp)
                                        .clickable(enabled = replyText.isNotBlank()) {
                                            val success = SocialRepository.sendReply(context, message.replyAction, replyText)
                                            if (success) {
                                                android.widget.Toast.makeText(context, context.getString(R.string.toast_reply_sent), android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                            dismissWithFlip()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = stringResource(R.string.msg_send_cap),
                                            tint = bannerBgColor,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.send),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            ),
                                            color = bannerBgColor
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.toast_tap_to_open),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
