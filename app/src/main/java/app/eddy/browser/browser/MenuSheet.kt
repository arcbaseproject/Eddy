package app.eddy.browser.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils

/** Compact page menu: four quick actions on top, everything else as a two-column grid of tiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(vm: BrowserViewModel, tab: BrowserTab?, bookmarked: Boolean, onDismiss: () -> Unit) {
    val webPage = tab != null && UrlUtils.isWebUrl(tab.url)
    fun act(block: () -> Unit): () -> Unit = { onDismiss(); block() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.gutter).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction(Icons.AutoMirrored.Rounded.ArrowForward, "Forward", tab?.canGoForward == true, Modifier.weight(1f), act { vm.goForward() })
                QuickAction(Icons.Rounded.Refresh, "Reload", tab?.webView != null, Modifier.weight(1f), act { vm.reloadOrStop() })
                QuickAction(
                    if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    if (bookmarked) "Bookmarked" else "Bookmark", webPage, Modifier.weight(1f), act { vm.toggleBookmark() },
                    highlighted = bookmarked,
                )
                QuickAction(Icons.Rounded.Share, "Share", webPage, Modifier.weight(1f), act { vm.share() })
            }

            val tiles = buildList<Tile> {
                add(Tile(Icons.Rounded.Add, "New tab", onClick = act { vm.newTab() }))
                add(Tile(Icons.Rounded.VisibilityOff, "New incognito tab", onClick = act { vm.newTab(incognito = true) }))
                add(Tile(Icons.Rounded.Search, "Find in page", enabled = tab?.webView != null && tab.error == null, onClick = { vm.openFind() }))
                add(
                    Tile(
                        Icons.Rounded.DesktopWindows, "Desktop site", enabled = tab?.webView != null,
                        state = if (tab?.desktopActive == true) "On" else "Off", onClick = act { vm.toggleDesktop() },
                    ),
                )
                add(Tile(Icons.Rounded.Home, "Add to home", enabled = webPage, onClick = act { tab?.let(vm::addShortcut) }))
                if (vm.tabs.closedCount > 0) add(Tile(Icons.Rounded.Restore, "Reopen closed tab", onClick = act { vm.restoreClosedTab() }))
                add(Tile(Icons.Rounded.Download, "Downloads", onClick = act { vm.screen = Screen.DOWNLOADS }))
                add(Tile(Icons.Rounded.History, "History", onClick = act { vm.screen = Screen.HISTORY }))
                add(Tile(Icons.Rounded.Bookmarks, "Bookmarks", onClick = act { vm.screen = Screen.BOOKMARKS }))
                add(Tile(Icons.Rounded.Settings, "Settings", onClick = act { vm.screen = Screen.SETTINGS }))
            }
            tiles.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { MenuTile(it, Modifier.weight(1f)) }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

private class Tile(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val state: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 76.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp).alphaIf(!enabled),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Icon(icon, null, Modifier.size(24.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun MenuTile(tile: Tile, modifier: Modifier) {
    Surface(
        onClick = tile.onClick,
        enabled = tile.enabled,
        modifier = modifier.heightIn(min = 60.dp).then(if (tile.state != null) Modifier.semantics { stateDescription = tile.state } else Modifier),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).alphaIf(!tile.enabled), verticalAlignment = Alignment.CenterVertically) {
            Icon(tile.icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(tile.label, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 2)
            tile.state?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun Modifier.alphaIf(dim: Boolean): Modifier = if (dim) alpha(0.38f) else this
