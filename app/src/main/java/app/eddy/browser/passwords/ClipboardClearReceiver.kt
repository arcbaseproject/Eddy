package app.eddy.browser.passwords

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Clears a copied password a minute later. An alarm, not a coroutine: by then the user is usually pasting in
 * another app, and a backgrounded Eddy is frozen or cannot read the clipboard to check it.
 */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        // Android hides the clipboard from background apps (null). Then the copy may still be ours, so clear it,
        // as password managers do; with access, clear only if the user has not copied something else since.
        val description = runCatching { clipboard.primaryClipDescription }.getOrNull()
        if (description == null || description.extras?.getBoolean(MARK) == true) runCatching { clipboard.clearPrimaryClip() }
    }

    companion object {
        /** ClipDescription extra that marks a clip as a secret Eddy copied. */
        const val MARK = "app.eddy.browser.SECRET"

        fun schedule(context: Context, delayMs: Long) {
            val pending = PendingIntent.getBroadcast(
                context, 0, Intent(context, ClipboardClearReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // Inexact is fine: a few seconds late still beats never.
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delayMs, pending)
        }
    }
}
