package app.eddy.browser.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.eddy.browser.data.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("eddy_settings")

/** Typed view over DataStore. All writes go through [update] so callers never touch raw keys. */
class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(block: (Settings) -> Settings) {
        context.dataStore.edit { prefs -> prefs.write(block(prefs.toSettings())) }
    }

    private object K {
        val theme = stringPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val dynamic = booleanPreferencesKey("dynamic")
        val palette = intPreferencesKey("palette")
        val toolbar = stringPreferencesKey("toolbar")
        val density = stringPreferencesKey("density")
        val motion = stringPreferencesKey("motion")
        val shortcutStyle = stringPreferencesKey("shortcut_style")
        val haptics = booleanPreferencesKey("haptics")
        val engine = stringPreferencesKey("engine")
        val customEngines = stringPreferencesKey("custom_engines")
        val searxng = stringPreferencesKey("searxng")
        val suggestions = booleanPreferencesKey("suggestions")
        val homepageMode = stringPreferencesKey("homepage_mode")
        val homepageUrl = stringPreferencesKey("homepage_url")
        val external = stringPreferencesKey("external_links")
        val downloadLocation = stringPreferencesKey("download_location")
        val adBlock = booleanPreferencesKey("ad_block")
        val trackers = booleanPreferencesKey("trackers")
        val filterLists = stringPreferencesKey("filter_lists")
        val filterUpdated = longPreferencesKey("filter_updated")
        val cookies = stringPreferencesKey("cookies")
        val js = booleanPreferencesKey("javascript")
        val dnt = booleanPreferencesKey("dnt")
        val restore = booleanPreferencesKey("restore_tabs")
        val tabLayout = stringPreferencesKey("tab_layout")
        val closeBehavior = stringPreferencesKey("close_behavior")
        val uaMode = stringPreferencesKey("ua_mode")
        val ua = stringPreferencesKey("ua")
        val zoom = intPreferencesKey("text_zoom")
        val debug = booleanPreferencesKey("web_debug")
        val shortcuts = stringPreferencesKey("shortcuts")
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            themeMode = this[K.theme].toEnum(d.themeMode),
            amoled = this[K.amoled] ?: d.amoled,
            dynamicColor = this[K.dynamic] ?: d.dynamicColor,
            palette = this[K.palette] ?: d.palette,
            toolbarPosition = this[K.toolbar].toEnum(d.toolbarPosition),
            homeDensity = this[K.density].toEnum(d.homeDensity),
            motion = this[K.motion].toEnum(d.motion),
            shortcutStyle = this[K.shortcutStyle].toEnum(d.shortcutStyle),
            hapticsEnabled = this[K.haptics] ?: d.hapticsEnabled,
            searchEngineId = this[K.engine] ?: d.searchEngineId,
            customEngines = this[K.customEngines]?.let(::decodeEngines) ?: emptyList(),
            searxngInstance = this[K.searxng] ?: d.searxngInstance,
            searchSuggestions = this[K.suggestions] ?: d.searchSuggestions,
            homepageMode = this[K.homepageMode].toEnum(d.homepageMode),
            homepageUrl = this[K.homepageUrl] ?: "",
            externalLinks = this[K.external].toEnum(d.externalLinks),
            downloadLocation = this[K.downloadLocation].toEnum(d.downloadLocation),
            adBlock = this[K.adBlock] ?: d.adBlock,
            trackerProtection = this[K.trackers] ?: d.trackerProtection,
            filterLists = this[K.filterLists]?.let(::decodeFilterLists) ?: DefaultFilterLists,
            filterListsUpdatedAt = this[K.filterUpdated] ?: 0L,
            cookieMode = this[K.cookies].toEnum(d.cookieMode),
            javascript = this[K.js] ?: d.javascript,
            doNotTrack = this[K.dnt] ?: d.doNotTrack,
            restoreTabs = this[K.restore] ?: d.restoreTabs,
            tabLayout = this[K.tabLayout].toEnum(d.tabLayout),
            closeTabBehavior = this[K.closeBehavior].toEnum(d.closeTabBehavior),
            userAgentMode = this[K.uaMode].toEnum(d.userAgentMode),
            customUserAgent = this[K.ua] ?: "",
            textZoom = this[K.zoom] ?: d.textZoom,
            webDebugging = this[K.debug] ?: d.webDebugging,
            shortcuts = this[K.shortcuts]?.let(::decodeShortcuts) ?: DefaultShortcuts,
        )
    }

    private fun MutablePreferences.write(s: Settings) {
        this[K.theme] = s.themeMode.name
        this[K.amoled] = s.amoled
        this[K.dynamic] = s.dynamicColor
        this[K.palette] = s.palette
        this[K.toolbar] = s.toolbarPosition.name
        this[K.density] = s.homeDensity.name
        this[K.motion] = s.motion.name
        this[K.shortcutStyle] = s.shortcutStyle.name
        this[K.haptics] = s.hapticsEnabled
        this[K.engine] = s.searchEngineId
        this[K.customEngines] = encodeEngines(s.customEngines)
        this[K.searxng] = s.searxngInstance
        this[K.suggestions] = s.searchSuggestions
        this[K.homepageMode] = s.homepageMode.name
        this[K.homepageUrl] = s.homepageUrl
        this[K.external] = s.externalLinks.name
        this[K.downloadLocation] = s.downloadLocation.name
        this[K.adBlock] = s.adBlock
        this[K.trackers] = s.trackerProtection
        this[K.filterLists] = encodeFilterLists(s.filterLists)
        this[K.filterUpdated] = s.filterListsUpdatedAt
        this[K.cookies] = s.cookieMode.name
        this[K.js] = s.javascript
        this[K.dnt] = s.doNotTrack
        this[K.restore] = s.restoreTabs
        this[K.tabLayout] = s.tabLayout.name
        this[K.closeBehavior] = s.closeTabBehavior.name
        this[K.uaMode] = s.userAgentMode.name
        this[K.ua] = s.customUserAgent
        this[K.zoom] = s.textZoom
        this[K.debug] = s.webDebugging
        this[K.shortcuts] = encodeShortcuts(s.shortcuts)
    }

    companion object {
        val DefaultShortcuts = listOf(
            Shortcut("d-wiki", "Wikipedia", "https://www.wikipedia.org", 0, 0),
            Shortcut("d-news", "Hacker News", "https://news.ycombinator.com", 1, 1),
            Shortcut("d-maps", "OpenStreetMap", "https://www.openstreetmap.org", 2, 2),
            Shortcut("d-gh", "GitHub", "https://github.com", 3, 3),
            Shortcut("d-yt", "YouTube", "https://m.youtube.com", 4, 4),
            Shortcut("d-reddit", "Reddit", "https://www.reddit.com", 5, 5),
        )

        val DefaultFilterLists = listOf(
            FilterList(
                "builtin-ads", "Eddy ad list", "asset://filters/eddy-ads.txt", enabled = true, builtIn = true,
            ),
            FilterList(
                "builtin-trackers", "Eddy tracker list", "asset://filters/eddy-trackers.txt",
                enabled = true, builtIn = true, tracker = true,
            ),
            FilterList(
                "pgl", "Peter Lowe's ad & tracking servers",
                "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
            ),
            FilterList(
                "stevenblack", "StevenBlack unified hosts",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", enabled = false,
            ),
        )

        private fun encodeShortcuts(list: List<Shortcut>) = JSONArray().also { arr ->
            list.forEach {
                arr.put(
                    JSONObject().put("id", it.id).put("t", it.title).put("u", it.url)
                        .put("s", it.shape).put("c", it.color).put("i", it.icon),
                )
            }
        }.toString()

        private fun decodeShortcuts(json: String): List<Shortcut> = runCatching {
            val arr = JSONArray(json)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                Shortcut(o.getString("id"), o.getString("t"), o.getString("u"), o.optInt("s"), o.optInt("c"), o.optString("i", "auto"))
            }
        }.getOrDefault(DefaultShortcuts)

        private fun encodeEngines(list: List<SearchEngine>) = JSONArray().also { arr ->
            list.forEach {
                arr.put(JSONObject().put("id", it.id).put("n", it.name).put("s", it.searchUrl).put("g", it.suggestUrl ?: ""))
            }
        }.toString()

        private fun decodeEngines(json: String): List<SearchEngine> = runCatching {
            val arr = JSONArray(json)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                SearchEngine(o.getString("id"), o.getString("n"), o.getString("s"), o.optString("g").ifBlank { null })
            }
        }.getOrDefault(emptyList())

        private fun encodeFilterLists(list: List<FilterList>) = JSONArray().also { arr ->
            list.forEach {
                arr.put(JSONObject().put("id", it.id).put("n", it.name).put("u", it.url).put("e", it.enabled).put("b", it.builtIn).put("t", it.tracker))
            }
        }.toString()

        private fun decodeFilterLists(json: String): List<FilterList> = runCatching {
            val arr = JSONArray(json)
            List(arr.length()) {
                val o = arr.getJSONObject(it)
                FilterList(o.getString("id"), o.getString("n"), o.getString("u"), o.optBoolean("e", true), o.optBoolean("b"), o.optBoolean("t"))
            }
        }.getOrDefault(DefaultFilterLists)
    }
}
