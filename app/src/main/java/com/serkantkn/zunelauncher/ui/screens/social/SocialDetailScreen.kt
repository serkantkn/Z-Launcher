package com.serkantkn.zunelauncher.ui.screens.social

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens

@Composable
fun SocialDetailScreen(
    message: SocialMessageModel?,
    onBack: () -> Unit,
    onReply: (SocialMessageModel, String) -> Unit,
    onOpen: (SocialMessageModel) -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val density = LocalDensity.current

    var rememberedMessage by remember { mutableStateOf(message) }
    if (message != null) rememberedMessage = message
    val displayMessage = rememberedMessage

    // Same 3D flip animation as W10MAppTile
    val rotation by animateFloatAsState(
        targetValue = if (message != null) 0f else 180f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "detail_card_flip"
    )

    BackHandler(enabled = message != null) {
        onBack()
    }

    if (displayMessage != null && rotation <= 90f) {
        var replyText by remember { mutableStateOf("") }
        val bgColor = if (zuneColors.isDark) Color.Black else ZuneColors.LightBackground
        val dividerColor = if (zuneColors.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)
        val scrollState = rememberScrollState()

        var accumulatedPullDown by remember { mutableFloatStateOf(0f) }
        val nestedScrollConnection = remember(onBack) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y < 0) {
                        accumulatedPullDown = 0f
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    if (source == NestedScrollSource.UserInput && available.y > 0) {
                        accumulatedPullDown += available.y
                        if (accumulatedPullDown > 30f) {
                            accumulatedPullDown = 0f
                            onBack()
                        }
                        return available
                    }
                    return Offset.Zero
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .pointerInput(onBack) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount > 25f) {
                            onBack()
                        }
                    }
                }
                .graphicsLayer {
                    rotationX = rotation
                    cameraDistance = 8f * density.density
                }
                .background(bgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = 48.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal,
                        bottom = 24.dp
                    )
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ZuneDimens.SpacingLg),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back_cap),
                            tint = zuneColors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.common_back), style = MaterialTheme.typography.bodyLarge, color = zuneColors.textMuted)
                    }
                    IconButton(onClick = { onBack() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.common_close_cap),
                            tint = zuneColors.textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Scrollable content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = ZuneDimens.SpacingLg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (displayMessage.icon != null) {
                            Icon(
                                bitmap = displayMessage.icon.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                                tint = Color.Unspecified
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF555555)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = displayMessage.appName.take(1).uppercase(), color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = displayMessage.title.uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))

                    Text(
                        text = displayMessage.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = ZuneDimens.SpacingXxl)
                    )
                }

                // Reply / Open action
                if (displayMessage.replyAction != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5))
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onBackground),
                            cursorBrush = SolidColor(zuneColors.accentColor),
                            decorationBox = { innerTextField ->
                                if (replyText.isEmpty()) Text(text = stringResource(R.string.type_message), color = zuneColors.textMuted)
                                innerTextField()
                            }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (replyText.isNotBlank()) zuneColors.accentColor else zuneColors.overlay.copy(alpha = 0.1f),
                                    CircleShape
                                )
                                .clickable(enabled = replyText.isNotBlank()) {
                                    onReply(displayMessage, replyText)
                                    replyText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.send),
                                tint = if (replyText.isNotBlank()) Color.White else zuneColors.textMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else if (displayMessage.openIntent != null) {
                    Text(
                        text = stringResource(R.string.social_open_in_app),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = zuneColors.accentColor,
                        modifier = Modifier.padding(vertical = 16.dp).clickable { onOpen(displayMessage) }
                    )
                }
            }
        }
    }
}
