package com.serkantkn.zunelauncher.ui.screens.social

import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.SocialMessageModel
import com.serkantkn.zunelauncher.ui.components.wpTilt
import com.serkantkn.zunelauncher.ui.theme.LocalZuneColors
import com.serkantkn.zunelauncher.ui.theme.ZuneDimens
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The pieces the Social hub is drawn from.
 *
 * The shape is the Windows Phone "Me" tile writ large: a person's face, what they said, and how
 * long ago. What is new is the small square in the corner of the face — the app the message came
 * from. A hub that gathers six apps into one list has to say which one each line belongs to, and
 * naming it in words would cost a line of text per message.
 */

// ── One message ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SocialMessageRow(
    message: SocialMessageModel,
    showAppName: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val zuneColors = LocalZuneColors.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wpTilt(interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        SocialAvatar(message = message)

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = message.heading,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Normal,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = remember(message.timestamp) {
                        DateUtils.getRelativeTimeSpanString(message.timestamp)
                            .toString()
                            .lowercase(Locale.getDefault())
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.textDim,
                    maxLines = 1
                )
            }

            // The conversation's title replaced the sender above, so the sender goes here instead.
            val second = when {
                message.conversationTitle.isNullOrBlank() -> null
                else -> message.title.takeIf { it.isNotBlank() }
            }
            if (second != null) {
                Text(
                    text = second,
                    style = MaterialTheme.typography.labelMedium,
                    color = zuneColors.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (showAppName) {
                Text(
                    text = message.appName.lowercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = zuneColors.textDim,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/**
 * The person's face with the app's own square in its corner.
 *
 * Both are squares rather than circles: this is Metro, and a round photo beside a row of square
 * tiles looks like it wandered in from somewhere else.
 */
@Composable
private fun SocialAvatar(message: SocialMessageModel) {
    val zuneColors = LocalZuneColors.current
    Box(modifier = Modifier.size(AVATAR)) {
        val face = message.icon
        if (face != null) {
            Image(
                bitmap = face.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(AVATAR)
            )
        } else {
            Box(
                modifier = Modifier.size(AVATAR).background(zuneColors.accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.heading.take(1).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                    color = Color.White
                )
            }
        }

        message.appIcon?.let { badge ->
            Image(
                bitmap = badge.asImageBitmap(),
                contentDescription = message.appName,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(BADGE)
                    .background(MaterialTheme.colorScheme.background)
                    .padding(1.dp)
            )
        }
    }
}

// ── Swiping one away ────────────────────────────────────────────────────────

@Composable
internal fun SwipeableSocialRow(
    message: SocialMessageModel,
    showAppName: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val offsetX = remember(message.id) { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val gapPx = with(density) { 12.dp.toPx() }
    val revealWidthPx = with(density) { 92.dp.toPx() }
    val dismissThresholdPx = with(density) { 170.dp.toPx() }

    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        if (offsetX.value > 0f) {
            val bgWidthDp = with(density) { (offsetX.value - gapPx).coerceAtLeast(0f).toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(bgWidthDp)
                    .matchParentSize()
                    .padding(vertical = 6.dp)
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
                        contentDescription = stringResource(R.string.common_delete),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    if (offsetX.value > revealWidthPx - 15f) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.common_delete),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(message.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            coroutineScope.launch {
                                when {
                                    offsetX.value > dismissThresholdPx -> {
                                        offsetX.animateTo(1000f, tween(200))
                                        onDismiss()
                                    }

                                    offsetX.value > revealWidthPx / 2f ->
                                        offsetX.animateTo(revealWidthPx, tween(180))

                                    else -> offsetX.animateTo(0f, tween(180))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch { offsetX.animateTo(0f, tween(180)) }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            coroutineScope.launch {
                                offsetX.snapTo((offsetX.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    )
                }
        ) {
            SocialMessageRow(
                message = message,
                showAppName = showAppName,
                onClick = {
                    if (offsetX.value > 10f) {
                        coroutineScope.launch { offsetX.animateTo(0f, tween(180)) }
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongClick
            )
        }
    }
}

// ── Section rules ───────────────────────────────────────────────────────────

/** An app's name with its count, and a hairline running off the edge: the Zune section rule. */
@Composable
internal fun SocialAppRule(appName: String, count: Int, onClear: () -> Unit) {
    val zuneColors = LocalZuneColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ZuneDimens.SpacingLg, bottom = ZuneDimens.SpacingSm)
    ) {
        Text(
            text = appName.lowercase(Locale.getDefault()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
            color = zuneColors.accentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .background(zuneColors.accentColor, RoundedCornerShape(2.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(zuneColors.accentColor.copy(alpha = 0.35f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.common_clear),
            style = MaterialTheme.typography.labelMedium,
            color = zuneColors.textMuted,
            modifier = Modifier.clickable(onClick = onClear).padding(vertical = 4.dp)
        )
    }
}

private val AVATAR = 48.dp
private val BADGE = 20.dp
