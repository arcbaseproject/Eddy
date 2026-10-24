package app.eddy.browser.util

import android.content.res.Resources
import androidx.annotation.StringRes

/**
 * Looks up UI text from string resources. One call works the same in composables, view models and services,
 * so text can move to strings.xml without threading a Context through. [EddyApp] installs the app's resources
 * before any screen or service runs; JVM tests install a lookup that reads strings.xml.
 */
object Strings {
    @Volatile var lookup: (Int, Array<out Any?>) -> String = { _, _ -> error("Strings.lookup is not installed") }

    fun install(resources: Resources) {
        lookup = { id, args -> if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args) }
    }
}

// shortcut: resolved per call from the application's resources, so text follows the locale the app runs in; a
// per-screen locale override would need stringResource() in composables instead.
fun str(@StringRes id: Int, vararg args: Any?): String = Strings.lookup(id, args)
