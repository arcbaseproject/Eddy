package app.eddy.browser.browser

import app.eddy.browser.R
import app.eddy.browser.util.str
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.util.Log
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.eddy.browser.data.models.ExternalLinks
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
                val url = uri.toString()
                if (request.isForMainFrame && blockAdNavigation(view, url, request.hasGesture() && !request.isRedirect)) return true
                if (request.isForMainFrame && request.hasGesture() && openInApp(view, uri)) return true
                val upgraded = factory.upgrade(url)
                // The upgraded page redirects straight back to http: upgrading again would loop forever.
                if (request.isForMainFrame && request.isRedirect && upgraded != url &&
                    tab.httpsUpgradedFrom?.let { UrlUtils.host(it) == UrlUtils.host(url) } == true
                ) {
                    view.stopLoading()
                    tab.error = PageError(ErrorKind.INSECURE, url, "")
                    tab.url = url
                    tab.isLoading = false
                    tab.security = Security.INSECURE
                    return true
                }
                if (request.isForMainFrame && upgraded != url) {
                    tab.httpsUpgradedFrom = url
                    view.loadUrl(upgraded)
                    return true
                }
                if (request.isForMainFrame) {
                    // Keep the marker for the upgraded URL itself; drop it once the tab moves somewhere else.
                    if (tab.httpsUpgradedFrom?.let { UrlUtils.host(it) != UrlUtils.host(url) } == true) tab.httpsUpgradedFrom = null
                    (view as? EddyWebView)?.let { factory.prepare(tab, it, url) }
                }
                return false
            }
            "about", "data", "blob", "file", "javascript", "view-source" -> return false
            "intent" -> {
                if (!request.isForMainFrame) return true
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
                if (!request.isForMainFrame) return true
                host.handleExternalIntent(tab, Intent(Intent.ACTION_VIEW, uri), request.hasGesture(), null)
                return true
            }
        }
    }

    /**
     * A tapped link to another site that an app claims (TikTok, WhatsApp, Telegram, Play Store…) opens in that app,
     * like Chrome does. Links within the current site stay here, so browsing tiktok.com in Eddy keeps working.
     * shortcut: Android 11+ only (FLAG_ACTIVITY_REQUIRE_NON_BROWSER); Android 10 would need QUERY_ALL_PACKAGES.
     */
    private fun openInApp(view: WebView, uri: Uri): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || host.settings.externalLinks == ExternalLinks.NEVER) return false
        if (UrlUtils.host(uri.toString()) == UrlUtils.host(tab.url)) return false
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER or Intent.FLAG_ACTIVITY_NEW_TASK)
        // Throws ActivityNotFoundException when only browsers handle the link.
        return runCatching { view.context.startActivity(intent) }.isSuccess
    }

    /**
     * Popunder ads open a window and send it to an ad server, often on a real tap, so the popup gesture
     * check lets them through; tab-under and redirect ads send the current tab there instead. Main-frame
     * loads skip [shouldInterceptRequest], so check the destination here: a popup closes, any other tab
     * stays on its page. A tap the user made shows a warning they can click through instead.
     * Same-site navigations pass so a blocked vendor's own site still works.
     */
    private fun blockAdNavigation(view: WebView, url: String, userTap: Boolean = false): Boolean {
        val h = UrlUtils.host(url)
        if (h.isEmpty() || (!tab.popup && !ContentBlocker.isThirdParty(h, tab.pageHost))) return false
        if (!host.blocker.isBlocked(h, tab.blockingActive, tab.trackerBlockingActive)) return false
        // Private browsing leaves no hosts in the app log.
        if (!tab.incognito) Log.d("ContentBlocker", "Stopped navigation to $h")
        tab.blockedRaw++
        tab.publishBlocked()
        if (tab.popup) {
            view.stopLoading()
            view.post { (view as? EddyWebView)?.let(host::closeWindow) }
        } else if (userTap) {
            tab.error = PageError(ErrorKind.BLOCKED, url, "")
        }
        return true
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        if (url.startsWith(ERROR_SCHEME) || (tab.popup && blockAdNavigation(view, url))) return
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
        // Reader mode rewrites the document, so a navigation wipes it too.
        if (tab.readerActive) ReaderMode.show(view.context, view) { ok -> if (!ok) tab.readerActive = false }
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
        // Compare hosts, not whole URLs: WebView normalises the URL it reports (a trailing slash, for one).
        val upgradedFrom = tab.httpsUpgradedFrom?.takeIf { UrlUtils.isHttps(url) && UrlUtils.host(it) == UrlUtils.host(url) }
        val kind = when {
            !factory.isOnline() -> ErrorKind.OFFLINE
            // The page exists over http and only the upgrade failed, so say that instead of "not available".
            upgradedFrom != null -> ErrorKind.INSECURE
            error.errorCode == ERROR_HOST_LOOKUP -> ErrorKind.DNS
            error.errorCode == ERROR_TIMEOUT -> ErrorKind.TIMEOUT
            error.errorCode == ERROR_FAILED_SSL_HANDSHAKE -> ErrorKind.SSL
            else -> ErrorKind.UNAVAILABLE
        }
        tab.error?.sslHandler?.cancel()
        tab.error = PageError(
            kind, if (kind == ErrorKind.INSECURE) upgradedFrom!! else url,
            error.description?.toString().orEmpty(),
        )
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
            SslError.SSL_EXPIRED -> str(R.string.the_certificate_has_expired)
            SslError.SSL_IDMISMATCH -> str(R.string.the_certificate_belongs_to_a_different_s)
            SslError.SSL_NOTYETVALID -> str(R.string.the_certificate_is_not_valid_yet)
            SslError.SSL_DATE_INVALID -> str(R.string.the_certificate_date_is_invalid)
            else -> str(R.string.the_certificate_is_invalid)
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
        if (!host.blocker.isRequestBlocked(request.url.toString(), reqHost, pageHost, tab.blockingActive, tab.trackerBlockingActive)) return null
        tab.blockedRaw++
        if (!tab.incognito) Log.d("ContentBlocker", "Blocked $reqHost on $pageHost")
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
        UrlUtils.isHttps(url) && factory.hasCertificateException(url) -> Security.ERROR
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
