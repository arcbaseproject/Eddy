package app.eddy.browser

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.eddy.browser.bookmarks.BookmarkManager
import app.eddy.browser.browser.FaviconCache
import app.eddy.browser.data.database.EddyDatabase
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.preferences.SettingsStore
import app.eddy.browser.downloads.DownloadEngine
import app.eddy.browser.downloads.DownloadService
import app.eddy.browser.history.HistoryManager
import app.eddy.browser.privacy.ContentBlocker
import app.eddy.browser.privacy.FilterUpdateService
import app.eddy.browser.privacy.SitePermissions
import app.eddy.browser.tabs.TabThumbnailManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** Manual dependency graph; everything heavy is lazy so cold start only pays for what the first frame needs. */
class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsStore = SettingsStore(app)

    /**
     * The first frame needs the theme, so exactly one small DataStore read is awaited at startup;
     * everything after that is observed asynchronously.
     */
    val settings: StateFlow<Settings> by lazy {
        val initial = runBlocking { settingsStore.settings.first() }
        settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, initial)
    }

    val database by lazy { EddyDatabase.create(app) }
    val history by lazy { HistoryManager(database.history(), scope) }
    val bookmarks by lazy { BookmarkManager(database.bookmarks(), scope) }
    val sites by lazy { SitePermissions(database.sites(), scope) }
    val favicons by lazy { FaviconCache(app, scope) }
    val thumbnails by lazy { TabThumbnailManager(app, scope) }
    val blocker by lazy { ContentBlocker(app, scope) }
    val downloads by lazy { DownloadEngine(app, scope, database.downloads()) { settings.value } }

    fun start() {
        DownloadService.createChannels(app)
        FilterUpdateService.schedule(app)
        scope.launch {
            val initial = settings.value
            blocker.reload(initial.filterLists)
            // Refresh stale lists in the background at most once every three days.
            if (System.currentTimeMillis() - initial.filterListsUpdatedAt > STALE_MS) {
                blocker.update(initial.filterLists)
                settingsStore.update { it.copy(filterListsUpdatedAt = System.currentTimeMillis()) }
            }
            history.prune()
        }
        scope.launch {
            downloads.events.filterNotNull().collect { event ->
                val open = PendingIntent.getActivity(
                    app, 0, Intent(app, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val n = NotificationCompat.Builder(app, DownloadService.CHANNEL_DONE)
                    .setSmallIcon(if (event.success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
                    .setContentTitle(event.fileName)
                    .setContentText(app.getString(if (event.success) R.string.download_complete else R.string.download_failed))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
                runCatching { app.getSystemService(NotificationManager::class.java).notify(event.id.toInt() + 10_000, n) }
            }
        }
    }

    private companion object {
        const val STALE_MS = 3L * 24 * 60 * 60 * 1000
    }
}
