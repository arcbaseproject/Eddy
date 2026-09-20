package app.eddy.browser.history

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.Screen
import app.eddy.browser.data.database.HistoryEntry
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.components.ConfirmDialog
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.ScreenScaffold
import app.eddy.browser.ui.components.SearchField
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.theme.Dimens
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(vm: BrowserViewModel) {
    var query by remember { mutableStateOf("") }
    val entries by remember(query) { vm.history.entries(query) }.collectAsStateWithLifecycle(emptyList())
    var clearMenu by remember { mutableStateOf(false) }
    var confirmAll by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val grouped = remember(entries) { entries.groupBy { Instant.ofEpochMilli(it.visitTime).atZone(zone).toLocalDate() } }

    ScreenScaffold(
        "History", onBack = { vm.screen = Screen.BROWSER },
        actions = {
            Box {
                EddyIconButton(Icons.Rounded.DeleteSweep, "Clear history", { clearMenu = true })
                DropdownMenu(clearMenu, { clearMenu = false }) {
                    listOf("Last hour" to TimeUnit.HOURS.toMillis(1), "Last 24 hours" to TimeUnit.DAYS.toMillis(1), "Last 7 days" to TimeUnit.DAYS.toMillis(7)).forEach { (label, ms) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = { clearMenu = false; vm.history.deleteSince(ms); vm.snackbar("History cleared: ${label.lowercase()}") })
                    }
                    DropdownMenuItem(text = { Text("All time") }, onClick = { clearMenu = false; confirmAll = true })
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, "Search history")
            if (entries.isEmpty()) {
                EmptyState(Icons.Rounded.History, if (query.isBlank()) "No history yet" else "No matches", if (query.isBlank()) "Pages you visit will show up here." else "Try a different search.")
            } else {
                LazyColumn(Modifier.weight(1f).navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                    grouped.forEach { (date, list) ->
                        stickyHeader(key = date.toString()) { DateHeader(date) }
                        items(list, key = { it.id }) { entry -> HistoryRow(vm, entry) }
                    }
                }
            }
        }
    }

    if (confirmAll) {
        ConfirmDialog("Clear all history?", "Eddy will erase every page from your browsing history.", "Clear all", { confirmAll = false }) {
            confirmAll = false
            vm.history.clear()
            vm.snackbar("History cleared")
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
    Text(
        label,
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = Dimens.gutter, vertical = 10.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(vm: BrowserViewModel, entry: HistoryEntry) {
    val haptics = rememberHaptics()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val time = remember(entry.visitTime) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(Instant.ofEpochMilli(entry.visitTime).atZone(ZoneId.systemDefault())) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .combinedClickable(role = Role.Button, onClickLabel = "Open", onLongClickLabel = "More actions", onClick = { vm.navigate(entry.url) }, onLongClick = { haptics.longPress(); menu = true })
                .padding(horizontal = Dimens.gutter, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SiteIcon(entry.host, size = Dimens.faviconLarge)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(entry.title.ifBlank { entry.host }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.host} · $time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            EddyIconButton(Icons.Rounded.Delete, "Delete ${entry.title.ifBlank { entry.host }}", { vm.history.delete(entry.id) }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("Open in new tab") }, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) }, onClick = { menu = false; vm.openInNewTab(entry.url) })
            DropdownMenuItem(text = { Text("Open in incognito tab") }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) }, onClick = { menu = false; vm.openInNewTab(entry.url, incognito = true) })
            DropdownMenuItem(text = { Text("Copy link") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = {
                menu = false
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", entry.url))
                vm.snackbar("Copied")
            })
            DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; vm.history.delete(entry.id) })
        }
    }
}
