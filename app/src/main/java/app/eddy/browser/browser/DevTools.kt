package app.eddy.browser.browser

import android.content.Context
import android.webkit.WebView

/**
 * On-page developer tools (console, network, elements, storage), provided by the bundled Eruda
 * console. WebView has no local debugger protocol, so the panel is JavaScript running inside the
 * page itself; it must be re-injected after every navigation.
 */
object DevTools {

    private const val ASSET = "eruda.min.js"
    private var source: String? = null

    private fun source(context: Context): String =
        source ?: context.assets.open(ASSET).bufferedReader().use { it.readText() }.also { source = it }

    /** Injects the panel, or brings it back after a navigation. Safe to call on a page that already has it. */
    fun show(context: Context, view: WebView) {
        val js = "if(!window.eruda){" + source(context) + "\neruda.init({defaults:{displaySize:55,theme:'Monokai Pro'}});}eruda.show();"
        view.evaluateJavascript(js, null)
    }

    /** Removes the panel from the current page; a reload would drop it anyway. */
    fun hide(view: WebView) {
        view.evaluateJavascript("if(window.eruda)eruda.destroy();", null)
    }
}
