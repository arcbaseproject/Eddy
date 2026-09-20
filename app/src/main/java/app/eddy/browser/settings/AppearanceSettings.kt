package app.eddy.browser.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.data.models.HomeDensity
import app.eddy.browser.data.models.MotionPref
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.ShortcutStyle
import app.eddy.browser.data.models.ThemeMode
import app.eddy.browser.data.models.ToolbarPosition
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.Palette

@Composable
fun AppearanceSettings(vm: BrowserViewModel, s: Settings) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    SettingsGroup("Theme") {
        ChoiceRow("Theme", ThemeMode.entries, s.themeMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, { v -> vm.launchSettings { it.copy(themeMode = v) } })
        GroupDivider()
        SwitchRow("AMOLED black", s.amoled, { v -> vm.launchSettings { it.copy(amoled = v) } }, "True black backgrounds in dark mode")
        if (dynamicAvailable) {
            GroupDivider()
            SwitchRow("Dynamic colors", s.dynamicColor, { v -> vm.launchSettings { it.copy(dynamicColor = v) } }, "Use colors from your wallpaper")
        }
        if (!dynamicAvailable || !s.dynamicColor) {
            GroupDivider()
            Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Palette.entries.forEachIndexed { i, p ->
                    val selected = s.palette == i
                    Box(
                        Modifier.size(Dimens.touchTarget).clip(CircleShape).background(p.swatch)
                            .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { vm.launchSettings { it.copy(palette = i) } }
                            .semantics { contentDescription = "${p.label} palette" + if (selected) ", selected" else "" },
                        contentAlignment = Alignment.Center,
                    ) { if (selected) Icon(Icons.Rounded.Check, null, tint = androidx.compose.ui.graphics.Color.White) }
                }
            }
        }
    }
    SettingsGroup("Layout") {
        ChoiceRow("Toolbar position", ToolbarPosition.entries, s.toolbarPosition, { if (it == ToolbarPosition.BOTTOM) "Bottom" else "Top" }, { v -> vm.launchSettings { it.copy(toolbarPosition = v) } })
        GroupDivider()
        ChoiceRow("Homepage layout", HomeDensity.entries, s.homeDensity, { if (it == HomeDensity.COMFORTABLE) "Comfortable" else "Compact" }, { v -> vm.launchSettings { it.copy(homeDensity = v) } })
        GroupDivider()
        ChoiceRow(
            "Shortcut appearance", ShortcutStyle.entries, s.shortcutStyle,
            { when (it) { ShortcutStyle.MIXED -> "Mixed shapes"; ShortcutStyle.SQUIRCLE -> "Squircles"; ShortcutStyle.CIRCLE -> "Circles"; ShortcutStyle.FLOWER -> "Flowers" } },
            { v -> vm.launchSettings { it.copy(shortcutStyle = v) } },
        )
    }
    SettingsGroup("Motion and feedback") {
        ChoiceRow("Animations", MotionPref.entries, s.motion, { if (it == MotionPref.FULL) "Full" else "Reduced" }, { v -> vm.launchSettings { it.copy(motion = v) } })
        GroupDivider()
        SwitchRow("Haptic feedback", s.hapticsEnabled, { v -> vm.launchSettings { it.copy(hapticsEnabled = v) } })
    }
    SettingsFootnote("Eddy also respects the system Remove animations setting.")
    SettingsGroup("Web content") {
        SliderRowPct(vm, s)
    }
}

@Composable
private fun SliderRowPct(vm: BrowserViewModel, s: Settings) {
    SliderRow(
        "Page text size", "${s.textZoom}%", s.textZoom.toFloat(), 75f..200f, steps = 4,
        onChange = { v -> vm.launchSettings { it.copy(textZoom = (Math.round(v / 5f) * 5)) } },
    )
}
