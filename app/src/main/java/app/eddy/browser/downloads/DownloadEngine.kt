package app.eddy.browser.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.URLUtil
import app.eddy.browser.data.database.DownloadDao
import app.eddy.browser.data.database.DownloadEntity
import app.eddy.browser.data.database.DownloadStatus
import app.eddy.browser.data.models.DownloadLocation
import app.eddy.browser.data.models.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

class DownloadRequest(
    val url: String,
    val userAgent: String,
    val referer: String,
    val contentDisposition: String,
    val mimeType: String,
    val contentLength: Long,
    val incognito: Boolean = false,
    /** Cookie header for [url], taken from the tab's own jar. Incognito has a separate one. */
    val cookie: String = "",
)

/** Momentary progress that is too chatty to write to the database. */
data class LiveProgress(val downloaded: Long, val total: Long, val bytesPerSecond: Long)

/**
 * Resumable HTTP downloader. Data is streamed to a private ".part" file (so pause/resume works with
 * Range requests) and published to the public Downloads collection through MediaStore when complete.
 */
class DownloadEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val dao: DownloadDao,
    private val settings: () -> Settings,
) {
    private val partDir = File(context.filesDir, "downloads").apply { mkdirs() }
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val intents = ConcurrentHashMap<Long, DownloadStatus>()
    /** id -> (host the cookie was read for, Cookie header). Never sent to a different host. */
    private val cookies = ConcurrentHashMap<Long, Pair<String, String>>()
    private val slots = Semaphore(MAX_PARALLEL)

    private val _live = MutableStateFlow<Map<Long, LiveProgress>>(emptyMap())
    val live: StateFlow<Map<Long, LiveProgress>> = _live

    private val _active = MutableStateFlow(0)
    /** Number of downloads currently running or waiting for a slot. */
    val active: StateFlow<Int> = _active

    private val _events = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<DownloadEvent> = _events

    init {
        scope.launch { dao.pauseInterrupted() }
    }

    suspend fun enqueue(request: DownloadRequest): Long {
        if (request.url.startsWith("data:", ignoreCase = true)) return saveDataUri(request)
        val name = sanitize(URLUtil.guessFileName(request.url, request.contentDisposition, request.mimeType.ifBlank { null }))
        val id = dao.insert(
            DownloadEntity(
                url = request.url, fileName = name, mimeType = request.mimeType,
                totalBytes = request.contentLength, userAgent = request.userAgent, referer = request.referer,
                incognito = request.incognito,
            ),
        )
        if (request.cookie.isNotEmpty()) cookies[id] = hostOf(request.url) to request.cookie
        start(id)
        return id
    }

    fun pause(id: Long) {
        intents[id] = DownloadStatus.PAUSED
        jobs[id]?.cancel(CancellationException("paused"))
    }

    fun resume(id: Long) {
        if (jobs.containsKey(id)) return
        start(id)
    }

    fun retry(id: Long) = resume(id)

    fun cancel(id: Long) {
        intents[id] = DownloadStatus.CANCELED
        val job = jobs[id]
        if (job != null) job.cancel(CancellationException("canceled")) else scope.launch { finishCanceled(id) }
    }

    fun delete(id: Long, deleteFile: Boolean) { scope.launch { deleteNow(id, deleteFile) } }

    /** An incognito session that ends takes its downloads with it; the saved files themselves stay. */
    suspend fun clearIncognito() { dao.incognitoIds().forEach { deleteNow(it, deleteFile = false) } }

    private suspend fun deleteNow(id: Long, deleteFile: Boolean) {
        jobs[id]?.let { intents[id] = DownloadStatus.CANCELED; it.cancelAndJoin() }
        val d = dao.get(id)
        if (d != null && deleteFile && d.contentUri.isNotEmpty()) {
            runCatching { context.contentResolver.delete(Uri.parse(d.contentUri), null, null) }
        }
        File(partDir, "$id.part").delete()
        dao.delete(id)
    }

    fun clearFinished() { scope.launch { dao.clearFinished() } }

    private suspend fun Job.cancelAndJoin() { cancel(); join() }

    private fun start(id: Long) {
        _active.update { it + 1 }
        DownloadService.start(context)
        val job = scope.launch(Dispatchers.IO) {
            try {
                slots.withPermit { run(id) }
            } finally {
                jobs.remove(id)
                intents.remove(id)
                cookies.remove(id)
                _live.update { it - id }
                _active.update { it - 1 }
            }
        }
        jobs[id] = job
    }

    private suspend fun run(id: Long) {
        val entity = dao.get(id) ?: return
        var d = entity.copy(status = DownloadStatus.RUNNING, error = "")
        dao.update(d)
        val part = File(partDir, "$id.part")
        try {
            d = transfer(d, part)
            publish(d, part)
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                if (intents[id] == DownloadStatus.CANCELED) finishCanceled(id)
                else dao.get(id)?.let { dao.update(it.copy(status = DownloadStatus.PAUSED)) }
            }
            throw e
        } catch (e: Exception) {
            withContext(NonCancellable) {
                dao.get(id)?.let { dao.update(it.copy(status = DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName)) }
                _events.tryEmit(DownloadEvent(id, entity.fileName, success = false))
            }
        }
    }

    private suspend fun transfer(start: DownloadEntity, part: File): DownloadEntity {
        var d = start
        var offset = if (d.resumable && part.exists()) part.length() else 0L
        if (offset == 0L) part.delete()
        var conn = open(d, offset)
        var redirects = 0
        while (conn.responseCode in 300..399 && redirects++ < MAX_REDIRECTS) {
            val from = URL(conn.url.toString())
            val next = URL(from, conn.getHeaderField("Location") ?: throw IOException("Bad redirect"))
            if (next.protocol != "http" && next.protocol != "https") throw IOException("Unsupported redirect")
            if (from.protocol == "https" && next.protocol == "http") throw IOException("Insecure redirect")
            conn.disconnect()
            d = d.copy(url = next.toString())
            conn = open(d, offset)
        }
        try {
            val code = conn.responseCode
            when {
                code == 206 -> Unit
                code == 200 -> if (offset > 0) { part.delete(); offset = 0 }
                code == 416 -> { part.delete(); throw IOException("Server rejected resume, retry to restart") }
                else -> throw IOException("HTTP $code")
            }
            val total = when {
                code == 206 -> conn.getHeaderField("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: -1L
                else -> conn.contentLengthLong
            }
            val disposition = conn.getHeaderField("Content-Disposition").orEmpty()
            val mime = conn.contentType?.substringBefore(';')?.trim().orEmpty().ifBlank { d.mimeType }
            val name = if (disposition.isNotEmpty() || d.fileName.startsWith("downloadfile")) {
                sanitize(URLUtil.guessFileName(d.url, disposition.ifEmpty { null }, mime.ifBlank { null }))
            } else d.fileName
            d = d.copy(
                totalBytes = total, mimeType = mime, fileName = name, downloadedBytes = offset,
                etag = conn.getHeaderField("ETag").orEmpty(), resumable = code == 206 || conn.getHeaderField("Accept-Ranges") == "bytes",
            )
            dao.update(d)
            copyStream(conn, d, part, offset)
            return d.copy(downloadedBytes = part.length())
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun copyStream(conn: HttpURLConnection, d: DownloadEntity, part: File, offset: Long) {
        val ctx = currentCoroutineContext()
        var downloaded = offset
        var lastEmit = System.nanoTime()
        var lastPersist = lastEmit
        var windowBytes = 0L
        var speed = 0L
        val buf = ByteArray(BUFFER)
        java.io.FileOutputStream(part, offset > 0).use { out ->
            conn.inputStream.use { input ->
                while (true) {
                    ctx.ensureActive()
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    downloaded += n
                    windowBytes += n
                    val now = System.nanoTime()
                    if (now - lastEmit >= EMIT_NANOS) {
                        val seconds = (now - lastEmit) / 1e9
                        speed = if (speed == 0L) (windowBytes / seconds).toLong() else ((speed * 0.6) + (windowBytes / seconds) * 0.4).toLong()
                        _live.update { it + (d.id to LiveProgress(downloaded, d.totalBytes, speed)) }
                        windowBytes = 0
                        lastEmit = now
                        if (now - lastPersist >= PERSIST_NANOS) {
                            dao.update(d.copy(downloadedBytes = downloaded))
                            lastPersist = now
                        }
                    }
                }
            }
        }
        if (d.totalBytes > 0 && downloaded < d.totalBytes) throw IOException("Connection closed early")
    }

    private fun open(d: DownloadEntity, offset: Long): HttpURLConnection {
        val conn = URL(d.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept-Encoding", "identity")
        if (d.userAgent.isNotBlank()) conn.setRequestProperty("User-Agent", d.userAgent)
        if (d.referer.isNotBlank()) conn.setRequestProperty("Referer", d.referer)
        cookies[d.id]?.takeIf { it.first == hostOf(d.url) }?.second?.let { conn.setRequestProperty("Cookie", it) }
        if (offset > 0) {
            conn.setRequestProperty("Range", "bytes=$offset-")
            if (d.etag.isNotEmpty() && !d.etag.startsWith("W/")) conn.setRequestProperty("If-Range", d.etag)
        }
        return conn
    }

    private suspend fun publish(d: DownloadEntity, part: File) {
        val resolver = context.contentResolver
        val folder = Environment.DIRECTORY_DOWNLOADS +
            if (settings().downloadLocation == DownloadLocation.EDDY_FOLDER) "/Eddy" else ""
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, d.fileName)
            put(MediaStore.Downloads.MIME_TYPE, d.mimeType.ifBlank { "application/octet-stream" })
            put(MediaStore.Downloads.RELATIVE_PATH, folder)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Cannot create file")
        try {
            resolver.openOutputStream(uri)!!.use { out -> part.inputStream().use { it.copyTo(out, BUFFER) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        part.delete()
        dao.update(d.copy(status = DownloadStatus.COMPLETED, contentUri = uri.toString(), totalBytes = d.downloadedBytes))
        _events.tryEmit(DownloadEvent(d.id, d.fileName, success = true, contentUri = uri.toString(), mimeType = d.mimeType))
    }

    /** Adds an already complete local file (a blob the page handed over) to Downloads and the list. */
    suspend fun importFile(source: File, name: String, mime: String): Long = withContext(Dispatchers.IO) {
        val id = dao.insert(
            DownloadEntity(url = "blob", fileName = sanitize(name), mimeType = mime, totalBytes = source.length(), downloadedBytes = source.length()),
        )
        val part = File(partDir, "$id.part")
        source.copyTo(part, overwrite = true)
        source.delete()
        val d = dao.get(id)!!
        try {
            publish(d, part)
        } catch (e: Exception) {
            dao.update(d.copy(status = DownloadStatus.FAILED, error = e.message.orEmpty()))
        }
        id
    }

    private suspend fun saveDataUri(request: DownloadRequest): Long = withContext(Dispatchers.IO) {
        val header = request.url.substringBefore(',')
        val payload = request.url.substringAfter(',')
        val bytes = if (header.endsWith(";base64")) Base64.decode(payload, Base64.DEFAULT) else Uri.decode(payload).toByteArray()
        val mime = header.removePrefix("data:").substringBefore(';').ifBlank { request.mimeType.ifBlank { "application/octet-stream" } }
        val name = sanitize(DownloadNames.forResponse(request.contentDisposition, mime))
        val id = dao.insert(DownloadEntity(url = "data-uri", fileName = name, mimeType = mime, totalBytes = bytes.size.toLong(), downloadedBytes = bytes.size.toLong()))
        File(partDir, "$id.part").writeBytes(bytes)
        val d = dao.get(id)!!
        try {
            publish(d, File(partDir, "$id.part"))
        } catch (e: Exception) {
            dao.update(d.copy(status = DownloadStatus.FAILED, error = e.message.orEmpty()))
        }
        id
    }

    private suspend fun finishCanceled(id: Long) {
        File(partDir, "$id.part").delete()
        dao.get(id)?.let { dao.update(it.copy(status = DownloadStatus.CANCELED, downloadedBytes = 0)) }
    }

    private fun hostOf(url: String) = runCatching { URL(url).host.lowercase() }.getOrDefault("")

    private fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().ifEmpty { "download" }

    private companion object {
        const val MAX_PARALLEL = 3
        const val MAX_REDIRECTS = 8
        const val BUFFER = 64 * 1024
        const val EMIT_NANOS = 400_000_000L
        const val PERSIST_NANOS = 2_000_000_000L
    }
}

data class DownloadEvent(
    val id: Long,
    val fileName: String,
    val success: Boolean,
    val contentUri: String = "",
    val mimeType: String = "",
)
