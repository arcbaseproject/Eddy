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

    /** Loopback and LAN names have no certificates, so HTTPS-only leaves them alone. Public IP addresses do not count. */
    fun isLocalHost(url: String): Boolean {
        if (!localHost.matches(url.substringAfter("://"))) return false
        val h = host(url).removeSurrounding("[", "]")
        return when {
            ':' in h -> isPrivateIpv6(h)
            h.all { it.isDigit() || it == '.' } -> isPrivateIpv4(h)
            else -> true
        }
    }

    /** Loopback, RFC 1918, link-local and carrier-grade NAT ranges. */
    private fun isPrivateIpv4(h: String): Boolean {
        val p = h.split('.').map { it.toIntOrNull() ?: return false }
        if (p.size != 4 || p.any { it > 255 }) return false
        return p[0] == 127 || p[0] == 10 || p[0] == 0 ||
            (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168) ||
            (p[0] == 169 && p[1] == 254) || (p[0] == 100 && p[1] in 64..127)
    }

    /** Loopback, link-local (fe80::/10) and unique-local (fc00::/7). */
    private fun isPrivateIpv6(h: String): Boolean {
        val a = h.lowercase().substringBefore('%')
        if (a == "::1") return true
        if (a.startsWith("::")) return false
        val first = a.substringBefore(':').toIntOrNull(16) ?: return false
        return first and 0xffc0 == 0xfe80 || first and 0xfe00 == 0xfc00
    }

    /** The https:// form of an http:// URL; anything else is returned unchanged. */
    fun toHttps(url: String): String = if (isHttp(url)) "https://" + url.substring(7) else url

    /** The http:// form of an https:// URL, used when the user chooses to continue without HTTPS. */
    fun toHttp(url: String): String = if (isHttps(url)) "http://" + url.substring(8) else url

    fun isWebUrl(url: String) = url.startsWith("http://", true) || url.startsWith("https://", true)

    /** Registrable-ish comparison used for third-party detection: last two labels. */
    fun sameSite(a: String, b: String): Boolean = siteKey(a) == siteKey(b)

    private val secondLevel = setOf("co", "com", "org", "net", "gov", "edu", "ac", "or", "ne", "go")

    // shortcut: the shared hosts most often seen in the wild, not the full public-suffix list; bundle the list if tenants on others matter.
    private val sharedHosts = setOf(
        "github.io", "gitlab.io", "pages.dev", "workers.dev", "vercel.app", "netlify.app", "herokuapp.com", "web.app",
        "firebaseapp.com", "appspot.com", "blogspot.com", "wordpress.com", "tumblr.com", "glitch.me", "onrender.com",
        "fly.dev", "azurewebsites.net", "cloudfront.net", "s3.amazonaws.com", "translate.goog", "ngrok.io", "ngrok-free.app",
    )

    /** Approximates the registrable domain without a full public-suffix list: "a.b.co.uk" -> "b.co.uk", "me.github.io" stays whole. */
    private fun siteKey(host: String): String {
        val labels = host.split('.')
        val keep = when {
            labels.size >= 3 && labels.takeLast(2).joinToString(".") in sharedHosts -> 3
            labels.size >= 4 && labels.takeLast(3).joinToString(".") in sharedHosts -> 4
            labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in secondLevel -> 3
            else -> 2
        }
        return if (labels.size <= keep) host else labels.takeLast(keep).joinToString(".")
    }
}
