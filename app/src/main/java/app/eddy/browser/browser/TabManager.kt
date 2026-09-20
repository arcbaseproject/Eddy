package app.eddy.browser.browser

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcel
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.ProfileStore
import app.eddy.browser.data.models.CloseTabBehavior
import app.eddy.browser.data.models.Settings
import app.eddy.browser.tabs.TabThumbnailManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ClosedTab(val url: String, val title: String, val state: Bundle?)

/**
 * Owns every tab and decides which ones keep a live WebView. Only a few WebViews stay alive; the
 * rest are serialised (history included) and rebuilt on demand, which keeps RAM flat with many tabs.
 */
class TabManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val thumbnails: TabThumbnailManager,
    private val factory: () -> WebViewFactory,
    private val settings: () -> Settings,
) {
    val tabs = mutableStateListOf<BrowserTab>()
    var selectedId by mutableStateOf<String?>(null)
        private set
    var closedCount by mutableIntStateOf(0)
        private set

    val selected: BrowserTab? get() = tabs.firstOrNull { it.id == selectedId }

    private val closed = ArrayDeque<ClosedTab>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateDir = File(context.filesDir, "tabstate").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "tabs.json")
    private var persistJob: Job? = null

    /** One thread for all tab-state files so a write always lands before the read that follows it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val stateIo = Dispatchers.IO.limitedParallelism(1)

    // ---- creation / selection -------------------------------------------------------------

    fun newTab(url: String = "", incognito: Boolean = false, select: Boolean = true, opener: BrowserTab? = null): BrowserTab {
        val tab = BrowserTab(incognito = incognito, initialUrl = url).also { it.openerId = opener?.id }
        val at = opener?.let { tabs.indexOf(it) + 1 }?.takeIf { it > 0 } ?: tabs.size
        tabs.add(at, tab)
        // Build the view before selecting: select() may start ensureWebView() synchronously and would create a second one.
        if (url.isNotEmpty()) tab.webView = createView(tab, null)
        if (select) select(tab)
        requestPersist()
        return tab
    }

    fun select(tab: BrowserTab) {
        val previous = selected
        if (previous != null && previous !== tab) {
            thumbnails.capture(previous)
            previous.webView?.onPause()
        }
        selectedId = tab.id
        tab.lastActive = System.currentTimeMillis()
        tab.webView?.onResume()
        if (tab.webView == null && !tab.isHome) scope.launch { ensureWebView(tab) }
        evictInactive(MAX_LIVE)
        requestPersist()
    }

    /** Builds the WebView for a restored/evicted tab, reading its serialised history off the main thread. */
    suspend fun ensureWebView(tab: BrowserTab) {
        if (tab.webView != null || tab.isHome) return
        val state = tab.savedState ?: if (tab.hasPersistedState) withContext(stateIo) { readState(tab.id) } else null
        if (tab.webView != null) return
        tab.webView = createView(tab, state)
        tab.savedState = null
        if (tab.id == selectedId) tab.webView?.onResume()
    }

    private fun createView(tab: BrowserTab, state: Bundle?, loadUrl: Boolean = true): EddyWebView {
        val view = factory().create(tab)
        val restored = state != null && view.restoreState(state) != null
        if (!restored && loadUrl && tab.url.isNotEmpty()) factory().load(tab, view, tab.url)
        return view
    }

    fun load(tab: BrowserTab, url: String) {
        tab.error?.sslHandler?.cancel()
        tab.error = null
        tab.url = url
        tab.title = ""
        tab.isLoading = true
        val view = tab.webView ?: createView(tab, null, loadUrl = false).also { tab.webView = it }
        factory().load(tab, view, url)
        requestPersist()
    }

    /** Returns a tab to the start page, releasing its WebView. */
    fun showHome(tab: BrowserTab) {
        destroyView(tab)
        tab.error?.sslHandler?.cancel()
        tab.error = null
        tab.url = ""
        tab.title = ""
        tab.favicon = null
        tab.isLoading = false
        tab.progress = 0
        tab.canGoBack = false
        tab.canGoForward = false
        tab.blockedCount = 0
        tab.hasPersistedState = false
        tab.savedState = null
        requestPersist()
    }

    fun createPopup(parent: BrowserTab, resultMsg: Message): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val tab = BrowserTab(incognito = parent.incognito, initialUrl = "about:blank").also { it.openerId = parent.id }
        tabs.add(tabs.indexOf(parent) + 1, tab)
        val view = factory().create(tab)
        tab.webView = view
        transport.webView = view
        resultMsg.sendToTarget()
        select(tab)
        return true
    }

    /** The renderer died: drop the WebView and show a recoverable error instead of a blank page. */
    fun discardView(tab: BrowserTab) {
        destroyView(tab)
        tab.isLoading = false
        tab.error = PageError(ErrorKind.UNAVAILABLE, tab.url, "The page stopped responding.")
    }

    fun destroyAll() = tabs.forEach(::destroyView)

    fun tabFor(view: WebView): BrowserTab? = tabs.firstOrNull { it.webView === view }

    // ---- closing --------------------------------------------------------------------------

    fun close(tab: BrowserTab, remember: Boolean = true) {
        val index = tabs.indexOf(tab)
        if (index < 0) return
        if (remember && !tab.incognito && !tab.isHome) rememberClosed(tab)
        val wasSelected = tab.id == selectedId
        tabs.removeAt(index)
        destroyView(tab)
        tab.error?.sslHandler?.cancel()
        thumbnails.remove(tab.id)
        File(stateDir, "${tab.id}.bin").delete()
        if (wasSelected) selectAfterClose(index, tab)
        if (tab.incognito && tabs.none { it.incognito }) endIncognitoSession()
        // There is always at least one normal tab; when the last one goes, a fresh new-tab page takes its place.
        if (tabs.none { !it.incognito }) newTab(select = wasSelected || selected == null || selected?.incognito != true)
        requestPersist()
    }

    fun closeAll(incognito: Boolean) {
        tabs.filter { it.incognito == incognito }.forEach { close(it, remember = false) }
    }

    fun restoreClosed(): BrowserTab? {
        val c = closed.removeFirstOrNull() ?: return null
        closedCount = closed.size
        val tab = BrowserTab(initialUrl = c.url, initialTitle = c.title)
        tab.savedState = c.state
        tabs.add(tab)
        select(tab)
        scope.launch { ensureWebView(tab) }
        return tab
    }

    private fun rememberClosed(tab: BrowserTab) {
        val state = tab.webView?.let { v -> Bundle().also { v.saveState(it) } } ?: tab.savedState
            ?: if (tab.hasPersistedState) readState(tab.id) else null
        closed.addFirst(ClosedTab(tab.url, tab.title, state))
        while (closed.size > MAX_CLOSED) closed.removeLast()
        closedCount = closed.size
    }

    private fun selectAfterClose(index: Int, closedTab: BrowserTab) {
        val sameMode = tabs.filter { it.incognito == closedTab.incognito }
        val next = tabs.firstOrNull { it.id == closedTab.openerId }
            ?: when (settings().closeTabBehavior) {
                CloseTabBehavior.RECENT -> sameMode.maxByOrNull { it.lastActive }
                CloseTabBehavior.ADJACENT -> tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.takeIf { it.incognito == closedTab.incognito }
                    ?: sameMode.lastOrNull()
            }
            ?: tabs.maxByOrNull { it.lastActive }
        if (next != null) select(next) else selectedId = null
    }

    /** With profile isolation the whole incognito cookie jar and storage disappears with the profile. */
    @android.annotation.SuppressLint("RequiresFeature")
    private fun endIncognitoSession(attempt: Int = 0) {
        if (!factory().incognitoIsolated) return
        try {
            ProfileStore.getInstance().deleteProfile(WebViewFactory.INCOGNITO_PROFILE)
        } catch (e: IllegalStateException) {
            // A WebView using the profile is still being torn down; try again shortly.
            if (attempt < 3) mainHandler.postDelayed({ if (tabs.none { it.incognito }) endIncognitoSession(attempt + 1) }, 600)
        } catch (_: RuntimeException) {
        }
    }

    // ---- memory ---------------------------------------------------------------------------

    fun evictInactive(keep: Int) {
        tabs.filter { it.webView != null && it.id != selectedId }
            .sortedByDescending { it.lastActive }
            .drop((keep - 1).coerceAtLeast(0))
            .forEach(::evict)
    }

    private fun evict(tab: BrowserTab) {
        val view = tab.webView ?: return
        val state = Bundle().also { view.saveState(it) }
        if (tab.incognito) tab.savedState = state else {
            writeState(tab.id, state)
            tab.hasPersistedState = true
        }
        destroyView(tab)
    }

    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) evictInactive(1)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) thumbnails.trim()
    }

    private fun destroyView(tab: BrowserTab) {
        val view = tab.webView ?: return
        tab.webView = null
        view.onScrolled = null
        view.onPull = null
        view.onPullRelease = null
        (view.parent as? ViewGroup)?.removeView(view)
        view.stopLoading()
        view.destroy()
    }

    fun onActivityDestroyed() = tabs.forEach { it.webView?.detachFromActivity(context.applicationContext) }

    // ---- persistence ----------------------------------------------------------------------

    fun requestPersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(1500)
            persistNow()
        }
    }

    /** Serialises tab list + live WebView history. The Bundle -> bytes step is main-thread; disk I/O is not. */
    fun persistNow() {
        persistJob?.cancel()
        val normal = tabs.filter { !it.incognito }
        val selectedNormal = selected?.takeIf { !it.incognito }?.id ?: normal.lastOrNull()?.id
        val live = normal.mapNotNull { t ->
            t.webView?.let { v -> t.id to Bundle().also { v.saveState(it) }.toBytes() }
        }
        val index = JSONObject().put("selected", selectedNormal).put(
            "tabs",
            JSONArray().also { arr ->
                normal.forEach { arr.put(JSONObject().put("id", it.id).put("url", it.url).put("title", it.title)) }
            },
        ).toString()
        val ids = normal.map { it.id }.toSet()
        scope.launch(stateIo) {
            live.forEach { (id, bytes) -> File(stateDir, "$id.bin").writeBytes(bytes) }
            stateDir.listFiles()?.filter { it.nameWithoutExtension !in ids }?.forEach { it.delete() }
            indexFile.writeText(index)
        }
        thumbnails.prune(tabs.map { it.id }.toSet())
    }

    /** Rebuilds the tab list from disk. Only the selected tab gets a WebView; the rest stay dormant. */
    suspend fun restore(): Boolean {
        val json = withContext(Dispatchers.IO) { runCatching { JSONObject(indexFile.readText()) }.getOrNull() } ?: return false
        val arr = json.optJSONArray("tabs") ?: return false
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.getString("id")
            tabs.add(
                BrowserTab(id = id, initialUrl = o.optString("url"), initialTitle = o.optString("title"))
                    .also { it.hasPersistedState = File(stateDir, "$id.bin").exists() },
            )
        }
        if (tabs.isEmpty()) return false
        val target = tabs.firstOrNull { it.id == json.optString("selected") } ?: tabs.last()
        select(target)
        return true
    }

    private fun writeState(id: String, state: Bundle) {
        val bytes = state.toBytes()
        scope.launch(stateIo) { File(stateDir, "$id.bin").writeBytes(bytes) }
    }

    private fun readState(id: String): Bundle? = runCatching { File(stateDir, "$id.bin").readBytes().toBundle() }.getOrNull()

    private fun Bundle.toBytes(): ByteArray {
        val p = Parcel.obtain()
        try {
            p.writeBundle(this)
            return p.marshall()
        } finally {
            p.recycle()
        }
    }

    private fun ByteArray.toBundle(): Bundle? {
        val p = Parcel.obtain()
        try {
            p.unmarshall(this, 0, size)
            p.setDataPosition(0)
            return p.readBundle(WebView::class.java.classLoader)
        } finally {
            p.recycle()
        }
    }

    private companion object {
        const val MAX_LIVE = 4
        const val MAX_CLOSED = 10
    }
}
