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
    var desktopScript: ScriptHandler? = null
    var cosmeticScript: ScriptHandler? = null
    var incognito = false
    /** Cancels the in-flight prerender, if any, when a new one starts or the omnibox closes. */
    var prerender: android.os.CancellationSignal? = null

    private val touchSlop = ViewConfiguration.get(ctx).scaledTouchSlop
    private val refreshThreshold = resources.displayMetrics.density * 72
    private var pullStartY = -1f
    private var pull = 0f
    /** Set once Chromium reports an unconsumed downward drag at the top. Pages that scroll an inner
     *  element keep scrollY at 0, so scrollY alone can't tell a pull from a normal scroll. */
    private var overscrolledTop = false

    /** The activity context is swapped in/out so dialogs work but the Activity is never leaked. */
    fun attachTo(context: Context) { ctx.baseContext = context }
    fun detachFromActivity(appContext: Context) { ctx.baseContext = appContext }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t - oldt, t)
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int, scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean,
    ): Boolean {
        if (deltaY < 0 && scrollY == 0) overscrolledTop = true
        return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pullStartY = if (scrollY == 0 && !canScrollVertically(-1)) event.y else -1f
                pull = 0f
                overscrolledTop = false
            }
            MotionEvent.ACTION_MOVE -> if (pullStartY >= 0 && event.pointerCount == 1) {
                // Measure the pull from where the page stopped consuming the drag.
                if (!overscrolledTop) { pullStartY = event.y; return super.onTouchEvent(event) }
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
