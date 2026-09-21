package app.eddy.browser.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.animation.effectSpring
import app.eddy.browser.ui.animation.spatialSpring
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.util.UrlUtils

/** Card that asks whether to save or update the login the user just submitted. */
@Composable
fun SaveLoginLayer(vm: BrowserViewModel, tab: BrowserTab?, atTop: Boolean, modifier: Modifier = Modifier) {
    val prompt = vm.savePrompt?.takeIf { it.tabId == tab?.id }
    AnimatedVisibility(
        visible = prompt != null,
        modifier = modifier.padding(10.dp),
        enter = slideInVertically(spatialSpring()) { if (atTop) -it else it } + fadeIn(effectSpring()),
        exit = slideOutVertically(spatialSpring()) { if (atTop) -it else it } + fadeOut(effectSpring()),
    ) {
        if (prompt != null) SaveLoginCard(prompt, vm)
    }
}

@Composable
private fun SaveLoginCard(prompt: SaveLoginPrompt, vm: BrowserViewModel) {
    val update = prompt.existingId != null
    var reveal by remember(prompt) { mutableStateOf(false) }
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, Modifier.size(24.dp))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        if (update) "Update password?" else "Save password?",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        UrlUtils.displayHost(prompt.origin) + if (prompt.username.isNotEmpty()) " · ${prompt.username}" else "",
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (reveal) prompt.password else "•".repeat(prompt.password.length.coerceIn(6, 16)),
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                EddyIconButton(
                    if (reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    if (reveal) "Hide password" else "Show password", { reveal = !reveal },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = vm::neverSaveLogin) { Text("Never") }
                TextButton(onClick = vm::dismissSaveLogin) { Text("Not now") }
                Button(onClick = vm::confirmSaveLogin) { Text(if (update) "Update" else "Save") }
            }
        }
    }
}

/** Saved logins for the focused field, shown next to the keyboard. Filling needs a tap. */
@Composable
fun AutofillLayer(vm: BrowserViewModel, tab: BrowserTab?, keyboardVisible: Boolean, modifier: Modifier = Modifier) {
    val offer = vm.autofillOffer?.takeIf { it.tabId == tab?.id }
    AnimatedVisibility(
        visible = offer != null && keyboardVisible,
        modifier = modifier.padding(10.dp),
        enter = slideInVertically(spatialSpring()) { it } + fadeIn(effectSpring()),
        exit = slideOutVertically(spatialSpring()) { it } + fadeOut(effectSpring()),
    ) {
        if (offer != null) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
                LazyRow(
                    Modifier.padding(vertical = 8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        Text(
                            "Fill for ${UrlUtils.displayHost(offer.origin)}",
                            Modifier.padding(end = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(offer.logins, key = { it.id }) { login ->
                        Surface(
                            onClick = { vm.fillLogin(offer, login) },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Key, null, Modifier.size(18.dp))
                                Text(
                                    login.username.ifEmpty { "Saved password" },
                                    Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.labelLarge, maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
