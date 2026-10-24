package app.eddy.browser.tabs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import app.eddy.browser.browser.BrowserTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Captures small page previews for the tab switcher. Bitmaps live in a byte-bounded LRU; normal
 * tabs are also persisted so previews survive process death without keeping every WebView alive.
 */
class TabThumbnailManager(context: Context, private val scope: CoroutineScope) {
    private val dir = File(context.filesDir, "thumbs").apply { mkdirs() }
    /** Bumped by [clear]; a capture taken before it is not written afterwards. */
    @Volatile private var generation = 0
    /** One file operation at a time, so a clear cannot interleave with a write still in flight. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 24).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun peek(tabId: String): Bitmap? = cache.get(tabId)

    /** Must run on the main thread (draws the live WebView). Compression happens off-thread. */
    fun capture(tab: BrowserTab) {
        val view = tab.webView ?: return
        if (view.width == 0 || view.height == 0 || tab.isHome) return
        val scale = TARGET_WIDTH.toFloat() / view.width
        val h = (view.height * scale).toInt().coerceAtLeast(1)
        val bmp = runCatching { Bitmap.createBitmap(TARGET_WIDTH, h, Bitmap.Config.RGB_565) }.getOrNull() ?: return
        runCatching {
            val c = Canvas(bmp)
            c.scale(scale, scale)
            // A parent applies the scroll offset when it draws a child; drawing the view directly must do it too.
            c.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
            view.draw(c)
        }.onFailure { return }
        cache.put(tab.id, bmp)
        tab.thumbVersion++
        val gen = generation
        if (!tab.incognito) scope.launch(io) {
            if (gen == generation) runCatching { File(dir, "${tab.id}.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) } }
        }
    }

    suspend fun load(tabId: String): Bitmap? {
        cache.get(tabId)?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = File(dir, "$tabId.jpg")
            if (!f.exists()) return@withContext null
            BitmapFactory.decodeFile(f.path)?.also { cache.put(tabId, it) }
        }
    }

    fun remove(tabId: String) {
        cache.remove(tabId)
        scope.launch(io) { File(dir, "$tabId.jpg").delete() }
    }

    fun trim() = cache.evictAll()

    /** Forgets every preview, on disk too, for "Clear cached images and files". */
    fun clear() {
        generation++
        cache.evictAll()
        scope.launch(io) { dir.listFiles()?.forEach { it.delete() } }
    }

    /** Drops files for tabs that no longer exist. */
    fun prune(liveIds: Set<String>) {
        scope.launch(io) {
            dir.listFiles()?.filter { it.nameWithoutExtension !in liveIds }?.forEach { it.delete() }
        }
    }

    private companion object {
        const val TARGET_WIDTH = 480
    }
}
