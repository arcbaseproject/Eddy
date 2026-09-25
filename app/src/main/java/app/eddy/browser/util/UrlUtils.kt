package app.eddy.browser.util

import app.eddy.browser.data.models.SearchEngine
import java.net.URLEncoder

/** Pure Kotlin (no android.*) so it stays unit-testable. */
object UrlUtils {
    private val explicitSchemes = setOf("http", "https", "file", "about", "data", "view-source")
    private val schemeRegex = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val localHost = Regex(
        "^(localhost|(\\d{1,3}\\.){3}\\d{1,3}|\\[[0-9a-fA-F:]+]|[\\p{L}\\p{N}-]+\\.(local|lan|internal|home))(:\\d{1,5})?([/?#].*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val domainLike = Regex(
        "^([\\p{L}\\p{N}-]+\\.)+[\\p{L}][\\p{L}\\p{N}-]{1,62}(:\\d{1,5})?([/?#].*)?$",
        RegexOption.IGNORE_CASE,
    )
    private val hostRegex = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://(?:[^/?#@]*@)?(\\[[^]]+]|[^/?#:]+)")

    fun isUrl(input: String): Boolean {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return false
        val scheme = schemeRegex.find(text)?.groupValues?.get(1)?.lowercase()
        if (scheme != null && scheme in explicitSchemes) return true
        return localHost.matches(text) || domainLike.matches(text)
    }

    /** Turns omnibox text into something loadable: a URL as typed, or a search on [engine]. */
    fun resolve(input: String, engine: SearchEngine): String {
        val text = input.trim()
        if (!isUrl(text)) return searchUrl(text, engine)
        val scheme = schemeRegex.find(text)?.groupValues?.get(1)?.lowercase()
        if (scheme != null && scheme in explicitSchemes) return text
        return (if (localHost.matches(text)) "http://" else "https://") + text
    }

    fun searchUrl(query: String, engine: SearchEngine): String =
        engine.searchUrl.replace("%s", URLEncoder.encode(query, "UTF-8"))

    fun host(url: String): String = hostRegex.find(url)?.groupValues?.get(1)?.lowercase().orEmpty()

    /** Host without a leading "www.", for display. */
    fun displayHost(url: String): String = host(url).removePrefix("www.")

    /** Permission grants belong to an origin, including its scheme and non-default port. */
    fun origin(url: String): String? = runCatching {
        val uri = java.net.URI(url)
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase() ?: return null
        val port = uri.port.takeIf { it >= 0 && it != (if (scheme == "https") 443 else 80) }
        "$scheme://$host" + (port?.let { ":$it" } ?: "")
    }.getOrNull()

    fun isHttps(url: String) = url.startsWith("https://", ignoreCase = true)

    fun isHttp(url: String) = url.startsWith("http://", ignoreCase = true)

    /** Loopback and LAN names have no certificates, so HTTPS-only leaves them alone. */
    fun isLocalHost(url: String): Boolean = localHost.matches(url.substringAfter("://"))

    /** The https:// form of an http:// URL; anything else is returned unchanged. */
    fun toHttps(url: String): String = if (isHttp(url)) "https://" + url.substring(7) else url

    /** The http:// form of an https:// URL, used when the user chooses to continue without HTTPS. */
    fun toHttp(url: String): String = if (isHttps(url)) "http://" + url.substring(8) else url

    fun isWebUrl(url: String) = url.startsWith("http://", true) || url.startsWith("https://", true)

    /** Registrable-ish comparison used for third-party detection: last two labels. */
    fun sameSite(a: String, b: String): Boolean = siteKey(a) == siteKey(b)

    private val secondLevel = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

    /** Approximates the registrable domain without a full public-suffix list: "a.b.co.uk" -> "b.co.uk". */
    private fun siteKey(host: String): String {
        val labels = host.split('.')
        val keep = if (labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in secondLevel) 3 else 2
        return if (labels.size <= keep) host else labels.takeLast(keep).joinToString(".")
    }
}
