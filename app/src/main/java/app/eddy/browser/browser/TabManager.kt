package app.eddy.browser.browser

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Parcel
import android.util.AtomicFile
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

class ClosedTab(val url: String, val title: String, var state: Bundle?)

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

    /** Tab ids whose WebView is being built. Main-thread only, so a plain set is enough. */
    private val building = mutableSetOf<String>()

    /** One thread for all tab-state files so a write always lands before the read that follows it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val stateIo = Dispatchers.IO.limitedParallelism(1)

    // ---- creation / selection -------------------------------------------------------------

    fun newTab(url: String = "", incognito: Boolean = false, select: Boolean = true, opener: BrowserTab? = null): BrowserTab {
        val tab = BrowserTab(incognito = incognito, initialUrl = url).also { it.openerId = opener?.id }
        if (incognito && tabs.none { it.incognito }) factory().startIncognitoSession()
        val at = opener?.let { tabs.indexOf(it) + 1 }?.takeIf { it > 0 } ?: tabs.size
        tabs.add(at, tab)
        // Build the view before selecting: select() may start ensureWebView() synchronously and would create a second one.
        if (url.isNotEmpty()) tab.webView = createView(tab, null)
        if (select) select(tab) else {
            tab.webView?.onPause()
            evictInactive(MAX_LIVE)
        }
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
        // building guards the await below: without it two callers both pass the null check and one view is orphaned.
        if (tab !in tabs || tab.webView != null || tab.isHome || !building.add(tab.id)) return
        val version = tab.restoreVersion
        try {
            val state = tab.savedState ?: if (tab.hasPersistedState) withContext(stateIo) { readState(tab.id) } else null
            if (tab !in tabs || tab.isHome || tab.webView != null || tab.restoreVersion != version) return
            tab.webView = createView(tab, state)
            tab.savedState = null
            if (tab.id == selectedId) tab.webView?.onResume() else tab.webView?.onPause()
            evictInactive(MAX_LIVE)
        } finally {
            building.remove(tab.id)
        }
    }

    private fun createView(tab: BrowserTab, state: Bundle?, loadUrl: Boolean = true): EddyWebView {
        val view = factory().create(tab)
        val restored = state != null && view.restoreState(state) != null
        tab.canGoBack = view.canGoBack()
        tab.canGoForward = view.canGoForward()
        if (!restored && loadUrl && tab.url.isNotEmpty()) factory().load(tab, view, tab.url)
        return view
    }

    fun load(tab: BrowserTab, url: String) {
        tab.restoreVersion++
        tab.savedState = null
        tab.hasPersistedState = false
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
        tab.blockedRaw = 0
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
        // Queued behind the read rememberClosed may have started: stateIo runs one file operation at a time.
        val stateFile = File(stateDir, "${tab.id}.bin")
        scope.launch(stateIo) { stateFile.delete() }
        if (wasSelected) selectAfterClose(index, tab)
        if (tab.incognito && tabs.none { it.incognito }) sweepIncognitoProfiles()
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
        // saveState must run on the main thread; the persisted copy is read off it and filled in after.
        val live = tab.webView?.let { v -> Bundle().also { v.saveState(it) } } ?: tab.savedState
        val entry = ClosedTab(tab.url, tab.title, live)
        closed.addFirst(entry)
        while (closed.size > MAX_CLOSED) closed.removeLast()
        closedCount = closed.size
        if (live == null && tab.hasPersistedState) {
            val id = tab.id
            scope.launch { entry.state = withContext(stateIo) { readState(id) } }
        }
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

    /**
     * Deletes every incognito storage profile that is not used by an open incognito tab. WebView refuses to delete
     * a profile while it is still winding down, so this retries for a while; anything left over is swept again the
     * next time the app starts, before any incognito WebView exists.
     */
    @android.annotation.SuppressLint("RequiresFeature")
    fun sweepIncognitoProfiles(attempt: Int = 0) {
        if (!factory().incognitoIsolated) return
        val active = if (tabs.any { it.incognito }) factory().incognitoProfile else null
        val store = ProfileStore.getInstance()
        var pending = false
        for (name in store.allProfileNames) {
            if (!name.startsWith(WebViewFactory.PROFILE_PREFIX) || name == active) continue
            try {
                store.deleteProfile(name)
            } catch (_: IllegalStateException) {
                pending = true
            } catch (_: RuntimeException) {
            }
        }
        if (pending && attempt < 30) mainHandler.postDelayed({ sweepIncognitoProfiles(attempt + 1) }, 1000)
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
        tab.restoreVersion++
        val view = tab.webView ?: return
        tab.webView = null
        view.onScrolled = null
        view.onPull = null
        view.onPullRelease = null
        factory().cancelPrerender(view)
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
            runCatching {
                live.forEach { (id, bytes) -> File(stateDir, "$id.bin").writeAtomically(bytes) }
                stateDir.listFiles()?.filter { it.nameWithoutExtension !in ids }?.forEach { it.delete() }
                indexFile.writeAtomically(index.toByteArray())
            }
        }
        thumbnails.prune(tabs.map { it.id }.toSet())
    }

    /** Rebuilds the tab list from disk. Only the selected tab gets a WebView; the rest stay dormant. */
    suspend fun restore(): Boolean {
        val json = withContext(stateIo) {
            runCatching { AtomicFile(indexFile).openRead().bufferedReader().use { JSONObject(it.readText()) } }.getOrNull()
        } ?: return false
        val arr = json.optJSONArray("tabs") ?: return false
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.matches(Regex("[a-fA-F0-9-]{36}")) } ?: continue
            if (tabs.any { it.id == id }) continue
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
        scope.launch(stateIo) { runCatching { File(stateDir, "$id.bin").writeAtomically(bytes) } }
    }

    private fun File.writeAtomically(bytes: ByteArray) {
        val file = AtomicFile(this)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (e: Exception) { file.failWrite(stream); throw e }
    }

    private fun readState(id: String): Bundle? = runCatching { AtomicFile(File(stateDir, "$id.bin")).openRead().use { it.readBytes().toBundle() } }.getOrNull()

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
