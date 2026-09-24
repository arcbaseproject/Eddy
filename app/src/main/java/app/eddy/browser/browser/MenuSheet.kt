package app.eddy.browser.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.ChromeReaderMode
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils

/**
 * Page menu. Page actions (forward, reload, bookmark, share, find, desktop site) only appear when a page is open,
 * so the new-tab page gets a short menu instead of a wall of greyed-out buttons. Everything else is grouped:
 * tabs, this page, library, settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(vm: BrowserViewModel, tab: BrowserTab?, bookmarked: Boolean, onDismiss: () -> Unit) {
    val webPage = tab != null && UrlUtils.isWebUrl(tab.url)
    fun act(block: () -> Unit): () -> Unit = { onDismiss(); block() }
    // Big system fonts cannot fit four buttons in a row without breaking words.
    val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.gutter).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (webPage) {
                val quick = listOf(
                    Quick(Icons.AutoMirrored.Rounded.ArrowForward, "Forward", tab?.canGoForward == true, false, act { vm.goForward() }),
                    Quick(Icons.Rounded.Refresh, "Reload", tab?.webView != null, false, act { vm.reloadOrStop() }),
                    Quick(
                        if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                        if (bookmarked) "Saved" else "Bookmark", true, bookmarked, act { vm.toggleBookmark() },
                    ),
                    Quick(Icons.Rounded.Share, "Share", true, false, act { vm.share() }),
                    Quick(Icons.Rounded.Search, "Find", tab?.webView != null && tab.error == null, false, { vm.openFind() }),
                    Quick(
                        Icons.Rounded.ChromeReaderMode, "Reader", tab?.webView != null, tab?.readerActive == true,
                        act { vm.toggleReader() },
                    ),
                    Quick(
                        if (vm.readingAloud) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                        if (vm.readingAloud) "Stop" else "Read aloud",
                        tab?.webView != null && tab.error == null, vm.readingAloud, act { vm.toggleReadAloud() },
                    ),
                    Quick(Icons.Rounded.Print, "Print", tab?.webView != null && tab.error == null, false, act { vm.printPage() }),
                )
                quick.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { QuickButton(it, Modifier.weight(1f)) } }
                }
            }

            Group {
                Row(Icons.Rounded.Add, "New tab", act { vm.newTab() })
                Row(Icons.Rounded.VisibilityOff, "New incognito tab", act { vm.newTab(incognito = true) })
                if (vm.tabs.closedCount > 0) Row(Icons.Rounded.Restore, "Reopen closed tab", act { vm.restoreClosedTab() })
            }

            if (webPage) {
                Group {
                    SwitchRow(Icons.Rounded.DesktopWindows, "Desktop site", tab?.desktopActive == true, tab?.webView != null) { onDismiss(); vm.toggleDesktop() }
                    Row(Icons.Rounded.Home, "Add to home screen", act { tab?.let(vm::addShortcut) })
                    if (DevTools.available(LocalContext.current)) {
                        SwitchRow(Icons.Rounded.Code, "Developer tools", tab?.devToolsActive == true, tab?.webView != null) { onDismiss(); vm.toggleDevTools() }
                    }
                }
            }

            val library = listOf(
                Quick(Icons.Rounded.Download, "Downloads", true, false, act { vm.screen = Screen.DOWNLOADS }),
                Quick(Icons.Rounded.History, "History", true, false, act { vm.screen = Screen.HISTORY }),
                Quick(Icons.Rounded.Bookmarks, "Bookmarks", true, false, act { vm.screen = Screen.BOOKMARKS }),
                Quick(Icons.Rounded.Key, "Passwords", true, false, act { vm.screen = Screen.PASSWORDS }),
            )
            library.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { QuickButton(it, Modifier.weight(1f)) } }
            }

            Group { Row(Icons.Rounded.Settings, "Settings", act { vm.screen = Screen.SETTINGS }) }
        }
    }
}

private class Quick(val icon: ImageVector, val label: String, val enabled: Boolean, val highlighted: Boolean, val onClick: () -> Unit)

/** Icon over label, used for the page actions and the library shortcuts. */
@Composable
private fun QuickButton(q: Quick, modifier: Modifier) {
    Surface(
        onClick = q.onClick,
        enabled = q.enabled,
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (q.highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (q.highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp).alpha(if (q.enabled) 1f else 0.38f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(q.icon, null, Modifier.size(24.dp))
            Text(q.label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** One rounded card holding a few related rows, separated by hairlines. */
@Composable
private fun Group(content: @Composable GroupScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column { GroupScope.content() }
    }
}

private object GroupScope

@Composable
private fun GroupScope.Row(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp).alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.padding(start = 16.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GroupScope.SwitchRow(icon: ImageVector, label: String, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).toggleable(checked, enabled = enabled, role = Role.Switch) { onToggle() }
            .padding(start = 18.dp, end = 14.dp).alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, Modifier.padding(start = 16.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
