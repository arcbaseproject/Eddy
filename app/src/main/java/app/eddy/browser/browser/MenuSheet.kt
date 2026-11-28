package app.eddy.browser.browser

import app.eddy.browser.R
import app.eddy.browser.util.str
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import app.eddy.browser.ui.animation.rememberHaptics
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.automirrored.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.automirrored.rounded.ChromeReaderMode
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
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
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.util.UrlUtils

/**
 * Page menu. Page actions (forward, reload, bookmark, share, find, desktop site) only appear when a page is open,
 * so the new-tab page gets a short menu instead of a wall of greyed-out buttons. Everything else is grouped:
 * tabs, this page, library, settings.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MenuSheet(vm: BrowserViewModel, tab: BrowserTab?, bookmarked: Boolean, hidden: Set<String>, savedOrder: List<String>, onDismiss: () -> Unit) {
    val webPage = tab != null && UrlUtils.isWebUrl(tab.url)
    val haptics = rememberHaptics()
    val reorder = remember { MenuReorder(savedOrder, haptics::longPress) { order -> vm.launchSettings { it.copy(menuOrder = order) } } }
    // A long press that turns into a drag still ends in a tap on the item under the finger; ignore it.
    fun act(block: () -> Unit): () -> Unit = { if (reorder.dragging == null) { onDismiss(); block() } }
    // Big system fonts cannot fit four buttons in a row without breaking words.
    val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4

    var editing by rememberSaveable { mutableStateOf(false) }
    val devToolsAvailable = DevTools.available(LocalContext.current)
    val ready = tab?.webView != null
    val healthy = ready && tab?.error == null
    val actions = buildList {
        fun item(
            action: MenuItem, icon: ImageVector, available: Boolean = true,
            enabled: Boolean = true, active: Boolean = false, displayLabel: Int = action.label,
            click: () -> Unit,
        ) {
            if (editing || (available && action.name !in hidden)) {
                add(MenuAction(action, str(if (editing) action.label else displayLabel), icon, enabled, active, click))
            }
        }
        item(MenuItem.Forward, Icons.AutoMirrored.Rounded.ArrowForward, webPage, tab?.canGoForward == true, click = act { vm.goForward() })
        item(MenuItem.Reload, Icons.Rounded.Refresh, webPage, ready, click = act { vm.reload() })
        item(MenuItem.Bookmark, if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, webPage,
            active = bookmarked, displayLabel = if (bookmarked) R.string.saved_2 else R.string.bookmark, click = act { vm.toggleBookmark() })
        item(MenuItem.Share, Icons.Rounded.Share, webPage, click = act { vm.share() })
        item(MenuItem.Find, Icons.Rounded.Search, webPage, healthy) { if (reorder.dragging == null) vm.openFind() }
        item(MenuItem.Reader, Icons.AutoMirrored.Rounded.ChromeReaderMode, webPage, ready, tab?.readerActive == true, click = act { vm.toggleReader() })
        item(MenuItem.ReadAloud, if (vm.readingAloud) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp, webPage, healthy,
            vm.readingAloud, if (vm.readingAloud) R.string.stop else R.string.read_aloud, act { vm.toggleReadAloud() })
        item(MenuItem.Print, Icons.Rounded.Print, webPage, healthy, click = act { vm.printPage() })
        item(MenuItem.NewTab, Icons.Rounded.Add, click = act { vm.newTab() })
        item(MenuItem.NewIncognitoTab, Icons.Rounded.VisibilityOff, click = act { vm.newTab(incognito = true) })
        item(MenuItem.ReopenClosedTab, Icons.Rounded.Restore, vm.tabs.closedCount > 0, click = act { vm.restoreClosedTab() })
        item(MenuItem.DesktopSite, Icons.Rounded.DesktopWindows, webPage, ready, tab?.desktopActive == true, click = act { vm.toggleDesktop() })
        item(MenuItem.FullScreen, Icons.Rounded.Fullscreen, webPage, active = vm.fullscreen, click = act { vm.toggleFullscreen() })
        item(MenuItem.PlaybackSpeed, Icons.Rounded.Speed, webPage && healthy) {}
        item(MenuItem.Translate, Icons.Rounded.Translate, webPage && ready, click = act { vm.translateVisible = true })
        item(MenuItem.AddToStartPage, Icons.Rounded.Home, webPage, click = act { tab?.let(vm::addShortcut) })
        item(MenuItem.ViewSource, Icons.Rounded.Code, webPage && healthy, click = act { vm.viewPageSource() })
        item(MenuItem.AddToHomeScreen, Icons.AutoMirrored.Rounded.AddToHomeScreen, webPage && tab?.incognito == false, click = act { tab?.let(vm::addToLauncher) })
        if (devToolsAvailable) {
            item(MenuItem.DeveloperTools, Icons.Rounded.Code, webPage, ready, tab?.devToolsActive == true, click = act { vm.toggleDevTools() })
        }
        item(MenuItem.Downloads, Icons.Rounded.Download, click = act { vm.screen = Screen.DOWNLOADS })
        item(MenuItem.History, Icons.Rounded.History, click = act { vm.screen = Screen.HISTORY })
        item(MenuItem.Bookmarks, Icons.Rounded.Bookmarks, click = act { vm.screen = Screen.BOOKMARKS })
        item(MenuItem.Passwords, Icons.Rounded.Key, click = act { vm.screen = Screen.PASSWORDS })
        item(MenuItem.Settings, Icons.Rounded.Settings, click = act { vm.screen = Screen.SETTINGS })
    }.sortedBy { reorder.rank(it.action) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(Modifier.fillMaxWidth()) {
                BottomSheetDefaults.DragHandle(Modifier.align(Alignment.Center))
                if (!editing) EddyIconButton(
                    Icons.Rounded.Edit, str(R.string.edit_menu), { editing = true },
                    Modifier.align(Alignment.CenterEnd).padding(end = Dimens.gutter - 12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        if (editing) Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(str(R.string.edit_menu), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { editing = false }) { Text(str(R.string.done)) }
        }
        key(editing) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.gutter).padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                actions.groupBy { it.action.section }.forEach { (section, items) ->
                    if (!editing && (section == Section.Quick || section == Section.Library)) {
                        // One FlowRow, not a Row per line, so a button dragged to another line keeps its gesture.
                        FlowRow(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp), Arrangement.spacedBy(12.dp), maxItemsInEachRow = perRow) {
                            items.forEach { key(it.action) { QuickButton(it, Modifier.weight(1f).reorderable(reorder, it.action)) } }
                        }
                    } else Group {
                        items.forEach { item -> key(item.action) { Box(if (editing) Modifier else Modifier.reorderable(reorder, item.action)) {
                            when {
                                editing -> SwitchRow(item.icon, item.label, item.action.name !in hidden, true) {
                                    vm.launchSettings { it.copy(hiddenMenuItems = it.hiddenMenuItems.toggleMenuItem(item.action.name)) }
                                }
                                item.action == MenuItem.PlaybackSpeed -> Box {
                                    var speedOpen by remember { mutableStateOf(false) }
                                    Row(item.icon, item.label, { if (reorder.dragging == null) speedOpen = true })
                                    DropdownMenu(expanded = speedOpen, onDismissRequest = { speedOpen = false }) {
                                        listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0, 2.5, 3.0).forEach { rate ->
                                            DropdownMenuItem(text = { Text(rate.toString().removeSuffix(".0") + "×") }, onClick = act { vm.setPlaybackSpeed(rate) })
                                        }
                                    }
                                }
                                item.action in setOf(MenuItem.DesktopSite, MenuItem.FullScreen, MenuItem.DeveloperTools) ->
                                    SwitchRow(item.icon, item.label, item.highlighted, item.enabled, item.onClick)
                                else -> Row(item.icon, item.label, item.onClick, item.enabled)
                            }
                        } } }
                    }
                }
            }
        }
    }
}

internal fun Set<String>.toggleMenuItem(id: String): Set<String> = if (id in this) this - id else this + id

private enum class Section { Quick, Tabs, Page, Library, Settings }

/**
 * Long-press drag to reorder items within their section. Each item reports its bounds; while dragging, the item
 * follows the finger and swaps places with the item of the same section under it. The order is saved on release.
 */
private class MenuReorder(saved: List<String>, private val onPick: () -> Unit, private val save: (List<String>) -> Unit) {
    var order by mutableStateOf((saved + MenuItem.entries.map { it.name }).distinct())
    var dragging by mutableStateOf<MenuItem?>(null)
    val bounds = mutableStateMapOf<MenuItem, Rect>()
    private var pointer by mutableStateOf(Offset.Zero)
    private var grab = Offset.Zero
    private var moved = false

    fun rank(item: MenuItem) = item.section.ordinal * 1000 + order.indexOf(item.name)

    fun offsetOf(item: MenuItem): Offset =
        if (dragging == item) bounds[item]?.let { pointer - grab - it.topLeft } ?: Offset.Zero else Offset.Zero

    fun start(item: MenuItem, local: Offset) {
        val b = bounds[item] ?: return
        onPick()
        dragging = item
        grab = local
        pointer = b.topLeft + local
        moved = false
    }

    fun drag(delta: Offset) {
        val d = dragging ?: return
        pointer += delta
        val target = bounds.entries.firstOrNull { (k, r) -> k != d && k.section == d.section && r.contains(pointer) }?.key ?: return
        // Taking the target's old index puts the dragged item on the far side of it, whichever way it moved.
        val i = order.indexOf(target.name)
        order = order.toMutableList().apply { remove(d.name); add(i, d.name) }
        moved = true
    }

    fun end() {
        if (moved) save(order)
        dragging = null
    }
}

@Composable
private fun Modifier.reorderable(r: MenuReorder, item: MenuItem): Modifier {
    DisposableEffect(item) { onDispose { r.bounds.remove(item) } }
    val lifted = r.dragging == item
    // Bounds come from outside the layer, so they are the item's place in the layout, not where it is drawn.
    return onGloballyPositioned { r.bounds[item] = it.boundsInRoot() }
        .zIndex(if (lifted) 1f else 0f)
        .graphicsLayer {
            val o = r.offsetOf(item)
            translationX = o.x
            translationY = o.y
            if (lifted) { scaleX = 1.05f; scaleY = 1.05f; shadowElevation = 8.dp.toPx() }
        }
        .pointerInput(item) {
            detectDragGesturesAfterLongPress(
                onDragStart = { r.start(item, it) },
                onDragEnd = r::end,
                onDragCancel = r::end,
            ) { change, delta -> change.consume(); r.drag(delta) }
        }
}

// Names are persisted in settings and exported backups.
private enum class MenuItem(val label: Int, val section: Section) {
    Forward(R.string.forward, Section.Quick),
    Reload(R.string.reload, Section.Quick),
    Bookmark(R.string.bookmark, Section.Quick),
    Share(R.string.share_2, Section.Quick),
    Find(R.string.find, Section.Quick),
    Reader(R.string.reader, Section.Quick),
    ReadAloud(R.string.read_aloud, Section.Quick),
    Print(R.string.print, Section.Quick),
    NewTab(R.string.new_tab, Section.Tabs),
    NewIncognitoTab(R.string.new_incognito_tab, Section.Tabs),
    ReopenClosedTab(R.string.reopen_closed_tab, Section.Tabs),
    DesktopSite(R.string.desktop_site, Section.Page),
    FullScreen(R.string.full_screen, Section.Page),
    PlaybackSpeed(R.string.playback_speed, Section.Page),
    Translate(R.string.translate, Section.Page),
    AddToStartPage(R.string.add_to_start_page, Section.Page),
    ViewSource(R.string.view_source, Section.Page),
    AddToHomeScreen(R.string.add_to_home_screen, Section.Page),
    DeveloperTools(R.string.developer_tools, Section.Page),
    Downloads(R.string.downloads, Section.Library),
    History(R.string.history, Section.Library),
    Bookmarks(R.string.bookmarks, Section.Library),
    Passwords(R.string.passwords, Section.Library),
    Settings(R.string.settings, Section.Settings)
}

private class MenuAction(
    val action: MenuItem, val label: String, val icon: ImageVector,
    val enabled: Boolean, val highlighted: Boolean, val onClick: () -> Unit,
)

/** Icon over label, used for the page actions and the library shortcuts. */
@Composable
private fun QuickButton(q: MenuAction, modifier: Modifier) {
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
