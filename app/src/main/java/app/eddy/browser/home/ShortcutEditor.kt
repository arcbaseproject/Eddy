package app.eddy.browser.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.eddy.browser.data.models.Shortcut
import app.eddy.browser.ui.components.ShortcutColors
import app.eddy.browser.ui.components.ShortcutIcons
import app.eddy.browser.ui.shapes.EddyPolygons
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.util.UrlUtils
import androidx.compose.material3.toShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShortcutEditor(initial: Shortcut, isNew: Boolean, onDismiss: () -> Unit, onSave: (Shortcut) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var url by remember { mutableStateOf(initial.url) }
    var icon by remember { mutableStateOf(initial.icon) }
    var color by remember { mutableStateOf(initial.color) }
    var shape by remember { mutableStateOf(initial.shape) }
    val normalizedUrl = url.trim().let { if (UrlUtils.isUrl(it)) it else "" }
    val valid = title.isNotBlank() && normalizedUrl.isNotEmpty()
    val scheme = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = Dimens.gutterLarge, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (isNew) "New shortcut" else "Edit shortcut", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(
                url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Address") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = url.isNotBlank() && normalizedUrl.isEmpty(),
            )

            Text("Icon", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Choice(selected = icon == ShortcutIcons.AUTO, description = "Site icon", onClick = { icon = ShortcutIcons.AUTO }) {
                    Text("Auto", style = MaterialTheme.typography.labelMedium)
                }
                ShortcutIcons.all.forEach { (key, glyph) ->
                    Choice(selected = icon == key, description = key, onClick = { icon = key }) { Icon(glyph, null, Modifier.size(22.dp)) }
                }
            }

            Text("Colour", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(ShortcutColors.COUNT) { i ->
                    Box(
                        Modifier.size(Dimens.touchTarget - 4.dp).clip(CircleShape).background(ShortcutColors.container(i, scheme))
                            .border(if (color.mod(ShortcutColors.COUNT) == i) 3.dp else 1.dp, if (color.mod(ShortcutColors.COUNT) == i) scheme.primary else scheme.outlineVariant, CircleShape)
                            .clickable { color = i }
                            .semantics { contentDescription = "Colour ${i + 1}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (color.mod(ShortcutColors.COUNT) == i) Icon(Icons.Rounded.Check, null, tint = ShortcutColors.content(i, scheme))
                    }
                }
            }

            Text("Shape", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EddyPolygons.all.forEachIndexed { i, polygon ->
                    Box(
                        Modifier.size(Dimens.touchTarget).clip(polygon.toShape())
                            .background(if (shape.mod(EddyPolygons.all.size) == i) scheme.primary else scheme.surfaceContainerHighest)
                            .clickable { shape = i }
                            .semantics { contentDescription = "Shape ${i + 1}" },
                    )
                }
            }
            Text(
                "Shapes take effect when Appearance > Shortcut appearance is set to Mixed shapes.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )

            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Button(
                    onClick = { onSave(initial.copy(title = title.trim(), url = UrlUtils.resolve(normalizedUrl, app.eddy.browser.data.models.SearchEngine("", "", "%s")), icon = icon, color = color, shape = shape)) },
                    enabled = valid,
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Choice(selected: Boolean, description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier.size(Dimens.touchTarget).clip(CircleShape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .border(if (selected) 2.dp else 0.dp, scheme.primary, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}
