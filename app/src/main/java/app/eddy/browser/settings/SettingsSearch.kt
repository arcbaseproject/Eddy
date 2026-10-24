package app.eddy.browser.settings

import app.eddy.browser.util.str
import app.eddy.browser.R
import androidx.annotation.StringRes
import app.eddy.browser.settings.SettingsPage.*

/**
 * One searchable setting. [path] is the pages to open, outermost first; [hint] holds extra words that should find it.
 * The title must match the row's title in the settings screens (SettingsIndexTest checks this).
 */
internal class SettingEntry(@StringRes val titleRes: Int, val path: List<SettingsPage>, val hint: String = "") {
    val title: String get() = str(titleRes)
    val where get() = path.joinToString(" › ") { it.title }
    // Built on use, not when the index loads, so it follows the current language. The English hint words still match.
    private val haystack get() = "$title $hint ${path.joinToString(" ") { it.title }}".lowercase()
    fun matches(words: List<String>) = words.all { it in haystack }
}

internal val settingsIndex = listOf(
    SettingEntry(R.string.default_search_engine, listOf(GENERAL, SEARCH_ENGINES), "google duckduckgo bing brave startpage"),
    SettingEntry(R.string.search_suggestions, listOf(GENERAL), "autocomplete predictions typing"),
    SettingEntry(R.string.new_tab_opens, listOf(GENERAL), "homepage home page start page custom"),
    SettingEntry(R.string.open_links_in_other_apps, listOf(GENERAL), "external intent"),
    SettingEntry(R.string.download_location, listOf(GENERAL), "downloads folder storage"),

    SettingEntry(R.string.theme, listOf(APPEARANCE), "dark light system mode night"),
    SettingEntry(R.string.amoled_black, listOf(APPEARANCE), "true black dark oled"),
    SettingEntry(R.string.app_icon, listOf(APPEARANCE), "launcher logo home screen pixel"),
    SettingEntry(R.string.dynamic_colors, listOf(APPEARANCE), "wallpaper material you"),
    SettingEntry(R.string.toolbar_position, listOf(APPEARANCE), "address bar top bottom"),
    SettingEntry(R.string.start_page, listOf(APPEARANCE), "new tab home customize sections"),
    SettingEntry(R.string.show_title, listOf(APPEARANCE, START_PAGE), "wordmark name heading hide start page"),
    SettingEntry(R.string.search_box, listOf(APPEARANCE, START_PAGE), "start page hide search field"),
    SettingEntry(R.string.shortcuts, listOf(APPEARANCE, START_PAGE), "start page tiles hide"),
    SettingEntry(R.string.frequently_visited, listOf(APPEARANCE, START_PAGE), "start page most visited hide"),
    SettingEntry(R.string.recently_visited, listOf(APPEARANCE, START_PAGE), "start page recent hide"),
    SettingEntry(R.string.background_shapes, listOf(APPEARANCE, START_PAGE), "start page ambient blobs motion hide"),
    SettingEntry(R.string.shortcut_columns, listOf(APPEARANCE, START_PAGE), "start page grid width"),
    SettingEntry(R.string.homepage_layout, listOf(APPEARANCE, START_PAGE), "home density compact comfortable"),
    SettingEntry(R.string.shortcut_appearance, listOf(APPEARANCE, START_PAGE), "icons shapes circles squircles"),
    SettingEntry(R.string.animations, listOf(APPEARANCE), "motion reduce"),
    SettingEntry(R.string.haptic_feedback, listOf(APPEARANCE), "vibration"),
    SettingEntry(R.string.page_text_size, listOf(APPEARANCE), "font zoom scale"),

    SettingEntry(R.string.ad_blocking, listOf(PRIVACY), "ads block adblock"),
    SettingEntry(R.string.tracker_protection, listOf(PRIVACY), "trackers analytics"),
    SettingEntry(R.string.filter_lists, listOf(PRIVACY, FILTER_LISTS), "blocklist hosts update custom"),
    SettingEntry(R.string.do_not_track_and_global_privacy_control, listOf(PRIVACY), "dnt gpc"),
    SettingEntry(R.string.offer_to_save_passwords, listOf(PRIVACY), "password manager login"),
    SettingEntry(R.string.autofill_passwords, listOf(PRIVACY), "password manager login fill"),
    SettingEntry(R.string.saved_passwords, listOf(PRIVACY), "password manager export import csv"),
    SettingEntry(R.string.cookies, listOf(PRIVACY), "third-party block"),
    SettingEntry(R.string.javascript, listOf(PRIVACY), "js scripts"),
    SettingEntry(R.string.site_permissions, listOf(PRIVACY, SITE_PERMISSIONS), "location camera microphone per-site"),
    SettingEntry(R.string.clear_browsing_data, listOf(PRIVACY), "delete history cache cookies wipe"),

    SettingEntry(R.string.restore_tabs_on_launch, listOf(TABS), "reopen session"),
    SettingEntry(R.string.switch_to_tabs_opened_by_links, listOf(TABS), "new tab background focus automatic"),
    SettingEntry(R.string.tab_switcher_layout, listOf(TABS), "grid list"),
    SettingEntry(R.string.after_closing_a_tab_show, listOf(TABS), "close neighbouring recent"),

    SettingEntry(R.string.user_agent, listOf(ADVANCED), "desktop mobile custom"),
    SettingEntry(R.string.add_custom_search_engine, listOf(GENERAL, SEARCH_ENGINES), "custom engine"),
    SettingEntry(R.string.add_filter_list, listOf(PRIVACY, FILTER_LISTS), "custom list url"),
    SettingEntry(R.string.web_debugging, listOf(ADVANCED), "developer inspect devtools usb"),
    SettingEntry(R.string.https_only_mode, listOf(PRIVACY), "https secure encryption upgrade insecure http"),
    SettingEntry(R.string.clear_when_i_leave, listOf(PRIVACY), "clear on exit quit delete data swipe recents"),
    SettingEntry(R.string.app_logs, listOf(ADVANCED), "logcat debug crash bug report"),
    SettingEntry(R.string.reset_all_settings, listOf(ADVANCED), "defaults restore"),

    SettingEntry(R.string.github, listOf(ABOUT), "version build about source code website"),
    SettingEntry(R.string.replay_welcome_tour, listOf(ABOUT), "onboarding intro"),
    SettingEntry(R.string.make_eddy_your_default_browser, listOf(ABOUT), "default browser role"),
    SettingEntry(R.string.support, listOf(SUPPORT), "feedback contact help bug report email"),
    SettingEntry(R.string.privacy_policy, listOf(PRIVACY_POLICY), "legal data"),
    SettingEntry(R.string.terms_of_service, listOf(TERMS), "legal tos"),
)

/** Settings whose title or keywords contain every word of [query]; empty for a blank query. */
internal fun searchSettings(query: String): List<SettingEntry> {
    val words = query.lowercase().split(' ').filter(String::isNotBlank)
    return if (words.isEmpty()) emptyList() else settingsIndex.filter { it.matches(words) }
}
