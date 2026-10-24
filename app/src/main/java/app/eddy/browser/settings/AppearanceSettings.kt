package app.eddy.browser.settings

import app.eddy.browser.R
import app.eddy.browser.util.str
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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
import app.eddy.browser.ui.animation.rememberSystemReducedMotion
import app.eddy.browser.ui.theme.Dimens
import app.eddy.browser.ui.theme.Palette

@Composable
fun AppearanceSettings(vm: BrowserViewModel, s: Settings) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    SettingsGroup(str(R.string.theme)) {
        ChoiceRow(str(R.string.theme), ThemeMode.entries, s.themeMode, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, { v -> vm.launchSettings { it.copy(themeMode = v) } })
        GroupDivider()
        SwitchRow(str(R.string.amoled_black), s.amoled, { v -> vm.launchSettings { it.copy(amoled = v) } }, str(R.string.true_black_backgrounds_in_dark_mode))
        if (dynamicAvailable) {
            GroupDivider()
            SwitchRow(str(R.string.dynamic_colors), s.dynamicColor, { v -> vm.launchSettings { it.copy(dynamicColor = v) } }, str(R.string.use_colors_from_your_wallpaper))
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
    SettingsGroup(str(R.string.app_icon)) { AppIconPicker() }
    SettingsFootnote(str(R.string.the_new_icon_appears_after_you_leave_edd))
    SettingsGroup(str(R.string.layout)) {
        NavRow(str(R.string.start_page), { vm.settingsStack.add(SettingsPage.START_PAGE) }, str(R.string.title_sections_shortcut_columns))
        GroupDivider()
        ChoiceRow(str(R.string.toolbar_position), ToolbarPosition.entries, s.toolbarPosition, { if (it == ToolbarPosition.BOTTOM) str(R.string.bottom) else str(R.string.top) }, { v -> vm.launchSettings { it.copy(toolbarPosition = v) } })
    }
    SettingsGroup(str(R.string.motion_and_feedback)) {
        // System "Remove animations" wins over this setting, so say so instead of showing "Full".
        val systemOff = rememberSystemReducedMotion()
        ChoiceRow(
            str(R.string.animations), MotionPref.entries, s.motion,
            { if (systemOff) str(R.string.off_in_system_settings) else if (it == MotionPref.FULL) str(R.string.full) else str(R.string.reduced) },
            { v -> vm.launchSettings { it.copy(motion = v) } }, enabled = !systemOff,
        )
        GroupDivider()
        SwitchRow(str(R.string.haptic_feedback), s.hapticsEnabled, { v -> vm.launchSettings { it.copy(hapticsEnabled = v) } })
    }
    SettingsFootnote(str(R.string.eddy_also_respects_the_system_remove_ani))
    SettingsGroup(str(R.string.web_content)) {
        SliderRowPct(vm, s)
        GroupDivider()
        SwitchRow(str(R.string.darken_websites), s.darkenPages, { v -> vm.launchSettings { it.copy(darkenPages = v) } }, str(R.string.dark_colors_for_sites_without_a_dark_the))
    }
}

/** Everything on the new-tab page can be switched off or renamed here. */
@Composable
fun StartPageSettings(vm: BrowserViewModel, s: Settings) {
    SettingsGroup(str(R.string.sections)) {
        SwitchRow(str(R.string.show_title), s.homeShowTitle, { v -> vm.launchSettings { it.copy(homeShowTitle = v) } })
        GroupDivider()
        SwitchRow(str(R.string.search_box), s.homeShowSearch, { v -> vm.launchSettings { it.copy(homeShowSearch = v) } })
        GroupDivider()
        SwitchRow(str(R.string.shortcuts), s.homeShowShortcuts, { v -> vm.launchSettings { it.copy(homeShowShortcuts = v) } })
        GroupDivider()
        SwitchRow(str(R.string.frequently_visited), s.homeShowFrequent, { v -> vm.launchSettings { it.copy(homeShowFrequent = v) } })
        GroupDivider()
        SwitchRow(str(R.string.recently_visited), s.homeShowRecent, { v -> vm.launchSettings { it.copy(homeShowRecent = v) } })
        GroupDivider()
        SwitchRow(str(R.string.background_shapes), s.homeShowBackground, { v -> vm.launchSettings { it.copy(homeShowBackground = v) } }, str(R.string.drifting_silhouettes_behind_the_page))
    }
    SettingsGroup(str(R.string.shortcuts)) {
        SliderRow(
            str(R.string.shortcut_columns), if (s.homeColumns == 0) str(R.string.automatic) else "${s.homeColumns}",
            s.homeColumns.toFloat(), 0f..6f, steps = 5,
            // 0 means automatic; one and two columns are not worth offering, so they land on three.
            onChange = { v -> vm.launchSettings { it.copy(homeColumns = v.toInt().let { n -> if (n in 1..2) 3 else n }) } },
        )
        GroupDivider()
        ChoiceRow(
            str(R.string.shortcut_appearance), ShortcutStyle.entries, s.shortcutStyle,
            { when (it) { ShortcutStyle.MIXED -> str(R.string.mixed_shapes); ShortcutStyle.SQUIRCLE -> str(R.string.squircles); ShortcutStyle.CIRCLE -> str(R.string.circles); ShortcutStyle.FLOWER -> str(R.string.flowers) } },
            { v -> vm.launchSettings { it.copy(shortcutStyle = v) } },
        )
    }
    SettingsGroup(str(R.string.layout)) {
        ChoiceRow(str(R.string.homepage_layout), HomeDensity.entries, s.homeDensity, { if (it == HomeDensity.COMFORTABLE) str(R.string.comfortable) else str(R.string.compact) }, { v -> vm.launchSettings { it.copy(homeDensity = v) } })
    }
    SettingsFootnote(str(R.string.shortcuts_themselves_are_added_and_reord))
}

@Composable
private fun AppIconPicker() {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppIcon.current(context)) }
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon.entries.forEach { icon ->
            val selected = icon == current
            val shape = RoundedCornerShape(16.dp)
            Box(
                // A 0.dp border still draws a hairline, which shows on the light Paper tile.
                Modifier.size(56.dp).clip(shape).background(colorResource(icon.background))
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
                    .clickable { AppIcon.set(icon); current = icon }
                    .semantics { contentDescription = "${icon.label} icon" + if (selected) ", selected" else "" },
            ) {
                // The foreground art sits inside the adaptive-icon safe zone; scale it to fill the tile.
                Image(painterResource(icon.foreground), null, Modifier.fillMaxSize().scale(1.4f))
            }
        }
    }
}

@Composable
private fun SliderRowPct(vm: BrowserViewModel, s: Settings) {
    SliderRow(
        str(R.string.page_text_size), "${s.textZoom}%", s.textZoom.toFloat(), 75f..200f, steps = 4,
        onChange = { v -> vm.launchSettings { it.copy(textZoom = (Math.round(v / 5f) * 5)) } },
    )
}
