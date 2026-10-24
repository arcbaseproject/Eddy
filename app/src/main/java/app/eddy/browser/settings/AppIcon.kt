package app.eddy.browser.settings

import app.eddy.browser.util.str
import androidx.annotation.StringRes
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import app.eddy.browser.R

/**
 * Launcher icons, one `activity-alias` each in the manifest. The enabled alias is the
 * stored choice, so there is no preference to keep in sync.
 */
enum class AppIcon(@StringRes private val labelRes: Int, @DrawableRes val foreground: Int, @ColorRes val background: Int) {
    DEFAULT(R.string.original, R.drawable.ic_launcher_foreground, R.color.icon_bg),
    FOREST(R.string.forest, R.drawable.ic_launcher_fg_forest, R.color.icon_bg_forest),
    VIOLET(R.string.violet, R.drawable.ic_launcher_fg_violet, R.color.icon_bg_violet),
    EMBER(R.string.ember, R.drawable.ic_launcher_fg_ember, R.color.icon_bg_ember),
    SUNSET(R.string.sunset, R.drawable.ic_launcher_fg_sunset, R.color.icon_bg_sunset),
    PAPER(R.string.paper, R.drawable.ic_launcher_fg_paper, R.color.icon_bg_paper),
    MIDNIGHT(R.string.midnight, R.drawable.ic_launcher_fg_midnight, R.color.icon_bg_midnight),
    NEON(R.string.neon, R.drawable.ic_launcher_fg_neon, R.color.icon_bg_neon),
    MONO(R.string.mono, R.drawable.ic_launcher_fg_mono, R.color.icon_bg_mono),
    INVERSE(R.string.inverse, R.drawable.ic_launcher_fg_inverse, R.color.icon_bg_inverse),
    PIXEL(R.string.pixel, R.drawable.ic_launcher_fg_pixel, R.color.icon_bg_pixel);

    val label: String get() = str(labelRes)

    private fun component(context: Context) = ComponentName(
        context.packageName,
        "app.eddy.browser.Launcher" + if (this == DEFAULT) "" else name.lowercase().replaceFirstChar(Char::uppercase),
    )

    companion object {
        /** Chosen in settings, applied in [applyPending]: Android closes the task when its launcher alias is disabled. */
        private var pending: AppIcon? = null

        fun current(context: Context): AppIcon {
            pending?.let { return it }
            val pm = context.packageManager
            return entries.firstOrNull { pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }
                ?: DEFAULT
        }

        fun set(icon: AppIcon) { pending = icon }

        // ponytail: a picker opened right after choosing an icon also stops the activity, so the switch closes it; add a flag if that bites.
        fun applyPending(context: Context) {
            val icon = pending ?: return
            pending = null
            val pm = context.packageManager
            // Enable the new alias before disabling the rest so the app never has zero launcher entries.
            pm.setComponentEnabledSetting(icon.component(context), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(context), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
