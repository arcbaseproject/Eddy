package app.eddy.browser.privacy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import app.eddy.browser.EddyApp
import kotlinx.coroutines.runBlocking

/**
 * Deletes browsing data when the task is swiped out of recents, for the "clear when I leave" setting.
 * A service is the only place Android still calls code after the task is gone; the activity's own
 * lifecycle callbacks are not guaranteed to run.
 */
class ExitCleanupService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val container = (application as EddyApp).container
        if (container.settings.value.clearOnExit) {
            // The process is about to go away, so this runs inline rather than on a scope that may never resume.
            runCatching {
                runBlocking { container.database.history().clear() }
                CookieManager.getInstance().apply { removeAllCookies(null); flush() }
                WebStorage.getInstance().deleteAllData()
                WebView(this).apply { clearCache(true); destroy() }
                container.favicons.clear()
            }
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        /** Cheap to call repeatedly; the service does nothing until the task is removed. */
        fun arm(context: Context) {
            runCatching { context.startService(Intent(context, ExitCleanupService::class.java)) }
        }
    }
}
