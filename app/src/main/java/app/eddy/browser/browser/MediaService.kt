package app.eddy.browser.browser

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.eddy.browser.MainActivity
import app.eddy.browser.R

/**
 * Keeps page audio and video playing (with a visible notification) after Eddy leaves the screen.
 * Without it the cached-app freezer stops the process a few seconds after the app is backgrounded.
 */
class MediaService : Service() {
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }

    // Only this app's playback is reported. The grace period covers the gap between tracks or before an ad.
    private val callback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            handler.removeCallbacks(stopRunnable)
            if (configs.isEmpty()) handler.postDelayed(stopRunnable, IDLE_STOP_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            pauseAll?.invoke()
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        audio.registerAudioPlaybackCallback(callback, handler)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        audio.unregisterAudioPlaybackCallback(callback)
        super.onDestroy()
    }

    private fun build() = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle(getString(R.string.media_playing))
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .addAction(
            android.R.drawable.ic_media_pause, getString(R.string.media_pause),
            PendingIntent.getService(this, 0, Intent(this, MediaService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE),
        )
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    companion object {
        private const val CHANNEL = "media"
        private const val NOTIFICATION_ID = 7002
        private const val ACTION_PAUSE = "app.eddy.browser.PAUSE_MEDIA"
        private const val IDLE_STOP_MS = 15_000L

        /** Set by the view model, which owns the tabs. */
        @Volatile var pauseAll: (() -> Unit)? = null

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_media), NotificationManager.IMPORTANCE_LOW),
            )
        }

        fun isPlaying(context: Context) = context.getSystemService(AudioManager::class.java).activePlaybackConfigurations.isNotEmpty()

        fun start(context: Context) = ContextCompat.startForegroundService(context, Intent(context, MediaService::class.java))

        fun stop(context: Context) = context.stopService(Intent(context, MediaService::class.java))
    }
}
