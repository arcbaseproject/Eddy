package app.eddy.browser.browser

import android.content.Context
import android.webkit.WebView

/**
 * Reader mode. Mozilla's Readability runs inside the page on a clone of the document, and the
 * extracted article replaces the live document. Like [DevTools] it lives in the page, so it has to
 * be re-applied after every navigation in a tab that has it on.
 */
object ReaderMode {

    private const val ASSET = "readability.js"
    private var source: String? = null

    private fun source(context: Context): String =
        source ?: context.assets.open(ASSET).bufferedReader().use { it.readText() }.also { source = it }

    /** Replaces the page with its article text. Does nothing on a page Readability cannot parse. */
    fun show(context: Context, view: WebView, onResult: (Boolean) -> Unit = {}) {
        val js = """(function(){
            if(window.__eddyReader) return true;
            ${source(context)}
            var article=null;
            try{ article=new Readability(document.cloneNode(true)).parse(); }catch(e){}
            if(!article||!article.content) return false;
            window.__eddyReader=true;
            document.body.innerHTML='<article id="eddy-reader"><h1>'+(article.title||'')+'</h1>'+
                (article.byline?'<p class="byline">'+article.byline+'</p>':'')+article.content+'</article>';
            document.querySelectorAll('link[rel=stylesheet],style').forEach(function(n){n.remove();});
            var s=document.createElement('style');
            s.textContent='html{-webkit-text-size-adjust:100%}body{margin:0;background:#fff;color:#16181c}'+
                '@media (prefers-color-scheme:dark){body{background:#101215;color:#e6e8eb}a{color:#8ab4f8}}'+
                '#eddy-reader{max-width:42em;margin:0 auto;padding:24px 16px 64px;line-height:1.6;'+
                'font:18px/1.6 Georgia,serif;word-wrap:break-word}'+
                '#eddy-reader h1{font-size:1.6em;line-height:1.25}#eddy-reader .byline{opacity:.7;font-size:.85em}'+
                '#eddy-reader img,#eddy-reader video{max-width:100%;height:auto}'+
                '#eddy-reader pre{overflow-x:auto}';
            document.head.appendChild(s);
            return true;
        })()"""
        view.evaluateJavascript(js) { onResult(it == "true") }
    }

    /** The original page is gone from the DOM, so leaving reader mode means loading it again. */
    fun hide(view: WebView) = view.reload()
}
