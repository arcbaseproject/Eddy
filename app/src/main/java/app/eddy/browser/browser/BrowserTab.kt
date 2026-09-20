package app.eddy.browser.browser

import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.SslErrorHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.eddy.browser.util.UrlUtils
import java.util.UUID

enum class Security { NONE, SECURE, INSECURE, ERROR }

enum class ErrorKind { OFFLINE, DNS, SSL, TIMEOUT, UNAVAILABLE }

class PageError(
    val kind: ErrorKind,
    val url: String,
    val detail: String,
    /** Held so the user can explicitly proceed; must be cancelled if unused. */
    val sslHandler: SslErrorHandler? = null,
)

/**
 * One browsing tab. UI-visible fields are snapshot state; the [webView] is created lazily and can be
 * destroyed while the tab is inactive ([savedState] / the persisted state file then restore it).
 */
class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val incognito: Boolean = false,
    initialUrl: String = "",
    initialTitle: String = "",
) {
    var url by mutableStateOf(initialUrl)
    var title by mutableStateOf(initialTitle)
    var favicon by mutableStateOf<Bitmap?>(null)
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var security by mutableStateOf(Security.NONE)
    var error by mutableStateOf<PageError?>(null)
    var blockedCount by mutableIntStateOf(0)
    var thumbVersion by mutableIntStateOf(0)
    /** Explicit desktop-mode choice for this tab; null follows the site/global default. */
    var desktopOverride by mutableStateOf<Boolean?>(null)
    var desktopActive by mutableStateOf(false)
    var webView by mutableStateOf<EddyWebView?>(null)
    var pullDistance by mutableIntStateOf(0)

    @Volatile var pageHost: String = UrlUtils.host(initialUrl)
    @Volatile var blockingActive: Boolean = true
    @Volatile var trackerBlockingActive: Boolean = true

    var savedState: Bundle? = null
    var hasPersistedState = false
    var lastActive = System.currentTimeMillis()
    var lastHistoryId = 0L
    var lastHistoryUrl = ""
    var lastHistoryAt = 0L
    var openerId: String? = null

    val isHome: Boolean get() = url.isEmpty()
    val host: String get() = UrlUtils.displayHost(url)

    fun displayTitle(fallback: String): String = when {
        title.isNotBlank() -> title
        host.isNotBlank() -> host
        else -> fallback
    }
}
