package com.serkantkn.zeta.ui.tv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Update
import androidx.core.content.ContextCompat
import com.serkantkn.zeta.R
import com.serkantkn.zeta.data.link.TvLinkNotices
import com.serkantkn.zeta.util.localizedString

/**
 * A TV app put on or updated, told in the corner as the Xbox told its news. Only apps that show
 * on a TV's home (they answer LEANBACK_LAUNCHER) are told of; the launcher's own update is not.
 */
internal object TvAppNews {
    @Volatile
    private var registered = false

    fun start(context: Context) {
        if (registered) return
        registered = true
        val app = context.applicationContext
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val pkg = intent.data?.schemeSpecificPart ?: return
                if (pkg == app.packageName) return
                val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                // An update arrives as removed, added (replacing) and replaced: told once, as replaced.
                if (intent.action == Intent.ACTION_PACKAGE_ADDED && replacing) return
                val pm = app.packageManager
                if (pm.getLeanbackLaunchIntentForPackage(pkg) == null) return
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull() ?: return
                val updated = intent.action == Intent.ACTION_PACKAGE_REPLACED
                TvLinkNotices.showSystem(
                    app,
                    title = label,
                    text = app.localizedString(if (updated) R.string.tv_news_updated else R.string.tv_news_installed),
                    header = app.localizedString(R.string.tv_news_apps),
                    icon = if (updated) Icons.Default.Update else Icons.Default.Download
                )
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
