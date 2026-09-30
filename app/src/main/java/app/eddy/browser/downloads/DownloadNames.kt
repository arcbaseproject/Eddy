package app.eddy.browser.downloads

import android.webkit.MimeTypeMap
import android.webkit.URLUtil

/** File names for downloads that have no usable URL (data: and blob:), taken from Content-Disposition or the type. */
object DownloadNames {
    /**
     * Types Android can execute or install. A download of one of these is worth a question, because a
     * page can start it without the user ever choosing a file.
     */
    private val executableExtensions = setOf(
        "apk", "apex", "xapk", "apks", "dex", "jar", "exe", "msi", "bat", "cmd", "com", "scr", "pif",
        "vbs", "js", "jse", "ps1", "sh", "deb", "rpm", "dmg", "app", "pkg",
    )

    fun isExecutable(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase() in executableExtensions

    private val filename = Regex("filename(\\*?)=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)

    /**
     * URLUtil.guessFileName, except a Content-Disposition name is kept as written and a generic type is not
     * trusted: servers send real videos as application/octet-stream, which guessFileName would rename to .bin.
     */
    fun forDownload(url: String, disposition: String, mime: String): String =
        if (filename.containsMatchIn(disposition)) forResponse(disposition, mime)
        else URLUtil.guessFileName(url, disposition.ifEmpty { null }, mime.takeUnless { it.isBlank() || isGeneric(it) })

    /** The name's own type when the server only said application/octet-stream, so the file opens in the right app. */
    fun typeFor(fileName: String, mime: String): String =
        if (mime.isNotBlank() && !isGeneric(mime)) mime
        else MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase()) ?: mime

    private fun isGeneric(mime: String) = mime.substringBefore(';').trim().equals("application/octet-stream", ignoreCase = true)

    fun forResponse(disposition: String, mime: String): String {
        filename.find(disposition)?.let { m ->
            val raw = m.groupValues[2].trim()
            // Only the RFC 5987 filename* form is percent-encoded; a plain name like "50% off.pdf" is taken as written.
            val name = if (m.groupValues[1].isEmpty()) raw else runCatching { java.net.URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8") }.getOrDefault(raw)
            if (name.isNotEmpty()) return name
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.substringBefore(';').trim().lowercase())
        return "download" + (ext?.let { ".$it" } ?: "")
    }
}
