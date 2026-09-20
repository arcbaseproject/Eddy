package app.eddy.browser.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.privacy.get
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils
import java.text.DateFormat

/** Per-site panel opened from the address bar: connection, blocking, permissions and stored data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(vm: BrowserViewModel, tab: BrowserTab, onDismiss: () -> Unit) {
    val host = UrlUtils.displayHost(tab.url)
    val site = vm.sites.peek(host)
    var refresh by remember { mutableStateOf(0) } // sites.peek is not observable, so nudge recomposition after edits
    val cookieCount = remember(tab.url, refresh) { vm.cookieCount(tab) }
    val cert = remember(tab.url) { tab.webView?.certificate }
    val settings by vm.settingsState.collectAsStateWithLifecycle()

    fun set(feature: SiteFeature, value: Int?) { vm.setSiteSetting(tab, feature, value); refresh++ }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.gutterLarge).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(host, size = Dimens.faviconLarge, live = tab.favicon)
                Column(Modifier.padding(start = 14.dp)) {
                    Text(host.ifEmpty { "This page" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (tab.security) {
                            Security.SECURE -> "Connection is secure"
                            Security.INSECURE -> "Connection is not secure"
                            Security.ERROR -> "Certificate problem"
                            Security.NONE -> "No connection information"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tab.security == Security.SECURE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            cert?.let {
                Text(
                    "Issued by ${it.issuedBy?.oName ?: it.issuedBy?.cName ?: "unknown"}" +
                        (it.validNotAfterDate?.let { d -> " · expires ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(d)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ad and tracker blocking", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (!settings.adBlock && !settings.trackerProtection) "Turned off in settings"
                        else "${tab.blockedCount} blocked on this page",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = site?.contentBlocking != SiteSettings.BLOCK,
                    onCheckedChange = { set(SiteFeature.CONTENT_BLOCKING, if (it) null else SiteSettings.BLOCK) },
                    enabled = settings.adBlock || settings.trackerProtection,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Always use desktop site", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = site?.desktop == SiteSettings.ALLOW,
                    onCheckedChange = { vm.toggleDesktopForSite(); refresh++ },
                )
            }
            HorizontalDivider()

            Text("Permissions", style = MaterialTheme.typography.titleSmall)
            PermissionRow("Location", site?.get(SiteFeature.LOCATION), "Ask") { set(SiteFeature.LOCATION, it) }
            PermissionRow("Camera", site?.get(SiteFeature.CAMERA), "Ask") { set(SiteFeature.CAMERA, it) }
            PermissionRow("Microphone", site?.get(SiteFeature.MICROPHONE), "Ask") { set(SiteFeature.MICROPHONE, it) }
            PermissionRow("JavaScript", site?.get(SiteFeature.JAVASCRIPT), if (settings.javascript) "Allowed (default)" else "Blocked (default)") { set(SiteFeature.JAVASCRIPT, it) }
            PermissionRow("Pop-ups", site?.get(SiteFeature.POPUPS), "Blocked (default)") { set(SiteFeature.POPUPS, it) }
            PermissionRow("Third-party cookies", site?.get(SiteFeature.THIRD_PARTY_COOKIES), "Follows settings") { set(SiteFeature.THIRD_PARTY_COOKIES, it) }
            HorizontalDivider()

            Text("Site data", style = MaterialTheme.typography.titleSmall)
            Text(
                if (cookieCount == 0) "No cookies stored for this page" else "$cookieCount cookies stored for this page",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.clearSiteData(tab); onDismiss() }) { Text("Clear site data") }
                TextButton(onClick = { vm.sites.reset(host); vm.setSiteSetting(tab, SiteFeature.CONTENT_BLOCKING, null); refresh++ }, enabled = site != null) { Text("Reset permissions") }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, value: Int?, defaultLabel: String, onChange: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.touchTarget), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Surface(onClick = { open = true }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                when (value) { SiteSettings.ALLOW -> "Allow"; SiteSettings.BLOCK -> "Block"; else -> defaultLabel },
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(defaultLabel) }, onClick = { open = false; onChange(null) })
                DropdownMenuItem(text = { Text("Allow") }, onClick = { open = false; onChange(SiteSettings.ALLOW) })
                DropdownMenuItem(text = { Text("Block") }, onClick = { open = false; onChange(SiteSettings.BLOCK) })
            }
        }
    }
}
