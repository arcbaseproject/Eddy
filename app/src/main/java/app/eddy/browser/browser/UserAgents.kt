package app.eddy.browser.browser

/** Derives clean mobile and desktop UA strings from the system WebView's default one. */
object UserAgents {
    private val chromeVersion = Regex("Chrome/([\\d.]+)")

    /** Drops the "wv" and "Version/4.0" markers so sites treat the browser like Chrome, not an embedded view. */
    fun mobile(default: String): String = default.replace("; wv", "").replace(" Version/4.0", "")

    fun desktop(default: String): String {
        val v = chromeVersion.find(default)?.groupValues?.get(1) ?: "126.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$v Safari/537.36"
    }
}
