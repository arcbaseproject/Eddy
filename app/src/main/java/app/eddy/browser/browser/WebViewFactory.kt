package app.eddy.browser.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.data.models.CookieMode
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.UserAgentMode
import app.eddy.browser.util.UrlUtils

/** Builds and configures WebViews. All per-tab and per-site policy is applied here in one place. */
class WebViewFactory(private val appContext: Context, private val host: BrowserHost) {

    private val defaultUa: String by lazy { WebSettings.getDefaultUserAgent(appContext) }
    private val mobileUa by lazy { UserAgents.mobile(defaultUa) }
    private val desktopUa by lazy { UserAgents.desktop(defaultUa) }
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)

    val incognitoIsolated: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun isOnline(): Boolean {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    fun create(tab: BrowserTab): EddyWebView {
        val view = EddyWebView(MutableContextWrapper(appContext))
        view.incognito = tab.incognito
        if (tab.incognito && incognitoIsolated) WebViewCompat.setProfile(view, INCOGNITO_PROFILE)

        view.setBackgroundColor(host.pageBackground)
        view.overScrollMode = View.OVER_SCROLL_NEVER
        view.isVerticalScrollBarEnabled = false
        if (tab.incognito) view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            mediaPlaybackRequiresUserGesture = true
            setGeolocationEnabled(true)
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            useWideViewPort = true
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION") allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = if (tab.incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        }

        val client = EddyWebViewClient(tab, host, this)
        view.webViewClient = client
        view.webChromeClient = EddyChromeClient(tab, host)
        view.setDownloadListener { url, ua, disposition, mime, length ->
            host.startDownload(tab, url, ua, disposition, mime, length)
        }
        view.setFindListener { active, total, _ -> host.onFindResult(tab, active, total) }
        view.setOnLongClickListener { longPress(tab, view) }
        view.onScrolled = { dy, y -> host.onScrolled(tab, dy, y) }
        view.onPull = { tab.pullDistance = it }
        view.onPullRelease = { if (it) view.reload() }

        applyGlobal(view, host.settings)
        prepare(tab, view, tab.url)
        return view
    }

    /** Settings that do not depend on the current site; also re-run when the user changes a preference. */
    fun applyGlobal(view: EddyWebView, s: Settings) {
        view.settings.textZoom = s.textZoom
        WebView.setWebContentsDebuggingEnabled(s.webDebugging)
        val cookies = cookieManager(view.incognito)
        cookies.setAcceptCookie(s.cookieMode != CookieMode.BLOCK_ALL)
        cookies.setAcceptThirdPartyCookies(view, s.cookieMode == CookieMode.ALLOW_ALL)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            if (s.doNotTrack && view.dntScript == null) {
                view.dntScript = WebViewCompat.addDocumentStartJavaScript(view, DNT_SCRIPT, setOf("*"))
            } else if (!s.doNotTrack) {
                view.dntScript?.remove()
                view.dntScript = null
            }
        }
    }

    /** Resolves site overrides for [url] and pushes them into the WebView. Cheap; safe to call per navigation. */
    fun prepare(tab: BrowserTab, view: EddyWebView, url: String) {
        val s = host.settings
        val site = host.sites.peek(UrlUtils.displayHost(url))
        view.settings.javaScriptEnabled = site?.javascript?.let { it == SiteSettings.ALLOW } ?: s.javascript
        val desktop = tab.desktopOverride
            ?: site?.desktop?.let { it == SiteSettings.ALLOW }
            ?: (s.userAgentMode == UserAgentMode.DESKTOP)
        val ua = when {
            desktop -> desktopUa
            s.userAgentMode == UserAgentMode.CUSTOM && s.customUserAgent.isNotBlank() -> s.customUserAgent
            else -> mobileUa
        }
        if (view.settings.userAgentString != ua) view.settings.userAgentString = ua
        view.settings.loadWithOverviewMode = desktop
        tab.desktopActive = desktop
        val thirdParty = site?.thirdPartyCookies?.let { it == SiteSettings.ALLOW } ?: (s.cookieMode == CookieMode.ALLOW_ALL)
        cookieManager(tab.incognito).setAcceptThirdPartyCookies(view, thirdParty && s.cookieMode != CookieMode.BLOCK_ALL)
        val blockingAllowed = site?.contentBlocking != SiteSettings.BLOCK
        tab.blockingActive = s.adBlock && blockingAllowed
        tab.trackerBlockingActive = s.trackerProtection && blockingAllowed
        // Offline: prefer whatever the HTTP cache has instead of failing outright.
        view.settings.cacheMode = when {
            tab.incognito -> WebSettings.LOAD_NO_CACHE
            !isOnline() -> WebSettings.LOAD_CACHE_ELSE_NETWORK
            else -> WebSettings.LOAD_DEFAULT
        }
    }

    fun load(tab: BrowserTab, view: EddyWebView, url: String) {
        prepare(tab, view, url)
        view.settings.allowFileAccess = url.startsWith("file:", ignoreCase = true)
        val headers = if (host.settings.doNotTrack && UrlUtils.isWebUrl(url)) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()
        view.loadUrl(url, headers)
    }

    @SuppressLint("RequiresFeature")
    fun cookieManager(incognito: Boolean): CookieManager =
        if (incognito && incognitoIsolated) {
            ProfileStore.getInstance().getOrCreateProfile(INCOGNITO_PROFILE).cookieManager
        } else {
            CookieManager.getInstance()
        }

    private fun longPress(tab: BrowserTab, view: EddyWebView): Boolean {
        val hit = view.hitTestResult
        val extra = hit.extra ?: return false
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> { host.onLongPress(tab, HitTarget(extra, null)); true }
            WebView.HitTestResult.IMAGE_TYPE -> { host.onLongPress(tab, HitTarget(null, extra)); true }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                val msg = Handler(Looper.getMainLooper()) { m ->
                    host.onLongPress(tab, HitTarget(m.data.getString("url"), m.data.getString("src") ?: extra))
                    true
                }.obtainMessage()
                view.requestFocusNodeHref(msg)
                true
            }
            else -> false
        }
    }

    companion object {
        const val INCOGNITO_PROFILE = "eddy_incognito"
        private const val DNT_SCRIPT =
            "try{Object.defineProperty(navigator,'doNotTrack',{get:function(){return '1'}});" +
                "Object.defineProperty(navigator,'globalPrivacyControl',{get:function(){return true}})}catch(e){}"
    }
}
