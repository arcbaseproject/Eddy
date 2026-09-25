package app.eddy.browser.downloads

import java.io.IOException

/** Validates a partial response before any bytes are appended to an existing download. */
object DownloadRanges {
    private val range = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)", RegexOption.IGNORE_CASE)

    fun total(header: String?, offset: Long, contentLength: Long): Long {
        val match = range.matchEntire(header.orEmpty()) ?: throw IOException("Invalid Content-Range")
        val start = match.groupValues[1].toLongOrNull() ?: throw IOException("Invalid Content-Range")
        val end = match.groupValues[2].toLongOrNull() ?: throw IOException("Invalid Content-Range")
        val total = match.groupValues[3].toLongOrNull() ?: -1L
        if (start != offset || end < start || end == Long.MAX_VALUE ||
            (total >= 0 && end >= total) || (contentLength >= 0 && end - start + 1 != contentLength)) {
            throw IOException("Server returned an unexpected byte range")
        }
        return total
    }
}
