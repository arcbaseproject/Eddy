package app.eddy.browser.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.eddy.browser.data.models.Shortcut
import app.eddy.browser.data.models.ShortcutStyle
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.ShortcutColors
import app.eddy.browser.ui.components.ShortcutIcons
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.shapes.MorphShape
import app.eddy.browser.ui.shapes.EddyPolygons
import app.eddy.browser.ui.shapes.rememberMorph
import app.eddy.browser.util.UrlUtils
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

/**
 * Shortcut grid with long-press-to-drag reordering. Releasing a long-press without moving opens the
 * edit/remove menu instead. Positions are tracked in root coordinates so the dragged tile keeps
 * following the finger even while neighbouring tiles swap places under it.
 */
@Composable
fun ShortcutGrid(
    shortcuts: List<Shortcut>,
    style: ShortcutStyle,
    columns: Int,
    tileSize: Dp,
    onOpen: (Shortcut) -> Unit,
    onEdit: (Shortcut) -> Unit,
    onRemove: (Shortcut) -> Unit,
    onReorder: (List<Shortcut>) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var pointerRoot by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    // Reordering happens on a local copy so it is instant; the new order is persisted once when the drag ends.
    var order by remember { mutableStateOf(shortcuts) }
    var lastSwap by remember { mutableLongStateOf(0L) }
    LaunchedEffect(shortcuts) { if (draggingId == null) order = shortcuts }

    val cells: List<Shortcut?> = order + null // trailing null is the "add" tile
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        cells.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { item ->
                    // With no shortcuts the lone add tile takes the full row so it sits in the centre.
                    Box(if (order.isEmpty()) Modifier.fillMaxWidth() else Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        if (item == null) {
                            AddTile(tileSize, onAdd)
                        } else {
                            val index = order.indexOfFirst { it.id == item.id }
                            val isDragging = draggingId == item.id
                            Box(
                                Modifier
                                    .onGloballyPositioned { c -> bounds[item.id] = Rect(c.positionInRoot(), Size(c.size.width.toFloat(), c.size.height.toFloat())) }
                                    .pointerInput(item.id) {
                                        var total = Offset.Zero
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { local ->
                                                haptics.longPress()
                                                draggingId = item.id
                                                grab = local
                                                total = Offset.Zero
                                                pointerRoot = (bounds[item.id]?.topLeft ?: Offset.Zero) + local
                                            },
                                            onDrag = { change, amount ->
                                                change.consume()
                                                total += amount
                                                pointerRoot = (bounds[item.id]?.topLeft ?: Offset.Zero) + change.position
                                                val now = System.currentTimeMillis()
                                                // A short cooldown lets layout catch up with a swap before the next one is considered.
                                                if (now - lastSwap < SWAP_COOLDOWN_MS) return@detectDragGesturesAfterLongPress
                                                val from = order.indexOfFirst { it.id == item.id }
                                                val target = bounds.entries.firstOrNull { (id, r) -> id != item.id && r.contains(pointerRoot) }?.key
                                                val to = order.indexOfFirst { it.id == target }
                                                if (to >= 0 && from >= 0) {
                                                    haptics.tick()
                                                    order = order.toMutableList().also { it.add(to, it.removeAt(from)) }
                                                    lastSwap = now
                                                }
                                            },
                                            onDragEnd = {
                                                if (total.getDistance() < 12f) menuFor = item.id
                                                draggingId = null
                                                if (order.map { it.id } != shortcuts.map { it.id }) onReorder(order)
                                            },
                                            onDragCancel = {
                                                draggingId = null
                                                if (order.map { it.id } != shortcuts.map { it.id }) onReorder(order)
                                            },
                                        )
                                    },
                            ) {
                                ShortcutTile(
                                    shortcut = item,
                                    index = index,
                                    style = style,
                                    tileSize = tileSize,
                                    lifted = isDragging,
                                    translation = {
                                        if (isDragging) pointerRoot - grab - (bounds[item.id]?.topLeft ?: pointerRoot - grab) else Offset.Zero
                                    },
                                    onClick = { onOpen(item) },
                                )
                                DropdownMenu(expanded = menuFor == item.id, onDismissRequest = { menuFor = null }) {
                                    DropdownMenuItem(
                                        text = { Text("Edit") },
                                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                        onClick = { menuFor = null; onEdit(item) },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove") },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                        onClick = { menuFor = null; onRemove(item) },
                                    )
                                }
                            }
                        }
                    }
                }
                // Keep the last row's tiles aligned with the rows above.
                repeat(columns - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShortcutTile(
    shortcut: Shortcut,
    index: Int,
    style: ShortcutStyle,
    tileSize: Dp,
    lifted: Boolean,
    translation: () -> Offset,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val polygon = EddyPolygons.forShortcut(style, shortcut.shape.takeIf { style == ShortcutStyle.MIXED } ?: index)
    val morph = rememberMorph(polygon, EddyPolygons.all.last())
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    // Pressing softens the silhouette towards a circle; the tile also dips slightly.
    val morphProgress by animateFloatAsState(if (pressed || lifted) 1f else 0f, spatialSpring(), label = "morph")
    val scale by animateFloatAsState(if (lifted) 1.14f else if (pressed) 0.94f else 1f, spatialSpring(), label = "scale")
    val container = ShortcutColors.container(shortcut.color, scheme)
    val content = ShortcutColors.content(shortcut.color, scheme)
    val shape = remember(morph, morphProgress) { MorphShape(morph, morphProgress) }
    val host = UrlUtils.displayHost(shortcut.url)

    Column(
        Modifier.widthIn(max = tileSize + 20.dp)
            .graphicsLayer {
                val t = translation()
                translationX = t.x
                translationY = t.y
                scaleX = scale
                scaleY = scale
                shadowElevation = if (lifted) 24f else 0f
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClickLabel = "Open ${shortcut.title}", onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(tileSize).clip(shape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            val glyph = ShortcutIcons.all[shortcut.icon]
            if (glyph != null) Icon(glyph, null, Modifier.size(tileSize * 0.44f), tint = content)
            else SiteIcon(host, size = tileSize * 0.62f, fetch = true, background = androidx.compose.ui.graphics.Color.Transparent)
        }
        Text(
            shortcut.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun AddTile(tileSize: Dp, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(tileSize).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(role = Role.Button, onClickLabel = "Add shortcut", onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, contentDescription = "Add shortcut", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Add", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    }
}

private const val SWAP_COOLDOWN_MS = 140L
