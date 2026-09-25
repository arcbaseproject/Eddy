package app.eddy.browser.browser

import android.Manifest
import android.content.Context
import android.content.Intent
import android.print.PrintDocumentAdapter
import android.content.pm.PackageManager
import android.net.Uri
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.core.content.ContextCompat
import app.eddy.browser.data.database.SiteSettings
import app.eddy.browser.privacy.get
import app.eddy.browser.privacy.SiteFeature
import app.eddy.browser.privacy.SitePermissions
import app.eddy.browser.util.UrlUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class PermissionAnswer(val allow: Boolean, val remember: Boolean)

/** Modal questions the browser has to put to the user; rendered by the UI layer. */
sealed interface Prompt {
    class Permission(
        val host: String,
        val features: List<SiteFeature>,
        val answer: CompletableDeferred<PermissionAnswer>,
        val incognito: Boolean,
    ) : Prompt

    class HttpAuth(val host: String, val realm: String, val handler: HttpAuthHandler) : Prompt
    class ExternalApp(val label: String?, val intent: Intent, val fallbackUrl: String?, val tabId: String) : Prompt

    /** A download of an executable or installer type, which the user confirms before it starts. */
    class RiskyDownload(val fileName: String, val sourceHost: String, val insecure: Boolean, val start: () -> Unit) : Prompt
}

/** One-shot requests the ViewModel cannot fulfil itself because they need an Activity. */
sealed interface UiEffect {
    class ChooseFile(val callback: ValueCallback<Array<Uri>>, val params: WebChromeClient.FileChooserParams) : UiEffect
    class RequestPermissions(val permissions: List<String>, val result: CompletableDeferred<Map<String, Boolean>>) : UiEffect
    class ShowCustomView(val view: View, val callback: WebChromeClient.CustomViewCallback) : UiEffect
    data object HideCustomView : UiEffect
    class Launch(val intent: Intent, val onFailure: (() -> Unit)? = null) : UiEffect
    class OpenFile(val uri: String, val mime: String) : UiEffect
    class Print(val adapter: PrintDocumentAdapter, val jobName: String) : UiEffect
}

class SnackbarMessage(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/** Turns WebView permission callbacks into site-policy decisions, prompts and Android runtime requests. */
class WebPermissionCoordinator(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sites: SitePermissions,
    private val showPrompt: (Prompt) -> Unit,
    private val requestRuntime: suspend (List<String>) -> Map<String, Boolean>,
    private val dismissPrompt: (Prompt) -> Unit = {},
) {
    private val webRequests = mutableMapOf<PermissionRequest, Job>()
    private val locations = mutableMapOf<String, Job>()

    fun cancel(request: PermissionRequest) { webRequests.remove(request)?.cancel() }
    fun cancelGeolocation(origin: String) { locations.remove(origin)?.cancel() }

    fun onWebRequest(request: PermissionRequest, incognito: Boolean) {
        val host = UrlUtils.origin(request.origin.toString()) ?: run { request.deny(); return }
        val map = request.resources.mapNotNull { res ->
            when (res) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> res to SiteFeature.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> res to SiteFeature.MICROPHONE
                else -> null
            }
        }
        // DRM licences are harmless to grant; other resource kinds (MIDI etc.) are refused.
        val drm = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
        if (map.isEmpty()) {
            if (drm.isEmpty()) request.deny() else request.grant(drm.toTypedArray())
            return
        }
        val job = scope.launch {
            val allowed = decide(host, map.map { it.second }, incognito)
            val runtime = allowed.mapNotNull {
                when (it) {
                    SiteFeature.CAMERA -> Manifest.permission.CAMERA
                    SiteFeature.MICROPHONE -> Manifest.permission.RECORD_AUDIO
                    else -> null
                }
            }
            val results = if (runtime.isEmpty()) emptyMap() else ensureRuntime(runtime)
            val granted = map.filter { (_, f) ->
                f in allowed && results[if (f == SiteFeature.CAMERA) Manifest.permission.CAMERA else Manifest.permission.RECORD_AUDIO] != false
            }.map { it.first } + drm
            if (granted.isEmpty()) request.deny() else request.grant(granted.toTypedArray())
        }
        webRequests[request] = job
        job.invokeOnCompletion { webRequests.remove(request) }
    }

    fun onGeolocation(origin: String, callback: GeolocationPermissions.Callback, incognito: Boolean) {
        val host = UrlUtils.origin(origin) ?: run { callback.invoke(origin, false, false); return }
        locations.remove(origin)?.cancel()
        val job = scope.launch {
            val allowed = SiteFeature.LOCATION in decide(host, listOf(SiteFeature.LOCATION), incognito)
            val ok = allowed && ensureRuntime(
                listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            ).values.any { it }
            callback.invoke(origin, ok, false)
        }
        locations[origin] = job
        job.invokeOnCompletion { locations.remove(origin) }
    }

    /** Applies stored site policy; anything undecided is put to the user in a single prompt. */
    private suspend fun decide(host: String, features: List<SiteFeature>, incognito: Boolean): Set<SiteFeature> {
        val stored = if (incognito) null else sites.peek(host)
        val undecided = features.filter { stored?.get(it) == null }
        val allowed = features.filter { stored?.get(it) == SiteSettings.ALLOW }.toMutableSet()
        if (undecided.isNotEmpty()) {
            val answer = CompletableDeferred<PermissionAnswer>()
            val prompt = Prompt.Permission(host, undecided, answer, incognito)
            showPrompt(prompt)
            val a = try { answer.await() } finally { dismissPrompt(prompt) }
            if (a.allow) allowed += undecided
            if (a.remember && !incognito) undecided.forEach { sites.set(host, it, if (a.allow) SiteSettings.ALLOW else SiteSettings.BLOCK) }
        }
        return allowed
    }

    private suspend fun ensureRuntime(perms: List<String>): Map<String, Boolean> {
        val missing = perms.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        val asked = if (missing.isEmpty()) emptyMap() else requestRuntime(missing)
        return perms.associateWith { it !in missing || asked[it] == true }
    }
}
