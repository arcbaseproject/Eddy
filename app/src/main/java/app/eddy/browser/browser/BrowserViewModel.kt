package app.eddy.browser.browser

import android.Manifest
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.eddy.browser.EddyApp
import app.eddy.browser.data.database.LoginEntity
import app.eddy.browser.data.database.SiteSettings
import androidx.webkit.JavaScriptReplyProxy
import org.json.JSONObject
import app.eddy.browser.data.models.ExternalLinks
import app.eddy.browser.data.models.HomepageMode
import app.eddy.browser.data.models.SearchEngine
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.Shortcut
import app.eddy.browser.downloads.BlobDownloader
import app.eddy.browser.downloads.DownloadRequest
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.settings.SettingsPage
import app.eddy.browser.util.UrlUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class Screen { BROWSER, TABS, BOOKMARKS, HISTORY, DOWNLOADS, PASSWORDS, SETTINGS }

enum class DataType { HISTORY, COOKIES, CACHE, DOWNLOADS, SITE_SETTINGS, PASSWORDS }

/** A submitted login waiting to see whether the sign-in worked before the user is asked to save it. */
private class PendingLogin(val tabId: String, val origin: String, val username: String, val password: String, val at: Long)

/** "Save password?" / "Update password?" card. [existingId] is set when the login already exists with another password. */
class SaveLoginPrompt(val tabId: String, val origin: String, val username: String, val password: String, val existingId: Long?)

/** Saved logins for the focused field's site. Filling needs a tap, then goes through [reply] to that frame only. */
class AutofillOffer(val tabId: String, val origin: String, val logins: List<LoginEntity>, val reply: JavaScriptReplyProxy)

class FindState {
    var query by mutableStateOf("")
    var active by mutableIntStateOf(0)
    var total by mutableIntStateOf(0)
}

/**
 * Single source of truth for browser UI state. Web callbacks arrive through [BrowserHost]; everything
 * that needs an Activity (file pickers, runtime permissions, external apps) leaves through [effects].
 */
class BrowserViewModel(private val app: Application) : AndroidViewModel(app), BrowserHost {
    private val container = (app as EddyApp).container

    val settingsState: StateFlow<Settings> = container.settings
    override val settings: Settings get() = settingsState.value
    override val blocker = container.blocker
    override val sites = container.sites
    override val favicons = container.favicons
    override var pageBackground: Int = 0xFF000000.toInt()
        private set

    val history = container.history
    val passwords = container.passwords
    val downloadItems = container.database.downloads().all()
    val bookmarks = container.bookmarks
    val downloads = container.downloads
    val thumbnails = container.thumbnails
    val store = container.settingsStore
    val blockerRules = container.blocker.ruleCount
    val blockerUpdating = container.blocker.updating

    private val factory = WebViewFactory(app, this)
    val tabs = TabManager(app, viewModelScope, thumbnails, { factory }, { settings })
    val incognitoIsolated: Boolean get() = factory.incognitoIsolated
    val pageBridgeSupported: Boolean get() = factory.pageBridgeSupported

    // ---- UI state -------------------------------------------------------------------------
    var screen by mutableStateOf(Screen.BROWSER)
    var switcherIncognito by mutableStateOf(false)
    var menuVisible by mutableStateOf(false)
    var siteInfoVisible by mutableStateOf(false)
    var editing by mutableStateOf(false)
        private set
    var omniboxText by mutableStateOf("")
        private set
    var chromeVisible by mutableStateOf(true)
        private set
    var find by mutableStateOf<FindState?>(null)
        private set
    var linkTarget by mutableStateOf<HitTarget?>(null)
    var customViewActive by mutableStateOf(false)
        private set
    val prompts = mutableStateListOf<Prompt>()
    var savePrompt by mutableStateOf<SaveLoginPrompt?>(null)
        private set
    var autofillOffer by mutableStateOf<AutofillOffer?>(null)
        private set
    /** True after the user passed the screen lock for this visit to the password manager. */
    var passwordsUnlocked by mutableStateOf(false)
    private var pendingLogin: PendingLogin? = null
    /** Set when the app was opened from a link, so the welcome tour never sits in front of the page the user asked for. */
    var onboardingSuppressed by mutableStateOf(false)
        private set
    val settingsStack = mutableStateListOf<SettingsPage>()

    val suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val effects = Channel<UiEffect>(Channel.UNLIMITED)
    val effectFlow = effects.receiveAsFlow()
    private val snackbarChannel = Channel<SnackbarMessage>(Channel.BUFFERED)
    val snackbars = snackbarChannel.receiveAsFlow()

    val isBookmarked: StateFlow<Boolean> = snapshotFlow { tabs.selected?.url.orEmpty() }
        .distinctUntilChanged()
        .flatMapLatest { if (UrlUtils.isWebUrl(it)) bookmarks.isBookmarked(it) else MutableStateFlow(false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val suggestionSource = SuggestionSource(history, bookmarks) { settings }
    private val permissions = WebPermissionCoordinator(
        app, viewModelScope, sites,
        showPrompt = { prompts.add(it) },
        requestRuntime = ::requestRuntimePermissions,
    )
    private val ready = CompletableDeferred<Unit>()
    private var suggestJob: Job? = null
    private var scrollAccum = 0
    private var ignoreScrollUntil = 0L

    init {
        viewModelScope.launch {
            if (!(settings.restoreTabs && tabs.restore())) openHomeTab()
            // Leftovers from a session that could not be deleted while the app was running.
            tabs.sweepIncognitoProfiles()
            ready.complete(Unit)
        }
        viewModelScope.launch {
            settingsState.collect { s ->
                tabs.tabs.forEach { t -> t.webView?.let { factory.applyGlobal(it, s); factory.prepare(t, it, t.url) } }
            }
        }
        viewModelScope.launch {
            settingsState.map { it.filterLists }.distinctUntilChanged().collect { blocker.reload(it) }
        }
        viewModelScope.launch {
            snapshotFlow { tabs.selected?.url }.distinctUntilChanged().collect { chromeVisible = true; autofillOffer = null }
        }
        viewModelScope.launch {
            snapshotFlow { tabs.tabs.any { it.incognito } }.distinctUntilChanged()
                .collect { any -> if (!any) downloads.clearIncognito() }
        }
    }

    /** Shows the welcome tour again, even in a session that started from a link. */
    fun replayOnboarding() {
        onboardingSuppressed = false
        screen = Screen.BROWSER
        launchSettings { it.copy(onboardingCompleted = false) }
    }

    fun launchSettings(block: (Settings) -> Settings) { viewModelScope.launch { store.update(block) } }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            viewModelScope.launch {
                // Only pages that failed because we were offline are retried automatically.
                tabs.tabs.filter { it.error?.kind == ErrorKind.OFFLINE && it.id == tabs.selectedId }.forEach { retry() }
            }
        }
    }

    init {
        app.getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
    }

    fun setPageBackground(color: Int) { pageBackground = color }

    // ---- navigation -----------------------------------------------------------------------

    private fun openHomeTab(incognito: Boolean = false): BrowserTab {
        val s = settings
        val custom = s.homepageMode == HomepageMode.CUSTOM && s.homepageUrl.isNotBlank()
        return tabs.newTab(url = if (custom) UrlUtils.resolve(s.homepageUrl, s.searchEngine) else "", incognito = incognito)
    }

    fun newTab(incognito: Boolean = false) {
        openHomeTab(incognito)
        screen = Screen.BROWSER
        stopEditing()
        menuVisible = false
    }

    fun navigate(url: String) {
        val tab = tabs.selected ?: openHomeTab()
        tabs.load(tab, url)
        screen = Screen.BROWSER
        stopEditing()
        chromeVisible = true
    }

    fun openInNewTab(url: String, incognito: Boolean = false, select: Boolean = true) {
        val opener = tabs.selected
        tabs.newTab(url, incognito || opener?.incognito == true, select, opener)
        if (select) screen = Screen.BROWSER else snackbar("Opened in a new tab", "Switch") {
            tabs.tabs.lastOrNull { it.url == url }?.let(tabs::select)
        }
    }

    fun submit(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return stopEditing()
        navigate(UrlUtils.resolve(q, settings.searchEngine))
    }

    fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_WEB_SEARCH) onboardingSuppressed = true
        viewModelScope.launch {
            ready.await()
            val text = when (intent.action) {
                Intent.ACTION_VIEW -> intent.dataString
                Intent.ACTION_WEB_SEARCH -> intent.getStringExtra("query")
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                MAIN_OPEN_DOWNLOADS -> { screen = Screen.DOWNLOADS; null }
                else -> null
            }?.trim().orEmpty()
            if (text.isEmpty()) return@launch
            val url = UrlUtils.resolve(text, settings.searchEngine)
            val current = tabs.selected
            if (current != null && current.isHome && !current.incognito) navigate(url) else openInNewTab(url)
        }
    }

    fun goBack() {
        val tab = tabs.selected ?: return
        val view = tab.webView
        when {
            view != null && view.canGoBack() -> { tab.error?.sslHandler?.cancel(); tab.error = null; view.goBack() }
            !tab.isHome -> tabs.showHome(tab)
        }
    }

    fun goForward() { tabs.selected?.webView?.takeIf { it.canGoForward() }?.goForward() }

    fun reloadOrStop() {
        val tab = tabs.selected ?: return
        val view = tab.webView
        val err = tab.error
        when {
            err != null -> retry()
            tab.isLoading -> view?.stopLoading()
            else -> view?.reload()
        }
    }

    fun retry() {
        val tab = tabs.selected ?: return
        val url = tab.error?.url ?: tab.url
        tab.error?.sslHandler?.cancel()
        tabs.load(tab, url)
    }

    /** Explicit user consent for a bad certificate: the WebView keeps the handler while the error page is shown. */
    fun proceedDespiteSslError() {
        val tab = tabs.selected ?: return
        val handler = tab.error?.sslHandler ?: return
        tab.error = null
        handler.proceed()
    }

    fun cancelSslError() {
        val tab = tabs.selected ?: return
        tab.error?.sslHandler?.cancel()
        if (tab.canGoBack) goBack() else tabs.showHome(tab)
    }

    fun goHome() { tabs.selected?.let(tabs::showHome) }

    /** Back-gesture priority chain; returns false when nothing is left to undo (the app may then exit). */
    fun onBack(): Boolean {
        val tab = tabs.selected
        return when {
            customViewActive -> { effects.trySend(UiEffect.HideCustomView); true }
            linkTarget != null -> { linkTarget = null; true }
            siteInfoVisible -> { siteInfoVisible = false; true }
            menuVisible -> { menuVisible = false; true }
            find != null -> { closeFind(); true }
            editing -> { stopEditing(); true }
            screen == Screen.SETTINGS && settingsStack.isNotEmpty() -> { settingsStack.removeAt(settingsStack.lastIndex); true }
            screen != Screen.BROWSER -> { screen = Screen.BROWSER; true }
            tab == null -> false
            tab.canGoBack || !tab.isHome -> { goBack(); true }
            tab.openerId != null && tabs.tabs.any { it.id == tab.openerId } -> { tabs.close(tab, remember = false); true }
            else -> false
        }
    }

    val canHandleBack: Boolean
        get() {
            val tab = tabs.selected
            return customViewActive || linkTarget != null || siteInfoVisible || menuVisible || find != null || editing ||
                screen != Screen.BROWSER || tab != null && (tab.canGoBack || !tab.isHome || tab.openerId != null)
        }

    // ---- omnibox --------------------------------------------------------------------------

    fun startEditing() {
        val tab = tabs.selected
        omniboxText = tab?.url?.takeIf { UrlUtils.isWebUrl(it) }.orEmpty()
        editing = true
        chromeVisible = true
        suggestions.value = emptyList()
        loadClipboardSuggestion()
    }

    fun stopEditing() {
        editing = false
        suggestJob?.cancel()
        suggestions.value = emptyList()
    }

    fun onOmniboxText(text: String) {
        omniboxText = text
        suggestJob?.cancel()
        suggestJob = viewModelScope.launch {
            if (text.isBlank()) {
                suggestions.value = suggestions.value.filter { it.kind == SuggestionKind.CLIPBOARD }
                return@launch
            }
            // Wait for a typing pause and swap the list once. The old list stays until the new one is ready, because
            // redrawing it on every keystroke made the field jump under the user's fingers.
            delay(SUGGEST_DEBOUNCE_MS)
            val local = suggestionSource.local(text)
            val private = tabs.selected?.incognito == true
            val remote = if (settings.searchSuggestions && !private && !UrlUtils.isUrl(text)) suggestionSource.remote(text) else emptyList()
            suggestions.value = local + remote
        }
    }

    private fun loadClipboardSuggestion() {
        val cm = app.getSystemService(ClipboardManager::class.java)
        val desc = cm.primaryClipDescription ?: return
        if (!desc.hasMimeType("text/plain") && !desc.hasMimeType("text/uri-list")) return
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() }.getOrNull()?.trim().orEmpty()
        if (text.isEmpty() || text.length > 2048 || text == tabs.selected?.url) return
        suggestions.value = listOf(Suggestion(SuggestionKind.CLIPBOARD, text, text, if (UrlUtils.isUrl(text)) "Link from clipboard" else "Text from clipboard"))
    }

    // ---- chrome & scrolling ---------------------------------------------------------------

    override fun onScrolled(tab: BrowserTab, dy: Int, scrollY: Int) {
        if (tab.id != tabs.selectedId || editing || find != null) return
        if (System.currentTimeMillis() < ignoreScrollUntil) return
        if (scrollY <= 8) { setChrome(true); scrollAccum = 0; return }
        if (dy == 0) return
        // Restart the accumulator when the direction flips so small jitters never toggle the chrome.
        if (dy > 0 != scrollAccum > 0) scrollAccum = 0
        scrollAccum += dy
        if (scrollAccum > SCROLL_THRESHOLD) setChrome(false) else if (scrollAccum < -SCROLL_THRESHOLD) setChrome(true)
    }

    private fun setChrome(visible: Boolean) {
        if (chromeVisible == visible) return
        chromeVisible = visible
        scrollAccum = 0
        ignoreScrollUntil = System.currentTimeMillis() + 450
    }

    fun showChrome() = setChrome(true)

    // ---- find in page ---------------------------------------------------------------------

    fun openFind() { find = FindState(); menuVisible = false; chromeVisible = true }

    fun findQuery(q: String) {
        val state = find ?: return
        state.query = q
        val view = tabs.selected?.webView ?: return
        if (q.isEmpty()) { view.clearMatches(); state.active = 0; state.total = 0 } else view.findAllAsync(q)
    }

    fun findNext(forward: Boolean) { tabs.selected?.webView?.findNext(forward) }

    fun closeFind() {
        tabs.selected?.webView?.clearMatches()
        find = null
    }

    override fun onFindResult(tab: BrowserTab, active: Int, total: Int) {
        find?.let { it.active = if (total == 0) 0 else active + 1; it.total = total }
    }

    // ---- tabs -----------------------------------------------------------------------------

    fun openTabSwitcher() {
        tabs.selected?.let { thumbnails.capture(it) }
        switcherIncognito = tabs.selected?.incognito == true
        stopEditing()
        menuVisible = false
        screen = Screen.TABS
    }

    fun selectTab(tab: BrowserTab) { tabs.select(tab); screen = Screen.BROWSER }

    fun closeTab(tab: BrowserTab) {
        val title = tab.displayTitle("Tab")
        val remembered = !tab.incognito && !tab.isHome
        val wasLast = tabs.tabs.count { it.incognito == tab.incognito } == 1
        tabs.close(tab)
        // Closing the last tab lands on the new-tab page instead of leaving an empty switcher.
        if (wasLast) screen = Screen.BROWSER
        if (remembered) snackbar("Closed $title", "Undo") { restoreClosedTab() }
    }

    fun restoreClosedTab() {
        if (tabs.restoreClosed() != null) screen = Screen.BROWSER
    }

    fun closeAllTabs(incognito: Boolean) {
        tabs.closeAll(incognito)
        if (tabs.tabs.none { it.incognito == switcherIncognito }) switcherIncognito = false
        // Leaving an empty switcher would be a dead end, so land on a fresh page.
        if (tabs.tabs.none { !it.incognito }) tabs.newTab()
        screen = Screen.BROWSER
    }

    // ---- page actions ---------------------------------------------------------------------

    fun toggleBookmark() {
        val tab = tabs.selected ?: return
        if (!UrlUtils.isWebUrl(tab.url)) return
        viewModelScope.launch {
            val added = bookmarks.toggle(tab.url, tab.title)
            snackbar(if (added) "Bookmark saved" else "Bookmark removed")
        }
    }

    fun share(url: String? = null) {
        val target = url ?: tabs.selected?.url?.takeIf { UrlUtils.isWebUrl(it) } ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, target)
        effects.trySend(UiEffect.Launch(Intent.createChooser(send, null)))
    }

    fun toggleDesktop() {
        val tab = tabs.selected ?: return
        val view = tab.webView ?: return
        tab.desktopOverride = !tab.desktopActive
        // A fresh load, not reload(): reload restores the old page scale, so desktop layout would not zoom out to fit.
        factory.load(tab, view, tab.url)
    }

    fun toggleDesktopForSite() {
        val tab = tabs.selected ?: return
        val host = UrlUtils.displayHost(tab.url)
        val on = sites.peek(host)?.desktop == SiteSettings.ALLOW
        sites.set(host, SiteFeature.DESKTOP, if (on) null else SiteSettings.ALLOW)
        tab.desktopOverride = null
        tab.webView?.let { factory.load(tab, it, tab.url) }
    }

    fun addShortcut(tab: BrowserTab) {
        if (!UrlUtils.isWebUrl(tab.url)) return
        viewModelScope.launch {
            store.update { s ->
                val n = s.shortcuts.size
                s.copy(shortcuts = s.shortcuts + Shortcut(UUID.randomUUID().toString(), tab.displayTitle(tab.host), tab.url, n, n))
            }
            snackbar("Added to home shortcuts")
        }
    }

    fun setSiteSetting(tab: BrowserTab, feature: SiteFeature, value: Int?) {
        val host = UrlUtils.displayHost(tab.url)
        sites.set(host, feature, value)
        tab.webView?.let { factory.prepare(tab, it, tab.url) }
        if (feature == SiteFeature.JAVASCRIPT || feature == SiteFeature.CONTENT_BLOCKING || feature == SiteFeature.THIRD_PARTY_COOKIES) {
            tab.webView?.reload()
        }
    }

    fun cookieCount(tab: BrowserTab): Int =
        factory.cookieManager(tab.incognito).getCookie(tab.url)?.split(';')?.count { it.isNotBlank() } ?: 0

    fun clearSiteData(tab: BrowserTab) {
        val cm = factory.cookieManager(tab.incognito)
        val url = tab.url
        cm.getCookie(url)?.split(';')?.forEach { pair ->
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty()) cm.setCookie(url, "$name=; Max-Age=0; Path=/")
        }
        cm.flush()
        val origin = Regex("^https?://[^/]+").find(url)?.value
        if (origin != null && !tab.incognito) WebStorage.getInstance().deleteOrigin(origin)
        tab.webView?.reload()
        snackbar("Site data cleared")
    }

    fun clearBrowsingData(types: Set<DataType>, historyRangeMillis: Long?) {
        viewModelScope.launch {
            if (DataType.HISTORY in types) if (historyRangeMillis == null) history.clear() else history.deleteSince(historyRangeMillis)
            if (DataType.COOKIES in types) {
                val cm = android.webkit.CookieManager.getInstance()
                cm.removeAllCookies(null)
                cm.flush()
                WebStorage.getInstance().deleteAllData()
            }
            if (DataType.CACHE in types) {
                val view = tabs.selected?.webView?.takeIf { !it.incognito }
                if (view != null) view.clearCache(true) else WebView(app).apply { clearCache(true); destroy() }
                container.favicons.clear()
                thumbnails.trim()
            }
            if (DataType.DOWNLOADS in types) downloads.clearFinished()
            if (DataType.SITE_SETTINGS in types) sites.resetAll()
            if (DataType.PASSWORDS in types) passwords.deleteAll()
            snackbar("Browsing data cleared")
        }
    }

    fun openFile(uri: String, mime: String) { effects.trySend(UiEffect.OpenFile(uri, mime)) }

    fun snackbar(text: String, action: String? = null, block: (() -> Unit)? = null) {
        snackbarChannel.trySend(SnackbarMessage(text, action, block))
    }

    // ---- passwords ------------------------------------------------------------------------

    override fun onAutofillMessage(tab: BrowserTab, origin: String, data: String, reply: JavaScriptReplyProxy) {
        if (!origin.startsWith("http://") && !origin.startsWith("https://")) return
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        when (json.optString("t")) {
            "submit" -> {
                val password = json.optString("p")
                if (!settings.savePasswords || tab.incognito || password.isEmpty() || password.length > MAX_PASSWORD_LENGTH) return
                pendingLogin = PendingLogin(tab.id, origin, json.optString("u").take(MAX_USERNAME_LENGTH), password, System.currentTimeMillis())
                viewModelScope.launch {
                    // Single-page apps never reload, so check once the request has had time to finish.
                    delay(PENDING_CHECK_MS)
                    resolvePendingLogin(tab, fromTimer = true)
                }
            }
            "focus" -> if (settings.autofillPasswords) viewModelScope.launch {
                val logins = passwords.forOrigin(origin)
                autofillOffer = if (logins.isEmpty()) null else AutofillOffer(tab.id, origin, logins, reply)
            }
        }
    }

    /**
     * A sign-in worked when the page that follows it no longer shows a password field. Only then is the user asked,
     * so a mistyped password is never offered for saving.
     */
    private fun resolvePendingLogin(tab: BrowserTab, fromTimer: Boolean) {
        val pending = pendingLogin ?: return
        if (pending.tabId != tab.id) return
        if (System.currentTimeMillis() - pending.at > PENDING_EXPIRY_MS) { pendingLogin = null; return }
        // A navigation is in flight; onPageFinished resolves it.
        if (fromTimer && tab.isLoading) return
        val view = tab.webView ?: return
        pendingLogin = null
        view.evaluateJavascript(AutofillScript.PASSWORD_FIELD_VISIBLE) { result ->
            if (result == "false") viewModelScope.launch { offerToSave(pending) }
        }
    }

    private suspend fun offerToSave(p: PendingLogin) {
        if (passwords.isBlocked(p.origin)) return
        val existing = passwords.find(p.origin, p.username)
        if (existing != null && passwords.decrypt(existing) == p.password) {
            passwords.touch(existing.id)
            return
        }
        savePrompt = SaveLoginPrompt(p.tabId, p.origin, p.username, p.password, existing?.id)
    }

    fun confirmSaveLogin() {
        val p = savePrompt ?: return
        savePrompt = null
        viewModelScope.launch {
            passwords.save(p.origin, p.username, p.password)
            snackbar(if (p.existingId != null) "Password updated" else "Password saved", "View") { screen = Screen.PASSWORDS }
        }
    }

    fun neverSaveLogin() {
        val p = savePrompt ?: return
        savePrompt = null
        passwords.neverSave(p.origin)
        snackbar("Eddy will not offer to save passwords for ${UrlUtils.displayHost(p.origin)}")
    }

    fun dismissSaveLogin() { savePrompt = null }

    fun fillLogin(offer: AutofillOffer, login: LoginEntity) {
        autofillOffer = null
        viewModelScope.launch {
            val password = passwords.decrypt(login) ?: return@launch snackbar("Eddy could not decrypt this password")
            offer.reply.postMessage(JSONObject().put("u", login.username).put("p", password).toString())
            passwords.touch(login.id)
        }
    }

    /** Copies a secret and clears it from the clipboard after a minute if nothing else replaced it. */
    fun copySecret(label: String, secret: String) {
        val clipboard = app.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText(label, secret).apply {
            description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        clipboard.setPrimaryClip(clip)
        snackbar("$label copied")
        viewModelScope.launch {
            delay(CLIPBOARD_CLEAR_MS)
            val current = runCatching { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull()
            if (current == secret) clipboard.clearPrimaryClip()
        }
    }

    // ---- prompts --------------------------------------------------------------------------

    fun dismissPrompt(prompt: Prompt) { prompts.remove(prompt) }

    fun answerPermission(prompt: Prompt.Permission, allow: Boolean, remember: Boolean) {
        prompt.answer.complete(PermissionAnswer(allow, remember))
        prompts.remove(prompt)
    }

    fun answerAuth(prompt: Prompt.HttpAuth, user: String?, password: String?) {
        if (user == null) prompt.handler.cancel() else prompt.handler.proceed(user, password.orEmpty())
        prompts.remove(prompt)
    }

    fun answerExternal(prompt: Prompt.ExternalApp, open: Boolean) {
        prompts.remove(prompt)
        if (open) effects.trySend(UiEffect.Launch(prompt.intent)) else prompt.fallbackUrl?.let(::navigate)
    }

    private suspend fun requestRuntimePermissions(perms: List<String>): Map<String, Boolean> {
        val result = CompletableDeferred<Map<String, Boolean>>()
        effects.send(UiEffect.RequestPermissions(perms, result))
        return result.await()
    }

    // ---- BrowserHost ----------------------------------------------------------------------

    override fun onPageFinished(tab: BrowserTab) {
        val url = tab.url
        val now = System.currentTimeMillis()
        if (!tab.incognito && UrlUtils.isWebUrl(url) && !(tab.lastHistoryUrl == url && now - tab.lastHistoryAt < 5000)) {
            tab.lastHistoryUrl = url
            tab.lastHistoryAt = now
            viewModelScope.launch { tab.lastHistoryId = history.record(url, tab.title) }
        }
        resolvePendingLogin(tab, fromTimer = false)
        viewModelScope.launch {
            delay(700) // let the page paint before taking the preview
            if (tab.id == tabs.selectedId && screen == Screen.BROWSER) thumbnails.capture(tab)
        }
        tabs.requestPersist()
    }

    override fun onTitleChanged(tab: BrowserTab) {
        if (!tab.incognito && tab.lastHistoryUrl == tab.url) history.updateTitle(tab.lastHistoryId, tab.title)
    }

    override fun handleExternalIntent(tab: BrowserTab, intent: Intent, userGesture: Boolean, fallbackUrl: String?) {
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        if (!userGesture || settings.externalLinks == ExternalLinks.NEVER) {
            // Redirects that no one asked for never leave the browser.
            if (fallbackUrl != null && UrlUtils.isWebUrl(fallbackUrl)) navigate(fallbackUrl)
            else if (userGesture) snackbar("Opening other apps is turned off")
            return
        }
        if (settings.externalLinks == ExternalLinks.ALWAYS) effects.trySend(UiEffect.Launch(intent))
        else prompts.add(Prompt.ExternalApp(null, intent, fallbackUrl))
    }

    override fun requestFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean =
        effects.trySend(UiEffect.ChooseFile(callback, params)).isSuccess

    override fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        customViewActive = true
        effects.trySend(UiEffect.ShowCustomView(view, callback))
    }

    override fun hideCustomView() {
        customViewActive = false
        effects.trySend(UiEffect.HideCustomView)
    }

    fun onCustomViewClosed() { customViewActive = false }

    override fun requestWebPermission(tab: BrowserTab, request: PermissionRequest) = permissions.onWebRequest(request, tab.incognito)

    override fun requestGeolocation(tab: BrowserTab, origin: String, callback: GeolocationPermissions.Callback) =
        permissions.onGeolocation(origin, callback, tab.incognito)

    override fun requestHttpAuth(tab: BrowserTab, handler: HttpAuthHandler, host: String, realm: String) {
        prompts.add(Prompt.HttpAuth(host, realm, handler))
    }

    override fun createPopup(parent: BrowserTab, resultMsg: Message, userGesture: Boolean): Boolean {
        val host = UrlUtils.displayHost(parent.url)
        val policy = sites.peek(host)?.popups
        val allow = policy == SiteSettings.ALLOW || (policy != SiteSettings.BLOCK && userGesture)
        if (!allow) {
            snackbar("Popup blocked", "Allow") { sites.set(host, SiteFeature.POPUPS, SiteSettings.ALLOW) }
            return false
        }
        screen = Screen.BROWSER
        return tabs.createPopup(parent, resultMsg)
    }

    override fun closeWindow(view: EddyWebView) {
        tabs.tabFor(view)?.let { tabs.close(it, remember = false) }
    }

    private val blobDownloader = BlobDownloader(app, viewModelScope, downloads) { message, id ->
        snackbar(message, if (id != null) "View" else null) { screen = Screen.DOWNLOADS }
    }

    override fun onBlobMessage(tab: BrowserTab, origin: String, data: String) = blobDownloader.onMessage(tab, origin, data)

    override fun startDownload(tab: BrowserTab, url: String, userAgent: String, contentDisposition: String, mime: String, length: Long) {
        if (url.startsWith("blob:")) {
            if (!pageBridgeSupported) return snackbar("This WebView is too old to save this download. Update Android System WebView.")
            return blobDownloader.start(tab, url, contentDisposition, mime)
        }
        if (url.startsWith("data:") && contentDisposition.isBlank() && tab.webView != null) {
            // Ask the page which name the link requested; WebView drops it for data: URLs.
            tab.webView?.evaluateJavascript("(window.__eddyDownloadName||'')") { raw ->
                val name = raw?.trim('"').orEmpty()
                startDownload(tab, url, userAgent, if (name.isNotEmpty()) "attachment; filename=\"$name\"" else "attachment", mime, length)
            }
            return
        }
        viewModelScope.launch {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) requestRuntimePermissions(listOf(Manifest.permission.POST_NOTIFICATIONS))
            val cookie = runCatching { factory.cookieManager(tab.incognito).getCookie(url) }.getOrNull().orEmpty()
            runCatching {
                downloads.enqueue(DownloadRequest(url, userAgent, tab.url, contentDisposition, mime, length, tab.incognito, cookie))
            }
                .onSuccess {
                    // A tab that only existed to fetch this file (nothing rendered, no history) is not worth keeping.
                    if (!tab.canGoBack && tab.title.isEmpty() && tab.error == null && tabs.tabs.size > 1) tabs.close(tab, remember = false)
                    snackbar("Download started", "View") { screen = Screen.DOWNLOADS }
                }
                .onFailure { snackbar("Could not start download") }
        }
    }

    override fun onLongPress(tab: BrowserTab, target: HitTarget) { linkTarget = target }

    override fun onRenderGone(tab: BrowserTab) = tabs.discardView(tab)

    // ---- lifecycle ------------------------------------------------------------------------

    fun onStop() {
        passwordsUnlocked = false
        tabs.persistNow()
    }

    override fun onCleared() {
        runCatching { app.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        tabs.destroyAll()
        super.onCleared()
    }

    val engine: SearchEngine get() = settings.searchEngine

    companion object {
        private const val SUGGEST_DEBOUNCE_MS = 200L
        const val MAIN_OPEN_DOWNLOADS = "app.eddy.browser.OPEN_DOWNLOADS"
        private const val SCROLL_THRESHOLD = 36
        private const val PENDING_CHECK_MS = 2500L
        private const val PENDING_EXPIRY_MS = 30_000L
        private const val CLIPBOARD_CLEAR_MS = 60_000L
        private const val MAX_PASSWORD_LENGTH = 256
        private const val MAX_USERNAME_LENGTH = 256
    }
}
