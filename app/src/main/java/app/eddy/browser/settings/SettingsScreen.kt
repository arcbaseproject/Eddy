package app.eddy.browser.settings

import app.eddy.browser.util.str
import android.content.Intent
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import android.app.role.RoleManager
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.MaterialTheme
import app.eddy.browser.ui.theme.Dimens
import androidx.compose.ui.Alignment
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.Language
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import app.eddy.browser.R
import app.eddy.browser.BuildConfig
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.Screen
import app.eddy.browser.data.models.CloseTabBehavior
import app.eddy.browser.data.models.DownloadLocation
import app.eddy.browser.data.models.ExternalLinks
import app.eddy.browser.data.models.HomepageMode
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.TabLayout
import app.eddy.browser.data.models.UserAgentMode
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.ConfirmDialog
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.components.ScreenScaffold
import app.eddy.browser.ui.components.SearchField
import app.eddy.browser.ui.components.TextInputDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun SettingsScreen(vm: BrowserViewModel, settings: Settings) {
    val page = vm.settingsStack.lastOrNull()
    val slide = spatialSpring<IntOffset>()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    fun closeSearch() { searching = false; query = "" }
    BackHandler(enabled = searching && page == null) { closeSearch() }
    ScreenScaffold(
        title = page?.title ?: str(R.string.settings),
        onBack = { if (searching && page == null) closeSearch() else if (!vm.onBack()) vm.screen = Screen.BROWSER },
        actions = {
            if (page == null) {
                if (searching) EddyIconButton(Icons.Rounded.Close, str(R.string.close_search), ::closeSearch)
                else EddyIconButton(Icons.Rounded.Search, str(R.string.search_settings), { searching = true })
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                // Pages are declared shallow-to-deep, so a higher ordinal means we are going deeper.
                val forward = (targetState?.ordinal ?: -1) > (initialState?.ordinal ?: -1)
                (slideInHorizontally(slide) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(160))) togetherWith
                    (slideOutHorizontally(slide) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(120))) using SizeTransform(clip = false)
            },
            label = "settingsPage",
            modifier = Modifier.padding(padding),
        ) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                when (p) {
                    null -> RootPage(vm, searching, query) { query = it }
                    SettingsPage.GENERAL -> GeneralSettings(vm, settings)
                    SettingsPage.APPEARANCE -> AppearanceSettings(vm, settings)
                    SettingsPage.PRIVACY -> PrivacySettings(vm, settings)
                    SettingsPage.TABS -> TabSettings(vm, settings)
                    SettingsPage.ADVANCED -> AdvancedSettings(vm, settings)
                    SettingsPage.START_PAGE -> StartPageSettings(vm, settings)
                    SettingsPage.SITE_PERMISSIONS -> SitePermissionsSettings(vm)
                    SettingsPage.FILTER_LISTS -> FilterListsSettings(vm, settings)
                    SettingsPage.SEARCH_ENGINES -> SearchSettings(vm, settings)
                    SettingsPage.ABOUT -> AboutSettings(vm)
                    SettingsPage.SUPPORT -> SupportSettings()
                    SettingsPage.PRIVACY_POLICY -> PrivacyPolicyPage()
                    SettingsPage.TERMS -> TermsPage()
                }
            }
        }
    }
}

@Composable
private fun RootPage(vm: BrowserViewModel, searching: Boolean, query: String, onQuery: (String) -> Unit) {
    fun open(p: SettingsPage) = vm.settingsStack.add(p)
    BetaBanner()
    if (searching) SearchField(query, onQuery, str(R.string.search_settings), Modifier.padding(top = 6.dp), autoFocus = true)
    if (searching && query.isNotBlank()) {
        val results = searchSettings(query)
        if (results.isEmpty()) {
            SettingsFootnote(str(R.string.no_settings_match, query.trim()))
        } else {
            SettingsGroup {
                results.forEachIndexed { i, entry ->
                    if (i > 0) GroupDivider()
                    NavRow(entry.title, { vm.settingsStack.addAll(entry.path) }, entry.where)
                }
            }
        }
        return
    }
    SettingsGroup {
        NavRow(str(R.string.general), { open(SettingsPage.GENERAL) }, str(R.string.search_homepage_links_downloads), Icons.Rounded.Settings)
        GroupDivider()
        NavRow(str(R.string.appearance), { open(SettingsPage.APPEARANCE) }, str(R.string.theme_colors_toolbar_motion), Icons.Rounded.Palette)
        GroupDivider()
        NavRow(str(R.string.privacy_and_security), { open(SettingsPage.PRIVACY) }, str(R.string.blocking_cookies_permissions), Icons.Rounded.Security)
        GroupDivider()
        NavRow(str(R.string.tabs_2), { open(SettingsPage.TABS) }, str(R.string.restore_layout_closing), Icons.Rounded.Tab)
        GroupDivider()
        NavRow(str(R.string.advanced), { open(SettingsPage.ADVANCED) }, str(R.string.user_agent_developer), Icons.Rounded.Build)
    }
    SettingsGroup {
        NavRow(str(R.string.about_eddy), { open(SettingsPage.ABOUT) }, str(R.string.version_and_default_browser), Icons.Rounded.Info)
        GroupDivider()
        NavRow(str(R.string.support), { open(SettingsPage.SUPPORT) }, str(R.string.send_feedback_or_report_a_bug), Icons.Rounded.Email)
    }
    SettingsGroup(str(R.string.legal)) {
        NavRow(str(R.string.privacy_policy), { open(SettingsPage.PRIVACY_POLICY) }, str(R.string.what_eddy_stores_and_what_it_sends), Icons.Rounded.PrivacyTip)
        GroupDivider()
        NavRow(str(R.string.terms_of_service), { open(SettingsPage.TERMS) }, str(R.string.licence_and_disclaimers), Icons.Rounded.Gavel)
    }
}

@Composable
private fun GeneralSettings(vm: BrowserViewModel, s: Settings) {
    var editHome by remember { mutableStateOf(false) }
    SettingsGroup(str(R.string.search)) {
        NavRow(str(R.string.default_search_engine), { vm.settingsStack.add(SettingsPage.SEARCH_ENGINES) }, s.searchEngine.name, Icons.Rounded.Search)
        GroupDivider()
        SwitchRow(str(R.string.search_suggestions), s.searchSuggestions, { v -> vm.launchSettings { it.copy(searchSuggestions = v) } }, str(R.string.sends_what_you_type_to, s.searchEngine.name))
    }
    SettingsGroup(str(R.string.browsing)) {
        ChoiceRow(str(R.string.new_tab_opens), HomepageMode.entries, s.homepageMode, { if (it == HomepageMode.HOME) str(R.string.start_page) else str(R.string.custom_page) }, { v -> vm.launchSettings { it.copy(homepageMode = v) } })
        if (s.homepageMode == HomepageMode.CUSTOM) {
            GroupDivider()
            ActionRow(str(R.string.custom_page), { editHome = true }, s.homepageUrl.ifBlank { str(R.string.not_set) })
        }
        GroupDivider()
        ChoiceRow(
            str(R.string.open_links_in_other_apps), ExternalLinks.entries, s.externalLinks,
            { when (it) { ExternalLinks.ASK -> str(R.string.ask_first); ExternalLinks.ALWAYS -> str(R.string.always); ExternalLinks.NEVER -> str(R.string.never) } },
            { v -> vm.launchSettings { it.copy(externalLinks = v) } },
        )
    }
    SettingsGroup(str(R.string.downloads)) {
        ChoiceRow(
            str(R.string.download_location), DownloadLocation.entries, s.downloadLocation,
            { if (it == DownloadLocation.DOWNLOADS) str(R.string.downloads) else "Downloads/Eddy" },
            { v -> vm.launchSettings { it.copy(downloadLocation = v) } },
        )
    }
    if (editHome) {
        TextInputDialog(str(R.string.custom_page), s.homepageUrl, str(R.string.address), keyboardType = KeyboardType.Uri, onDismiss = { editHome = false }) { v ->
            vm.launchSettings { it.copy(homepageUrl = v) }
            editHome = false
        }
    }
}

@Composable
private fun TabSettings(vm: BrowserViewModel, s: Settings) {
    SettingsGroup(str(R.string.behavior)) {
        SwitchRow(str(R.string.restore_tabs_on_launch), s.restoreTabs, { v -> vm.launchSettings { it.copy(restoreTabs = v) } }, str(R.string.eddy_does_not_restore_incognito_tabs))
        GroupDivider()
        SwitchRow(str(R.string.switch_to_tabs_opened_by_links), s.switchToNewTabs, { v -> vm.launchSettings { it.copy(switchToNewTabs = v) } }, str(R.string.when_off_they_open_in_the_background))
        GroupDivider()
        ChoiceRow(str(R.string.tab_switcher_layout), TabLayout.entries, s.tabLayout, { if (it == TabLayout.GRID) str(R.string.grid) else str(R.string.list) }, { v -> vm.launchSettings { it.copy(tabLayout = v) } })
        GroupDivider()
        ChoiceRow(
            str(R.string.after_closing_a_tab_show), CloseTabBehavior.entries, s.closeTabBehavior,
            { if (it == CloseTabBehavior.ADJACENT) str(R.string.the_neighbouring_tab) else str(R.string.the_most_recently_used_tab) },
            { v -> vm.launchSettings { it.copy(closeTabBehavior = v) } },
        )
    }
}

@Composable
private fun AdvancedSettings(vm: BrowserViewModel, s: Settings) {
    var editUa by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    SettingsGroup(str(R.string.compatibility)) {
        ChoiceRow(
            str(R.string.user_agent), UserAgentMode.entries, s.userAgentMode,
            { when (it) { UserAgentMode.DEFAULT -> str(R.string.mobile_default); UserAgentMode.DESKTOP -> str(R.string.desktop_site_everywhere); UserAgentMode.CUSTOM -> str(R.string.custom) } },
            { v -> vm.launchSettings { it.copy(userAgentMode = v) } },
        )
        if (s.userAgentMode == UserAgentMode.CUSTOM) {
            GroupDivider()
            ActionRow(str(R.string.custom_user_agent), { editUa = true }, s.customUserAgent.ifBlank { str(R.string.not_set_uses_mobile_default) })
        }
    }
    SettingsGroup(str(R.string.developer)) {
        SwitchRow(str(R.string.web_debugging), s.webDebugging, { v -> vm.launchSettings { it.copy(webDebugging = v) } }, str(R.string.web_debugging_hint))
        GroupDivider()
        ActionRow(str(R.string.app_logs), vm::viewAppLogs, str(R.string.recent_log_output_from_eddy_for_bug_repo))
        GroupDivider()
        ActionRow(str(R.string.reset_all_settings), { confirmReset = true }, str(R.string.restores_defaults_bookmarks_and_history))
    }
    if (editUa) TextInputDialog(str(R.string.custom_user_agent), s.customUserAgent, str(R.string.user_agent_string), onDismiss = { editUa = false }) { v -> vm.launchSettings { it.copy(customUserAgent = v) }; editUa = false }
    if (confirmReset) {
        ConfirmDialog(str(R.string.reset_all_settings_2), str(R.string.eddy_restores_every_setting_to_its_defau), str(R.string.reset_2), { confirmReset = false }) {
            confirmReset = false
            vm.launchSettings { Settings(shortcuts = it.shortcuts, onboardingCompleted = it.onboardingCompleted) }
        }
    }
}

@Composable
private fun AboutSettings(vm: BrowserViewModel) {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    fun held() = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    var isDefault by remember { mutableStateOf(held()) }
    // The choice is made in a system screen, so re-check whenever we come back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = held() }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = held() }

    AboutHeader(vm::openInNewTab)
    SettingsGroup {
        ActionRow(str(R.string.replay_welcome_tour), vm::replayOnboarding, str(R.string.walk_through_eddy_s_setup_again))
        GroupDivider()
        if (isDefault) {
            ActionRow(str(R.string.default_browser), {}, str(R.string.eddy_is_your_default_browser), Icons.Rounded.CheckCircle)
        } else {
            ActionRow(str(R.string.make_eddy_your_default_browser), {
                if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                } else {
                    context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, str(R.string.opens_a_system_prompt))
        }
    }
    SettingsFootnote(
        str(R.string.eddy_has_no_advertising_analytics_or_tra),
    )
}

private const val REPO_URL = "https://github.com/arcbaseproject/Eddy"
private const val SITE_URL = "https://eddybrowser.xyz"

/** App icon, name and version, with links to the website and the source code. */
@Composable
private fun AboutHeader(open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The adaptive-icon foreground is drawn on a 108dp canvas with a 72dp safe zone, so it is oversized and clipped.
        Box(Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(colorResource(R.color.icon_bg)), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.requiredSize(144.dp))
        }
        Text(str(R.string.app_name), Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineMedium)
        Text(
            str(R.string.version, BuildConfig.VERSION_NAME),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "A fast, private browser. No ads, no tracking.",
            Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton({ open(SITE_URL) }) {
                Icon(Icons.Rounded.Language, null, Modifier.size(18.dp))
                Text(str(R.string.website), Modifier.padding(start = 8.dp))
            }
            FilledTonalButton({ open(REPO_URL) }) {
                Icon(painterResource(R.drawable.ic_github), null, Modifier.size(18.dp))
                Text(str(R.string.github), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Slim reminder that the app is still pre-release; shown at the top of the settings list. */
@Composable
private fun BetaBanner() {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Science, null, Modifier.size(18.dp))
            Text(
                str(R.string.beta_eddy_is_in_development_expect_bugs),
                Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
