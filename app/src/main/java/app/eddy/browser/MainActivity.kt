package app.eddy.browser

import android.Manifest
import android.app.PictureInPictureParams
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.eddy.browser.browser.BrowserRoot
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.UiEffect
import app.eddy.browser.data.models.ThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_OPEN_DOWNLOADS = BrowserViewModel.MAIN_OPEN_DOWNLOADS
    }

    private val vm: BrowserViewModel by viewModels()

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var permissionWaiter: CompletableDeferred<Map<String, Boolean>>? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        var uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        // Camera apps often return no data at all and just fill the file we gave them.
        if (uris == null && result.resultCode == RESULT_OK) {
            cameraUri?.takeIf { uri -> uri.path?.let { File(cacheDir, "capture/" + File(it).name).length() > 0 } == true }?.let { uris = arrayOf(it) }
        }
        cameraUri = null
        callback.onReceiveValue(uris)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        permissionWaiter?.complete(result)
        permissionWaiter = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settingsState.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val incognito = vm.tabs.selected?.incognito == true
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            } || incognito
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                window.isNavigationBarContrastEnforced = false
                onDispose {}
            }
            BrowserRoot(vm)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { vm.effectFlow.collect(::handleEffect) }
        }
        if (savedInstanceState == null) vm.handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.tabs.selected?.webView?.resumeTimers()
    }

    override fun onStop() {
        super.onStop()
        vm.onStop()
        // Keep timers running while something is playing so background audio is not cut off.
        if (!getSystemService(AudioManager::class.java).isMusicActive) vm.tabs.selected?.webView?.pauseTimers()
    }

    override fun onDestroy() {
        if (isFinishing || !isChangingConfigurations) vm.tabs.onActivityDestroyed()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        vm.tabs.onTrimMemory(level)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (customView != null) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    private suspend fun handleEffect(effect: UiEffect) {
        when (effect) {
            is UiEffect.ChooseFile -> launchFileChooser(effect)
            is UiEffect.RequestPermissions -> {
                val waiter = CompletableDeferred<Map<String, Boolean>>()
                permissionWaiter = waiter
                permissionLauncher.launch(effect.permissions.toTypedArray())
                effect.result.complete(waiter.await())
            }
            is UiEffect.ShowCustomView -> showCustomView(effect.view, effect.callback)
            UiEffect.HideCustomView -> hideCustomView()
            is UiEffect.Launch -> try {
                startActivity(effect.intent)
            } catch (_: ActivityNotFoundException) {
                vm.snackbar("No app can open this")
            } catch (_: SecurityException) {
                vm.snackbar("Could not open this")
            }
            is UiEffect.OpenFile -> try {
                startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(effect.uri), effect.mime.ifBlank { "*/*" })
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            } catch (_: Exception) {
                vm.snackbar("No app can open this file, or it was moved")
            }
        }
    }

    private suspend fun launchFileChooser(effect: UiEffect.ChooseFile) {
        fileCallback?.onReceiveValue(null)
        fileCallback = effect.callback
        val params = effect.params
        val accepts = params.acceptTypes.joinToString(",")
        val wantsPhoto = accepts.isBlank() || "image" in accepts || "*/*" in accepts
        val wantsVideo = "video" in accepts
        var cameraAllowed = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        // A site that explicitly asks for camera capture justifies the permission prompt; a plain upload does not.
        if (!cameraAllowed && params.isCaptureEnabled && (wantsPhoto || wantsVideo)) {
            val waiter = CompletableDeferred<Map<String, Boolean>>()
            permissionWaiter = waiter
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            cameraAllowed = waiter.await()[Manifest.permission.CAMERA] == true
        }
        val extra = mutableListOf<Intent>()
        if (cameraAllowed && wantsPhoto && !wantsVideo) {
            val dir = File(cacheDir, "capture").apply { mkdirs() }
            val file = File(dir, "IMG_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
            cameraUri = uri
            extra += Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (cameraAllowed && wantsVideo) extra += Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        val intent = if (params.isCaptureEnabled && extra.isNotEmpty()) extra.first()
        else Intent.createChooser(params.createIntent(), params.title ?: "Choose file").putExtra(Intent.EXTRA_INITIAL_INTENTS, extra.toTypedArray())
        try {
            fileLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
        }
    }

    private fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) { callback.onCustomViewHidden(); return }
        customView = view
        customViewCallback = callback
        (window.decorView as FrameLayout).addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun hideCustomView() {
        val view = customView ?: return
        (window.decorView as FrameLayout).removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
        vm.onCustomViewClosed()
    }
}
