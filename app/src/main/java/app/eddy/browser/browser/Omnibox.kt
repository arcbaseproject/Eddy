package app.eddy.browser.browser

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.effectSpring
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.SiteIcon
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.UrlTextStyle
import app.eddy.browser.util.UrlUtils

private val PillShape = RoundedCornerShape(26.dp)

/**
 * The single browsing bar: back, address, tab switcher, menu. While scrolling down it shrinks to a slim
 * domain-only pill; tapping that restores it. Forward, reload and new tab live in the menu.
 */
@Composable
fun BrowserBar(
    tab: BrowserTab?,
    tabCount: Int,
    compact: Boolean,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onExpand: () -> Unit,
    onSiteInfo: () -> Unit,
    onStop: () -> Unit,
    onNewTab: () -> Unit,
    onTabs: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height by animateDpAsState(if (compact) Dimens.omniboxHeightCompact else Dimens.omniboxHeight, spatialSpring(), label = "barHeight")
    val progress by animateFloatAsState(if (tab?.isLoading == true) tab.progress / 100f else 0f, effectSpring(), label = "progress")
    val loadingColor = MaterialTheme.colorScheme.primary
    val home = tab == null || tab.isHome
    val label = when {
        tab == null || tab.isHome -> "Search or type URL"
        tab.error != null -> UrlUtils.displayHost(tab.url).ifEmpty { "Page unavailable" }
        tab.host.isNotEmpty() -> tab.host
        else -> tab.url.ifEmpty { "Search or type URL" }
    }

    Surface(
        modifier = modifier.fillMaxWidth().height(height).clip(PillShape),
        shape = PillShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Box(
            Modifier.fillMaxSize()
                // Progress is drawn over the content because the Surface paints its own background first.
                .drawWithContent {
                    drawContent()
                    if (progress > 0f && progress < 1f) {
                        drawRect(loadingColor, Offset(0f, size.height - Dimens.progressHeight.toPx()), Size(size.width * progress, Dimens.progressHeight.toPx()))
                    }
                },
        ) {
            if (compact) {
                Text(
                    label, Modifier.align(Alignment.Center).clickable(role = Role.Button, onClickLabel = "Show toolbar", onClick = onExpand).fillMaxSize().wrapContentSize(),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    EddyIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, enabled = tab != null && (tab.canGoBack || !tab.isHome))
                    Row(
                        Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                            .clickable(role = Role.Button, onClickLabel = "Edit address", onClick = onEdit),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tab != null && !home) SecurityButton(tab, tab.incognito, onSiteInfo)
                        else Icon(
                            if (tab?.incognito == true) Icons.Rounded.VisibilityOff else Icons.Rounded.Search, null,
                            Modifier.padding(horizontal = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            label, Modifier.weight(1f),
                            style = UrlTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (home) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        if (tab != null && tab.blockedCount > 0) BlockedBadge(tab.blockedCount, onSiteInfo)
                        // Only shown while loading; reload lives in the menu and pull-to-refresh.
                        if (tab?.isLoading == true) EddyIconButton(Icons.Rounded.Close, "Stop loading", onStop)
                    }
                    EddyIconButton(Icons.Rounded.Add, "New tab", onNewTab)
                    TabCountButton(tabCount, tab?.incognito == true, onTabs)
                    EddyIconButton(Icons.Rounded.MoreVert, "Menu", onMenu)
                }
            }
        }
    }
}

@Composable
private fun SecurityButton(tab: BrowserTab, incognito: Boolean, onClick: () -> Unit) {
    val (icon, description) = when {
        incognito && tab.security == Security.NONE -> Icons.Rounded.VisibilityOff to "Incognito tab"
        tab.security == Security.SECURE -> Icons.Rounded.Lock to "Secure connection. Open site information"
        tab.security == Security.INSECURE -> Icons.Rounded.Warning to "Not secure. Open site information"
        tab.security == Security.ERROR -> Icons.Rounded.GppBad to "Connection problem. Open site information"
        else -> Icons.Rounded.Search to "Site information"
    }
    val tint = when (tab.security) {
        Security.SECURE -> MaterialTheme.colorScheme.primary
        Security.INSECURE, Security.ERROR -> MaterialTheme.colorScheme.error
        Security.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onClick).heightIn(min = Dimens.touchTarget).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (tab.favicon != null && tab.security == Security.SECURE) {
            SiteIcon(tab.host, size = 22.dp, live = tab.favicon, modifier = Modifier.semantics { contentDescription = description })
        } else {
            Icon(icon, description, Modifier.size(20.dp), tint = tint)
        }
        // A word next to the icon so "not secure" never depends on colour alone.
        if (tab.security == Security.INSECURE) {
            Text("Not secure", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelSmall, color = tint, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BlockedBadge(count: Int, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = "Blocked requests", onClick = onClick)
            .heightIn(min = 32.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Shield, "$count requests blocked", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
        Text(if (count > 99) "99+" else "$count", Modifier.padding(start = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun TabCountButton(count: Int, incognito: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val slide = spatialSpring<IntOffset>()
    val fade = effectSpring<Float>()
    Box(
        Modifier.size(Dimens.touchTarget).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Open tab switcher") { haptics.tick(); onClick() }
            .semantics { contentDescription = "$count tabs" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).border(2.dp, if (incognito) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                count,
                transitionSpec = { (slideInVertically(slide) { it } + fadeIn(fade)) togetherWith (slideOutVertically(slide) { -it } + fadeOut(fade)) },
                label = "tabCount",
            ) { n ->
                Text(if (n > 99) ":D" else "$n", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Expanded editing state: text field plus suggestions in one rounded surface that grows with its
 * content. Suggestions sit next to the keyboard when the toolbar is at the bottom.
 */
@Composable
fun OmniboxEditor(
    initialText: String,
    suggestions: List<Suggestion>,
    incognito: Boolean,
    atTop: Boolean,
    engineName: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onPick: (Suggestion) -> Unit,
    onFill: (Suggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(initialText, TextRange(0, initialText.length))) }
    val voice = rememberVoiceState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Dictation streams partial transcripts, and every one of them requeries the suggestions. Letting the
    // list resize mid-hold would slide the field, and the finger would come off the mic button.
    val shown = if (voice.listening) remember(voice.listening) { suggestions } else suggestions
    val list = @Composable {
        if (shown.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState(), reverseScrolling = !atTop)) {
                val ordered = if (atTop) shown else shown.reversed()
                ordered.forEach { SuggestionRow(it, onPick = { onPick(it) }, onFill = { onFill(it); value = TextFieldValue(it.text, TextRange(it.text.length)) }) }
            }
        }
    }
    val field = @Composable {
        Row(Modifier.fillMaxWidth().heightIn(min = Dimens.omniboxHeight).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (incognito) Icons.Rounded.VisibilityOff else Icons.Rounded.Search, null,
                Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary,
            )
            BasicTextField(
                value = value,
                onValueChange = { value = it; onTextChange(it.text) },
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp).voiceTrace(voice).focusRequester(focus),
                singleLine = true,
                textStyle = UrlTextStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onSubmit(value.text) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                if (voice.listening) "Listening" else "Search or type a URL",
                                style = UrlTextStyle,
                                color = if (voice.listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        inner()
                    }
                },
            )
            VoiceInputButton({ spoken -> value = TextFieldValue(spoken, TextRange(spoken.length)); onTextChange(spoken) }, voice)
            // The slot stays put while dictating: a button appearing beside the mic would shift it too.
            if (value.text.isNotEmpty() || voice.listening) {
                EddyIconButton(Icons.Rounded.Close, "Clear text", { value = TextFieldValue(""); onTextChange("") })
            }
            EddyIconButton(Icons.AutoMirrored.Rounded.ArrowForward, "Go", { onSubmit(value.text) }, tint = MaterialTheme.colorScheme.primary)
        }
    }

    Surface(
        modifier.fillMaxWidth().animateContentSize(tween(150)), // a spring here overshoots and makes the field bounce
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column {
            if (!atTop) list()
            field()
            if (atTop) list()
        }
    }
}

@Composable
private fun SuggestionRow(s: Suggestion, onPick: () -> Unit, onFill: () -> Unit) {
    val icon: ImageVector = when (s.kind) {
        SuggestionKind.CLIPBOARD -> Icons.Rounded.ContentPaste
        SuggestionKind.BOOKMARK -> Icons.Rounded.Bookmark
        SuggestionKind.HISTORY -> Icons.Rounded.History
        SuggestionKind.SEARCH -> Icons.Rounded.Search
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onPick).padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text(s.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.subtitle.isNotEmpty()) {
                Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        EddyIconButton(Icons.Rounded.NorthWest, "Use ${s.title} in address bar", onFill, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Compact find-in-page bar that floats next to the chrome without covering the page. */
@Composable
fun FindBar(state: FindState, onQuery: (String) -> Unit, onNext: (Boolean) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer, shadowElevation = 4.dp) {
        Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = state.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f).padding(vertical = 14.dp).focusRequester(focus),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSecondaryContainer),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext(true) }),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (state.query.isEmpty()) Text("Find in page", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
                        inner()
                    }
                },
            )
            if (state.query.isNotEmpty()) {
                Text(
                    if (state.total == 0) "No results" else "${state.active} of ${state.total}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            EddyIconButton(Icons.Rounded.KeyboardArrowUp, "Previous result", { onNext(false) }, enabled = state.total > 0)
            EddyIconButton(Icons.Rounded.KeyboardArrowDown, "Next result", { onNext(true) }, enabled = state.total > 0)
            EddyIconButton(Icons.Rounded.Close, "Close find", onClose)
        }
    }
}
