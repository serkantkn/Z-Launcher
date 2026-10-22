package com.serkantkn.zeta.ui.components

import android.content.ComponentName
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.serkantkn.zeta.data.model.ExternalHub
import com.serkantkn.zeta.data.model.HubId
import com.serkantkn.zeta.data.model.HubType
import java.util.Locale

/** A hub's name as Zeta writes it: lowercase, as every hub title is. */
@Composable
fun hubTitle(hub: HubId): String = when (hub) {
    is HubType -> stringResource(hub.titleRes)
    is ExternalHub -> {
        val context = LocalContext.current
        remember(hub) { externalHubLabel(context, hub) }
    }
}

/** An external hub's name: its service's label, or its app's when the service has none. */
fun externalHubLabel(context: Context, hub: ExternalHub): String {
    val pm = context.packageManager
    val label = runCatching {
        pm.getServiceInfo(ComponentName(hub.packageName, hub.className), 0).loadLabel(pm).toString()
    }.getOrNull() ?: runCatching {
        pm.getApplicationInfo(hub.packageName, 0).loadLabel(pm).toString()
    }.getOrNull() ?: hub.packageName
    return label.lowercase(Locale.getDefault())
}

private val externalFaces = android.util.LruCache<String, com.serkantkn.zeta.util.TileIconFace>(32)

/**
 * What another app's hub shows on its tile: the glyph its service declares (`android:icon`, one
 * shape the tile's ink fills), or its name's first letter when it declares none.
 */
fun externalHubFace(context: Context, hub: ExternalHub): com.serkantkn.zeta.util.TileIconFace {
    externalFaces.get(hub.key)?.let { return it }
    val pm = context.packageManager
    val face = runCatching {
        val service = pm.getServiceInfo(ComponentName(hub.packageName, hub.className), 0)
        if (service.icon == 0) return@runCatching null
        val drawable = pm.getDrawable(hub.packageName, service.icon, service.applicationInfo) ?: return@runCatching null
        val bitmap = drawable.toBitmap(144, 144)
        com.serkantkn.zeta.util.TileIconFace.Glyph(bitmap.asImageBitmap())
    }.getOrNull() ?: com.serkantkn.zeta.util.TileIconFace.Letter(
        externalHubLabel(context, hub).firstOrNull()?.uppercase() ?: "?"
    )
    externalFaces.put(hub.key, face)
    return face
}

/** The glyph a hub is drawn with where only a glyph fits: the switcher, folder chips, the splash. */
fun hubGlyph(hub: HubId): ImageVector = when (hub) {
    is HubType -> getHubIcon(hub)
    is ExternalHub -> Icons.Default.Extension
}
