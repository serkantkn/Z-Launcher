package com.serkantkn.zeta.hubkit

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.serkantkn.zeta.hubkit.protocol.HubNode
import com.serkantkn.zeta.hubkit.protocol.HubProtocol
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Command
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Event
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Node
import com.serkantkn.zeta.hubkit.protocol.HubProtocol.Prop
import com.serkantkn.zeta.hubkit.protocol.HubSpecValidator
import com.serkantkn.zeta.hubkit.protocol.IZetaHost
import com.serkantkn.zeta.hubkit.protocol.IZetaHub
import java.util.Locale

/**
 * A hub for Zeta. Extend it, describe the hub in [hub], and declare the service in the manifest:
 *
 * ```xml
 * <service android:name=".MyHub" android:exported="true" android:label="@string/hub_name">
 *     <intent-filter><action android:name="com.serkantkn.zeta.action.HUB" /></intent-filter>
 *     <meta-data android:name="com.serkantkn.zeta.hub.protocol" android:value="1" />
 * </service>
 * ```
 *
 * Zeta binds to it while the hub is open, draws what [hub] describes and sends touches back to
 * the lambdas given there. Everything here runs on the main thread. Only Zeta may talk to the
 * service: a release build answers nothing else (a debuggable build answers any Zeta build, so
 * it can be tried against Zeta's development build).
 */
abstract class ZetaHubService : Service() {

    /** Describes the hub as it is now. Runs again after [invalidate] and after every event. */
    abstract fun hub(): HubSpec

    /** The hub came into view in Zeta. */
    open fun onHubShown() {}

    /** The hub went out of view (Zeta went home, or the screen went off). */
    open fun onHubHidden() {}

    /** The page with this index is now the one in front. */
    open fun onPageChanged(index: Int) {}

    /** What Zeta told us about itself when it opened the hub; null while nobody is connected. */
    var host: HostInfo? = null
        private set

    /**
     * The language Zeta speaks, which may not be the phone's. While Zeta is connected the
     * service's resources are in it too, so `getString` gives the hub's words in Zeta's language;
     * format dates and numbers with it as well.
     */
    val hubLocale: Locale
        get() = host?.locale?.takeIf { it.isNotBlank() }?.let(Locale::forLanguageTag) ?: Locale.getDefault()

    private var localized: Context? = null

    override fun getResources(): Resources = localized?.resources ?: super.getResources()

    private fun baseConfiguration(): Configuration = super.getResources().configuration

    /** A value the hub is drawn from: setting it describes the hub again. */
    protected fun <T> mutableHubStateOf(value: T): HubState<T> = HubState(value, ::invalidate)

    /** Describes the hub again soon, once, however many times this is called meanwhile. */
    fun invalidate() {
        if (renderPosted) return
        renderPosted = true
        main.post {
            renderPosted = false
            render()
        }
    }

    /** Opens [page] over the hub. Back closes it again. */
    fun openSubPage(page: SubPage) {
        val key = SUB_PREFIX + (page.key ?: "page${++subPageCount}")
        subPages.removeAll { it.key == key }
        subPages.add(OpenPage(key) { page })
        invalidate()
    }

    /** Closes the sub-page on top, if any. */
    fun closeSubPage() {
        if (subPages.isNotEmpty()) subPages.removeAt(subPages.lastIndex)
        invalidate()
    }

    /** A short note at the top of the screen. */
    fun toast(text: String) = command(Command.TOAST) { putString(Command.TEXT, text) }

    /** Opens a page on the web in Zeta's internet hub; https only. */
    fun openUrl(url: String) = command(Command.OPEN_URL) { putString(Command.URL, url) }

    /** The system's share sheet with [text]. */
    fun share(text: String) = command(Command.SHARE) { putString(Command.TEXT, text) }

    /** Closes the hub. */
    fun closeHub() = command(Command.CLOSE_HUB) {}

    // ── Inside ──────────────────────────────────────────────────────────────

    private class OpenPage(val key: String, val make: () -> SubPage)

    private val main = Handler(Looper.getMainLooper())
    private var callback: IZetaHost? = null
    private var callerUid = -1
    /** The Zeta that is connected, to let it read the pictures the hub points at. */
    private var zetaPackage: String? = null
    private var handlers = Handlers()
    private val subPages = ArrayList<OpenPage>()
    private var subPageCount = 0
    private var renderPosted = false

    private val binder = object : IZetaHub.Stub() {
        override fun hello(hostInfo: Bundle?, callback: IZetaHost?) {
            val uid = Binder.getCallingUid()
            if (callback == null || !isZeta(uid)) {
                Log.w(TAG, "a caller that is not Zeta (uid $uid) was turned away")
                return
            }
            val info = HostInfo.from(hostInfo ?: Bundle.EMPTY)
            val zeta = packageManager.getPackagesForUid(uid)?.firstOrNull { it in ZetaIdentity.PACKAGES }
            main.post {
                callerUid = uid
                zetaPackage = zeta
                this@ZetaHubService.callback = callback
                host = info
                localized = createConfigurationContext(
                    Configuration(baseConfiguration()).apply { setLocale(hubLocale) }
                )
                render()
            }
        }

        override fun event(event: Bundle?) {
            if (event == null || Binder.getCallingUid() != callerUid) return
            val copy = Bundle(event)
            main.post { dispatch(copy) }
        }

        override fun bye() {
            if (Binder.getCallingUid() != callerUid) return
            main.post { disconnect() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == HubProtocol.ACTION_HUB) binder else null

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return false
    }

    private fun disconnect() {
        callback = null
        zetaPackage = null
        host = null
        localized = null
        callerUid = -1
    }

    /**
     * What is wrong with the hub's description as it stands - for a test of your own:
     * `assertEquals(emptyList(), Robolectric.buildService(MyHub::class.java).create().get().validate())`.
     */
    fun validate(): List<HubSpecValidator.Problem> = HubSpecValidator.check(describe(Handlers()))

    /** The description as it stands, open sub-pages included; [made] collects what touches do. */
    private fun describe(made: Handlers): HubNode {
        val spec = hub()
        val scope = HubScope(made).apply(spec.content)
        val children = ArrayList(scope.nodes)
        subPages.forEach { open ->
            val page = runCatching { open.make() }.getOrNull() ?: return@forEach
            val content = PageScope(open.key, made).apply(page.content)
            children.add(HubNode(Node.SUB_PAGE, open.key, Bundle().apply {
                putString(Prop.TITLE, page.title)
            }, content.nodes))
        }
        return HubNode(Node.HUB, "hub", Bundle().apply {
            putString(Prop.TITLE, spec.title ?: label())
        }, children)
    }

    private fun render() {
        val to = callback ?: return
        val made = Handlers()
        val root = describe(made)
        handlers = made
        sharePictures(root)
        try {
            to.spec(root)
        } catch (e: RemoteException) {
            Log.w(TAG, "Zeta is gone", e)
            disconnect()
        }
    }

    /** Lets Zeta read the content:// pictures the description points at; https ones it fetches. */
    private fun sharePictures(node: HubNode) {
        val zeta = zetaPackage ?: return
        node.string(Prop.IMAGE)?.let { image ->
            val uri = android.net.Uri.parse(image)
            if (uri.scheme == "content") {
                runCatching { grantUriPermission(zeta, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    .onFailure { Log.w(TAG, "could not let Zeta read $uri: is it your FileProvider's?", it) }
            }
        }
        node.children.forEach { sharePictures(it) }
    }

    private fun dispatch(event: Bundle) {
        val key = event.getString(Event.KEY).orEmpty()
        when (event.getString(Event.KIND)) {
            Event.CLICK -> {
                val opens = handlers.opens[key]
                if (opens != null) {
                    val sub = SUB_PREFIX + key
                    subPages.removeAll { it.key == sub }
                    subPages.add(OpenPage(sub, opens))
                } else {
                    handlers.clicks[key]?.invoke()
                }
            }
            Event.LONG_CLICK -> handlers.longClicks[key]?.invoke()
            Event.ACTION -> handlers.actions[key]?.invoke()
            Event.TOGGLE -> handlers.toggles[key]?.invoke(event.getBoolean(Event.CHECKED))
            Event.BACK -> {
                val at = subPages.indexOfFirst { it.key == key }
                if (at >= 0) while (subPages.size > at) subPages.removeAt(subPages.lastIndex)
            }
            Event.PAGE -> onPageChanged(event.getInt(Event.INDEX))
            Event.VISIBLE -> if (event.getBoolean(Event.SHOWN)) onHubShown() else onHubHidden()
        }
        // A handler may have changed what the hub is drawn from without telling anyone.
        invalidate()
    }

    private fun command(kind: String, fill: Bundle.() -> Unit) {
        val to = callback ?: return
        try {
            to.command(Bundle().apply { putString(Command.KIND, kind); fill() })
        } catch (e: RemoteException) {
            Log.w(TAG, "Zeta is gone", e)
            disconnect()
        }
    }

    /** The service's label from the manifest, in Zeta's language when it is a string resource. */
    private fun label(): String = runCatching {
        val info = packageManager.getServiceInfo(ComponentName(this, javaClass), 0)
        if (info.labelRes != 0) getString(info.labelRes) else info.loadLabel(packageManager).toString()
    }.getOrDefault(javaClass.simpleName)

    /** Zeta, signed with Zeta's key - or any Zeta build, when this app is a debuggable build. */
    private fun isZeta(uid: Int): Boolean {
        val packages = packageManager.getPackagesForUid(uid) ?: return false
        return packages.any { ZetaIdentity.isZeta(this, it) }
    }

    /**
     * Writes the hub's live tile, as [hub] describes it now, to Zeta while the hub is closed - after
     * the data behind it changed in the background. See [ZetaTiles].
     */
    fun publishTile(): Boolean {
        val scope = HubScope(Handlers()).apply(hub().content)
        val tile = scope.nodes.firstOrNull { it.type == Node.LIVE_TILE } ?: return false
        return ZetaTiles.update(this, javaClass) {
            count = tile.int(Prop.COUNT)
            line = tile.string(Prop.LINE)
            title = tile.string(Prop.TITLE)
        }
    }

    private companion object {
        const val TAG = "ZetaHubKit"
        const val SUB_PREFIX = "sub:"
    }
}

/** What Zeta said about itself when it opened the hub. */
class HostInfo internal constructor(
    /** The protocol Zeta speaks. */
    val protocol: Int,
    /** The kinds of things Zeta can draw, with their versions. */
    val components: Map<String, Int>,
    /** "phone" or "tablet". */
    val formFactor: String,
    /** "aura", "mosaic" or "panorama". */
    val layout: String,
    /** The language Zeta speaks, as a BCP 47 tag. */
    val locale: String,
    val dark: Boolean,
    /** The accent colour, ARGB. */
    val accent: Int
) {
    internal companion object {
        fun from(b: Bundle): HostInfo {
            val components = b.getBundle(HubProtocol.Host.COMPONENTS)
            return HostInfo(
                protocol = b.getInt(HubProtocol.Host.PROTOCOL, 1),
                components = components?.keySet()?.associateWith { components.getInt(it) }.orEmpty(),
                formFactor = b.getString(HubProtocol.Host.FORM_FACTOR, "phone"),
                layout = b.getString(HubProtocol.Host.LAYOUT, "mosaic"),
                locale = b.getString(HubProtocol.Host.LOCALE, ""),
                dark = b.getBoolean(HubProtocol.Host.DARK),
                accent = b.getInt(HubProtocol.Host.ACCENT)
            )
        }
    }
}
