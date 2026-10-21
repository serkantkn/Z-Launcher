package com.serkantkn.zeta.ui.tv

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
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.link.LinkNotice
import com.serkantkn.zeta.data.link.ShownNotice

/**
 * A call or a message from a paired phone: who, what, and which phone - a pane of Vista's glass
 * in the bottom right corner, or the Xbox's dark notice rising at the bottom in the middle. A new
 * one replaces the one before.
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
        LinkNotice.Kind.SYSTEM -> notice.app.orEmpty()
    }
    val icon = shown.icon ?: when (notice.kind) {
        LinkNotice.Kind.CALL -> Icons.Default.Call
        LinkNotice.Kind.MISSED_CALL -> Icons.Default.CallMissed
        LinkNotice.Kind.SMS -> Icons.Default.Sms
        else -> Icons.Default.Chat
    }
    if (!vista) {
        XboxNotice(shown, line, icon, slide.value)
        return
    }
    val shape = RoundedCornerShape(3.dp)
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
                Brush.verticalGradient(listOf(Vista.PanelTop.copy(alpha = 0.97f), Vista.PanelBottom.copy(alpha = 0.97f)))
            )
            .vistaGlass(strength = 0.5f)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (notice.kind == LinkNotice.Kind.MISSED_CALL) Color(0xFFB8322E) else Color.White.copy(alpha = 0.18f),
                        CircleShape
                    )
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    color = Color(0xFFDCEBFA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = notice.name,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.SemiBold,
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
                if (shown.phone.isNotBlank()) Text(
                    text = shown.phone,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = Color(0xFFC4DAF2),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

private const val SLIDE_MILLIS = 320

/**
 * The Xbox's notice, as its achievements and messages came up: a dark band at the bottom in the
 * middle of the screen, a green disc with the sign at its left, two lines beside it.
 */
@Composable
private fun XboxNotice(shown: ShownNotice, line: String, icon: androidx.compose.ui.graphics.vector.ImageVector, slide: Float) {
    val notice = shown.notice
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .padding(bottom = 14.dp)
            .width(500.dp)
            .graphicsLayer {
                translationY = slide * (size.height + 48.dp.toPx())
                alpha = 1f - slide * 0.7f
            }
            .clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF2404546), Color(0xF2222526))))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(if (notice.kind == LinkNotice.Kind.MISSED_CALL) Color(0xFFB8322E) else Xbox.Green, CircleShape)
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = notice.name,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notice.text ?: line,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = SelawikFamily, fontSize = 16.sp),
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val foot = listOf(if (notice.text != null) line else "", shown.phone).filter { it.isNotBlank() }.joinToString(" · ")
            if (foot.isNotBlank()) Text(
                text = foot,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = SelawikFamily, fontSize = 13.sp),
                color = Xbox.GreenLit,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

