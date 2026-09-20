package app.eddy.browser.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Memory + disk favicon store keyed by host. Incognito icons stay in memory only. */
class FaviconCache(context: Context, private val scope: CoroutineScope) {
    private val memory = LruCache<String, Bitmap>(96)
    private val dir = File(context.cacheDir, "favicons").apply { mkdirs() }
    private val misses = ConcurrentHashMap.newKeySet<String>()

    fun peek(host: String): Bitmap? = memory.get(host)

    fun put(host: String, bitmap: Bitmap, persist: Boolean) {
        if (host.isEmpty()) return
        val icon = if (bitmap.width > MAX_SIZE) Bitmap.createScaledBitmap(bitmap, MAX_SIZE, MAX_SIZE, true) else bitmap
        memory.put(host, icon)
        misses.remove(host)
        if (persist) scope.launch(Dispatchers.IO) {
            runCatching { fileFor(host).outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        }
    }

    suspend fun load(host: String): Bitmap? {
        peek(host)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = fileFor(host)
            if (!f.exists()) return@withContext null
            BitmapFactory.decodeFile(f.path)?.also { memory.put(host, it) }
        }
    }

    /** Best effort direct fetch of /favicon.ico from the site itself (never a third-party service). */
    suspend fun fetch(host: String): Bitmap? {
        if (host.isEmpty() || host in misses) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("https://$host/favicon.ico").openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                try {
                    if (conn.responseCode != 200 || conn.contentLength > 256 * 1024) return@runCatching null
                    BitmapFactory.decodeStream(conn.inputStream)
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()?.also { put(host, it, persist = true) } ?: run { misses.add(host); null }
        }
    }

    fun clear() {
        memory.evictAll()
        misses.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun fileFor(host: String) = File(dir, host.hashCode().toUInt().toString(16) + ".png")

    private companion object {
        const val MAX_SIZE = 96
    }
}
