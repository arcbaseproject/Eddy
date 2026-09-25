package app.eddy.browser.downloads

import android.content.Context
import android.util.Base64
import app.eddy.browser.browser.BrowserTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    }

    private val dir = File(context.cacheDir, "blob").apply { mkdirs() }
    private val jobs = ConcurrentHashMap<Int, Job>()
    private val ids = AtomicInteger()
    /** One thread keeps chunk writes in the order they arrived. */
    private val io = Dispatchers.IO.limitedParallelism(1)

    fun start(tab: BrowserTab, blobUrl: String, disposition: String, mime: String) {
        val view = tab.webView ?: return
        val origin = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE).find(tab.url)?.value?.lowercase() ?: return
        val id = ids.incrementAndGet()
        val type = mime.ifBlank { "application/octet-stream" }
        jobs[id] = Job(tab.id, origin, DownloadNames.forResponse(disposition, type), type, File(dir, "$id.tmp"), tab.incognito)
        view.evaluateJavascript(script(id, blobUrl), null)
    }

    fun onMessage(tab: BrowserTab, origin: String, data: String) {
        val json = runCatching { JSONObject(data) }.getOrNull() ?: return
        val id = json.optInt("id", -1)
        val job = jobs[id] ?: return
        if (job.tabId != tab.id || !job.origin.equals(origin, ignoreCase = true)) return
        when (json.optString("t")) {
            "chunk" -> {
                val bytes = runCatching { Base64.decode(json.optString("d"), Base64.DEFAULT) }.getOrNull() ?: return fail(id, job)
                scope.launch(io) {
                    if (job.failed) return@launch
                    job.size += bytes.size
                    if (job.size > MAX_BYTES) return@launch fail(id, job)
                    runCatching { FileOutputStream(job.file, true).use { it.write(bytes) } }.onFailure { fail(id, job) }
                }
            }
            "end" -> scope.launch(io) {
                if (job.failed) return@launch
                jobs.remove(id)
                if (!job.file.exists()) job.file.createNewFile() // an empty blob is still a valid download
                val name = json.optString("name").ifBlank { job.name }
                val mime = json.optString("mime").ifBlank { job.mime }
                val downloadId = runCatching { engine.importFile(job.file, name, mime, job.incognito) }.getOrNull()
                onResult(if (downloadId != null) "Saved $name" else "Could not save $name", downloadId)
            }
            "error" -> fail(id, job)
        }
    }

    private fun fail(id: Int, job: Job) {
        job.failed = true
        jobs.remove(id)
        scope.launch(io) { job.file.delete() }
        onResult("Could not download ${job.name}", null)
    }

    private fun script(id: Int, url: String): String = """
(function(id, url) {
  var bridge = window.EddyBlob;
  if (!bridge) return;
  function send(o) { o.id = id; bridge.postMessage(JSON.stringify(o)); }
  fetch(url).then(function (r) { return r.blob(); }).then(function (blob) {
    var CHUNK = 786432, offset = 0;
    function next() {
      if (offset >= blob.size) { send({ t: 'end', mime: blob.type, name: window.__eddyDownloadName || '' }); window.__eddyDownloadName = ''; return; }
      var reader = new FileReader();
      reader.onload = function () {
        var s = reader.result;
        send({ t: 'chunk', d: s.substring(s.indexOf(',') + 1) });
        offset += CHUNK;
        next();
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
    }
}
