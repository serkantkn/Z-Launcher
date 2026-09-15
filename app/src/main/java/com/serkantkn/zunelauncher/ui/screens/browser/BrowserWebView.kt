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
import android.webkit.PermissionRequest
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
    val onError: (message: String) -> Unit
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
 */
@SuppressLint("SetJavaScriptEnabled")
internal fun createBrowserWebView(context: Context, callbacks: BrowserWebCallbacks): WebView =
    WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(android.graphics.Color.TRANSPARENT)

        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true

            // Pinch to zoom. Supported by default, but without the built-in controls the gesture
            // itself is ignored — which is why pages could not be zoomed at all.
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false

            // A page that opens another window gets one, rather than silently doing nothing.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true

            // Video should not start on its own, and should be allowed to go full screen.
            mediaPlaybackRequiresUserGesture = true
            allowContentAccess = true
            allowFileAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

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
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                callbacks.onProgress(newProgress)
            }

            /**
             * A page asking for a new window: the address it wants is fished out of a throwaway
             * WebView and opened as a tab of our own.
             */
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                val host = view ?: return false
                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                val catcher = WebView(host.context)
                catcher.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        v: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        request?.url?.toString()?.let(callbacks.onOpenInNewTab)
                        catcher.destroy()
                        return true
                    }
                }
                transport.webView = catcher
                resultMsg.sendToTarget()
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
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        } else {
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        }
    } catch (e: Exception) {
        ZuneLog.w(TAG, "could not make sense of $url", e)
        return false
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        // An intent: link can name a fallback web address for exactly this case.
        val fallback = intent.getStringExtra("browser_fallback_url")
        if (!fallback.isNullOrBlank()) {
            return try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(fallback))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                true
            } catch (inner: Exception) {
                false
            }
        }
        onError(url)
        true
    } catch (e: Exception) {
        ZuneLog.w(TAG, "nothing would open $url", e)
        false
    }
}

private const val TAG = "BrowserWebView"
