package app.eddy.browser.settings

import app.eddy.browser.util.str
import app.eddy.browser.R
import androidx.annotation.StringRes

/** Destinations inside the settings screen; the root list is the empty back stack. */
enum class SettingsPage(@StringRes private val titleRes: Int) {
    GENERAL(R.string.general),
    APPEARANCE(R.string.appearance),
    PRIVACY(R.string.privacy),
    TABS(R.string.tabs_2),
    ADVANCED(R.string.advanced),
    START_PAGE(R.string.start_page),
    SITE_PERMISSIONS(R.string.site_permissions),
    FILTER_LISTS(R.string.filter_lists),
    SEARCH_ENGINES(R.string.search_engines),
    ABOUT(R.string.about),
    SUPPORT(R.string.support),
    PRIVACY_POLICY(R.string.privacy_policy),
    TERMS(R.string.terms_of_service);

    val title: String get() = str(titleRes)
}
