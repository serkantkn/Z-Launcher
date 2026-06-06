package com.serkantkn.zunelauncher.ui.screens.social

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
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

    var rememberedMessage by remember { mutableStateOf(message) }
    if (message != null) {
        rememberedMessage = message
    }

    AnimatedVisibility(
        visible = message != null,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(400)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(400)
        )
    ) {
        val displayMessage = rememberedMessage ?: return@AnimatedVisibility

        var replyText by remember { mutableStateOf("") }
        val bgColor = if (zuneColors.isDark) Color.Black else ZuneColors.LightBackground
        val dividerColor = if (zuneColors.isDark) Color(0xFF2A2A2A) else Color(0xFFE0E0E0)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = 60.dp,
                        start = ZuneDimens.ScreenPaddingHorizontal,
                        end = ZuneDimens.ScreenPaddingHorizontal,
                        bottom = 80.dp
                    )
            ) {
                // Back row
                Row(
                    modifier = Modifier
                        .clickable { onBack() }
                        .padding(bottom = ZuneDimens.SpacingXl),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Geri",
                        tint = zuneColors.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "geri",
                        style = MaterialTheme.typography.bodyLarge,
                        color = zuneColors.textMuted
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header (App Icon + Name)
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
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape),
                                tint = Color.Unspecified
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF555555)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayMessage.appName.take(1).uppercase(),
                                    color = Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = displayMessage.title.uppercase(),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    HorizontalDivider(color = dividerColor, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(ZuneDimens.SpacingLg))

                    // Message Content
                    Text(
                        text = displayMessage.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = ZuneDimens.SpacingXxl)
                    )
                }

                // Reply Action
                if (displayMessage.replyAction != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (zuneColors.isDark) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                                )
                                .padding(12.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            cursorBrush = SolidColor(zuneColors.accentColor),
                            decorationBox = { innerTextField ->
                                if (replyText.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.type_message),
                                        color = zuneColors.textMuted
                                    )
                                }
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
                        text = "uygulamada aç",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = zuneColors.accentColor,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .clickable { onOpen(displayMessage) }
                    )
                }
            }
        }
    }
}
