package app.eddy.browser.settings

import app.eddy.browser.R
import app.eddy.browser.util.str
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Https
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.DataType
import app.eddy.browser.browser.Screen
import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.data.models.CookieMode
import app.eddy.browser.data.models.FilterList
import app.eddy.browser.data.models.Settings
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.privacy.get
import app.eddy.browser.ui.components.ChoiceDialog
import app.eddy.browser.ui.components.CheckRow
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.EddyIconButton
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

@Composable
fun PrivacySettings(vm: BrowserViewModel, s: Settings) {
    var clearing by remember { mutableStateOf(false) }
    val rules by vm.blockerRules.collectAsStateWithLifecycle()

    SettingsGroup(str(R.string.protection)) {
        SwitchRow(str(R.string.ad_blocking), s.adBlock, { v -> vm.launchSettings { it.copy(adBlock = v) } }, str(R.string.blocks_requests_to_known_ad_servers_doma, rules), Icons.Rounded.Shield)
        GroupDivider()
        SwitchRow(str(R.string.tracker_protection), s.trackerProtection, { v -> vm.launchSettings { it.copy(trackerProtection = v) } }, str(R.string.blocks_analytics_and_tracking_domains), Icons.Rounded.VisibilityOff)
        GroupDivider()
        NavRow(str(R.string.filter_lists), { vm.settingsStack.add(SettingsPage.FILTER_LISTS) }, str(R.string.enable_add_and_update_lists), Icons.Rounded.Tune)
        GroupDivider()
        SwitchRow(str(R.string.do_not_track_and_global_privacy_control), s.doNotTrack, { v -> vm.launchSettings { it.copy(doNotTrack = v) } }, str(R.string.asks_sites_not_to_sell_or_share_your_dat), Icons.Rounded.Lock)
        GroupDivider()
        SwitchRow(str(R.string.https_only_mode), s.httpsOnly, { v -> vm.launchSettings { it.copy(httpsOnly = v) } }, str(R.string.loads_sites_over_https_and_warns_before), Icons.Rounded.Https)
        GroupDivider()
        SwitchRow(str(R.string.always_open_incognito), s.alwaysIncognito, { v -> vm.launchSettings { it.copy(alwaysIncognito = v) } }, str(R.string.new_tabs_links_from_other_apps_and_widge), Icons.Rounded.VisibilityOff)
    }
    SettingsGroup(str(R.string.passwords)) {
        SwitchRow(str(R.string.offer_to_save_passwords), s.savePasswords, { v -> vm.launchSettings { it.copy(savePasswords = v) } }, if (vm.pageBridgeSupported) str(R.string.asks_after_you_sign_in_or_sign_up) else str(R.string.needs_a_newer_android_system_webview), Icons.Rounded.Key, enabled = vm.pageBridgeSupported)
        GroupDivider()
        SwitchRow(str(R.string.autofill_passwords), s.autofillPasswords, { v -> vm.launchSettings { it.copy(autofillPasswords = v) } }, if (vm.pageBridgeSupported) str(R.string.suggests_a_saved_login_when_you_tap_a_si) else str(R.string.needs_a_newer_android_system_webview), Icons.Rounded.Lock, enabled = vm.pageBridgeSupported)
        GroupDivider()
        ActionRow(str(R.string.saved_passwords), { vm.screen = Screen.PASSWORDS }, str(R.string.needs_your_screen_lock), Icons.Rounded.Key)
    }
    SettingsGroup(str(R.string.site_data)) {
        ChoiceRow(
            str(R.string.cookies), CookieMode.entries, s.cookieMode,
            { when (it) { CookieMode.ALLOW_ALL -> str(R.string.allow_all); CookieMode.BLOCK_THIRD_PARTY -> str(R.string.block_third_party); CookieMode.BLOCK_ALL -> str(R.string.block_all_may_break_sites) } },
            { v -> vm.launchSettings { it.copy(cookieMode = v) } }, Icons.Rounded.Cookie,
        )
        GroupDivider()
        SwitchRow(str(R.string.javascript), s.javascript, { v -> vm.launchSettings { it.copy(javascript = v) } }, str(R.string.turning_this_off_breaks_most_modern_site), Icons.Rounded.Code)
        GroupDivider()
        NavRow(str(R.string.site_permissions), { vm.settingsStack.add(SettingsPage.SITE_PERMISSIONS) }, str(R.string.per_site_choices_for_location_camera_and), Icons.Rounded.Language)
        GroupDivider()
        ActionRow(str(R.string.clear_browsing_data), { clearing = true }, str(R.string.history_cookies_cache_and_more), Icons.Rounded.DeleteSweep)
        GroupDivider()
        SwitchRow(str(R.string.clear_when_i_leave), s.clearOnExit, { v -> vm.launchSettings { it.copy(clearOnExit = v) } }, str(R.string.deletes_history_open_tabs_cookies_site_d), Icons.Rounded.Delete)
    }
    SettingsFootnote(
        if (vm.incognitoIsolated) str(R.string.incognito_tabs_use_a_separate_storage_pr)
        else str(R.string.this_device_s_webview_cannot_isolate_inc),
    )
    if (clearing) ClearDataDialog(vm) { clearing = false }
}

@Composable
private fun ClearDataDialog(vm: BrowserViewModel, onDismiss: () -> Unit) {
    var types by remember { mutableStateOf(setOf(DataType.HISTORY, DataType.CACHE)) }
    var range by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    val ranges = listOf<Long?>(TimeUnit.HOURS.toMillis(1), TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(7), null)
    fun rangeLabel(r: Long?) = when (r) { TimeUnit.HOURS.toMillis(1) -> str(R.string.last_hour); TimeUnit.DAYS.toMillis(1) -> str(R.string.last_24_hours); TimeUnit.DAYS.toMillis(7) -> str(R.string.last_7_days); else -> str(R.string.all_time) }
    fun toggle(t: DataType, on: Boolean) { types = if (on) types + t else types - t }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str(R.string.clear_browsing_data)) },
        text = {
            Column {
                TextButton(onClick = { picking = true }) { Text(str(R.string.browsing_history_range, rangeLabel(range))) }
                CheckRow(str(R.string.browsing_history), DataType.HISTORY in types) { toggle(DataType.HISTORY, it) }
                CheckRow(str(R.string.cookies_and_site_data), DataType.COOKIES in types) { toggle(DataType.COOKIES, it) }
                CheckRow(str(R.string.cached_images_and_files), DataType.CACHE in types) { toggle(DataType.CACHE, it) }
                CheckRow(str(R.string.download_list), DataType.DOWNLOADS in types) { toggle(DataType.DOWNLOADS, it) }
                CheckRow(str(R.string.site_permissions), DataType.SITE_SETTINGS in types) { toggle(DataType.SITE_SETTINGS, it) }
                CheckRow(str(R.string.saved_passwords), DataType.PASSWORDS in types) { toggle(DataType.PASSWORDS, it) }
            }
        },
        confirmButton = { TextButton(enabled = types.isNotEmpty(), onClick = { vm.clearBrowsingData(types, range); onDismiss() }) { Text(str(R.string.clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str(R.string.cancel)) } },
    )
    if (picking) ChoiceDialog(str(R.string.time_range), ranges, range, ::rangeLabel, { picking = false }) { range = it }
}

@Composable
fun SitePermissionsSettings(vm: BrowserViewModel) {
    val rows by remember { vm.sites.all() }.collectAsStateWithLifecycle(emptyList())
    if (rows.isEmpty()) {
        EmptyState(Icons.Rounded.Language, str(R.string.no_site_exceptions), str(R.string.site_choices_you_set_in_the_site_informa))
        return
    }
    SettingsGroup {
        rows.forEachIndexed { i, site ->
            if (i > 0) GroupDivider()
            val summary = SiteFeature.entries.mapNotNull { f ->
                site.get(f)?.let { v ->
                    // Text size stores a percentage; every other feature stores allow/block.
                    if (f == SiteFeature.TEXT_ZOOM) "${label(f)}: $v%"
                    else "${label(f)}: ${if (v == SiteSettings.ALLOW) "allow" else "block"}"
                }
            }.joinToString(" · ")
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(site.host, style = MaterialTheme.typography.bodyLarge)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EddyIconButton(Icons.Rounded.Delete, str(R.string.reset, site.host), { vm.sites.reset(site.host) })
            }
        }
    }
    OutlinedButton(onClick = vm.sites::resetAll, Modifier.padding(16.dp)) { Text(str(R.string.reset_all_site_permissions)) }
}

private fun label(f: SiteFeature) = when (f) {
    SiteFeature.LOCATION -> "location"
    SiteFeature.CAMERA -> "camera"
    SiteFeature.MICROPHONE -> "microphone"
    SiteFeature.JAVASCRIPT -> str(R.string.javascript)
    SiteFeature.POPUPS -> "pop-ups"
    SiteFeature.THIRD_PARTY_COOKIES -> "3rd-party cookies"
    SiteFeature.DESKTOP -> "desktop site"
    SiteFeature.CONTENT_BLOCKING -> "blocking"
    SiteFeature.TEXT_ZOOM -> "text size"
}

@Composable
fun FilterListsSettings(vm: BrowserViewModel, s: Settings) {
    val scope = rememberCoroutineScope()
    val updating by vm.blockerUpdating.collectAsStateWithLifecycle()
    val rules by vm.blockerRules.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    SettingsGroup(str(R.string.lists)) {
        s.filterLists.forEachIndexed { i, list ->
            if (i > 0) GroupDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(list.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        str(if (list.tracker) R.string.trackers else R.string.ads) + if (list.builtIn) str(R.string.built_in_suffix) else " · " + list.url.substringAfter("://").substringBefore('/'),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!list.builtIn) EddyIconButton(Icons.Rounded.Delete, str(R.string.remove, list.name), {
                    vm.blocker.deleteCache(list)
                    vm.launchSettings { it.copy(filterLists = it.filterLists.filterNot { l -> l.id == list.id }) }
                })
                Switch(list.enabled, { on ->
                    vm.launchSettings { it.copy(filterLists = it.filterLists.map { l -> if (l.id == list.id) l.copy(enabled = on) else l }) }
                    // A list enabled for the first time has nothing cached yet; fetch it right away.
                    if (on && !list.builtIn) scope.launch { vm.blocker.update(s.filterLists.map { l -> if (l.id == list.id) l.copy(enabled = true) else l }) }
                })
            }
        }
    }
    SettingsGroup {
        ActionRow(str(R.string.add_filter_list), { adding = true }, str(R.string.host_or_domain_lists_by_url), Icons.Rounded.Add)
        GroupDivider()
        ActionRow(
            if (updating) "Updating…" else str(R.string.update_now),
            { scope.launch { val fail = vm.blocker.update(s.filterLists); vm.launchSettings { it.copy(filterListsUpdatedAt = System.currentTimeMillis()) }; vm.snackbar(if (fail == 0) str(R.string.filter_lists_updated) else "$fail lists failed to update") } },
            (if (s.filterListsUpdatedAt > 0) str(R.string.last_updated) + DateUtils.getRelativeTimeSpanString(s.filterListsUpdatedAt) else str(R.string.never_updated)) + " · $rules domains active",
            Icons.Rounded.Refresh,
        )
    }
    SettingsFootnote(str(R.string.eddy_refreshes_lists_about_once_a_day_wh))

    if (adding) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("https://") }
        var tracker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text(str(R.string.add_filter_list)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.name)) }, singleLine = true)
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text(str(R.string.list_url)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    CheckRow(str(R.string.treat_as_tracker_list), tracker) { tracker = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && url.startsWith("https://") && url.length > 10, onClick = {
                    val list = FilterList(UUID.randomUUID().toString(), name.trim(), url.trim(), tracker = tracker)
                    vm.launchSettings { it.copy(filterLists = it.filterLists + list) }
                    scope.launch { vm.blocker.update(s.filterLists + list) }
                    adding = false
                }) { Text(str(R.string.add)) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(str(R.string.cancel)) } },
        )
    }
}
