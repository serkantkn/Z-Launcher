package com.serkantkn.zeta.ui.screens.external

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.serkantkn.zeta.hubkit.protocol.HubNode
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Event
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Icon
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Node
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Prop
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Style
import com.serkantkn.zeta.ui.components.MetroEmpty
import com.serkantkn.zeta.ui.components.MetroNotice
import com.serkantkn.zeta.ui.components.MetroRule
import com.serkantkn.zeta.ui.components.MetroSwitchRow
import com.serkantkn.zeta.ui.components.MetroValueRow
import com.serkantkn.zeta.ui.components.bottomBarHeight
import com.serkantkn.zeta.ui.components.wpTilt
import com.serkantkn.zeta.ui.theme.LocalZuneColors

/** Sends an event about one node back to its hub app. */
internal typealias HubEventSink = (kind: String, key: String, extra: android.os.Bundle.() -> Unit) -> Unit

/**
 * A page of an external hub: what the app described, top to bottom, in Zeta's own parts
 * (PLATFORM.md, "Öğeler"). Kinds this Zeta does not know are left out.
 */
@Composable
internal fun HubNodeColumn(
    nodes: List<HubNode>,
    send: HubEventSink,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = bottomBarHeight())
) {
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding) {
        items(nodes, key = { it.key }, contentType = { it.type }) { node ->
            HubNodeView(node, send)
        }
    }
}

@Composable
private fun HubNodeView(node: HubNode, send: HubEventSink) {
    val key = node.key
    when (node.type) {
        Node.SECTION -> {
            val action = node.string(Prop.ACTION_LABEL)
            MetroRule(
                title = node.string(Prop.TITLE).orEmpty(),
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                actionLabel = action,
                onAction = action?.let { { send(Event.ACTION, key) {} } }
            )
        }
        Node.ITEM -> HubItemRow(node, send)
        Node.TEXT -> HubText(node)
        Node.BUTTON -> HubButton(node.string(Prop.LABEL).orEmpty()) { send(Event.CLICK, key) {} }
        Node.EMPTY -> {
            val action = node.string(Prop.ACTION_LABEL)
            MetroEmpty(
                message = node.string(Prop.MESSAGE).orEmpty(),
                actionLabel = action,
                onAction = action?.let { { send(Event.ACTION, key) {} } }
            )
        }
        Node.NOTICE -> {
            val action = node.string(Prop.ACTION_LABEL)
            if (action != null) {
                MetroNotice(node.string(Prop.MESSAGE).orEmpty(), action, { send(Event.ACTION, key) {} })
            } else {
                HubText(node.string(Prop.MESSAGE).orEmpty(), Style.BODY, Modifier.padding(vertical = 8.dp))
            }
        }
        Node.LOADING -> HubLoading()
        Node.SWITCH -> MetroSwitchRow(
            title = node.string(Prop.TITLE).orEmpty(),
            checked = node.boolean(Prop.CHECKED),
            onCheckedChange = { checked -> send(Event.TOGGLE, key) { putBoolean(Event.CHECKED, checked) } },
            subtitle = node.string(Prop.SUBTITLE)
        )
        Node.VALUE -> MetroValueRow(
            title = node.string(Prop.TITLE).orEmpty(),
            value = node.string(Prop.VALUE).orEmpty(),
            onClick = { send(Event.CLICK, key) {} }
        )
    }
}

/** A line of a list: a picture, the words, a number - pressed in like every Zeta line. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HubItemRow(node: HubNode, send: HubEventSink) {
    val zuneColors = LocalZuneColors.current
    val interaction = remember { MutableInteractionSource() }
    val clickable = node.boolean(Prop.CLICKABLE)
    val longClickable = node.boolean(Prop.LONG_CLICKABLE)
    val modifier = if (clickable || longClickable) {
        Modifier
            .wpTilt(interaction)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onLongClick = if (longClickable) ({ send(Event.LONG_CLICK, node.key) {} }) else null,
                onClick = { if (clickable) send(Event.CLICK, node.key) {} }
            )
    } else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        node.string(Prop.IMAGE)?.let { image ->
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(if (node.boolean(Prop.ROUND)) CircleShape else RectangleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.string(Prop.TITLE).orEmpty(),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            node.string(Prop.SUBTITLE)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = zuneColors.textMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            node.string(Prop.META)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = zuneColors.textDim,
                    maxLines = 1
                )
            }
        }
        val count = node.int(Prop.COUNT)
        if (count > 0) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Light),
                color = zuneColors.accentColor,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun HubText(node: HubNode) {
    HubText(node.string(Prop.TEXT).orEmpty(), node.string(Prop.STYLE) ?: Style.BODY, Modifier.padding(vertical = 6.dp))
}

@Composable
private fun HubText(text: String, style: String, modifier: Modifier) {
    val zuneColors = LocalZuneColors.current
    when (style) {
        Style.TITLE -> Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier
        )
        Style.CAPTION -> Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = zuneColors.textDim,
            modifier = modifier
        )
        else -> Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = modifier
        )
    }
}

/** A plain rectangular button with a thin frame, as Zeta's are. */
@Composable
private fun HubButton(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(vertical = 10.dp)
            .wpTilt(interaction)
            .border(2.dp, ink)
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text = label.lowercase(), style = MaterialTheme.typography.bodyLarge, color = ink)
    }
}

/** Still coming: Zeta's dots running across. */
@Composable
internal fun HubLoading(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(vertical = 24.dp)
    ) {
        com.serkantkn.zeta.ui.components.ZuneLoadingDots()
    }
}

/** A bar command's glyph, from the names the protocol allows. */
internal fun hubIcon(name: String?): ImageVector = when (name) {
    Icon.ADD -> Icons.Default.Add
    Icon.REFRESH -> Icons.Default.Refresh
    Icon.SEARCH -> Icons.Default.Search
    Icon.EDIT -> Icons.Default.Edit
    Icon.DELETE -> Icons.Default.Delete
    Icon.SHARE -> Icons.Default.Share
    Icon.SETTINGS -> Icons.Default.Settings
    Icon.CHECK -> Icons.Default.Check
    Icon.CLOSE -> Icons.Default.Close
    Icon.STAR -> Icons.Default.Star
    Icon.PLAY -> Icons.Default.PlayArrow
    Icon.PAUSE -> Icons.Default.Pause
    Icon.NEXT -> Icons.Default.SkipNext
    Icon.PREVIOUS -> Icons.Default.SkipPrevious
    Icon.SEND -> Icons.Default.Send
    Icon.FILTER -> Icons.Default.FilterList
    Icon.SORT -> Icons.Default.Sort
    Icon.PIN -> Icons.Default.PushPin
    Icon.CALENDAR -> Icons.Default.CalendarMonth
    Icon.MAP -> Icons.Default.Map
    else -> Icons.Default.Extension
}
