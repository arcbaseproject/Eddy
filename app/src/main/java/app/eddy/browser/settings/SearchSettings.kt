package app.eddy.browser.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import app.eddy.browser.R
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.data.models.SearchEngine
import app.eddy.browser.ui.components.TextInputDialog
import app.eddy.browser.ui.components.EddyIconButton
import java.util.UUID

/** Default engine choice, SearXNG instance and custom URL-template engines. */
@Composable
fun SearchSettings(vm: BrowserViewModel, s: app.eddy.browser.data.models.Settings) {
    var adding by remember { mutableStateOf(false) }
    var editInstance by remember { mutableStateOf(false) }

    SettingsGroup("Default search engine") {
        s.allEngines.forEachIndexed { i, engine ->
            if (i > 0) GroupDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(role = Role.RadioButton) { vm.launchSettings { it.copy(searchEngineId = engine.id) } }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.searchEngineId == engine.id, onClick = null)
                EngineLogo(engine.id, Modifier.padding(start = 16.dp))
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(engine.name, style = MaterialTheme.typography.bodyLarge)
                    Text(engine.searchUrl.substringAfter("://").substringBefore('/'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!engine.builtIn) {
                    EddyIconButton(Icons.Rounded.Delete, "Remove ${engine.name}", {
                        vm.launchSettings { it.copy(customEngines = it.customEngines.filterNot { c -> c.id == engine.id }, searchEngineId = if (it.searchEngineId == engine.id) "duckduckgo" else it.searchEngineId) }
                    })
                }
            }
        }
    }
    SettingsGroup("Customize") {
        if (s.searchEngineId == "searxng") {
            NavRowLike("SearXNG instance", s.searxngInstance) { editInstance = true }
            GroupDivider()
        }
        NavRowLike("Add custom search engine", "Use %s where the query goes", icon = true) { adding = true }
    }
    SettingsFootnote("Eddy asks your default engine for suggestions. Turn them off in General.")

    if (editInstance) {
        TextInputDialog("SearXNG instance", s.searxngInstance, "Instance URL", keyboardType = KeyboardType.Uri, validate = { it.startsWith("http://") || it.startsWith("https://") }, onDismiss = { editInstance = false }) { v ->
            vm.launchSettings { it.copy(searxngInstance = v.trimEnd('/')) }
            editInstance = false
        }
    }
    if (adding) AddEngineDialog({ adding = false }) { e -> vm.launchSettings { it.copy(customEngines = it.customEngines + e) }; adding = false }
}

/** Bundled logos for the built-in engines; custom engines get a generic search icon. */
@Composable
private fun EngineLogo(id: String, modifier: Modifier = Modifier) {
    val logo = when (id) {
        "duckduckgo" -> R.drawable.engine_duckduckgo
        "brave" -> R.drawable.engine_brave
        "startpage" -> R.drawable.engine_startpage
        "google" -> R.drawable.engine_google
        "bing" -> R.drawable.engine_bing
        "ecosia" -> R.drawable.engine_ecosia
        "searxng" -> R.drawable.engine_searxng
        else -> null
    }
    // A light tile keeps the logos, which assume a white page, readable in dark mode.
    Box(modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        if (logo != null) Image(painterResource(logo), null, Modifier.fillMaxSize())
        else Icon(Icons.Rounded.Search, null, Modifier.size(20.dp), tint = Color.DarkGray)
    }
}

@Composable
private fun NavRowLike(title: String, subtitle: String, icon: Boolean = false, onClick: () -> Unit) {
    if (icon) ActionRow(title, onClick, subtitle, Icons.Rounded.Add) else ActionRow(title, onClick, subtitle)
}

@Composable
private fun AddEngineDialog(onDismiss: () -> Unit, onAdd: (SearchEngine) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var suggest by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && url.startsWith("http") && "%s" in url && (suggest.isBlank() || "%s" in suggest)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom search engine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Search URL (with %s)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = "%s" !in url)
                OutlinedTextField(suggest, { suggest = it }, Modifier.fillMaxWidth(), label = { Text("Suggestions URL (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(SearchEngine("custom-" + UUID.randomUUID(), name.trim(), url.trim(), suggest.trim().ifBlank { null })) }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
