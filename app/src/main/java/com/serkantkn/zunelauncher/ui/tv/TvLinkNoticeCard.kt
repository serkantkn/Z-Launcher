package com.serkantkn.zunelauncher.ui.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.link.LinkNotice
import com.serkantkn.zunelauncher.data.link.ShownNotice

/**
 * A call or a message from a paired phone, in the TV's bottom right corner: who, what, and which
 * phone - Media Center's blue card in Windows 7's design, a pane of Vista's glass in Vista's. It
 * slides in from the edge, and a new one replaces the one before.
 */
@Composable
internal fun TvLinkNoticeCard(shown: ShownNotice?) {
    shown ?: return
    val notice = shown.notice
    val vista = LocalVista.current
    val slide = remember(shown.shownAt) { Animatable(1f) }
    LaunchedEffect(shown.shownAt) { slide.animateTo(0f, tween(SLIDE_MILLIS, easing = FastOutSlowInEasing)) }

    val line = when (notice.kind) {
        LinkNotice.Kind.CALL -> stringResource(R.string.tv_link_calling)
        LinkNotice.Kind.MISSED_CALL -> stringResource(R.string.tv_link_missed)
        LinkNotice.Kind.SMS -> stringResource(R.string.tv_link_message)
        LinkNotice.Kind.APP_MESSAGE -> stringResource(R.string.tv_link_app_message, notice.app.orEmpty())
        LinkNotice.Kind.CALL_ENDED -> ""
    }
    val icon = when (notice.kind) {
        LinkNotice.Kind.CALL -> Icons.Default.Call
        LinkNotice.Kind.MISSED_CALL -> Icons.Default.CallMissed
        LinkNotice.Kind.SMS -> Icons.Default.Sms
        else -> Icons.Default.Chat
    }
    val shape = RoundedCornerShape(if (vista) 3.dp else 8.dp)
    Box(
        modifier = Modifier
            .padding(end = 44.dp, bottom = 40.dp)
            .width(440.dp)
            .graphicsLayer {
                translationX = slide.value * (size.width + 60.dp.toPx())
                alpha = 1f - slide.value * 0.6f
            }
            .clip(shape)
            .background(
                if (vista) Brush.verticalGradient(listOf(Vista.PanelTop.copy(alpha = 0.97f), Vista.PanelBottom.copy(alpha = 0.97f)))
                else Brush.verticalGradient(listOf(MediaCenter.Top.copy(alpha = 0.97f), MediaCenter.Strip.copy(alpha = 0.97f)))
            )
            .then(if (vista) Modifier.vistaGlass(strength = 0.5f) else Modifier.border(1.5.dp, MediaCenter.Glow.copy(alpha = 0.7f), shape))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (notice.kind == LinkNotice.Kind.MISSED_CALL) Color(0xFFB8322E)
                        else if (vista) Color.White.copy(alpha = 0.18f) else MediaCenter.Glow.copy(alpha = 0.35f),
                        CircleShape
                    )
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    color = if (vista) Color(0xFFDCEBFA) else MediaCenter.Dim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notice.name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = if (vista) FontWeight.SemiBold else FontWeight.Light,
                        fontSize = 26.sp
                    ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                notice.text?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 21.sp),
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = shown.phone,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = if (vista) Color(0xFFC4DAF2) else MediaCenter.Dim.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private const val SLIDE_MILLIS = 320
