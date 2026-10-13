package com.serkantkn.zunelauncher.ui.screens.browser

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.serkantkn.zunelauncher.util.ZuneLog

/** Everything the hub wants to hear about from a page. */
internal class BrowserWebCallbacks(
    val onStarted: (url: String?, title: String?) -> Unit,
    val onFinished: (url: String?, title: String?) -> Unit,
    val onProgress: (Int) -> Unit,
    val onNavState: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    val onDownload: (url: String, userAgent: String?, disposition: String?, mime: String?, length: Long) -> Unit,
    val onOpenInNewTab: (url: String) -> Unit,
    val onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit,
    val onExitFullscreen: () -> Unit,
    val onFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Boolean,
    val onSitePermission: (PermissionRequest) -> Unit,
    val onGeolocation: (origin: String, callback: android.webkit.GeolocationPermissions.Callback) -> Unit,
    val onError: (message: String) -> Unit,
    /** The page's renderer died under it; the view is no longer usable and has to go. */
    val onRenderGone: () -> Unit = {}
)

/** The user agent a desktop site expects to see, for the "desktop version" switch. */
internal const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

/**
 * A page, set up the way a browser sets one up.
 *
 * All of this used to live inline in the hub with four settings and two callbacks; a WebView with
 * only that much wiring is not a browser — it cannot zoom, cannot upload a file, cannot play a
 * video full screen and cannot follow a link that is not http. Everything here exists to close one
 * of those holes.
 *
 * A [isPrivate] page keeps as little as the platform lets it. Without androidx.webkit's profile
 * API (not a dependency here) every WebView in the process shares one cookie jar, so a private tab
 * cannot have a jar of its own: what it can do is keep nothing on disk — no DOM storage, no
 * databases, no cache — refuse third-party cookies, and have its session cookies swept when the
 * last private tab closes (the hub does that). Cookies a site sets with an expiry date still land
 * in the shared jar; that is the limit of this approach.
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createBrowserWebView(
    context: Context,
    callbacks: BrowserWebCallbacks,
    isPrivate: Boolean = false
): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // A page that sets no background of its own is drawn on white, as every browser draws
        // it; left transparent, the launcher's wallpaper showed through behind its text. In dark
        // mode the page is darkened algorithmically, and this white with it.
        setBackgroundColor(android.graphics.Color.WHITE)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = !isPrivate
            databaseEnabled = !isPrivate
            if (isPrivate) cacheMode = WebSettings.LOAD_NO_CACHE
            loadWithOverviewMode = true
            useWideViewPort = true

            // Pinch to zoom. Supported by default, but without the built-in controls the gesture
            // itself is ignored — which is why pages could not be zoomed at all.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // A page that opens another window gets one, rather than silently doing nothing.
            setSupportMultipleWindows(true)
            // ...but only when someone tapped for it; a script popping windows on its own is
            // the oldest trick there is, and onCreateWindow checks the gesture too.
            javaScriptCanOpenWindowsAutomatically = false

            // Video should not start on its own, and should be allowed to go full screen.
            mediaPlaybackRequiresUserGesture = true

            // A page gets to be a page, and nothing more. No file:// — that is the launcher's
            // own storage. No content:// either: every provider this app can read, a page would
            // be able to read through it, including the one that hands files to other apps.
            allowFileAccess = false
            allowContentAccess = false
            // What browsers themselves now do: a secure page does not get to pull anything over
            // an insecure one, because a single injected script is the whole page.
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, !isPrivate)

        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                callbacks.onStarted(url, view?.title)
                callbacks.onNavState(view?.canGoBack() == true, view?.canGoForward() == true)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                callbacks.onFinished(url, view?.title)
                callbacks.onNavState(view?.canGoBack() == true, view?.canGoForward() == true)
            }

            /**
             * A link that is not a web address belongs to some other app: a phone number to the
             * dialler, an address to the mail app, a market link to the store. The WebView cannot
             * follow any of them, so they are handed over instead of failing.
             */
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "http" || uri.scheme == "https") return false
                return handOverToAnApp(context, uri.toString(), callbacks.onError)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                // Only the page itself is worth complaining about; a missing image is not.
                if (request?.isForMainFrame != true) return
                val description = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.description?.toString()
                } else {
                    null
                }
                callbacks.onError(description ?: "")
            }

            /**
             * The renderer behind this page was killed — out of memory, or a crash of its own.
             * Returning false here would take the whole launcher down with it; instead the hub
             * is told to throw the view away and offer the page again.
             */
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                val crashed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail?.didCrash() == true
                ZuneLog.w(TAG, "render process gone (crashed=$crashed) for ${view?.url}")
                callbacks.onRenderGone()
                return true
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                callbacks.onProgress(newProgress)
            }

            /**
             * A page asking for a new window: the address it wants is fished out of a throwaway
             * WebView and opened as a tab of our own.
             *
             * Only a window the user asked for by tapping gets one. The throwaway view is also
             * put down after a moment if the page never navigates it — a window.open() that
             * is then written into by script would otherwise leave a WebView alive for good.
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!isUserGesture) return false
                val host = view ?: return false
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val catcher = WebView(host.context)
                var done = false
                val giveUp = Runnable {
                    if (!done) {
                        done = true
                        catcher.destroy()
                    }
                }
                catcher.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        if (!done) {
                            done = true
                            host.removeCallbacks(giveUp)
                            request?.url?.toString()?.let(callbacks.onOpenInNewTab)
                            catcher.destroy()
                        }
                        return true
                    }
                }
                transport.webView = catcher
                resultMsg.sendToTarget()
                host.postDelayed(giveUp, POPUP_CATCHER_TIMEOUT_MS)
                return true
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || callback == null) return
                callbacks.onEnterFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                callbacks.onExitFullscreen()
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                if (filePathCallback == null || fileChooserParams == null) return false
                return callbacks.onFileChooser(filePathCallback, fileChooserParams)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.let(callbacks.onSitePermission)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: android.webkit.GeolocationPermissions.Callback?
            ) {
                if (origin == null || callback == null) return
                callbacks.onGeolocation(origin, callback)
            }
        }

        setDownloadListener { url, userAgent, disposition, mime, length ->
            callbacks.onDownload(url, userAgent, disposition, mime, length)
        }
    }

/**
 * Sends a non-web address to whatever app owns it. Returns whether anything took it — false means
 * the page should be left to deal with it, which for these schemes means showing nothing.
 */
private fun handOverToAnApp(context: Context, url: String, onError: (String) -> Unit): Boolean {
    val intent = try {
        if (url.startsWith("intent:")) {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME).sanitisedForAPage()
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
    } catch (e: Exception) {
        ZuneLog.w(TAG, "could not make sense of $url", e)
        return false
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // An intent: link that nothing on the phone answers to is not sent at all, so it cannot
    // reach an activity that was never meant to be reachable from a web page.
    val fallback = intent.getStringExtra("browser_fallback_url")
    val resolvable = url.startsWith("intent:").not() ||
        intent.resolveActivity(context.packageManager) != null
    if (!resolvable) return openFallback(context, fallback) || run { onError(url); true }

    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        // An intent: link can name a fallback web address for exactly this case.
        if (openFallback(context, fallback)) return true
        onError(url)
        true
    } catch (e: Exception) {
        ZuneLog.w(TAG, "nothing would open $url", e)
        false
    }
}

/**
 * Strips an intent: link down to what a page may ask for.
 *
 * As parsed, the link can name a component (any activity in any app, exported or, from inside
 * this one, not), carry a selector, and ask for URI grants on the launcher's own providers. A
 * browser's job is to open a browsable activity by action and data, and nothing beyond that.
 */
private fun Intent.sanitisedForAPage(): Intent = apply {
    component = null
    selector = null
    addCategory(Intent.CATEGORY_BROWSABLE)
    flags = flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION).inv()
}

private fun openFallback(context: Context, fallback: String?): Boolean {
    if (fallback.isNullOrBlank()) return false
    val uri = Uri.parse(fallback)
    if (uri.scheme != "http" && uri.scheme != "https") return false
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }
}

/** How long a popup's throwaway view waits for the page to navigate it before it is put down. */
private const val POPUP_CATCHER_TIMEOUT_MS = 5_000L

private const val TAG = "BrowserWebView"
