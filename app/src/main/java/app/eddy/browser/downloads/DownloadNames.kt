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

    private val parameter = Regex(
        """(?:^|;)\s*([\w*-]+)\s*=\s*(?:"([^"]*)"|([^;]*))""",
        RegexOption.IGNORE_CASE,
    )

    internal fun responseName(disposition: String): String? {
        var plain: String? = null
        for (match in parameter.findAll(disposition)) {
            val (key, quoted, token) = match.destructured
            val value = quoted.ifEmpty { token.trim() }
            when {
                key.equals("filename", true) && value.isNotBlank() -> plain = value
                key.equals("filename*", true) -> {
                    val parts = value.split('\'', limit = 3)
                    if (parts.size == 3 && parts[0].equals("UTF-8", true)) {
                        // Only the RFC 5987 filename* form is percent-encoded; a plain name like "50% off.pdf" is taken as written.
                        val decoded = runCatching { java.net.URLDecoder.decode(parts[2].replace("+", "%2B"), "UTF-8") }.getOrNull()
                        if (!decoded.isNullOrBlank()) return decoded
                    }
                }
            }
        }
        return plain
    }

    /**
     * URLUtil.guessFileName, except a Content-Disposition name is kept as written and a generic type is not
     * trusted: servers send real videos as application/octet-stream, which guessFileName would rename to .bin.
     */
    fun forDownload(url: String, disposition: String, mime: String): String {
        responseName(disposition)?.let { return it }
        val type = mime.substringBefore(';').trim().takeUnless { it.isBlank() || isGeneric(it) }
        // PHP endpoints name the script that served the file. Let the response type supply its extension.
        val path = url.substringBefore('#').substringBefore('?')
        val source = if (path.endsWith(".php", ignoreCase = true)) "" else url
        return URLUtil.guessFileName(source, null, type)
    }

    /** The name's own type when the server only said application/octet-stream, so the file opens in the right app. */
    fun typeFor(fileName: String, mime: String): String =
        if (mime.isNotBlank() && !isGeneric(mime)) mime
        else MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase()) ?: mime

    private fun isGeneric(mime: String) = mime.substringBefore(';').trim().equals("application/octet-stream", ignoreCase = true)

    fun forResponse(disposition: String, mime: String): String {
        responseName(disposition)?.let { return it }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.substringBefore(';').trim().lowercase())
        return "download" + (ext?.let { ".$it" } ?: "")
    }
}
