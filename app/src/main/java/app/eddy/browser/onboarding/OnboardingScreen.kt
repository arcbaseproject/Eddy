package app.eddy.browser.onboarding

import android.app.Activity
import android.app.role.RoleManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.eddy.browser.R
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.ThemeMode
import app.eddy.browser.data.models.ToolbarPosition
import app.eddy.browser.ui.animation.effectSpring
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.shapes.MorphShape
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.LocalReducedMotion
import app.eddy.browser.ui.theme.Palette
import androidx.compose.material3.toShape
import kotlinx.coroutines.launch
import kotlin.math.floor

private const val PAGE_COUNT = 5

/**
 * First-run tour. One hero shape sits above a pager and morphs from page to page while the swipe is in
 * progress; the theme, colour, search engine and toolbar choices apply to the real settings immediately,
 * so the tour re-themes itself as the user picks.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnboardingScreen(vm: BrowserViewModel, settings: Settings, modifier: Modifier = Modifier) {
    val pager = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val scheme = MaterialTheme.colorScheme
    fun finish() { haptics.confirm(); vm.launchSettings { it.copy(onboardingCompleted = true) } }

    BackHandler(enabled = true) {
        if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    val shapes = remember {
        listOf(MaterialShapes.Cookie9Sided, MaterialShapes.SoftBurst, MaterialShapes.Clover4Leaf, MaterialShapes.Flower, MaterialShapes.Cookie12Sided)
    }
    val morphs = remember { shapes.zipWithNext { a, b -> androidx.graphics.shapes.Morph(a, b) } }
    val tints = listOf(scheme.primaryContainer, scheme.tertiaryContainer, scheme.secondaryContainer, scheme.primaryContainer, scheme.tertiaryContainer)
    val onTints = listOf(scheme.onPrimaryContainer, scheme.onTertiaryContainer, scheme.onSecondaryContainer, scheme.onPrimaryContainer, scheme.onTertiaryContainer)
    val icons: List<ImageVector?> = listOf(null, Icons.Rounded.Shield, Icons.Rounded.TouchApp, Icons.Rounded.Palette, Icons.Rounded.RocketLaunch)

    LaunchedEffect(pager.currentPage) { if (pager.currentPage > 0) haptics.tick() }

    Surface(modifier.fillMaxSize(), color = scheme.background) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        // Landscape and very short screens drop the hero so the text and controls always fit.
        val compact = maxHeight < 620.dp
        val position = pager.currentPage + pager.currentPageOffsetFraction
        val base = floor(position).toInt().coerceIn(0, PAGE_COUNT - 2)
        val fraction = (position - base).coerceIn(0f, 1f)
        val color = lerp(tints[base], tints[base + 1], fraction)
        val onColor = lerp(onTints[base], onTints[base + 1], fraction)
        AmbientBlobs(color, position)
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().height(Dimens.touchTarget + 8.dp).padding(horizontal = Dimens.gutter), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                // Every slide except the last can be skipped; the last one has its own "Start browsing" button.
                AnimatedVisibility(pager.currentPage < PAGE_COUNT - 1, enter = fadeIn(), exit = fadeOut()) {
                    FilledTonalButton(onClick = ::finish, Modifier.heightIn(min = Dimens.touchTarget)) { Text("Skip") }
                }
            }

            // Hero: position runs 0..4 continuously while swiping, so shape, colour and icon blend with the finger.
            // It takes whatever height the pager leaves, up to a comfortable maximum.
            if (!compact) {
                val breathe by rememberInfiniteTransition(label = "breathe").animateFloat(
                    1f, 1.045f, infiniteRepeatable(tween(2800), RepeatMode.Reverse), label = "breathe",
                )
                val pulse = if (LocalReducedMotion.current) 1f else breathe
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().heightIn(min = 140.dp), contentAlignment = Alignment.Center) {
                    val heroSize = minOf(maxHeight - 16.dp, maxWidth * 0.72f, 320.dp)
                    Box(
                        Modifier.size(heroSize)
                            .graphicsLayer { rotationZ = position * 36f; scaleX = pulse; scaleY = pulse }
                            .background(color, MorphShape(morphs[base], fraction)),
                    )
                    // The icon does not rotate with the shape; it cross-fades between neighbouring pages.
                    listOf(base to 1f - fraction, base + 1 to fraction).forEach { (page, alpha) ->
                        Box(Modifier.size(heroSize * 0.5f).alpha(alpha).graphicsLayer { scaleX = 0.8f + 0.2f * alpha; scaleY = 0.8f + 0.2f * alpha }, contentAlignment = Alignment.Center) {
                            val icon = icons[page]
                            if (icon == null) {
                                Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().clip(RoundedCornerShape(heroSize * 0.15f)))
                            } else {
                                Icon(icon, null, Modifier.fillMaxSize(0.72f), tint = onColor)
                            }
                        }
                    }
                }
            }

            HorizontalPager(pager, if (compact) Modifier.weight(1f).fillMaxWidth() else Modifier.height(PAGER_HEIGHT).fillMaxWidth(), pageSpacing = 16.dp) { page ->
                Column(
                    Modifier.fillMaxSize().padding(horizontal = Dimens.gutterLarge)
                        .graphicsLayer {
                            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).let { kotlin.math.abs(it) }
                            alpha = 1f - offset.coerceIn(0f, 1f)
                            translationY = offset.coerceIn(0f, 1f) * 24.dp.toPx()
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> PageText("Welcome to Eddy", "A fast browser that keeps your data on your device.")
                        1 -> {
                            PageText("Private by default", "Eddy blocks ads and trackers and sends no telemetry. Your history, bookmarks and passwords never leave your phone.")
                            FlowRow(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Feature(Icons.Rounded.Block, "Ad blocking")
                                Feature(Icons.Rounded.Shield, "Tracker protection")
                                Feature(Icons.Rounded.VisibilityOff, "Incognito tabs")
                                Feature(Icons.Rounded.Key, "Password manager")
                            }
                        }
                        2 -> {
                            PageText("Built for one hand", "Address bar, tabs and menu sit within thumb reach. The bar shrinks as you scroll, so pages get the space.")
                            BarDemo(Modifier.padding(top = 20.dp))
                            ToolbarChoice(settings.toolbarPosition) { v -> vm.launchSettings { it.copy(toolbarPosition = v) } }
                        }
                        3 -> {
                            PageText("Make it yours", "Pick a look and a search engine. You can change both later in Settings.")
                            LookPicker(vm, settings)
                        }
                        else -> {
                            PageText("You're ready", "Make Eddy your default browser and links from other apps open here.")
                            DefaultBrowserButton()
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutterLarge, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                // Back appears from the second slide on and slides the dots over to make room.
                AnimatedVisibility(
                    pager.currentPage > 0,
                    enter = fadeIn(effectSpring()) + scaleIn(spatialSpring(), initialScale = 0.6f) + expandHorizontally(spatialSpring()),
                    exit = fadeOut(effectSpring()) + scaleOut(spatialSpring(), targetScale = 0.6f) + shrinkHorizontally(spatialSpring()),
                ) {
                    FilledTonalIconButton(
                        onClick = { haptics.tick(); scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        Modifier.padding(end = 16.dp).size(56.dp),
                    ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                }
                Indicators(pager.currentPage, Modifier.weight(1f))
                val last = pager.currentPage == PAGE_COUNT - 1
                Button(
                    onClick = { if (last) finish() else { haptics.tick(); scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } } },
                    Modifier.heightIn(min = 56.dp).animateContentSize(spatialSpring()),
                    shape = CircleShape,
                ) {
                    Text(if (last) "Start browsing" else "Next", style = MaterialTheme.typography.titleMedium)
                    if (!last) Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(start = 8.dp).size(20.dp))
                }
            }
        }
      }
    }
}

private val PAGER_HEIGHT = 360.dp

/** Two large, slow shapes behind everything. They take the hero's colour and slide against the swipe for depth. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AmbientBlobs(color: Color, position: Float) {
    val reduced = LocalReducedMotion.current
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(80_000, easing = LinearEasing), RepeatMode.Restart), label = "spin",
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(300.dp)
                .graphicsLayer { translationX = 110.dp.toPx() - position * 26.dp.toPx(); translationY = -90.dp.toPx(); rotationZ = if (reduced) 0f else spin; alpha = 0.28f }
                .background(color, MaterialShapes.Cookie9Sided.toShape()),
        )
        Box(
            Modifier.align(Alignment.BottomStart).size(240.dp)
                .graphicsLayer { translationX = -100.dp.toPx() + position * 22.dp.toPx(); translationY = 90.dp.toPx(); rotationZ = if (reduced) 0f else -spin * 0.8f; alpha = 0.22f }
                .background(color, MaterialShapes.Clover4Leaf.toShape()),
        )
    }
}

@Composable
private fun PageText(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
    Text(
        body, Modifier.padding(top = 12.dp).widthIn(max = 420.dp), style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
    )
}

@Composable
private fun Feature(icon: ImageVector, label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Dots that stretch into a pill for the current page. */
@Composable
private fun Indicators(current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { i ->
            val width by animateDpAsState(if (i == current) 28.dp else 8.dp, spatialSpring(), label = "dotWidth")
            val color by animateColorAsState(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotColor")
            Box(Modifier.height(8.dp).size(width, 8.dp).clip(CircleShape).background(color))
        }
    }
}

/** A miniature of the real bar that keeps shrinking and growing, to show what scrolling does. */
@Composable
private fun BarDemo(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val phase by rememberInfiniteTransition(label = "demo").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "phase",
    )
    val t = if (reduced) 0f else ((phase - 0.25f) / 0.5f).coerceIn(0f, 1f) // hold at both ends
    Surface(
        modifier.fillMaxWidth().widthIn(max = 320.dp).height((52 - 20 * t).dp).semantics { contentDescription = "Preview of the bottom bar shrinking while you scroll" },
        shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).alpha(1f - t), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.graphicsLayer { rotationZ = 180f }.size(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("example.com", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelLarge)
                }
                Icon(Icons.Rounded.MoreVert, null, Modifier.size(20.dp))
            }
            Text("example.com", Modifier.alpha(t), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ToolbarChoice(selected: ToolbarPosition, onSelect: (ToolbarPosition) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.padding(top = 20.dp)) {
        listOf(ToolbarPosition.BOTTOM to "Bottom", ToolbarPosition.TOP to "Top").forEachIndexed { i, (pos, label) ->
            SegmentedButton(selected == pos, { onSelect(pos) }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(label) }
        }
    }
}

@Composable
private fun LookPicker(vm: BrowserViewModel, s: Settings) {
    Column(Modifier.padding(top = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SingleChoiceSegmentedButtonRow {
            listOf(ThemeMode.SYSTEM to "Auto", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark").forEachIndexed { i, (mode, label) ->
                SegmentedButton(s.themeMode == mode, { vm.launchSettings { it.copy(themeMode = mode) } }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
            }
        }
        // Palettes only apply without dynamic colour, so picking one turns dynamic colour off.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Palette.entries.forEachIndexed { i, p ->
                val selected = !s.dynamicColor && s.palette == i
                Box(
                    Modifier.size(Dimens.touchTarget - 6.dp).clip(CircleShape).background(p.swatch)
                        .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable(role = Role.RadioButton) { vm.launchSettings { it.copy(palette = i, dynamicColor = false) } }
                        .semantics { contentDescription = "${p.label} colors" + if (selected) ", selected" else "" },
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            s.allEngines.filter { it.builtIn && it.id != "searxng" }.take(5).forEach { engine ->
                val on = s.searchEngineId == engine.id
                Surface(
                    onClick = { vm.launchSettings { it.copy(searchEngineId = engine.id) } },
                    shape = CircleShape,
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (on) Icon(Icons.Rounded.Check, null, Modifier.padding(end = 6.dp).size(16.dp))
                        Text(engine.name, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultBrowserButton() {
    val context = LocalContext.current
    val roleManager = remember { context.getSystemService(RoleManager::class.java) }
    fun held() = Build.VERSION.SDK_INT >= 29 && roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
    var isDefault by remember { mutableStateOf(held()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { isDefault = held() }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isDefault = held() }

    Column(Modifier.padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (isDefault) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text("Eddy is your default browser", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleSmall)
            }
        } else if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
            OutlinedButton(onClick = { request.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)) }) { Text("Make Eddy the default") }
        }
    }
}
