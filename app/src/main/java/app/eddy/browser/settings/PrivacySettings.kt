package app.eddy.browser.settings

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

    SettingsGroup("Protection") {
        SwitchRow("Ad blocking", s.adBlock, { v -> vm.launchSettings { it.copy(adBlock = v) } }, "Blocks requests to known ad servers · $rules domains loaded", Icons.Rounded.Shield)
        GroupDivider()
        SwitchRow("Tracker protection", s.trackerProtection, { v -> vm.launchSettings { it.copy(trackerProtection = v) } }, "Blocks analytics and tracking domains", Icons.Rounded.VisibilityOff)
        GroupDivider()
        NavRow("Filter lists", { vm.settingsStack.add(SettingsPage.FILTER_LISTS) }, "Enable, add and update lists", Icons.Rounded.Tune)
        GroupDivider()
        SwitchRow("Do Not Track and Global Privacy Control", s.doNotTrack, { v -> vm.launchSettings { it.copy(doNotTrack = v) } }, "Asks sites not to sell or share your data", Icons.Rounded.Lock)
    }
    SettingsGroup("Passwords") {
        SwitchRow("Offer to save passwords", s.savePasswords, { v -> vm.launchSettings { it.copy(savePasswords = v) } }, if (vm.pageBridgeSupported) "Asks after you sign in or sign up" else "Needs a newer Android System WebView", Icons.Rounded.Key, enabled = vm.pageBridgeSupported)
        GroupDivider()
        SwitchRow("Autofill passwords", s.autofillPasswords, { v -> vm.launchSettings { it.copy(autofillPasswords = v) } }, if (vm.pageBridgeSupported) "Suggests a saved login when you tap a sign-in field" else "Needs a newer Android System WebView", Icons.Rounded.Lock, enabled = vm.pageBridgeSupported)
        GroupDivider()
        ActionRow("Saved passwords", { vm.screen = Screen.PASSWORDS }, "Needs your screen lock", Icons.Rounded.Key)
    }
    SettingsGroup("Site data") {
        ChoiceRow(
            "Cookies", CookieMode.entries, s.cookieMode,
            { when (it) { CookieMode.ALLOW_ALL -> "Allow all"; CookieMode.BLOCK_THIRD_PARTY -> "Block third-party"; CookieMode.BLOCK_ALL -> "Block all (may break sites)" } },
            { v -> vm.launchSettings { it.copy(cookieMode = v) } }, Icons.Rounded.Cookie,
        )
        GroupDivider()
        SwitchRow("JavaScript", s.javascript, { v -> vm.launchSettings { it.copy(javascript = v) } }, "Turning this off breaks most modern sites", Icons.Rounded.Code)
        GroupDivider()
        NavRow("Site permissions", { vm.settingsStack.add(SettingsPage.SITE_PERMISSIONS) }, "Per-site choices for location, camera and more", Icons.Rounded.Language)
        GroupDivider()
        ActionRow("Clear browsing data", { clearing = true }, "History, cookies, cache and more", Icons.Rounded.DeleteSweep)
    }
    SettingsFootnote(
        if (vm.incognitoIsolated) "Incognito tabs use a separate storage profile. Eddy deletes it when the last incognito tab closes."
        else "This device's WebView cannot isolate incognito storage. Incognito tabs skip history and cache but share cookies with normal tabs.",
    )
    if (clearing) ClearDataDialog(vm) { clearing = false }
}

@Composable
private fun ClearDataDialog(vm: BrowserViewModel, onDismiss: () -> Unit) {
    var types by remember { mutableStateOf(setOf(DataType.HISTORY, DataType.CACHE)) }
    var range by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }
    val ranges = listOf<Long?>(TimeUnit.HOURS.toMillis(1), TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(7), null)
    fun rangeLabel(r: Long?) = when (r) { TimeUnit.HOURS.toMillis(1) -> "Last hour"; TimeUnit.DAYS.toMillis(1) -> "Last 24 hours"; TimeUnit.DAYS.toMillis(7) -> "Last 7 days"; else -> "All time" }
    fun toggle(t: DataType, on: Boolean) { types = if (on) types + t else types - t }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear browsing data") },
        text = {
            Column {
                TextButton(onClick = { picking = true }) { Text("Browsing history range: ${rangeLabel(range)}") }
                CheckRow("Browsing history", DataType.HISTORY in types) { toggle(DataType.HISTORY, it) }
                CheckRow("Cookies and site data", DataType.COOKIES in types) { toggle(DataType.COOKIES, it) }
                CheckRow("Cached images and files", DataType.CACHE in types) { toggle(DataType.CACHE, it) }
                CheckRow("Download list", DataType.DOWNLOADS in types) { toggle(DataType.DOWNLOADS, it) }
                CheckRow("Site permissions", DataType.SITE_SETTINGS in types) { toggle(DataType.SITE_SETTINGS, it) }
                CheckRow("Saved passwords", DataType.PASSWORDS in types) { toggle(DataType.PASSWORDS, it) }
            }
        },
        confirmButton = { TextButton(enabled = types.isNotEmpty(), onClick = { vm.clearBrowsingData(types, range); onDismiss() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (picking) ChoiceDialog("Time range", ranges, range, ::rangeLabel, { picking = false }) { range = it }
}

@Composable
fun SitePermissionsSettings(vm: BrowserViewModel) {
    val rows by remember { vm.sites.all() }.collectAsStateWithLifecycle(emptyList())
    if (rows.isEmpty()) {
        EmptyState(Icons.Rounded.Language, "No site exceptions", "Site choices you set in the site information panel appear here.")
        return
    }
    SettingsGroup {
        rows.forEachIndexed { i, site ->
            if (i > 0) GroupDivider()
            val summary = SiteFeature.entries.mapNotNull { f ->
                site.get(f)?.let { v -> "${label(f)}: ${if (v == SiteSettings.ALLOW) "allow" else "block"}" }
            }.joinToString(" · ")
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(site.host, style = MaterialTheme.typography.bodyLarge)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                EddyIconButton(Icons.Rounded.Delete, "Reset ${site.host}", { vm.sites.reset(site.host) })
            }
        }
    }
    OutlinedButton(onClick = vm.sites::resetAll, Modifier.padding(16.dp)) { Text("Reset all site permissions") }
}

private fun label(f: SiteFeature) = when (f) {
    SiteFeature.LOCATION -> "location"
    SiteFeature.CAMERA -> "camera"
    SiteFeature.MICROPHONE -> "microphone"
    SiteFeature.JAVASCRIPT -> "JavaScript"
    SiteFeature.POPUPS -> "pop-ups"
    SiteFeature.THIRD_PARTY_COOKIES -> "3rd-party cookies"
    SiteFeature.DESKTOP -> "desktop site"
    SiteFeature.CONTENT_BLOCKING -> "blocking"
}

@Composable
fun FilterListsSettings(vm: BrowserViewModel, s: Settings) {
    val scope = rememberCoroutineScope()
    val updating by vm.blockerUpdating.collectAsStateWithLifecycle()
    val rules by vm.blockerRules.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    SettingsGroup("Lists") {
        s.filterLists.forEachIndexed { i, list ->
            if (i > 0) GroupDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                    Text(list.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        (if (list.tracker) "Trackers" else "Ads") + if (list.builtIn) " · built in" else " · " + list.url.substringAfter("://").substringBefore('/'),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!list.builtIn) EddyIconButton(Icons.Rounded.Delete, "Remove ${list.name}", {
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
        ActionRow("Add filter list", { adding = true }, "Host or domain lists by URL", Icons.Rounded.Add)
        GroupDivider()
        ActionRow(
            if (updating) "Updating…" else "Update now",
            { scope.launch { val fail = vm.blocker.update(s.filterLists); vm.launchSettings { it.copy(filterListsUpdatedAt = System.currentTimeMillis()) }; vm.snackbar(if (fail == 0) "Filter lists updated" else "$fail lists failed to update") } },
            (if (s.filterListsUpdatedAt > 0) "Last updated " + DateUtils.getRelativeTimeSpanString(s.filterListsUpdatedAt) else "Never updated") + " · $rules domains active",
            Icons.Rounded.Refresh,
        )
    }
    SettingsFootnote("Eddy refreshes lists about once a day when you are online. It applies domain rules only.")

    if (adding) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("https://") }
        var tracker by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add filter list") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("List URL") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                    CheckRow("Treat as tracker list", tracker) { tracker = it }
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && url.startsWith("https://") && url.length > 10, onClick = {
                    val list = FilterList(UUID.randomUUID().toString(), name.trim(), url.trim(), tracker = tracker)
                    vm.launchSettings { it.copy(filterLists = it.filterLists + list) }
                    scope.launch { vm.blocker.update(s.filterLists + list) }
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}
