package app.eddy.browser.downloads

import android.webkit.MimeTypeMap

/** File names for downloads that have no usable URL (data: and blob:), taken from Content-Disposition or the type. */
object DownloadNames {
    private val filename = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)

    fun forResponse(disposition: String, mime: String): String {
        filename.find(disposition)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { return java.net.URLDecoder.decode(it, "UTF-8") }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime.substringBefore(';').trim().lowercase())
        return "download" + (ext?.let { ".$it" } ?: "")
    }
}
