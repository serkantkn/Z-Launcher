package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.LruCache
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

/** An icon pack the user has installed. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * Reads the icon packs installed on the phone.
 *
 * Every Android icon pack, whichever launcher it was written for, ships the same two files: an
 * `appfilter.xml` mapping a component to a drawable name, and a `drawable.xml` listing everything
 * the pack contains for pickers. They live in the pack's assets, or — for packs built as
 * libraries — in its `res/xml`. Both are read here so the same code serves either shape.
 *
 * An appfilter is thousands of lines, and it used to be parsed on the main thread the first time
 * a list asked for an icon, inside a `remember` — a visible stall on every pack change. It is
 * parsed once, on IO, into caches that several threads may read; [ready] ticks when a parse
 * lands so a list drawn before it can ask again.
 */
class IconPackRepository(private val context: Context) {

    private val packageManager = context.packageManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** appfilter, per pack, parsed once. Read from any thread. */
    private val filters = ConcurrentHashMap<String, Map<String, String>>()

    /** The pack's whole drawable list, for the picker. */
    private val catalogues = ConcurrentHashMap<String, List<String>>()

    /** Packs whose parse is under way, so a list asking twenty times starts one parse, not twenty. */
    private val parsing = ConcurrentHashMap.newKeySet<String>()

    private val drawableCache = LruCache<String, Drawable>(180)

    private val _ready = MutableStateFlow(0)

    /** Ticks whenever a pack's filter has been parsed or dropped; a list keys its icons on it. */
    val ready: StateFlow<Int> = _ready.asStateFlow()

    /**
     * Packs are found the way every launcher finds them: by the theme intents the pack declares.
     * A pack usually declares several, so the result is de-duplicated by package.
     */
    fun installedPacks(): List<IconPackInfo> {
        val found = linkedMapOf<String, IconPackInfo>()
        THEME_ACTIONS.forEach { action ->
            val resolved = try {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA)
            } catch (e: Exception) {
                ZuneLog.w(TAG, "icon pack query failed for $action", e)
                emptyList()
            }
            resolved.forEach { info ->
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || found.containsKey(pkg)) return@forEach
                found[pkg] = IconPackInfo(pkg, info.loadLabel(packageManager).toString())
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    /**
     * The pack's own icon for an app, or null when the pack says nothing about it. Parses the
     * pack's filter if it has not been read yet — so this is for background callers, such as
     * the tile factory; a list on the main thread uses [iconForIfReady].
     */
    fun iconFor(packPackage: String, packageName: String, activityName: String): Drawable? {
        val filter = appFilter(packPackage)
        return iconFrom(filter, packPackage, packageName, activityName)
    }

    /**
     * The same, without ever parsing on the caller's thread: null until the pack has been read,
     * with the read started on IO and [ready] ticking when it lands.
     */
    fun iconForIfReady(packPackage: String, packageName: String, activityName: String): Drawable? {
        val filter = filters[packPackage]
        if (filter == null) {
            prepare(packPackage)
            return null
        }
        return iconFrom(filter, packPackage, packageName, activityName)
    }

    /** Reads a pack's filter on IO, once, and says so through [ready]. */
    fun prepare(packPackage: String) {
        if (packPackage.isBlank() || filters.containsKey(packPackage)) return
        if (!parsing.add(packPackage)) return
        scope.launch {
            try {
                appFilter(packPackage)
            } finally {
                parsing.remove(packPackage)
                _ready.value = _ready.value + 1
            }
        }
    }

    private fun iconFrom(filter: Map<String, String>, packPackage: String, packageName: String, activityName: String): Drawable? {
        if (filter.isEmpty()) return null
        val drawable = filter[componentKey(packageName, activityName)]
            ?: filter[packageFallbackKey(packageName)]
            ?: return null
        return drawableByName(packPackage, drawable)
    }

    /** Every drawable the pack offers, for picking one by hand. */
    fun drawableNames(packPackage: String): List<String> = catalogues.getOrPut(packPackage) {
        val resources = resourcesOf(packPackage) ?: return@getOrPut emptyList()
        val listed = openPackXml(packPackage, resources, "drawable.xml", "drawable")
            ?.use { parser -> readDrawableList(parser) }
            .orEmpty()
        // Some packs ship no drawable.xml; their appfilter is the next best catalogue.
        val names = listed.ifEmpty { appFilter(packPackage).values.distinct() }
        names.filter { it.isNotBlank() }.distinct().sorted()
    }

    fun drawableByName(packPackage: String, name: String): Drawable? {
        val key = "$packPackage/$name"
        synchronized(drawableCache) { drawableCache.get(key) }?.let { return it }
        val resources = resourcesOf(packPackage) ?: return null
        return try {
            @Suppress("DISCOURAGED_API_USAGE")
            val id = resources.getIdentifier(name, "drawable", packPackage)
            if (id == 0) null else resources.getDrawable(id, null)?.also {
                synchronized(drawableCache) { drawableCache.put(key, it) }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "icon pack drawable $key failed", e)
            null
        }
    }

    /** Forgets everything read from every pack, so a reinstalled pack is read afresh. */
    fun invalidate() {
        filters.clear()
        catalogues.clear()
        synchronized(drawableCache) { drawableCache.evictAll() }
        _ready.value = _ready.value + 1
    }

    /** Forgets one pack — the one the phone just said came, went or changed. */
    fun invalidate(packPackage: String): Boolean {
        val hadFilter = filters.remove(packPackage) != null
        val hadCatalogue = catalogues.remove(packPackage) != null
        if (!hadFilter && !hadCatalogue) return false
        synchronized(drawableCache) {
            drawableCache.snapshot().keys.filter { it.startsWith("$packPackage/") }.forEach { drawableCache.remove(it) }
        }
        _ready.value = _ready.value + 1
        return true
    }

    /**
     * Drops a pack's caches when the phone says that package changed. A pack updated in place
     * would otherwise keep serving its old filter until the launcher was restarted.
     * [onInvalidated] is for whoever else caches what this served — the tile factory.
     */
    fun watchPackages(changes: Flow<String>, onInvalidated: () -> Unit) {
        scope.launch {
            changes.collect { packageName ->
                if (invalidate(packageName)) {
                    ZuneLog.d(TAG, "icon pack $packageName changed; read afresh")
                    onInvalidated()
                }
            }
        }
    }

    private fun appFilter(packPackage: String): Map<String, String> = filters.getOrPut(packPackage) {
        val resources = resourcesOf(packPackage) ?: return@getOrPut emptyMap()
        openPackXml(packPackage, resources, "appfilter.xml", "appfilter")
            ?.use { parser -> readAppFilter(parser) }
            .orEmpty()
    }

    private fun resourcesOf(packPackage: String): Resources? = try {
        packageManager.getResourcesForApplication(packPackage)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    /**
     * Opens one of the pack's XML files, from its assets if it is there and from its compiled
     * `res/xml` if it is not. [PackXml] hides which of the two it turned out to be.
     */
    private fun openPackXml(
        packPackage: String,
        resources: Resources,
        assetName: String,
        resourceName: String
    ): PackXml? {
        try {
            val stream = resources.assets.open(assetName)
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(stream, null)
            return PackXml(parser) { stream.close() }
        } catch (_: Exception) {
            // No such asset: fall through to the compiled resource.
        }
        return try {
            @Suppress("DISCOURAGED_API_USAGE")
            val id = resources.getIdentifier(resourceName, "xml", packPackage)
            if (id == 0) null else {
                val parser = resources.getXml(id)
                PackXml(parser) { parser.close() }
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "icon pack $packPackage has no readable $resourceName", e)
            null
        }
    }

    private class PackXml(val parser: XmlPullParser, private val close: () -> Unit) {
        inline fun <T> use(block: (XmlPullParser) -> T): T = try {
            block(parser)
        } finally {
            try {
                close()
            } catch (_: Exception) {
                // Nothing left to do about a file that will not close.
            }
        }
    }

    private fun readAppFilter(parser: XmlPullParser): Map<String, String> = buildMap {
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                        put(component, drawable)
                        packageOf(component)?.let { putIfAbsent(packageFallbackKey(it), drawable) }
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "appfilter parse stopped early", e)
        }
    }

    private fun readDrawableList(parser: XmlPullParser): List<String> = buildList {
        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    parser.getAttributeValue(null, "drawable")?.let { add(it) }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            ZuneLog.w(TAG, "drawable list parse stopped early", e)
        }
    }

    companion object {
        private const val TAG = "IconPackRepository"

        /** Every theme intent the common icon packs declare. */
        private val THEME_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "org.adw.launcher.icons.ACTION_PICK_ICON",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "ch.deletescape.lawnchair.ICONPACK",
            "com.teslacoilsw.launcher.THEME"
        )

        /** How appfilter.xml names one launcher activity. */
        fun componentKey(packageName: String, activityName: String): String =
            "ComponentInfo{$packageName/$activityName}"

        /**
         * Packs written against an older version of an app name an activity that no longer
         * exists, so a per-package key is kept alongside: any entry for the package will do when
         * the exact activity is not listed.
         */
        fun packageFallbackKey(packageName: String): String = "package:$packageName"

        /** The package out of a "ComponentInfo{pkg/activity}" key, or null when it is malformed. */
        fun packageOf(component: String): String? {
            val body = component.substringAfter("ComponentInfo{", "").substringBefore("}")
            if (body.isBlank()) return null
            val slash = body.indexOf('/')
            return if (slash <= 0) null else body.substring(0, slash)
        }
    }
}
