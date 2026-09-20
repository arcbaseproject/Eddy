package app.eddy.browser.privacy

import android.content.Context
import app.eddy.browser.data.models.FilterList
import app.eddy.browser.util.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/**
 * Host-based request blocker. Rules are parsed once, cached on disk as plain domain lists and held
 * in two immutable hash sets; [isBlocked] is a handful of hash lookups so it is safe to call from
 * WebView's request-interception thread for every subresource.
 */
class ContentBlocker(private val context: Context, private val scope: CoroutineScope) {

    private class Rules(val ads: Set<String>, val trackers: Set<String>)

    @Volatile private var rules = Rules(emptySet(), emptySet())
    private val firstLoad = CountDownLatch(1)
    private val mutex = Mutex()
    private val cacheDir = File(context.filesDir, "filters").apply { mkdirs() }

    private val _ruleCount = MutableStateFlow(0)
    val ruleCount: StateFlow<Int> = _ruleCount
    private val _updating = MutableStateFlow(false)
    val updating: StateFlow<Boolean> = _updating

    fun isBlocked(host: String, ads: Boolean, trackers: Boolean): Boolean {
        if (!ads && !trackers) return false
        awaitFirstLoad()
        val r = rules
        var h = host
        while (true) {
            if ((ads && h in r.ads) || (trackers && h in r.trackers)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            val rest = h.substring(dot + 1)
            if (!rest.contains('.')) return (ads && rest in r.ads) || (trackers && rest in r.trackers)
            h = rest
        }
    }

    /** Called on WebView's IO thread; only blocks until the very first rule load ends. */
    private fun awaitFirstLoad() {
        if (firstLoad.count > 0L) firstLoad.await(500, TimeUnit.MILLISECONDS)
    }

    fun reload(lists: List<FilterList>) {
        scope.launch(Dispatchers.IO) { loadFromCache(lists) }
    }

    private suspend fun loadFromCache(lists: List<FilterList>) = mutex.withLock {
        val ads = HashSet<String>()
        val trackers = HashSet<String>()
        for (list in lists) {
            if (!list.enabled) continue
            val target = if (list.tracker) trackers else ads
            runCatching {
                if (list.url.startsWith(ASSET_PREFIX)) {
                    context.assets.open(list.url.removePrefix(ASSET_PREFIX)).bufferedReader().useLines { collect(it, target) }
                } else {
                    val f = cacheFile(list)
                    if (f.exists()) f.bufferedReader().useLines { seq -> seq.forEach { target.add(it) } }
                }
            }
        }
        rules = Rules(ads, trackers)
        _ruleCount.value = ads.size + trackers.size
        firstLoad.countDown()
    }

    /** Downloads remote lists, stores the parsed domains, then reloads. Returns the number of failures. */
    suspend fun update(lists: List<FilterList>): Int = withContext(Dispatchers.IO) {
        _updating.value = true
        var failures = 0
        try {
            for (list in lists) {
                if (!list.enabled || list.url.startsWith(ASSET_PREFIX)) continue
                runCatching { download(list) }.onFailure { failures++ }
            }
            loadFromCache(lists)
        } finally {
            _updating.value = false
        }
        failures
    }

    fun cachedRuleCount(list: FilterList): Int =
        if (list.url.startsWith(ASSET_PREFIX)) -1 else cacheFile(list).takeIf { it.exists() }?.let { f ->
            f.bufferedReader().useLines { it.count() }
        } ?: 0

    fun deleteCache(list: FilterList) {
        cacheFile(list).delete()
    }

    private fun cacheFile(list: FilterList) = File(cacheDir, "${list.id.filter { it.isLetterOrDigit() || it == '-' }}.dom")

    private fun download(list: FilterList) {
        val conn = URL(list.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept-Encoding", "gzip")
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            val raw = if (conn.contentEncoding == "gzip") GZIPInputStream(conn.inputStream) else conn.inputStream
            val domains = LinkedHashSet<String>()
            raw.bufferedReader().useLines { collect(it, domains) }
            check(domains.isNotEmpty()) { "empty list" }
            val tmp = File(cacheDir, cacheFile(list).name + ".tmp")
            tmp.bufferedWriter().use { w -> domains.forEach { w.write(it); w.newLine() } }
            check(tmp.renameTo(cacheFile(list)) || cacheFile(list).delete() && tmp.renameTo(cacheFile(list)))
        } finally {
            conn.disconnect()
        }
    }

    private fun collect(lines: Sequence<String>, out: MutableSet<String>) {
        for (line in lines) parseLine(line)?.let(out::add)
    }

    companion object {
        const val ASSET_PREFIX = "asset://"
        private val domainRegex = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")
        private val ipRegex = Regex("^[0-9.]+$")
        private val hostsSinks = setOf("0.0.0.0", "127.0.0.1", "::1", "::")

        /** Understands hosts files, plain domain lists and the simple `||domain^` Adblock form. */
        fun parseLine(raw: String): String? {
            var l = raw.trim()
            if (l.isEmpty() || l[0] == '#' || l[0] == '!' || l[0] == '[' || l.startsWith("@@") || l.contains("##")) return null
            val hash = l.indexOf('#')
            if (hash > 0) l = l.substring(0, hash).trim()
            val candidate = when {
                l.startsWith("||") -> {
                    val end = l.indexOfAny(charArrayOf('^', '/', '$', '*', ':'), 2)
                    val rest = if (end < 0) "" else l.substring(end)
                    if (rest.isNotEmpty() && rest[0] != '^' && rest[0] != '$') return null
                    l.substring(2, if (end < 0) l.length else end)
                }
                else -> {
                    val parts = l.split(Regex("\\s+"))
                    when {
                        parts.size >= 2 && parts[0] in hostsSinks -> parts[1]
                        parts.size == 1 -> parts[0]
                        else -> return null
                    }
                }
            }.lowercase()
            return candidate.takeIf { domainRegex.matches(it) && !ipRegex.matches(it) && it != "localhost.localdomain" }
        }

        /** Same-site requests are never blocked so a blocked vendor's own website keeps working. */
        fun isThirdParty(requestHost: String, pageHost: String) =
            pageHost.isNotEmpty() && !UrlUtils.sameSite(requestHost, pageHost)
    }
}
