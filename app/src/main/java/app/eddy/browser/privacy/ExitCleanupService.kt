package app.eddy.browser.privacy

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import app.eddy.browser.AppContainer
import app.eddy.browser.EddyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Deletes browsing data when the task is swiped out of recents, for the "clear when I leave" setting.
 * A service is the only place Android still calls code after the task is gone; the activity's own
 * lifecycle callbacks are not guaranteed to run.
 */
class ExitCleanupService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    // Not sticky: a restart after a low-memory kill would only spin the app up in the background for nothing.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        val container = (application as EddyApp).container
        if (container.settings.value.clearOnExit) {
            // The process is about to go away, so this runs inline rather than on a scope that may never resume.
            runCatching { runBlocking { ExitCleanup.run(this@ExitCleanupService, container) } }
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    companion object {
        /** Cheap to call repeatedly; the service does nothing until the task is removed. */
        fun arm(context: Context) {
            ExitCleanup.markOpen(context)
            runCatching { context.startService(Intent(context, ExitCleanupService::class.java)) }
        }
    }
}

/**
 * Android stops idle background services about a minute after the app leaves the screen, so [ExitCleanupService]
 * often never hears about the swipe. A marker file records a session that has not been cleaned up yet; the next
 * cold start finishes the job before any tab is restored.
 */
object ExitCleanup {
    private fun marker(context: Context) = File(context.filesDir, "session_open")

    fun markOpen(context: Context) { runCatching { marker(context).createNewFile() } }

    fun pending(context: Context) = marker(context).exists()

    /** Call on the main thread: the WebView storage APIs need it. */
    suspend fun run(context: Context, container: AppContainer) {
        withContext(Dispatchers.IO) {
            container.database.history().clear()
            container.favicons.clear()
            // Restored tabs carry their whole back/forward history, so they go too. The folders stay: their owners hold them open.
            listOf("tabs.json", "tabs.json.bak", "tabs.json.new").forEach { File(context.filesDir, it).delete() }
            listOf("tabstate", "thumbs").forEach { dir -> File(context.filesDir, dir).listFiles()?.forEach { it.deleteRecursively() } }
        }
        CookieManager.getInstance().apply { removeAllCookies(null); flush() }
        WebStorage.getInstance().deleteAllData()
        WebView(context).apply { clearCache(true); destroy() }
        marker(context).delete()
    }
}
