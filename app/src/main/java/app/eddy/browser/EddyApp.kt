package app.eddy.browser

import android.app.Application
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.eddy.browser.util.Strings
import app.eddy.browser.widget.SearchWidget

class EddyApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Strings.install(resources)
        container = AppContainer(this)
        container.start()
        // Long-press on the launcher icon offers an incognito tab; dynamic so it follows whichever icon alias is enabled.
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(this, ShortcutInfoCompat.Builder(this, "incognito")
                .setShortLabel(getString(R.string.shortcut_incognito))
                .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_incognito))
                .setIntent(Intent(this, MainActivity::class.java).setAction(SearchWidget.ACTION_INCOGNITO))
                .build())
        }
    }
}
