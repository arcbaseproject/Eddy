package app.eddy.browser.browser

import android.graphics.Bitmap
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.net.Uri
import app.eddy.browser.util.UrlUtils

class EddyChromeClient(private val tab: BrowserTab, private val host: BrowserHost) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        if (tab.error != null) return
        tab.progress = newProgress
        tab.isLoading = newProgress < 100
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        tab.title = title.orEmpty()
        host.onTitleChanged(tab)
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        icon ?: return
        tab.favicon = icon
        host.favicons.put(UrlUtils.displayHost(tab.url), icon, persist = !tab.incognito)
    }

    override fun onShowCustomView(view: View, callback: CustomViewCallback) = host.showCustomView(view, callback)
    override fun onHideCustomView() = host.hideCustomView()

    /** Avoids the grey placeholder box some sites show before a video starts. */
    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun onPermissionRequest(request: PermissionRequest) = host.requestWebPermission(tab, request)

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
        host.requestGeolocation(tab, origin, callback)

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.requestFileChooser(filePathCallback, fileChooserParams)

    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean =
        host.createPopup(tab, resultMsg, isUserGesture)

    override fun onCloseWindow(window: WebView) {
        (window as? EddyWebView)?.let(host::closeWindow)
    }
}
