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
import androidx.webkit.Profile
import androidx.webkit.PrerenderOperationCallback
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.downloads.BlobDownloader
import app.eddy.browser.data.models.CookieMode
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.UserAgentMode
import app.eddy.browser.util.UrlUtils
import java.util.concurrent.ConcurrentHashMap

/** Builds and configures WebViews. All per-tab and per-site policy is applied here in one place. */
@androidx.annotation.OptIn(markerClass = [WebSettingsCompat.ExperimentalSpeculativeLoading::class,
    Profile.ExperimentalPreconnect::class, Profile.ExperimentalWarmUpRendererProcess::class])
class WebViewFactory(private val appContext: Context, private val host: BrowserHost) {

    private val defaultUa: String by lazy { WebSettings.getDefaultUserAgent(appContext) }
    private val mobileUa by lazy { UserAgents.mobile(defaultUa) }
    private val desktopUa by lazy { UserAgents.desktop(defaultUa) }
    /** Client hints (Sec-CH-UA-*, navigator.userAgentData) the system WebView reports; captured from the first view. */
    private var mobileMeta: UserAgentMetadata? = null
    private val desktopMeta by lazy {
        mobileMeta?.let {
            UserAgentMetadata.Builder(it).setMobile(false).setPlatform("Linux").setModel("").apply {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA_FORM_FACTORS)) setFormFactors(listOf("Desktop"))
            }.build()
        }
    }
    private val mainExecutor by lazy { java.util.concurrent.Executor { Handler(Looper.getMainLooper()).post(it) } }
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val certificateExceptions = mutableSetOf<String>()
    private var sslPreferencesCleared = false

    fun rememberCertificateException(url: String) { certificateExceptions.add(UrlUtils.host(url)) }
    fun hasCertificateException(url: String): Boolean = UrlUtils.host(url) in certificateExceptions

    /** Each incognito session gets its own storage profile, so a new session can never see an old one. */
    @Volatile var incognitoProfile: String = newProfileName()
        private set

    fun startIncognitoSession() { incognitoProfile = newProfileName() }

    /** Password capture, autofill and blob downloads need message channels and start-up scripts (newer WebView only). */
    val pageBridgeSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

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
        if (tab.incognito && incognitoIsolated) WebViewCompat.setProfile(view, incognitoProfile)
        if (!sslPreferencesCleared) {
            // Chromium may outlive the previous ViewModel. Do not inherit untracked SSL decisions.
            view.clearSslPreferences()
            sslPreferencesCleared = true
        }

        // Pages that set no background of their own must render like they do in any browser: dark text on white.
        // Tinting this with the app theme would make plain pages unreadable in dark mode.
        view.setBackgroundColor(Color.WHITE)
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
        // WebView hides PublicKeyCredential by default. Sites like GitHub then drop passkey and
        // "Continue with Google/Apple" sign-in, since they load those behind a WebAuthn check.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
            WebSettingsCompat.setWebAuthenticationSupport(view.settings, WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_BROWSER)
        }
        // Instant back/forward (the page keeps running instead of reloading) and prerendering support.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.BACK_FORWARD_CACHE)) {
            WebSettingsCompat.setBackForwardCacheEnabled(view.settings, true)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SPECULATIVE_LOADING)) {
            WebSettingsCompat.setSpeculativeLoadingStatus(view.settings, WebSettingsCompat.SPECULATIVE_LOADING_PRERENDER_ENABLED)
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

        registerAutofill(tab, view)
        applyGlobal(view, host.settings)
        prepare(tab, view, tab.url)
        return view
    }

    /**
     * Gives every page a message channel to the password manager. The origin passed to the host is the one
     * the WebView reports for the sending frame, so a page cannot claim to be another site.
     */
    private fun registerAutofill(tab: BrowserTab, view: EddyWebView) {
        if (!pageBridgeSupported) return
        WebViewCompat.addWebMessageListener(view, AutofillScript.NAME, setOf("*")) { _, message, sourceOrigin, isMainFrame, reply ->
            // Subframes are ignored: a third-party iframe must not be able to raise a save prompt or an autofill offer.
            if (isMainFrame) message.data?.let { host.onAutofillMessage(tab, sourceOrigin.toString(), it, reply) }
        }
        WebViewCompat.addDocumentStartJavaScript(view, AutofillScript.SOURCE, setOf("*"))
        WebViewCompat.addWebMessageListener(view, BlobDownloader.NAME, setOf("*")) { _, message, sourceOrigin, isMainFrame, _ ->
            if (isMainFrame) message.data?.let { host.onBlobMessage(tab, sourceOrigin.toString(), it) }
        }
    }

    /** Settings that do not depend on the current site; also re-run when the user changes a preference. */
    fun applyGlobal(view: EddyWebView, s: Settings) {
        WebView.setWebContentsDebuggingEnabled(s.webDebugging)
        // ponytail: WebView only darkens while its theme is dark, i.e. the system is in dark mode; the in-app Dark theme does not count.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(view.settings, s.darkenPages)
        }
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
        view.settings.textZoom = site?.textZoom ?: s.textZoom
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
        applyDesktopExtras(view, desktop)
        tab.desktopActive = desktop
        val thirdParty = site?.thirdPartyCookies?.let { it == SiteSettings.ALLOW } ?: (s.cookieMode == CookieMode.ALLOW_ALL)
        cookieManager(tab.incognito).setAcceptThirdPartyCookies(view, thirdParty && s.cookieMode != CookieMode.BLOCK_ALL)
        val blockingAllowed = site?.contentBlocking != SiteSettings.BLOCK
        tab.blockingActive = s.adBlock && blockingAllowed
        tab.trackerBlockingActive = s.trackerProtection && blockingAllowed
        applyCosmetic(view, tab.blockingActive)
        // Offline: prefer whatever the HTTP cache has instead of failing outright.
        view.settings.cacheMode = when {
            tab.incognito -> WebSettings.LOAD_NO_CACHE
            !isOnline() -> WebSettings.LOAD_CACHE_ELSE_NETWORK
            else -> WebSettings.LOAD_DEFAULT
        }
    }

    /** Request blocking removes the ad but leaves its empty slot behind; this hides the slot too. */
    @SuppressLint("RequiresFeature")
    private fun applyCosmetic(view: EddyWebView, active: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        if (active && view.cosmeticScript == null) {
            view.cosmeticScript = WebViewCompat.addDocumentStartJavaScript(view, COSMETIC_SCRIPT, setOf("*"))
        } else if (!active) {
            view.cosmeticScript?.remove()
            view.cosmeticScript = null
        }
    }

    /**
     * The UA string alone is not enough: sites also read client hints, and responsive pages lay out at
     * device width through their viewport meta tag. Desktop mode overrides both, like Chrome's does.
     */
    @SuppressLint("RequiresFeature")
    private fun applyDesktopExtras(view: EddyWebView, desktop: Boolean) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            if (mobileMeta == null) mobileMeta = WebSettingsCompat.getUserAgentMetadata(view.settings)
            (if (desktop) desktopMeta else mobileMeta)?.let { WebSettingsCompat.setUserAgentMetadata(view.settings, it) }
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        if (desktop && view.desktopScript == null) {
            view.desktopScript = WebViewCompat.addDocumentStartJavaScript(view, DESKTOP_VIEWPORT_SCRIPT, setOf("*"))
        } else if (!desktop) {
            view.desktopScript?.remove()
            view.desktopScript = null
        }
    }

    /**
     * Hosts the user chose to reach over plain http after an upgrade failed. Session-scoped on purpose:
     * an exception to HTTPS-only should not outlive the app.
     */
    private val insecureHosts = ConcurrentHashMap.newKeySet<String>()

    fun allowInsecure(host: String) { if (host.isNotEmpty()) insecureHosts.add(host) }

    /** Also covers the site's other hosts: neverssl.com hands http traffic to a random subdomain. */
    fun insecureAllowed(host: String): Boolean =
        host in insecureHosts || insecureHosts.any { host.endsWith(".$it") }

    /** The URL to actually load: https:// when HTTPS-only is on and this host has no exception. */
    fun upgrade(url: String): String =
        if (host.settings.httpsOnly && UrlUtils.isHttp(url) && !UrlUtils.isLocalHost(url) && !insecureAllowed(UrlUtils.host(url))) {
            UrlUtils.toHttps(url)
        } else {
            url
        }

    fun load(tab: BrowserTab, view: EddyWebView, requested: String) {
        val url = upgrade(requested)
        tab.httpsUpgradedFrom = if (url != requested) requested else null
        prepare(tab, view, url)
        view.settings.allowFileAccess = url.startsWith("file:", ignoreCase = true)
        val headers = if (host.settings.doNotTrack && UrlUtils.isWebUrl(url)) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()
        view.loadUrl(url, headers)
    }

    @SuppressLint("RequiresFeature")
    fun cookieManager(incognito: Boolean): CookieManager =
        if (incognito && incognitoIsolated) {
            ProfileStore.getInstance().getOrCreateProfile(incognitoProfile).cookieManager
        } else {
            CookieManager.getInstance()
        }

    /** Opens DNS/TCP/TLS to [url] before the user commits, so the navigation starts on a live connection. */
    fun preconnect(url: String, incognito: Boolean) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.PRECONNECT) || !UrlUtils.isWebUrl(url)) return
        runCatching { profile(incognito).preconnect(url) }
    }

    /**
     * Starts loading and rendering [url] in the background; if the user then navigates there, the page is
     * already painted. Never used in incognito tabs, where a speculative fetch would leak intent.
     */
    fun prerender(view: EddyWebView, url: String) {
        cancelPrerender(view)
        if (view.incognito || !WebViewFeature.isFeatureSupported(WebViewFeature.PRERENDER_WITH_URL) || !UrlUtils.isWebUrl(url)) return
        val signal = android.os.CancellationSignal()
        view.prerender = signal
        runCatching {
            WebViewCompat.prerenderUrlAsync(view, url, signal, mainExecutor, object : PrerenderOperationCallback {
                override fun onPrerenderActivated() {}
                override fun onError(e: androidx.webkit.PrerenderException) {}
            })
        }.onFailure { view.prerender = null }
    }

    fun cancelPrerender(view: EddyWebView) {
        view.prerender?.cancel()
        view.prerender = null
    }

    /** Keeps a renderer process ready so the next page does not pay for process start-up. */
    fun warmUp() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WARM_UP_RENDERER_PROCESS)) return
        runCatching { profile(false).warmUpRendererProcess() }
    }

    private fun profile(incognito: Boolean): Profile = ProfileStore.getInstance().getOrCreateProfile(
        if (incognito && incognitoIsolated) incognitoProfile else Profile.DEFAULT_PROFILE_NAME,
    )

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
        const val PROFILE_PREFIX = "eddy_incognito"
        private fun newProfileName() = PROFILE_PREFIX + "_" + java.util.UUID.randomUUID().toString().take(8)
        /** Rewrites viewport meta tags while the document parses, then stops watching. */
        private const val DESKTOP_VIEWPORT_SCRIPT =
            "(function(){if(window!==top)return;var W='width=1024';function f(){document.querySelectorAll('meta[name=viewport]')" +
                ".forEach(function(m){if(m.content!==W)m.content=W})}var o=new MutationObserver(f);" +
                "o.observe(document,{childList:true,subtree:true});document.addEventListener('DOMContentLoaded',function(){f();o.disconnect()})})()"
        /**
         * Generic ad-slot selectors, kept deliberately narrow: every one names advertising outright,
         * so a false positive hides only a slot the request blocker has already emptied.
         */
        private const val COSMETIC_CSS =
            ".adsbygoogle,ins.adsbygoogle,[id^=\"google_ads_iframe\"],[id^=\"div-gpt-ad\"],[id^=\"google_ads_\"]," +
                "iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication.com\"],iframe[src*=\"adnxs.com\"]," +
                "iframe[src*=\"amazon-adsystem.com\"],.ad-slot,.ad-banner,.ad-container,.ad-wrapper,.ad-placeholder," +
                ".adsbox,.advertisement,.advert-container,.sponsored-ad,.banner-ads,[data-ad-slot],[data-ad-client]," +
                "[aria-label=\"advertisement\" i]{display:none!important}"
        /** Injected before the document parses so slots never flash into view. */
        private const val COSMETIC_SCRIPT =
            "(function(){var s=document.createElement('style');s.textContent='" + COSMETIC_CSS + "';" +
                "(document.head||document.documentElement).appendChild(s)})()"
        private const val DNT_SCRIPT =
            "try{Object.defineProperty(navigator,'doNotTrack',{get:function(){return '1'}});" +
                "Object.defineProperty(navigator,'globalPrivacyControl',{get:function(){return true}})}catch(e){}"
    }
}
