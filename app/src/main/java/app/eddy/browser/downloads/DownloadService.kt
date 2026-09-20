package app.eddy.browser.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.eddy.browser.MainActivity
import app.eddy.browser.R
import app.eddy.browser.EddyApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Keeps the process alive (with a visible notification) while downloads are running in the background. */
class DownloadService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as EddyApp
        val engine = app.container.downloads
        startForeground(NOTIFICATION_ID, build(engine.active.value, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        job?.cancel()
        job = app.container.scope.launch {
            combine(engine.active, engine.live) { active, live -> active to live }.collect { (active, live) ->
                if (active == 0) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val known = live.values.filter { it.total > 0 }
                    val percent = if (known.isEmpty()) null else (known.sumOf { it.downloaded } * 100 / known.sumOf { it.total }).toInt()
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(active, percent))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun build(active: Int, percent: Int?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ACTIVE)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(resources.getQuantityString(R.plurals.downloading_count, active.coerceAtLeast(1), active.coerceAtLeast(1)))
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        const val CHANNEL_ACTIVE = "downloads_active"
        const val CHANNEL_DONE = "downloads_done"
        private const val NOTIFICATION_ID = 7001

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ACTIVE, context.getString(R.string.channel_downloads_active), NotificationManager.IMPORTANCE_LOW),
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, context.getString(R.string.channel_downloads_done), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}
