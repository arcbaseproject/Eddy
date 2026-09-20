package app.eddy.browser.settings

import android.content.Intent
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.activity.result.contract.ActivityResultContracts
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
import app.eddy.browser.ui.components.ScreenScaffold
import app.eddy.browser.ui.components.TextInputDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun SettingsScreen(vm: BrowserViewModel, settings: Settings) {
    val page = vm.settingsStack.lastOrNull()
    val slide = spatialSpring<IntOffset>()
    ScreenScaffold(
        title = page?.title ?: "Settings",
        onBack = { if (!vm.onBack()) vm.screen = Screen.BROWSER },
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
                    null -> RootPage(vm)
                    SettingsPage.GENERAL -> GeneralSettings(vm, settings)
                    SettingsPage.APPEARANCE -> AppearanceSettings(vm, settings)
                    SettingsPage.PRIVACY -> PrivacySettings(vm, settings)
                    SettingsPage.TABS -> TabSettings(vm, settings)
                    SettingsPage.ADVANCED -> AdvancedSettings(vm, settings)
                    SettingsPage.SITE_PERMISSIONS -> SitePermissionsSettings(vm)
                    SettingsPage.FILTER_LISTS -> FilterListsSettings(vm, settings)
                    SettingsPage.SEARCH_ENGINES -> SearchSettings(vm, settings)
                    SettingsPage.ABOUT -> AboutSettings()
                    SettingsPage.PRIVACY_POLICY -> PrivacyPolicyPage()
                    SettingsPage.TERMS -> TermsPage()
                }
            }
        }
    }
}

@Composable
private fun RootPage(vm: BrowserViewModel) {
    fun open(p: SettingsPage) = vm.settingsStack.add(p)
    BetaBanner()
    SettingsGroup {
        NavRow("General", { open(SettingsPage.GENERAL) }, "Search, homepage, links, downloads", Icons.Rounded.Settings)
        GroupDivider()
        NavRow("Appearance", { open(SettingsPage.APPEARANCE) }, "Theme, colors, toolbar, motion", Icons.Rounded.Palette)
        GroupDivider()
        NavRow("Privacy and security", { open(SettingsPage.PRIVACY) }, "Blocking, cookies, permissions", Icons.Rounded.Security)
        GroupDivider()
        NavRow("Tabs", { open(SettingsPage.TABS) }, "Restore, layout, closing", Icons.Rounded.Tab)
        GroupDivider()
        NavRow("Advanced", { open(SettingsPage.ADVANCED) }, "User agent, search engines, developer", Icons.Rounded.Build)
    }
    SettingsGroup {
        NavRow("About Eddy", { open(SettingsPage.ABOUT) }, "Version and default browser", Icons.Rounded.Info)
    }
    SettingsGroup("Legal") {
        NavRow("Privacy policy", { open(SettingsPage.PRIVACY_POLICY) }, "What Eddy stores and what it sends", Icons.Rounded.PrivacyTip)
        GroupDivider()
        NavRow("Terms of service", { open(SettingsPage.TERMS) }, "Rules for using the app", Icons.Rounded.Gavel)
    }
}

@Composable
private fun GeneralSettings(vm: BrowserViewModel, s: Settings) {
    var editHome by remember { mutableStateOf(false) }
    SettingsGroup("Search") {
        NavRow("Default search engine", { vm.settingsStack.add(SettingsPage.SEARCH_ENGINES) }, s.searchEngine.name, Icons.Rounded.Search)
        GroupDivider()
        SwitchRow("Search suggestions", s.searchSuggestions, { v -> vm.launchSettings { it.copy(searchSuggestions = v) } }, "Sends what you type to ${s.searchEngine.name}")
    }
    SettingsGroup("Browsing") {
        ChoiceRow("New tab opens", HomepageMode.entries, s.homepageMode, { if (it == HomepageMode.HOME) "Start page" else "Custom page" }, { v -> vm.launchSettings { it.copy(homepageMode = v) } })
        if (s.homepageMode == HomepageMode.CUSTOM) {
            GroupDivider()
            ActionRow("Custom page", { editHome = true }, s.homepageUrl.ifBlank { "Not set" })
        }
        GroupDivider()
        ChoiceRow(
            "Open links in other apps", ExternalLinks.entries, s.externalLinks,
            { when (it) { ExternalLinks.ASK -> "Ask first"; ExternalLinks.ALWAYS -> "Always"; ExternalLinks.NEVER -> "Never" } },
            { v -> vm.launchSettings { it.copy(externalLinks = v) } },
        )
    }
    SettingsGroup("Downloads") {
        ChoiceRow(
            "Download location", DownloadLocation.entries, s.downloadLocation,
            { if (it == DownloadLocation.DOWNLOADS) "Downloads" else "Downloads/Eddy" },
            { v -> vm.launchSettings { it.copy(downloadLocation = v) } },
        )
    }
    if (editHome) {
        TextInputDialog("Custom page", s.homepageUrl, "Address", keyboardType = KeyboardType.Uri, onDismiss = { editHome = false }) { v ->
            vm.launchSettings { it.copy(homepageUrl = v) }
            editHome = false
        }
    }
}

@Composable
private fun TabSettings(vm: BrowserViewModel, s: Settings) {
    SettingsGroup("Behavior") {
        SwitchRow("Restore tabs on launch", s.restoreTabs, { v -> vm.launchSettings { it.copy(restoreTabs = v) } }, "Eddy does not restore incognito tabs")
        GroupDivider()
        ChoiceRow("Tab switcher layout", TabLayout.entries, s.tabLayout, { if (it == TabLayout.GRID) "Grid" else "List" }, { v -> vm.launchSettings { it.copy(tabLayout = v) } })
        GroupDivider()
        ChoiceRow(
            "After closing a tab, show", CloseTabBehavior.entries, s.closeTabBehavior,
            { if (it == CloseTabBehavior.ADJACENT) "The neighbouring tab" else "The most recently used tab" },
            { v -> vm.launchSettings { it.copy(closeTabBehavior = v) } },
        )
    }
}

@Composable
private fun AdvancedSettings(vm: BrowserViewModel, s: Settings) {
    var editUa by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    SettingsGroup("Compatibility") {
        ChoiceRow(
            "User agent", UserAgentMode.entries, s.userAgentMode,
            { when (it) { UserAgentMode.DEFAULT -> "Mobile (default)"; UserAgentMode.DESKTOP -> "Desktop site everywhere"; UserAgentMode.CUSTOM -> "Custom" } },
            { v -> vm.launchSettings { it.copy(userAgentMode = v) } },
        )
        if (s.userAgentMode == UserAgentMode.CUSTOM) {
            GroupDivider()
            ActionRow("Custom user agent", { editUa = true }, s.customUserAgent.ifBlank { "Not set (uses mobile default)" })
        }
    }
    SettingsGroup("Search and filtering") {
        NavRow("Custom search engines", { vm.settingsStack.add(SettingsPage.SEARCH_ENGINES) })
        GroupDivider()
        NavRow("Custom filter lists", { vm.settingsStack.add(SettingsPage.FILTER_LISTS) })
    }
    SettingsGroup("Developer") {
        SwitchRow("Web debugging", s.webDebugging, { v -> vm.launchSettings { it.copy(webDebugging = v) } }, "Lets chrome://inspect attach to pages over USB")
        GroupDivider()
        ActionRow("Reset all settings", { confirmReset = true }, "Restores defaults. Bookmarks and history stay.")
    }
    if (editUa) TextInputDialog("Custom user agent", s.customUserAgent, "User agent string", onDismiss = { editUa = false }) { v -> vm.launchSettings { it.copy(customUserAgent = v) }; editUa = false }
    if (confirmReset) {
        ConfirmDialog("Reset all settings?", "Eddy restores every setting to its default.", "Reset", { confirmReset = false }) {
            confirmReset = false
            vm.launchSettings { Settings(shortcuts = it.shortcuts) }
        }
    }
}

@Composable
private fun AboutSettings() {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    fun held() = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    var isDefault by remember { mutableStateOf(held()) }
    // The choice is made in a system screen, so re-check whenever we come back to the foreground.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = held() }
    val requestRole = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = held() }

    SettingsGroup {
        ActionRow("Version", {}, BuildConfig.VERSION_NAME)
        GroupDivider()
        if (isDefault) {
            ActionRow("Default browser", {}, "Eddy is your default browser", Icons.Rounded.CheckCircle)
        } else {
            ActionRow("Make Eddy your default browser", {
                if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    requestRole.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                } else {
                    context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, "Opens a system prompt")
        }
    }
    SettingsFootnote(
        "Eddy has no advertising, analytics or tracking code and sends no telemetry. " +
            "It contacts the network to load the pages you open, fetch search suggestions (if you enable them), " +
            "fetch favicons for sites on your home page, and update filter lists.",
    )
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
                "Beta · Eddy is in development. Expect bugs.",
                Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
