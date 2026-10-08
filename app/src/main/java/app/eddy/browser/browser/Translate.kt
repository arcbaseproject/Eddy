package app.eddy.browser.browser

import java.net.URLEncoder
import java.util.Locale

/**
 * Page translation through Google Translate's web proxy. WebView has no built-in translator, and the proxy
 * needs no API key or extra library. Google fetches the page itself, so pages behind a login do not translate.
 */
object Translate {
    /** Common languages, by Google Translate code. The device language is put first by [languages]. */
    private val codes = listOf(
        "en", "es", "fr", "de", "it", "pt", "nl", "pl", "uk", "ru", "tr", "ar", "fa", "hi", "bn",
        "id", "vi", "th", "zh-CN", "zh-TW", "ja", "ko", "sv", "da", "no", "fi", "cs", "el", "he", "ro", "hu",
    )

    fun deviceLanguage(locale: Locale = Locale.getDefault()): String = when {
        locale.language == "zh" -> if (locale.country in setOf("TW", "HK", "MO") || locale.script == "Hant") "zh-TW" else "zh-CN"
        locale.language == "nb" || locale.language == "nn" -> "no"
        locale.language == "iw" -> "he"
        else -> locale.language
    }

    fun languages(locale: Locale = Locale.getDefault()): List<String> {
        val device = deviceLanguage(locale)
        return listOf(device) + (codes - device)
    }

    /** The language's name in that language, e.g. "Deutsch", so a user can find their own in any UI language. */
    fun label(code: String): String {
        val l = Locale.forLanguageTag(code)
        return l.getDisplayName(l).replaceFirstChar { it.titlecase(l) }
    }

    /** True when [url] is already a translated copy served by the proxy. */
    fun isTranslated(url: String) = proxied.containsMatchIn(url)

    /** The proxy URL that shows [url] in [lang]. A page that is already translated switches language instead. */
    fun url(url: String, lang: String): String =
        if (isTranslated(url) && targetParam.containsMatchIn(url)) {
            targetParam.replace(url) { it.groupValues[1] + lang }
        } else {
            "https://translate.google.com/translate?sl=auto&tl=$lang&hl=$lang&u=" + URLEncoder.encode(url, "UTF-8")
        }

    private val proxied = Regex("^https?://[^/?#]+\\.translate\\.goog(?:[/?#:]|$)", RegexOption.IGNORE_CASE)
    private val targetParam = Regex("([?&]_x_tr_tl=)[^&#]*")
}
