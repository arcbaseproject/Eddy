package app.eddy.browser.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Message
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.eddy.browser.privacy.ContentBlocker
import app.eddy.browser.util.UrlUtils
import java.io.ByteArrayInputStream

class EddyWebViewClient(
    private val tab: BrowserTab,
    private val host: BrowserHost,
    private val factory: WebViewFactory,
) : WebViewClient() {

    /**
     * A blocked request answers with empty content of the type the page asked for. Answering an image with
     * an error made WebView draw its broken-image placeholder where the ad used to be; a transparent pixel
     * leaves nothing behind.
     */
    private fun blocked(request: WebResourceRequest): WebResourceResponse {
        val accept = request.requestHeaders["Accept"].orEmpty()
        val url = request.url.toString().substringBefore('?').lowercase()
        val image = accept.contains("image/") || IMAGE_SUFFIXES.any { url.endsWith(it) }
        val mime = when {
            image -> "image/gif"
            accept.contains("text/css") || url.endsWith(".css") -> "text/css"
            accept.contains("javascript") || url.endsWith(".js") -> "application/javascript"
            accept.contains("text/html") -> "text/html"
            else -> "text/plain"
        }
        val body = if (image) TRANSPARENT_GIF else ByteArray(0)
        return WebResourceResponse(mime, null, 200, "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(body))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val uri = request.url
        val scheme = uri.scheme?.lowercase().orEmpty()
        when (scheme) {
            "http", "https" -> {
                if (request.isForMainFrame) (view as? EddyWebView)?.let { factory.prepare(tab, it, uri.toString()) }
                return false
            }
            "about", "data", "blob", "file", "javascript", "view-source" -> return false
            "intent" -> {
                val raw = uri.toString()
                runCatching { Intent.parseUri(raw, Intent.URI_INTENT_SCHEME) }.getOrNull()?.let {
                    // Strip anything that could target our own components or carry selectors.
                    it.selector = null
                    it.component = null
                    host.handleExternalIntent(tab, it, request.hasGesture(), it.getStringExtra("browser_fallback_url"))
                }
                return true
            }
            else -> {
                host.handleExternalIntent(tab, Intent(Intent.ACTION_VIEW, uri), request.hasGesture(), null)
                return true
            }
        }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (url.startsWith(ERROR_SCHEME)) return
        tab.pageHost = UrlUtils.host(url)
        val newPage = tab.url != url
        tab.url = url
        tab.isLoading = true
        tab.progress = 5
        tab.error?.sslHandler?.cancel()
        tab.error = null
        if (newPage) { tab.blockedRaw = 0; tab.blockedCount = 0 }
        tab.security = securityFor(url)
        tab.favicon = host.favicons.peek(UrlUtils.displayHost(url))
        syncNav(view)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (url.startsWith(ERROR_SCHEME)) return
        tab.isLoading = false
        tab.progress = 100
        tab.publishBlocked()
        syncNav(view)
        // The panel lives in the page, so a navigation wipes it; put it back where the user left it on.
        if (tab.devToolsActive) DevTools.show(view.context, view)
        if (tab.error == null) host.onPageFinished(tab)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        if (url.startsWith(ERROR_SCHEME)) return
        tab.url = url
        if (tab.error == null) tab.security = securityFor(url)
        syncNav(view)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        val url = request.url.toString()
        val kind = when {
            !factory.isOnline() -> ErrorKind.OFFLINE
            error.errorCode == ERROR_HOST_LOOKUP -> ErrorKind.DNS
            error.errorCode == ERROR_TIMEOUT -> ErrorKind.TIMEOUT
            error.errorCode == ERROR_FAILED_SSL_HANDSHAKE -> ErrorKind.SSL
            else -> ErrorKind.UNAVAILABLE
        }
        tab.error?.sslHandler?.cancel()
        tab.error = PageError(kind, url, error.description?.toString().orEmpty())
        tab.url = url
        tab.isLoading = false
        tab.security = if (kind == ErrorKind.SSL) Security.ERROR else securityFor(url)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        val url = error.url ?: view.url.orEmpty()
        // Sub-resource certificate problems must never be waved through.
        if (url != tab.url && url != view.url) { handler.cancel(); return }
        tab.error?.sslHandler?.cancel()
        val reason = when (error.primaryError) {
            SslError.SSL_UNTRUSTED -> "A trusted authority did not issue this certificate."
            SslError.SSL_EXPIRED -> "The certificate has expired."
            SslError.SSL_IDMISMATCH -> "The certificate belongs to a different site."
            SslError.SSL_NOTYETVALID -> "The certificate is not valid yet."
            SslError.SSL_DATE_INVALID -> "The certificate date is invalid."
            else -> "The certificate is invalid."
        }
        tab.error = PageError(ErrorKind.SSL, url, reason, handler)
        tab.url = url
        tab.isLoading = false
        tab.security = Security.ERROR
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        this.host.requestHttpAuth(tab, handler, host, realm)
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (request.isForMainFrame || (!tab.blockingActive && !tab.trackerBlockingActive)) return null
        val reqHost = request.url.host?.lowercase() ?: return null
        val pageHost = tab.pageHost
        if (!ContentBlocker.isThirdParty(reqHost, pageHost)) return null
        if (!host.blocker.isBlocked(reqHost, tab.blockingActive, tab.trackerBlockingActive)) return null
        tab.blockedRaw++
        return blocked(request)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        host.onRenderGone(tab)
        return true
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) = dontResend.sendToTarget()

    private fun syncNav(view: WebView) {
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
    }

    private fun securityFor(url: String) = when {
        UrlUtils.isHttps(url) -> Security.SECURE
        url.startsWith("http://", true) -> Security.INSECURE
        else -> Security.NONE
    }

    private companion object {
        const val ERROR_SCHEME = "chrome-error://"
        val IMAGE_SUFFIXES = listOf(".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".avif", ".ico", ".bmp")
        /** 1x1 fully transparent GIF. */
        val TRANSPARENT_GIF: ByteArray = android.util.Base64.decode(
            "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", android.util.Base64.DEFAULT,
        )
    }
}
