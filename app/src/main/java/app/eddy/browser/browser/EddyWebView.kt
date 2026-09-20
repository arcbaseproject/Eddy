package app.eddy.browser.browser

import android.content.Context
import android.content.ContextWrapper
import android.content.MutableContextWrapper
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.webkit.ScriptHandler

/**
 * WebView with two extras the browser chrome needs: scroll-direction callbacks (to collapse the
 * toolbar) and a pull-to-refresh gesture that only starts when the page is scrolled to the top.
 */
class EddyWebView(private val ctx: MutableContextWrapper) : WebView(ctx) {
    /** Called with the vertical scroll delta in px and the resulting scrollY. */
    var onScrolled: ((dy: Int, scrollY: Int) -> Unit)? = null
    /** Dampened pull distance in px while pulling down at the top of the page. */
    var onPull: ((Int) -> Unit)? = null
    var onPullRelease: ((triggered: Boolean) -> Unit)? = null
    var dntScript: ScriptHandler? = null
    var incognito = false

    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val refreshThreshold = resources.displayMetrics.density * 72
    private var pullStartY = -1f
    private var pull = 0f

    /** The activity context is swapped in/out so dialogs work but the Activity is never leaked. */
    fun attachTo(context: Context) { ctx.baseContext = context }
    fun detachFromActivity(appContext: Context) { ctx.baseContext = appContext }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t - oldt, t)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullStartY = if (scrollY == 0 && !canScrollVertically(-1)) event.y else -1f
                pull = 0f
            }
            MotionEvent.ACTION_MOVE -> if (pullStartY >= 0 && event.pointerCount == 1) {
                val dy = event.y - pullStartY
                if (dy > touchSlop * 2) {
                    pull = (dy - touchSlop * 2) * 0.5f
                    onPull?.invoke(pull.toInt())
                } else if (dy < -touchSlop) {
                    pullStartY = -1f
                    if (pull > 0f) onPull?.invoke(0)
                    pull = 0f
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (pull > 0f) {
                    onPullRelease?.invoke(event.actionMasked == MotionEvent.ACTION_UP && pull >= refreshThreshold)
                    onPull?.invoke(0)
                }
                pullStartY = -1f
                pull = 0f
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        /** Finds the Activity behind a wrapped context (needed for dialogs). */
        fun unwrap(context: Context): Context? {
            var c: Context? = context
            while (c is ContextWrapper) c = c.baseContext
            return c
        }
    }
}
