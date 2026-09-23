package app.eddy.browser.settings

import app.eddy.browser.settings.SettingsPage.*

/**
 * One searchable setting. [path] is the pages to open, outermost first; [hint] holds extra words that should find it.
 * The title must match the row's title in the settings screens (SettingsIndexTest checks this).
 */
internal class SettingEntry(val title: String, val path: List<SettingsPage>, val hint: String = "") {
    val where get() = path.joinToString(" › ") { it.title }
    private val haystack = "$title $hint ${path.joinToString(" ") { it.title }}".lowercase()
    fun matches(words: List<String>) = words.all { it in haystack }
}

internal val settingsIndex = listOf(
    SettingEntry("Default search engine", listOf(GENERAL, SEARCH_ENGINES), "google duckduckgo bing brave startpage"),
    SettingEntry("Search suggestions", listOf(GENERAL), "autocomplete predictions typing"),
    SettingEntry("New tab opens", listOf(GENERAL), "homepage home page start page custom"),
    SettingEntry("Open links in other apps", listOf(GENERAL), "external intent"),
    SettingEntry("Download location", listOf(GENERAL), "downloads folder storage"),

    SettingEntry("Theme", listOf(APPEARANCE), "dark light system mode night"),
    SettingEntry("AMOLED black", listOf(APPEARANCE), "true black dark oled"),
    SettingEntry("Dynamic colors", listOf(APPEARANCE), "wallpaper material you"),
    SettingEntry("Toolbar position", listOf(APPEARANCE), "address bar top bottom"),
    SettingEntry("Start page", listOf(APPEARANCE), "new tab home customize sections"),
    SettingEntry("Show title", listOf(APPEARANCE, START_PAGE), "wordmark name heading hide start page"),
    SettingEntry("Search box", listOf(APPEARANCE, START_PAGE), "start page hide search field"),
    SettingEntry("Shortcuts", listOf(APPEARANCE, START_PAGE), "start page tiles hide"),
    SettingEntry("Frequently visited", listOf(APPEARANCE, START_PAGE), "start page most visited hide"),
    SettingEntry("Recently visited", listOf(APPEARANCE, START_PAGE), "start page recent hide"),
    SettingEntry("Background shapes", listOf(APPEARANCE, START_PAGE), "start page ambient blobs motion hide"),
    SettingEntry("Shortcut columns", listOf(APPEARANCE, START_PAGE), "start page grid width"),
    SettingEntry("Homepage layout", listOf(APPEARANCE, START_PAGE), "home density compact comfortable"),
    SettingEntry("Shortcut appearance", listOf(APPEARANCE, START_PAGE), "icons shapes circles squircles"),
    SettingEntry("Animations", listOf(APPEARANCE), "motion reduce"),
    SettingEntry("Haptic feedback", listOf(APPEARANCE), "vibration"),
    SettingEntry("Page text size", listOf(APPEARANCE), "font zoom scale"),

    SettingEntry("Ad blocking", listOf(PRIVACY), "ads block adblock"),
    SettingEntry("Tracker protection", listOf(PRIVACY), "trackers analytics"),
    SettingEntry("Filter lists", listOf(PRIVACY, FILTER_LISTS), "blocklist hosts update custom"),
    SettingEntry("Do Not Track and Global Privacy Control", listOf(PRIVACY), "dnt gpc"),
    SettingEntry("Offer to save passwords", listOf(PRIVACY), "password manager login"),
    SettingEntry("Autofill passwords", listOf(PRIVACY), "password manager login fill"),
    SettingEntry("Saved passwords", listOf(PRIVACY), "password manager export import csv"),
    SettingEntry("Cookies", listOf(PRIVACY), "third-party block"),
    SettingEntry("JavaScript", listOf(PRIVACY), "js scripts"),
    SettingEntry("Site permissions", listOf(PRIVACY, SITE_PERMISSIONS), "location camera microphone per-site"),
    SettingEntry("Clear browsing data", listOf(PRIVACY), "delete history cache cookies wipe"),

    SettingEntry("Restore tabs on launch", listOf(TABS), "reopen session"),
    SettingEntry("Tab switcher layout", listOf(TABS), "grid list"),
    SettingEntry("After closing a tab, show", listOf(TABS), "close neighbouring recent"),

    SettingEntry("User agent", listOf(ADVANCED), "desktop mobile custom"),
    SettingEntry("Custom search engines", listOf(ADVANCED, SEARCH_ENGINES), "add engine"),
    SettingEntry("Custom filter lists", listOf(ADVANCED, FILTER_LISTS), "add list url"),
    SettingEntry("Web debugging", listOf(ADVANCED), "developer inspect devtools usb"),
    SettingEntry("Reset all settings", listOf(ADVANCED), "defaults restore"),

    SettingEntry("Version", listOf(ABOUT), "build about"),
    SettingEntry("Replay welcome tour", listOf(ABOUT), "onboarding intro"),
    SettingEntry("Make Eddy your default browser", listOf(ABOUT), "default browser role"),
    SettingEntry("Privacy policy", listOf(PRIVACY_POLICY), "legal data"),
    SettingEntry("Terms of service", listOf(TERMS), "legal tos"),
)

/** Settings whose title or keywords contain every word of [query]; empty for a blank query. */
internal fun searchSettings(query: String): List<SettingEntry> {
    val words = query.lowercase().split(' ').filter(String::isNotBlank)
    return if (words.isEmpty()) emptyList() else settingsIndex.filter { it.matches(words) }
}
