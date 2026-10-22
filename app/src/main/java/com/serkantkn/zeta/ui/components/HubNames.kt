package com.serkantkn.zeta.ui.components

import android.content.ComponentName
import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
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

/** The glyph a hub is drawn with where only a glyph fits: the switcher, folder chips, the splash. */
fun hubGlyph(hub: HubId): ImageVector = when (hub) {
    is HubType -> getHubIcon(hub)
    is ExternalHub -> Icons.Default.Extension
}
