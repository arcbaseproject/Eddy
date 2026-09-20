package app.eddy.browser.privacy

import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.data.database.SiteSettingsDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

enum class SiteFeature { LOCATION, CAMERA, MICROPHONE, JAVASCRIPT, POPUPS, THIRD_PARTY_COOKIES, DESKTOP, CONTENT_BLOCKING }

fun SiteSettings.get(f: SiteFeature): Int? = when (f) {
    SiteFeature.LOCATION -> location
    SiteFeature.CAMERA -> camera
    SiteFeature.MICROPHONE -> microphone
    SiteFeature.JAVASCRIPT -> javascript
    SiteFeature.POPUPS -> popups
    SiteFeature.THIRD_PARTY_COOKIES -> thirdPartyCookies
    SiteFeature.DESKTOP -> desktop
    SiteFeature.CONTENT_BLOCKING -> contentBlocking
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
}

val SiteSettings.isEmpty: Boolean
    get() = SiteFeature.entries.all { get(it) == null }

/**
 * Per-site overrides. The table is tiny, so it is mirrored in memory and [peek] can be used from
 * WebView callbacks that must answer synchronously.
 */
class SitePermissions(private val dao: SiteSettingsDao, private val scope: CoroutineScope) {
    private val cache = ConcurrentHashMap<String, SiteSettings>()

    init {
        scope.launch { dao.all().collect { rows -> cache.clear(); rows.forEach { cache[it.host] = it } } }
    }

    fun peek(host: String): SiteSettings? = cache[host]

    fun all(): Flow<List<SiteSettings>> = dao.all()

    fun set(host: String, feature: SiteFeature, value: Int?) {
        if (host.isEmpty()) return
        val updated = (cache[host] ?: SiteSettings(host)).with(feature, value)
        // Update the mirror first so a synchronous read right after sees the change.
        if (updated.isEmpty) cache.remove(host) else cache[host] = updated
        scope.launch { if (updated.isEmpty) dao.delete(host) else dao.upsert(updated) }
    }

    fun reset(host: String) {
        cache.remove(host)
        scope.launch { dao.delete(host) }
    }

    fun resetAll() {
        cache.clear()
        scope.launch { dao.clear() }
    }
}
