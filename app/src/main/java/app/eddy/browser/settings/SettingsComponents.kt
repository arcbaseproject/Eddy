package app.eddy.browser.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.eddy.browser.ui.components.ChoiceDialog
import app.eddy.browser.ui.theme.Dimens

@Composable
fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.gutter).padding(top = 12.dp)) {
        if (title != null) {
            Text(title, Modifier.padding(start = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun RowShell(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.padding(end = 16.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, icon: ImageVector? = null, enabled: Boolean = true) {
    RowShell(
        icon, title, subtitle,
        Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
    ) { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
}

@Composable
fun NavRow(title: String, onClick: () -> Unit, subtitle: String? = null, icon: ImageVector? = null) {
    RowShell(icon, title, subtitle, Modifier.clickable(role = Role.Button, onClick = onClick)) {
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ActionRow(title: String, onClick: () -> Unit, subtitle: String? = null, icon: ImageVector? = null, destructive: Boolean = false) {
    RowShell(icon, title, subtitle, Modifier.clickable(role = Role.Button, onClick = onClick)) {
        if (destructive) Box(Modifier.size(0.dp))
    }
}

@Composable
fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, icon: ImageVector? = null, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    RowShell(icon, title, label(selected), Modifier.clickable(enabled = enabled, role = Role.Button) { open = true })
    if (open) ChoiceDialog(title, options, selected, label, { open = false }, onSelect)
}

@Composable
fun SliderRow(title: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit, icon: ImageVector? = null) {
    Column(Modifier.padding(bottom = 6.dp)) {
        RowShell(icon, title, valueLabel)
        Slider(value, onChange, Modifier.padding(horizontal = 20.dp), valueRange = range, steps = steps)
    }
}

@Composable
fun GroupDivider() = HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

@Composable
fun SettingsFootnote(text: String) {
    Text(text, Modifier.padding(horizontal = Dimens.gutterLarge + 4.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
