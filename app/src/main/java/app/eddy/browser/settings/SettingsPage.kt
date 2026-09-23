package app.eddy.browser.settings

/** Destinations inside the settings screen; the root list is the empty back stack. */
enum class SettingsPage(val title: String) {
    GENERAL("General"),
    APPEARANCE("Appearance"),
    PRIVACY("Privacy"),
    TABS("Tabs"),
    ADVANCED("Advanced"),
    START_PAGE("Start page"),
    SITE_PERMISSIONS("Site permissions"),
    FILTER_LISTS("Filter lists"),
    SEARCH_ENGINES("Search engines"),
    ABOUT("About"),
    PRIVACY_POLICY("Privacy policy"),
    TERMS("Terms of service"),
}
