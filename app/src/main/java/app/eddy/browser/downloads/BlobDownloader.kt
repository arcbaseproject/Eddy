package app.eddy.browser.downloads

import app.eddy.browser.R
import app.eddy.browser.util.str
import android.content.Context
import android.os.SystemClock
import android.util.Base64
import app.eddy.browser.browser.BrowserTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Saves `blob:` downloads. A blob only exists inside the page that made it, so the page itself reads it in
 * chunks and posts them to the browser over a message channel. Only jobs the browser started are accepted, and
 * each message must come from the same origin as the tab that asked for the download.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BlobDownloader(
    context: Context,
    private val scope: CoroutineScope,
    private val engine: DownloadEngine,
    private val onResult: (String, Long?) -> Unit,
) {
    private class Job(val tabId: String, val origin: String, val name: String, val mime: String, val file: File, val incognito: Boolean) {
        var size = 0L
        var failed = false
        @Volatile var lastSeen = SystemClock.uptimeMillis()
    }

    // Buffers left by an earlier run belong to downloads nobody can finish any more.
    private val dir = File(context.cacheDir, "blob").apply { deleteRecursively(); mkdirs() }
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val ids = AtomicInteger()
    /** One thread keeps chunk writes in the order they arrived. */
    private val io = Dispatchers.IO.limitedParallelism(1)

    fun start(tab: BrowserTab, blobUrl: String, disposition: String, mime: String) {
        val view = tab.webView ?: return
        val origin = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE).find(tab.url)?.value?.lowercase() ?: return
        val type = mime.ifBlank { "application/octet-stream" }
        val name = DownloadNames.forResponse(disposition, type)
        if (jobs.size >= MAX_JOBS) return onResult(str(R.string.could_not_download, name), null)
        val id = ids.incrementAndGet()
        // A fresh, empty, uniquely named buffer: nothing from another run can end up in this file.
        val file = runCatching { File.createTempFile("blob-", ".tmp", dir) }.getOrElse { return onResult(str(R.string.could_not_download, name), null) }
        val job = Job(tab.id, origin, name, type, file, tab.incognito)
        jobs[id] = job
        view.evaluateJavascript(script(id, blobUrl), null)
        // A page that navigates away or stops sending never posts "end"; give up after a quiet minute.
        scope.launch {
            while (jobs[id] === job) {
                delay(IDLE_MS / 4)
                if (jobs[id] === job && SystemClock.uptimeMillis() - job.lastSeen > IDLE_MS) fail(id, job)
            }
        }
    }

    /** The tab is gone, so its unfinished downloads are too, private ones included. */
    fun cancelTab(tabId: String) {
        jobs.entries.filter { it.value.tabId == tabId }.forEach { (id, job) -> fail(id, job, quiet = true) }
    }

    fun onMessage(tab: BrowserTab, origin: String, data: String, reply: (String) -> Unit) {
        // Checked before parsing: a chunk is at most CHUNK bytes, about 4/3 of that as base64.
        if (data.length > MAX_MESSAGE) return
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        val id = json.optInt("id", -1)
        val job = jobs[id] ?: return
        if (job.tabId != tab.id || !job.origin.equals(origin, ignoreCase = true)) return
        job.lastSeen = SystemClock.uptimeMillis()
        when (json.optString("t")) {
            "chunk" -> {
                val encoded = json.optString("d")
                if (job.size + encoded.length / 4 * 3 > MAX_BYTES) return fail(id, job)
                // The page waits for "ack" before reading the next chunk, so at most one is ever queued here.
                scope.launch(io) {
                    if (job.failed) return@launch
                    val bytes = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return@launch fail(id, job)
                    job.size += bytes.size
                    if (job.size > MAX_BYTES) return@launch fail(id, job)
                    runCatching { FileOutputStream(job.file, true).use { it.write(bytes) } }
                        .onSuccess { withContext(Dispatchers.Main) { runCatching { reply("ack") } } }
                        .onFailure { fail(id, job) }
                }
            }
            "end" -> scope.launch(io) {
                if (job.failed) return@launch
                jobs.remove(id)
                if (!job.file.exists()) job.file.createNewFile() // an empty blob is still a valid download
                // The page may rename the file after the download started, but not into an installer nobody was asked about.
                val name = json.optString("name").takeUnless { it.isBlank() || DownloadNames.isExecutable(it) && !DownloadNames.isExecutable(job.name) } ?: job.name
                val mime = json.optString("mime").ifBlank { job.mime }
                val downloadId = runCatching { engine.importFile(job.file, name, mime, job.incognito) }.getOrNull()
                onResult(if (downloadId != null) str(R.string.saved, name) else str(R.string.could_not_save, name), downloadId)
            }
            "error" -> fail(id, job)
        }
    }

    private fun fail(id: Int, job: Job, quiet: Boolean = false) {
        if (!jobs.remove(id, job)) return
        job.failed = true
        scope.launch(io) { job.file.delete() }
        if (!quiet) onResult(str(R.string.could_not_download, job.name), null)
    }

    private fun script(id: Int, url: String): String = """
(function(id, url) {
  var bridge = window.EddyBlob;
  if (!bridge) return;
  function send(o) { o.id = id; bridge.postMessage(JSON.stringify(o)); }
  fetch(url).then(function (r) { return r.blob(); }).then(function (blob) {
    var CHUNK = $CHUNK, offset = 0;
    function next() {
      if (offset >= blob.size) { send({ t: 'end', mime: blob.type, name: window.__eddyDownloadName || '' }); window.__eddyDownloadName = ''; return; }
      var reader = new FileReader();
      reader.onload = function () {
        var s = reader.result;
        offset += CHUNK;
        // The browser answers "ack" once the chunk is on disk; only then is the next one read.
        bridge.onmessage = function (e) { if (e.data === 'ack') { bridge.onmessage = null; next(); } };
        send({ t: 'chunk', d: s.substring(s.indexOf(',') + 1) });
      };
      reader.onerror = function () { send({ t: 'error' }); };
      reader.readAsDataURL(blob.slice(offset, offset + CHUNK));
    }
    next();
  }).catch(function () { send({ t: 'error' }); });
})(${id}, ${JSONObject.quote(url)});
""".trimIndent()

    companion object {
        const val NAME = "EddyBlob"
        private const val MAX_BYTES = 200L * 1024 * 1024
        private const val CHUNK = 786432
        private const val MAX_MESSAGE = CHUNK / 3 * 4 + 1024
        private const val MAX_JOBS = 4
        private const val IDLE_MS = 60_000L
    }
}
