package app.eddy.browser.downloads

import java.io.ByteArrayOutputStream
import java.net.URI

/** Pure rules the downloader applies per request, kept apart so they stay unit-testable. */
object DownloadPolicy {
    /**
     * The Referer for one hop, following the browser default (strict-origin-when-cross-origin): the full page URL
     * without its fragment to the same origin, only the origin to other sites, and nothing from HTTPS to HTTP.
     */
    fun referrerFor(page: String, target: String): String? {
        val from = runCatching { URI(page) }.getOrNull() ?: return null
        val to = runCatching { URI(target) }.getOrNull() ?: return null
        val fromScheme = from.scheme?.lowercase() ?: return null
        if (fromScheme != "http" && fromScheme != "https") return null
        if (fromScheme == "https" && to.scheme?.lowercase() != "https") return null
        val origin = "$fromScheme://${from.rawAuthority?.substringAfter('@') ?: return null}"
        val sameOrigin = fromScheme == to.scheme?.lowercase() && from.host.equals(to.host, true) && port(from) == port(to)
        return if (sameOrigin) page.substringBefore('#') else "$origin/"
    }

    private fun port(u: URI) = if (u.port >= 0) u.port else if (u.scheme.equals("https", true)) 443 else 80

    /** Decodes %XX escapes straight to bytes; other characters are taken as UTF-8. Binary data survives unchanged. */
    fun percentDecode(s: String): ByteArray {
        val out = ByteArrayOutputStream(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val hi = if (c == '%' && i + 2 < s.length) Character.digit(s[i + 1], 16) else -1
            val lo = if (hi >= 0) Character.digit(s[i + 2], 16) else -1
            if (hi >= 0 && lo >= 0) {
                out.write(hi * 16 + lo)
                i += 3
            } else {
                val end = if (Character.isHighSurrogate(c) && i + 1 < s.length) i + 2 else i + 1
                out.write(s.substring(i, end).toByteArray(Charsets.UTF_8))
                i = end
            }
        }
        return out.toByteArray()
    }
}
