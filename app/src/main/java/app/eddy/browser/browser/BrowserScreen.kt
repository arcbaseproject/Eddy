package app.eddy.browser.browser

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.eddy.browser.bookmarks.BookmarksScreen
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.ToolbarPosition
import app.eddy.browser.downloads.DownloadsScreen
import app.eddy.browser.history.HistoryScreen
import app.eddy.browser.onboarding.OnboardingScreen
import app.eddy.browser.passwords.PasswordsScreen
import app.eddy.browser.home.HomeScreen
import app.eddy.browser.settings.SettingsScreen
import app.eddy.browser.tabs.TabSwitcher
import app.eddy.browser.ui.animation.effectSpring
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.LocalFavicons
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.EddyTheme
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf

/** Root composable: theme, the browsing page, full-screen overlays, sheets and prompts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserRoot(vm: BrowserViewModel) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val selected = vm.tabs.selected
    val incognito = if (vm.screen == Screen.TABS) vm.switcherIncognito else selected?.incognito == true

    EddyTheme(settings, incognito) {
        val scheme = MaterialTheme.colorScheme
        LaunchedEffect(scheme.background) { vm.setPageBackground(scheme.background.toArgb()) }
        var backProgress by remember { mutableFloatStateOf(0f) }

        PredictiveBackHandler(enabled = vm.canHandleBack) { events ->
            try {
                events.collect { backProgress = it.progress }
                vm.onBack()
            } catch (_: CancellationException) {
                // Gesture cancelled: fall through and reset the preview.
            } finally {
                backProgress = 0f
            }
        }

        CompositionLocalProvider(LocalFavicons provides vm.favicons) {
            Box(Modifier.fillMaxSize().background(scheme.background)) {
                // The page stays composed (so its WebView survives) but is hidden while an overlay covers it. On
                // Android 10 with three-button navigation the bar otherwise showed through the overlay's bottom edge.
                val covered = vm.screen != Screen.BROWSER || (!settings.onboardingCompleted && !vm.onboardingSuppressed)
                Box(Modifier.fillMaxSize().alpha(if (covered) 0f else 1f)) { BrowserPage(vm, settings, selected) }

                val overlayModifier = Modifier.graphicsLayer {
                    val s = 1f - 0.08f * backProgress
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.25f * backProgress
                }
                Overlay(vm.screen == Screen.TABS, overlayModifier) { TabSwitcher(vm, settings) }
                Overlay(vm.screen == Screen.BOOKMARKS, overlayModifier) { BookmarksScreen(vm) }
                Overlay(vm.screen == Screen.HISTORY, overlayModifier) { HistoryScreen(vm) }
                Overlay(vm.screen == Screen.DOWNLOADS, overlayModifier) { DownloadsScreen(vm) }
                Overlay(vm.screen == Screen.PASSWORDS, overlayModifier) { PasswordsScreen(vm) }
                Overlay(vm.screen == Screen.SETTINGS, overlayModifier) { SettingsScreen(vm, settings) }

                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) {
                    vm.snackbars.collect { m ->
                        snackbar.currentSnackbarData?.dismiss()
                        val r = snackbar.showSnackbar(m.text, m.actionLabel, duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) m.action?.invoke()
                    }
                }
                val lift by animateFloatAsState(
                    if (vm.screen == Screen.BROWSER && settings.toolbarPosition == ToolbarPosition.BOTTOM && !vm.editing) 1f else 0f,
                    spatialSpring(), label = "snackLift",
                )
                SnackbarHost(
                    snackbar,
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp + (lift * 76).dp),
                )

                AnimatedVisibility(
                    visible = !settings.onboardingCompleted && !vm.onboardingSuppressed,
                    enter = fadeIn(effectSpring()),
                    exit = fadeOut(effectSpring()) + scaleOut(spatialSpring(), targetScale = 1.06f),
                ) {
                    OnboardingScreen(vm, settings)
                }

                PromptHost(vm)
                if (vm.menuVisible) MenuSheet(vm, selected, vm.isBookmarked.collectAsStateWithLifecycle().value) { vm.menuVisible = false }
                if (vm.siteInfoVisible && selected != null) SiteInfoSheet(vm, selected) { vm.siteInfoVisible = false }
                vm.linkTarget?.let { LinkSheet(vm, it) { vm.linkTarget = null } }
            }
        }
    }
}

@Composable
private fun Overlay(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(effectSpring()) + scaleIn(spatialSpring(), initialScale = 0.92f) + slideInVertically(spatialSpring()) { it / 12 },
        exit = fadeOut(effectSpring()) + scaleOut(spatialSpring(), targetScale = 0.94f),
    ) {
        Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@Composable
private fun BrowserPage(vm: BrowserViewModel, settings: Settings, tab: BrowserTab?) {
    val atTop = settings.toolbarPosition == ToolbarPosition.TOP
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val browsing = vm.screen == Screen.BROWSER

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Box(Modifier.fillMaxSize()) {
        // While typing into a page the keyboard, not the toolbar, gets the space. The editor manages its own insets.
        Column(Modifier.fillMaxSize().then(if (vm.editing) Modifier else Modifier.imePadding())) {
            if (atTop) Chrome(vm, tab, atTop = true, hidden = imeVisible && !vm.editing)
            Box(Modifier.weight(1f).fillMaxWidth().then(if (atTop) Modifier.navigationBarsPadding() else Modifier)) {
                if (tab != null) {
                    if (tab.isHome) {
                        HomeScreen(vm, settings, tab.incognito, contentPaddingFor(atTop))
                    } else {
                        WebContent(tab, visible = browsing, modifier = if (atTop) Modifier else Modifier.statusBarsPadding())
                    }
                    PullIndicator(tab)
                    tab.error?.let { err ->
                        ErrorPage(
                            err, onRetry = vm::retry, onBack = vm::cancelSslError.takeIf { err.kind == ErrorKind.SSL } ?: vm::goBack,
                            onProceedUnsafe = vm::proceedDespiteSslError,
                            modifier = if (atTop) Modifier else Modifier.statusBarsPadding(),
                        )
                    }
                }
                FindLayer(vm, atTop, Modifier.align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter))
                AutofillLayer(vm, tab, imeVisible, Modifier.align(Alignment.BottomCenter))
                SaveLoginLayer(vm, tab, atTop, Modifier.align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter))
            }
            if (!atTop) Chrome(vm, tab, atTop = false, hidden = imeVisible && !vm.editing)
        }

        AnimatedVisibility(vm.editing, enter = fadeIn(effectSpring()), exit = fadeOut(effectSpring())) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close address bar", role = androidx.compose.ui.semantics.Role.Button, onClick = vm::stopEditing)
                    .semantics { contentDescription = "Close address bar" },
            )
        }
        AnimatedVisibility(
            visible = vm.editing,
            modifier = Modifier.align(if (atTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(if (atTop) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            enter = fadeIn(effectSpring()) + scaleIn(spatialSpring(), initialScale = 0.9f, transformOrigin = TransformOrigin(0.5f, if (atTop) 0f else 1f)) +
                slideInVertically(spatialSpring()) { if (atTop) -it / 4 else it / 4 },
            exit = fadeOut(effectSpring()) + scaleOut(spatialSpring(), targetScale = 0.94f, transformOrigin = TransformOrigin(0.5f, if (atTop) 0f else 1f)),
        ) {
            OmniboxEditor(
                initialText = vm.omniboxText,
                suggestions = suggestions,
                incognito = tab?.incognito == true,
                atTop = atTop,
                engineName = settings.searchEngine.name,
                onTextChange = vm::onOmniboxText,
                onSubmit = vm::submit,
                onPick = { vm.submit(it.text) },
                onFill = { vm.onOmniboxText(it.text) },
            )
        }
    }
}

@Composable
private fun contentPaddingFor(atTop: Boolean): PaddingValues =
    if (atTop) PaddingValues() else WindowInsets.statusBars.asPaddingValues()

/** Address pill plus toolbar. While scrolling down only a slim pill remains, so the page gets the room. */
@Composable
private fun Chrome(vm: BrowserViewModel, tab: BrowserTab?, atTop: Boolean, hidden: Boolean) {
    val home = tab?.isHome != false
    val expanded = vm.chromeVisible || home
    if (hidden) return
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .then(if (atTop) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
            .padding(horizontal = 10.dp, vertical = Dimens.chromeGap),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.chromeGap),
    ) {
        BrowserBar(
            tab = tab,
            tabCount = vm.tabs.tabs.count { it.incognito == (tab?.incognito == true) },
            compact = !expanded,
            onBack = vm::goBack,
            onEdit = vm::startEditing,
            onExpand = vm::showChrome,
            onSiteInfo = { vm.siteInfoVisible = true },
            onStop = vm::reloadOrStop,
            onNewTab = { vm.newTab(incognito = tab?.incognito == true) },
            onTabs = vm::openTabSwitcher,
            onMenu = { vm.menuVisible = true },
        )
    }
}

@Composable
private fun WebContent(tab: BrowserTab, visible: Boolean, modifier: Modifier = Modifier) {
    val web = tab.webView
    if (web == null) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val activity = LocalContext.current
    androidx.compose.runtime.key(tab.id, web) {
        AndroidView(
            factory = {
                (web.parent as? ViewGroup)?.removeView(web)
                web.attachTo(activity)
                web
            },
            update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE },
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.PullIndicator(tab: BrowserTab) {
    val pull = tab.pullDistance
    if (pull <= 0) return
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val progress = (pull / threshold).coerceIn(0f, 1f)
    Surface(
        Modifier.align(Alignment.TopCenter).statusBarsPadding()
            .offset { IntOffset(0, (pull.coerceAtMost((threshold * 1.4f).toInt()) - 48.dp.roundToPx())) }
            .size(40.dp).alpha(progress),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Refresh, "Pull to refresh", Modifier.rotate(progress * 270f), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FindLayer(vm: BrowserViewModel, atTop: Boolean, modifier: Modifier) {
    val state = vm.find
    AnimatedVisibility(
        visible = state != null,
        modifier = modifier.padding(10.dp),
        enter = slideInVertically(spatialSpring()) { if (atTop) -it else it } + fadeIn(effectSpring()),
        exit = slideOutVertically(spatialSpring()) { if (atTop) -it else it } + fadeOut(effectSpring()),
    ) {
        if (state != null) FindBar(state, vm::findQuery, vm::findNext, vm::closeFind)
    }
}
