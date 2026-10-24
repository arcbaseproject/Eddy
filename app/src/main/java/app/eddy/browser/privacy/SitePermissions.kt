package app.eddy.browser.privacy

import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.data.database.SiteSettingsDao
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** [TEXT_ZOOM] holds a percentage; every other feature holds [SiteSettings.ALLOW] or [SiteSettings.BLOCK]. */
enum class SiteFeature { LOCATION, CAMERA, MICROPHONE, JAVASCRIPT, POPUPS, THIRD_PARTY_COOKIES, DESKTOP, CONTENT_BLOCKING, TEXT_ZOOM }

fun SiteSettings.get(f: SiteFeature): Int? = when (f) {
    SiteFeature.LOCATION -> location
    SiteFeature.CAMERA -> camera
    SiteFeature.MICROPHONE -> microphone
    SiteFeature.JAVASCRIPT -> javascript
    SiteFeature.POPUPS -> popups
    SiteFeature.THIRD_PARTY_COOKIES -> thirdPartyCookies
    SiteFeature.DESKTOP -> desktop
    SiteFeature.CONTENT_BLOCKING -> contentBlocking
    SiteFeature.TEXT_ZOOM -> textZoom
}

fun SiteSettings.with(f: SiteFeature, v: Int?): SiteSettings = when (f) {
    SiteFeature.LOCATION -> copy(location = v)
    SiteFeature.CAMERA -> copy(camera = v)
    SiteFeature.MICROPHONE -> copy(microphone = v)
    SiteFeature.JAVASCRIPT -> copy(javascript = v)
    SiteFeature.POPUPS -> copy(popups = v)
    SiteFeature.THIRD_PARTY_COOKIES -> copy(thirdPartyCookies = v)
    SiteFeature.DESKTOP -> copy(desktop = v)
    SiteFeature.CONTENT_BLOCKING -> copy(contentBlocking = v)
    SiteFeature.TEXT_ZOOM -> copy(textZoom = v)
}

val SiteSettings.isEmpty: Boolean
    get() = SiteFeature.entries.all { get(it) == null }

/**
 * Per-site overrides. The table is tiny, so it is mirrored in memory and [peek] can be used from
 * WebView callbacks that must answer synchronously. Choices made in incognito live only in memory
 * until [clearPrivate], on top of the saved ones.
 */
class SitePermissions(private val dao: SiteSettingsDao, private val scope: CoroutineScope) {
    private val cache = ConcurrentHashMap<String, SiteSettings>()
    private val private = ConcurrentHashMap<String, SiteSettings>()
    private val loaded = CompletableDeferred<Unit>()
    // Writes run one at a time against the stored row, so a stale snapshot never drops a newer choice.
    private val writes = Mutex()

    init {
        scope.launch {
            dao.all().first().forEach { cache.putIfAbsent(it.host, it) }
            loaded.complete(Unit)
        }
    }

    fun peek(host: String, incognito: Boolean = false): SiteSettings? =
        if (incognito) private[host] ?: cache[host] else cache[host]

    fun all(): Flow<List<SiteSettings>> = dao.all()

    fun set(host: String, feature: SiteFeature, value: Int?, incognito: Boolean = false) {
        if (host.isEmpty()) return
        if (incognito) {
            private[host] = (peek(host, true) ?: SiteSettings(host)).with(feature, value)
            return
        }
        // Update the mirror first so a synchronous read right after sees the change.
        cache.compute(host) { _, old -> (old ?: SiteSettings(host)).with(feature, value).takeUnless { it.isEmpty } }
        scope.launch {
            writes.withLock {
                loaded.await()
                val updated = (dao.get(host) ?: SiteSettings(host)).with(feature, value)
                if (updated.isEmpty) dao.delete(host) else dao.upsert(updated)
                if (updated.isEmpty) cache.remove(host) else cache[host] = updated
            }
        }
    }

    /** Forgets every choice made in incognito, once the last private tab closes. */
    fun clearPrivate() = private.clear()

    fun reset(host: String) {
        cache.remove(host)
        private.remove(host)
        scope.launch { writes.withLock { loaded.await(); dao.delete(host); cache.remove(host) } }
    }

    fun resetAll() {
        cache.clear()
        private.clear()
        scope.launch { writes.withLock { loaded.await(); dao.clear(); cache.clear() } }
    }
}
