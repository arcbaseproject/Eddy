package app.eddy.browser.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.data.database.SiteVisit
import app.eddy.browser.data.models.HomeDensity
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.Shortcut
import app.eddy.browser.ui.animation.entrance
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.LocalReducedMotion
import app.eddy.browser.util.UrlUtils
import java.util.UUID

/** New-tab page: lots of air, one big search field, shortcuts, and what you visit most. */
@Composable
fun HomeScreen(
    vm: BrowserViewModel,
    settings: Settings,
    incognito: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    val compact = settings.homeDensity == HomeDensity.COMPACT

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(Modifier.fillMaxSize()) {
        AmbientShapes()
        BoxWithConstraints(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.TopCenter) {
            val columns = if (maxWidth >= 600.dp) 6 else 4
            Column(
                Modifier.widthIn(max = Dimens.screenMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.gutterLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(if (compact) 40.dp else 72.dp))
                Wordmark(incognito, Modifier.entrance(0))
                Spacer(Modifier.height(if (compact) 20.dp else 32.dp))
                HomeSearchBar(settings.searchEngine.name, incognito, vm::startEditing, Modifier.entrance(1))
                Spacer(Modifier.height(if (compact) 24.dp else 40.dp))

                if (!incognito) {
                    ShortcutGrid(
                        shortcuts = settings.shortcuts,
                        style = settings.shortcutStyle,
                        columns = columns,
                        tileSize = if (compact) Dimens.shortcutSizeCompact else Dimens.shortcutSize,
                        onOpen = { vm.navigate(it.url) },
                        onEdit = { editing = it },
                        onRemove = { s -> vm.launchSettings { it.copy(shortcuts = it.shortcuts.filterNot { x -> x.id == s.id }) } },
                        onReorder = { list -> vm.launchSettings { it.copy(shortcuts = list) } },
                        onAdd = { editing = Shortcut(UUID.randomUUID().toString(), "", "https://", settings.shortcuts.size, settings.shortcuts.size) },
                        modifier = Modifier.entrance(2),
                    )
                    Spacer(Modifier.height(32.dp))
                    VisitedSections(vm)
                } else {
                    IncognitoNote(Modifier.entrance(2))
                }
                Spacer(Modifier.height(32.dp))
            }
        }
      }
    }

    editing?.let { shortcut ->
        ShortcutEditor(
            initial = shortcut,
            isNew = settings.shortcuts.none { it.id == shortcut.id },
            onDismiss = { editing = null },
            onSave = { saved ->
                vm.launchSettings { s ->
                    val exists = s.shortcuts.any { it.id == saved.id }
                    s.copy(shortcuts = if (exists) s.shortcuts.map { if (it.id == saved.id) saved else it } else s.shortcuts + saved)
                }
                editing = null
            },
        )
    }
}

@Composable
private fun Wordmark(incognito: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (incognito) "Incognito" else "Eddy",
            style = MaterialTheme.typography.displayMedium,
            color = if (incognito) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun HomeSearchBar(engineName: String, incognito: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                "Search or type a URL",
                Modifier.weight(1f).padding(start = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // With big system fonts the placeholder needs the room more than the engine name does.
            if (LocalDensity.current.fontScale <= 1.3f) Text(engineName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun VisitedSections(vm: BrowserViewModel) {
    val frequent by remember { vm.history.frequentSites(8) }.collectAsStateWithLifecycle(emptyList())
    val recent by remember { vm.history.recentSites(5) }.collectAsStateWithLifecycle(emptyList())
    if (frequent.isNotEmpty()) {
        SectionTitle("Frequently visited", Modifier.entrance(3))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.entrance(3).fillMaxWidth(),
        ) {
            items(frequent, key = { it.host }) { site ->
                Surface(
                    onClick = { vm.navigate(site.url) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.heightIn(min = Dimens.touchTarget),
                ) {
                    Row(Modifier.padding(start = 8.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        SiteIcon(site.host, size = 28.dp, fetch = true)
                        Text(site.host, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    val recentShown = recent.filter { r -> frequent.take(3).none { it.host == r.host } }.take(4)
    if (recentShown.isNotEmpty()) {
        SectionTitle("Recently visited", Modifier.entrance(4))
        Column(Modifier.entrance(4).fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)) {
            recentShown.forEach { RecentRow(it) { vm.navigate(it.url) } }
        }
    }
}

@Composable
private fun RecentRow(site: SiteVisit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SiteIcon(site.host, size = Dimens.faviconLarge, fetch = true)
        Column(Modifier.padding(start = 14.dp)) {
            Text(site.title.ifBlank { site.host }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(site.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(start = 4.dp, bottom = 10.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun IncognitoNote(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("Private browsing", style = MaterialTheme.typography.titleMedium)
        Text(
            "Eddy does not save the pages you open here to history. It erases cookies and site data when you close the last incognito tab. " +
                "Websites and your network can still see what you do.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Slow drifting silhouettes behind the content. Cheap: two graphicsLayer transforms, no recomposition. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AmbientShapes() {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "ambient")
    val spin by transition.animateFloat(
        0f, 360f, infiniteRepeatable(tween(90_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    val drift by transition.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(14_000), RepeatMode.Reverse), label = "drift",
    )
    val first = MaterialTheme.colorScheme.primaryContainer
    val second = MaterialTheme.colorScheme.tertiaryContainer
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(280.dp)
                .graphicsLayer {
                    translationX = 90.dp.toPx() + if (reduced) 0f else drift * 16.dp.toPx()
                    translationY = -70.dp.toPx()
                    rotationZ = if (reduced) 0f else spin
                    alpha = 0.55f
                }
                .background(first, MaterialShapes.Cookie9Sided.toShape()),
        )
        Box(
            Modifier.align(Alignment.BottomStart).size(220.dp)
                .graphicsLayer {
                    translationX = -80.dp.toPx()
                    translationY = 60.dp.toPx() + if (reduced) 0f else drift * 12.dp.toPx()
                    rotationZ = if (reduced) 0f else -spin * 0.8f
                    alpha = 0.4f
                }
                .background(second, MaterialShapes.Clover4Leaf.toShape()),
        )
    }
}
