package app.eddy.browser.tabs

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eddy.browser.browser.BrowserTab
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.TabLayout
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.ConfirmDialog
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.theme.Dimens

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TabSwitcher(vm: BrowserViewModel, settings: Settings, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val incognito = vm.switcherIncognito
    val all = vm.tabs.tabs
    val shown = all.filter { it.incognito == incognito }
    val normalCount = all.count { !it.incognito }
    val incognitoCount = all.count { it.incognito }
    var menu by remember { mutableStateOf(false) }
    var confirmCloseAll by remember { mutableStateOf(false) }
    val columns = if (settings.tabLayout == TabLayout.LIST) 1 else 2
    // Long-press drag to reorder, tracked in root coordinates like the start page's shortcut grid.
    val slots = remember { mutableStateMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var lastSwap by remember { mutableLongStateOf(0L) }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SegmentedButton(
                        selected = !incognito, onClick = { vm.switcherIncognito = false; haptics.tick() },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Tabs · $normalCount") }
                    SegmentedButton(
                        selected = incognito, onClick = { vm.switcherIncognito = true; haptics.tick() },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp)) },
                    ) { Text("Incognito · $incognitoCount") }
                }
                Box {
                    EddyIconButton(Icons.Rounded.MoreVert, "More tab actions", { menu = true })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Close all tabs") }, enabled = shown.isNotEmpty(),
                            leadingIcon = { Icon(Icons.Rounded.Close, null) }, onClick = { menu = false; confirmCloseAll = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Reopen closed tab") }, enabled = vm.tabs.closedCount > 0,
                            leadingIcon = { Icon(Icons.Rounded.Restore, null) }, onClick = { menu = false; vm.restoreClosedTab() },
                        )
                    }
                }
            }

            if (shown.isEmpty()) {
                EmptyState(
                    if (incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Home,
                    if (incognito) "No incognito tabs" else "No tabs",
                    if (incognito) {
                        if (vm.incognitoIsolated) "Incognito tabs save no history. Their cookies go away when you close them."
                        else "Incognito tabs save no history. This device's WebView shares their cookies with normal tabs."
                    } else "Open a new tab to start browsing.",
                    Modifier.weight(1f),
                )
            } else {
                // Opens scrolled to the current tab rather than the top of a long list.
                val grid = remember(incognito) { LazyGridState(shown.indexOfFirst { it.id == vm.tabs.selectedId }.coerceAtLeast(0)) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.weight(1f),
                    state = grid,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Dimens.gutter, end = Dimens.gutter, top = 8.dp, bottom = 120.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(shown, key = { it.id }) { tab ->
                        val dragging = draggingId == tab.id
                        TabCard(
                            vm = vm,
                            tab = tab,
                            selected = tab.id == vm.tabs.selectedId,
                            thumbHeight = if (columns == 1) 120.dp else Dimens.tabCardThumbHeight,
                            dragging = dragging,
                            // The dragged card follows the finger, so only the others animate into their new places.
                            modifier = Modifier
                                .animateItem(placementSpec = if (dragging) null else spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow, IntOffset.VisibilityThreshold))
                                .zIndex(if (dragging) 1f else 0f)
                                .onGloballyPositioned { c -> slots[tab.id] = Rect(c.positionInRoot(), c.size.toSize()) }
                                .pointerInput(tab.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { local ->
                                            haptics.longPress()
                                            draggingId = tab.id
                                            grab = local
                                            finger = (slots[tab.id]?.topLeft ?: Offset.Zero) + local
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            finger = (slots[tab.id]?.topLeft ?: Offset.Zero) + change.position
                                            val now = System.currentTimeMillis()
                                            // Let layout catch up with one swap before considering the next.
                                            if (now - lastSwap < 150) return@detectDragGesturesAfterLongPress
                                            val visible = grid.layoutInfo.visibleItemsInfo.map { it.key }
                                            val target = shown.firstOrNull { it.id != tab.id && it.id in visible && slots[it.id]?.contains(finger) == true }
                                            if (target != null) { haptics.tick(); vm.tabs.move(tab, target); lastSwap = now }
                                        },
                                        onDragEnd = { draggingId = null },
                                        onDragCancel = { draggingId = null },
                                    )
                                }
                                .graphicsLayer {
                                    if (dragging) (finger - grab - (slots[tab.id]?.topLeft ?: finger - grab)).let { translationX = it.x; translationY = it.y }
                                },
                        )
                    }
                }
            }
        }

        Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
            TextButton(onClick = { vm.screen = app.eddy.browser.browser.Screen.BROWSER }, Modifier.align(Alignment.CenterStart).height(Dimens.touchTarget)) { Text("Done") }
            ExtendedFloatingActionButton(
                onClick = { haptics.confirm(); vm.newTab(incognito) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(if (incognito) "New incognito tab" else "New tab") },
                modifier = Modifier.align(Alignment.Center),
                containerColor = if (incognito) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
            )
        }
    }

    if (confirmCloseAll) {
        ConfirmDialog(
            title = if (incognito) "Close all incognito tabs?" else "Close all tabs?",
            message = "${shown.size} tabs will be closed.",
            confirmLabel = "Close all",
            onDismiss = { confirmCloseAll = false },
            onConfirm = { confirmCloseAll = false; haptics.confirm(); vm.closeAllTabs(incognito) },
        )
    }
}

@Composable
private fun TabCard(vm: BrowserViewModel, tab: BrowserTab, selected: Boolean, thumbHeight: androidx.compose.ui.unit.Dp, dragging: Boolean, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    val thumb by produceState(vm.thumbnails.peek(tab.id), tab.id, tab.thumbVersion) { value = vm.thumbnails.load(tab.id) }
    val scheme = MaterialTheme.colorScheme
    val border by animateFloatAsState(if (selected) 3f else 0f, spatialSpring(), label = "selected")
    val lift by animateFloatAsState(if (dragging) 1.05f else 1f, spatialSpring(), label = "lift")
    val title = tab.displayTitle(if (tab.isHome) "New tab" else "Tab")

    SwipeToDismissBox(
        rememberSwipeToDismissBoxState(), backgroundContent = {}, modifier.graphicsLayer { scaleX = lift; scaleY = lift },
        gesturesEnabled = !dragging,
        onDismiss = { haptics.confirm(); vm.closeTab(tab) },
    ) {
        Surface(
            modifier = Modifier.semantics { contentDescription = "$title. ${tab.host}. Swipe sideways to close, long-press and drag to move." },
            shape = RoundedCornerShape(28.dp),
            color = if (tab.incognito) scheme.tertiaryContainer.copy(alpha = 0.5f) else scheme.surfaceContainerHigh,
            border = if (border > 0.05f) androidx.compose.foundation.BorderStroke(border.dp, scheme.primary) else null,
            onClick = { vm.selectTab(tab) },
        ) {
            Column(Modifier.padding(6.dp)) {
                Row(Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (tab.isHome) Icon(Icons.Rounded.Home, null, Modifier.size(20.dp), tint = scheme.primary)
                    else SiteIcon(tab.host, size = 20.dp, live = tab.favicon)
                    Text(title, Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    EddyIconButton(Icons.Rounded.Close, "Close $title", { haptics.confirm(); vm.closeTab(tab) }, Modifier.size(40.dp))
                }
                Box(
                    Modifier.fillMaxWidth().height(thumbHeight).clip(RoundedCornerShape(22.dp)).background(scheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = thumb
                    when {
                        bmp != null -> Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                        tab.isHome -> Icon(Icons.Rounded.Home, null, Modifier.size(40.dp), tint = scheme.outline)
                        else -> SiteIcon(tab.host, size = 56.dp, live = tab.favicon)
                    }
                }
                Text(
                    if (tab.isHome) "Start page" else tab.host,
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
