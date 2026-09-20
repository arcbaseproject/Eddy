package app.eddy.browser.browser

import android.content.Intent
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.net.Uri
import app.eddy.browser.data.models.Settings
import app.eddy.browser.privacy.ContentBlocker
import app.eddy.browser.privacy.SitePermissions

/** What a link long-press resolved to. */
class HitTarget(val linkUrl: String?, val imageUrl: String?)

/** Everything a tab's WebView needs from the rest of the app; implemented by the ViewModel layer. */
interface BrowserHost {
    val settings: Settings
    val blocker: ContentBlocker
    val sites: SitePermissions
    val favicons: FaviconCache
    /** Colour painted behind pages before first paint, so dark themes do not flash white. */
    val pageBackground: Int

    fun onPageFinished(tab: BrowserTab)
    fun onTitleChanged(tab: BrowserTab)
    fun handleExternalIntent(tab: BrowserTab, intent: Intent, userGesture: Boolean, fallbackUrl: String?)
    fun requestFileChooser(callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams): Boolean
    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
    fun hideCustomView()
    fun requestWebPermission(tab: BrowserTab, request: PermissionRequest)
    fun requestGeolocation(tab: BrowserTab, origin: String, callback: GeolocationPermissions.Callback)
    fun requestHttpAuth(tab: BrowserTab, handler: HttpAuthHandler, host: String, realm: String)
    fun createPopup(parent: BrowserTab, resultMsg: Message, userGesture: Boolean): Boolean
    fun closeWindow(view: EddyWebView)
    fun startDownload(tab: BrowserTab, url: String, userAgent: String, contentDisposition: String, mime: String, length: Long)
    fun onLongPress(tab: BrowserTab, target: HitTarget)
    fun onRenderGone(tab: BrowserTab)
    fun onFindResult(tab: BrowserTab, active: Int, total: Int)
    fun onScrolled(tab: BrowserTab, dy: Int, scrollY: Int)
}
