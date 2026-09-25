package app.eddy.browser.data.models

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ToolbarPosition { BOTTOM, TOP }
enum class CookieMode { ALLOW_ALL, BLOCK_THIRD_PARTY, BLOCK_ALL }
enum class HomepageMode { HOME, CUSTOM }
enum class ExternalLinks { ASK, ALWAYS, NEVER }
enum class TabLayout { GRID, LIST }
enum class MotionPref { FULL, REDUCED }
enum class HomeDensity { COMFORTABLE, COMPACT }
enum class ShortcutStyle { MIXED, SQUIRCLE, CIRCLE, FLOWER }
enum class DownloadLocation { DOWNLOADS, EDDY_FOLDER }
enum class CloseTabBehavior { ADJACENT, RECENT }
enum class UserAgentMode { DEFAULT, DESKTOP, CUSTOM }

/** [searchUrl] and [suggestUrl] contain a single `%s` placeholder for the URL-encoded query. */
data class SearchEngine(
    val id: String,
    val name: String,
    val searchUrl: String,
    val suggestUrl: String? = null,
    val builtIn: Boolean = false,
)

data class Shortcut(
    val id: String,
    val title: String,
    val url: String,
    val shape: Int = 0,
    val color: Int = 0,
    /** "auto" = favicon or initial, otherwise a key of [app.eddy.browser.ui.components.ShortcutIcons]. */
    val icon: String = "auto",
)

data class FilterList(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
    /** Tracker lists follow the "Tracker protection" switch, others follow "Ad blocking". */
    val tracker: Boolean = false,
)

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val amoled: Boolean = false,
    val dynamicColor: Boolean = true,
    val palette: Int = 0,
    val toolbarPosition: ToolbarPosition = ToolbarPosition.BOTTOM,
    val homeDensity: HomeDensity = HomeDensity.COMFORTABLE,
    val motion: MotionPref = MotionPref.FULL,
    val shortcutStyle: ShortcutStyle = ShortcutStyle.MIXED,
    /** Start-page composition. */
    val homeShowTitle: Boolean = true,
    val homeShowSearch: Boolean = true,
    val homeShowShortcuts: Boolean = true,
    val homeShowFrequent: Boolean = true,
    val homeShowRecent: Boolean = true,
    val homeShowBackground: Boolean = true,
    /** 0 follows the screen width. */
    val homeColumns: Int = 0,
    val hapticsEnabled: Boolean = true,
    val searchEngineId: String = "duckduckgo",
    val customEngines: List<SearchEngine> = emptyList(),
    val searxngInstance: String = "https://searx.be",
    val searchSuggestions: Boolean = true,
    val homepageMode: HomepageMode = HomepageMode.HOME,
    val homepageUrl: String = "",
    val externalLinks: ExternalLinks = ExternalLinks.ASK,
    val downloadLocation: DownloadLocation = DownloadLocation.DOWNLOADS,
    val adBlock: Boolean = true,
    val trackerProtection: Boolean = true,
    val filterLists: List<FilterList> = emptyList(),
    val filterListsUpdatedAt: Long = 0L,
    val cookieMode: CookieMode = CookieMode.BLOCK_THIRD_PARTY,
    val javascript: Boolean = true,
    val doNotTrack: Boolean = true,
    /** Upgrades http:// pages to https:// and shows a warning when the secure version does not load. */
    val httpsOnly: Boolean = true,
    /** Clears history, cookies, site data and cache when the app is swiped away. */
    val clearOnExit: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val savePasswords: Boolean = true,
    val autofillPasswords: Boolean = true,
    val restoreTabs: Boolean = true,
    val tabLayout: TabLayout = TabLayout.GRID,
    val closeTabBehavior: CloseTabBehavior = CloseTabBehavior.ADJACENT,
    val userAgentMode: UserAgentMode = UserAgentMode.DEFAULT,
    val customUserAgent: String = "",
    val textZoom: Int = 100,
    val darkenPages: Boolean = false,
    val webDebugging: Boolean = false,
    val shortcuts: List<Shortcut> = emptyList(),
) {
    val allEngines: List<SearchEngine>
        get() = SearchEngines.builtIn(searxngInstance) + customEngines

    val searchEngine: SearchEngine
        get() = allEngines.firstOrNull { it.id == searchEngineId } ?: allEngines.first()
}

object SearchEngines {
    fun builtIn(searxngInstance: String): List<SearchEngine> = listOf(
        SearchEngine(
            "duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s",
            "https://duckduckgo.com/ac/?type=list&q=%s", true,
        ),
        SearchEngine(
            "brave", "Brave Search", "https://search.brave.com/search?q=%s",
            "https://search.brave.com/api/suggest?q=%s", true,
        ),
        SearchEngine(
            "startpage", "Startpage", "https://www.startpage.com/do/search?q=%s",
            "https://www.startpage.com/suggestions?q=%s&format=opensearch", true,
        ),
        SearchEngine(
            "google", "Google", "https://www.google.com/search?q=%s",
            "https://suggestqueries.google.com/complete/search?client=firefox&q=%s", true,
        ),
        SearchEngine(
            "bing", "Bing", "https://www.bing.com/search?q=%s",
            "https://api.bing.com/osjson.aspx?query=%s", true,
        ),
        SearchEngine(
            "ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s",
            "https://ac.ecosia.org/?q=%s&type=list", true,
        ),
        SearchEngine(
            "searxng", "SearXNG", searxngInstance.trimEnd('/') + "/search?q=%s", null, true,
        ),
    )
}
