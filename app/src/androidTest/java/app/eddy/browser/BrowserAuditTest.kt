package app.eddy.browser

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.downloads.DownloadRequest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs against real WebViews, Room, and activity lifecycle on a disposable emulator. */
class BrowserAuditTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var vm: BrowserViewModel

    @Before fun start() {
        val app = instrumentation.targetContext.applicationContext as EddyApp
        runBlocking { app.container.settingsStore.update { it.copy(restoreTabs = false, onboardingCompleted = true, httpsOnly = false) } }
        scenario = ActivityScenario.launch(Intent(instrumentation.targetContext, MainActivity::class.java))
        scenario.onActivity { vm = ViewModelProvider(it)[BrowserViewModel::class.java] }
        await { vm.tabs.selected != null }
        main { vm.onboardingSuppressedForTest(); vm.tabs.closeAll(true); vm.tabs.closeAll(false) }
    }

    @After fun finish() { scenario.close() }

    private fun BrowserViewModel.onboardingSuppressedForTest() {
        launchSettings { it.copy(onboardingCompleted = true, httpsOnly = false) }
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(condition: () -> Boolean) {
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (System.nanoTime() < until) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            Thread.sleep(50)
        }
        var detail = ""
        main { detail = vm.tabs.selected?.let { "url=${it.url}, title=${it.title}, loading=${it.isLoading}, back=${it.canGoBack}, error=${it.error?.detail}" }.orEmpty() }
        fail("Condition did not become true within 15 seconds: $detail")
    }

    @Test fun backgroundTabsRespectLiveBudget() {
        main { repeat(8) { vm.tabs.newTab("about:blank", select = false) } }
        main { assertTrue("Background tabs bypass the four-WebView budget", vm.tabs.tabs.count { it.webView != null } <= 4) }
    }

    @Test fun closingDuringRestoreDoesNotRecreateWebView() {
        lateinit var tab: app.eddy.browser.browser.BrowserTab
        main {
            tab = vm.tabs.newTab()
            tab.url = "about:blank"
            tab.hasPersistedState = true
            vm.tabs.select(tab) // Starts a disk read and suspends before building.
            vm.tabs.close(tab, remember = false)
        }
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        main { assertNull("Closed tab owns an orphan WebView", tab.webView) }
    }

    @Test fun navigationDuringRestoreKeepsNewView() {
        lateinit var tab: app.eddy.browser.browser.BrowserTab
        lateinit var expected: android.webkit.WebView
        main {
            tab = vm.tabs.newTab()
            tab.url = "about:blank"
            tab.hasPersistedState = true
            vm.tabs.select(tab)
            vm.tabs.load(tab, "data:text/html,new")
            expected = tab.webView!!
        }
        Thread.sleep(300)
        main { assertSame("Restore replaced the new navigation's WebView", expected, tab.webView) }
    }

    @Test fun staleOpenerDoesNotConsumeSystemBack() {
        main {
            val parent = vm.tabs.selected!!
            val child = vm.tabs.newTab(opener = parent)
            vm.tabs.close(parent, remember = false)
            assertSame(child, vm.tabs.selected)
            assertFalse("Back handler enabled even though onBack cannot handle it", vm.canHandleBack)
        }
    }

    @Test fun backgroundExternalFallbackDoesNotNavigateSelectedTab() {
        lateinit var selected: app.eddy.browser.browser.BrowserTab
        main {
            selected = vm.tabs.selected!!
            val background = vm.tabs.newTab("about:blank", select = false)
            vm.handleExternalIntent(background, Intent(Intent.ACTION_VIEW, Uri.parse("missing://test")), false, "https://example.com/")
            assertEquals("Background frame replaced foreground page", "", selected.url)
        }
    }

    @Test fun privateDataDownloadRetainsPrivateFlag() = runBlocking {
        val id = vm.downloads.enqueue(DownloadRequest("data:text/plain,private", "", "", "attachment; filename=private.txt", "text/plain", 7, true))
        val app = instrumentation.targetContext.applicationContext as EddyApp
        try {
            assertTrue("Private data download is stored as a normal download", app.container.database.downloads().get(id)!!.incognito)
        } finally { vm.downloads.delete(id, true) }
    }

    @Test fun recreationKeepsSelectedTabAndView() {
        lateinit var before: android.webkit.WebView
        var id = ""
        main {
            vm.navigate("data:text/html,<title>Recreation</title>ok")
            before = vm.tabs.selected!!.webView!!
            id = vm.tabs.selectedId!!
        }
        await { !vm.tabs.selected!!.isLoading }
        scenario.recreate()
        scenario.onActivity {
            val after = ViewModelProvider(it)[BrowserViewModel::class.java]
            assertEquals(id, after.tabs.selectedId)
            assertSame(before, after.tabs.selected!!.webView)
        }
    }

    @Test fun javascriptDialogsWork() {
        main { vm.navigate("data:text/html,<title>Dialogs</title><button onclick=\"alert('audit');document.title='accepted'\">alert</button>") }
        await { vm.tabs.selected!!.title == "Dialogs" }
        main { vm.tabs.selected!!.webView!!.evaluateJavascript("document.querySelector('button').click()", null) }
        Thread.sleep(300)
        await { android.view.inspector.WindowInspector.getGlobalWindowViews().any { it.findViewById<android.view.View>(android.R.id.button1) != null } }
        main { android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOf { it.findViewById<android.view.View>(android.R.id.button1) }.performClick() }
        await { vm.tabs.selected!!.title == "accepted" }
    }
    private fun javascript(script: String): String {
        val done = CountDownLatch(1)
        var result = ""
        main { vm.tabs.selected!!.webView!!.evaluateJavascript(script) { result = it; done.countDown() } }
        assertTrue("JavaScript callback timed out", done.await(10, TimeUnit.SECONDS))
        return result
    }

    @Test fun historySurvivesEvictionAndRestore() {
        HttpFixture().use { server ->
            main { vm.navigate(server.origin + "/a") }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            main { vm.navigate(server.origin + "/b") }
            await { vm.tabs.selected!!.title == "B" && vm.tabs.selected!!.canGoBack }
            lateinit var original: app.eddy.browser.browser.BrowserTab
            main {
                original = vm.tabs.selected!!
                vm.tabs.newTab()
                vm.tabs.evictInactive(1)
                assertNull(original.webView)
                vm.tabs.select(original)
            }
            await { original.webView != null && original.canGoBack }
            main { vm.goBack() }
            await { original.title == "A" && !original.isLoading }
            main { vm.goForward() }
            await { original.title == "B" && !original.isLoading }
        }
    }

    @Test fun redirectsAndPostKeepMethod() {
        HttpFixture().use { server ->
            main { vm.navigate(server.origin + "/redirect") }
            await { vm.tabs.selected!!.title == "B" && !vm.tabs.selected!!.isLoading }
            main { vm.navigate(server.origin + "/form") }
            await { vm.tabs.selected!!.title == "Form" && !vm.tabs.selected!!.isLoading }
            javascript("document.forms[0].submit()")
            await { vm.tabs.selected!!.title == "POST" && !vm.tabs.selected!!.isLoading }
        }
    }

    @Test fun rendererCrashCanReload() {
        HttpFixture().use { server ->
            main { vm.navigate(server.origin + "/a") }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            main { vm.tabs.selected!!.webView!!.loadUrl("chrome://crash") }
            await { vm.tabs.selected!!.error != null && vm.tabs.selected!!.webView == null }
            main { vm.retry() }
            await { vm.tabs.selected!!.webView != null && vm.tabs.selected!!.error == null && !vm.tabs.selected!!.isLoading }
            assertEquals("\"A\"", javascript("document.title"))
        }
    }

    @Test fun stoppedPermissionEffectStillCompletes() {
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
        }
        val result = kotlinx.coroutines.CompletableDeferred<Map<String, Boolean>>()
        main { vm.effects.trySend(app.eddy.browser.browser.UiEffect.RequestPermissions(listOf(android.Manifest.permission.CAMERA), result)) }
        Thread.sleep(400)
        shell("input keyevent KEYCODE_HOME")
        Thread.sleep(400)
        shell("am start -n app.eddy.browser/.MainActivity")
        Thread.sleep(400)
        shell("input keyevent KEYCODE_BACK")
        await { result.isCompleted }
    }

    @Test fun canceledWebPermissionRemovesPrompt() {
        val request = object : android.webkit.PermissionRequest() {
            var granted = false
            override fun getOrigin() = Uri.parse("https://audit-permission.example")
            override fun getResources() = arrayOf(RESOURCE_VIDEO_CAPTURE)
            override fun grant(resources: Array<out String>) { granted = true }
            override fun deny() {}
        }
        main {
            vm.requestWebPermission(vm.tabs.selected!!, request)
            assertTrue(vm.prompts.any { it is app.eddy.browser.browser.Prompt.Permission })
            vm.cancelWebPermission(request)
        }
        await { vm.prompts.none { it is app.eddy.browser.browser.Prompt.Permission } }
        assertFalse(request.granted)
    }

    @Test fun isolatedPrivateCookiesAndStorage() {
        org.junit.Assume.assumeTrue("This WebView lacks profiles", vm.incognitoIsolated)
        HttpFixture().use { server ->
            main { vm.navigate(server.origin + "/a") }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            javascript("document.cookie='normal=yes;path=/';localStorage.setItem('normal','yes')")
            main { vm.openInNewTab(server.origin + "/a", incognito = true) }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            assertEquals("\"|null\"", javascript("document.cookie+'|'+localStorage.getItem('normal')"))
            javascript("document.cookie='private=yes;path=/';localStorage.setItem('private','yes')")
            main { vm.tabs.closeAll(true); vm.openInNewTab(server.origin + "/a", incognito = true) }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            assertEquals("\"|null\"", javascript("document.cookie+'|'+localStorage.getItem('private')"))
        }
    }

    @Test fun privateBlobDownloadRetainsPrivateFlag() = runBlocking {
        val source = File(instrumentation.targetContext.cacheDir, "audit-blob.txt").apply { writeText("private") }
        val id = vm.downloads.importFile(source, "audit-blob.txt", "text/plain", incognito = true)
        val app = instrumentation.targetContext.applicationContext as EddyApp
        try { assertTrue(app.container.database.downloads().get(id)!!.incognito) }
        finally { vm.downloads.delete(id, true) }
    }

    @Test fun acceptedBadCertificateNeverShowsSecure() {
        HttpFixture(tls = true).use { server ->
            main { vm.navigate(server.origin + "/a") }
            await { vm.tabs.selected!!.error?.kind == app.eddy.browser.browser.ErrorKind.SSL }
            main { vm.proceedDespiteSslError() }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            main { assertEquals(app.eddy.browser.browser.Security.ERROR, vm.tabs.selected!!.security) }
        }
    }

    @Test fun homeDuringRestoreStaysHome() {
        lateinit var tab: app.eddy.browser.browser.BrowserTab
        main {
            tab = vm.tabs.newTab()
            tab.url = "about:blank"
            tab.hasPersistedState = true
            vm.tabs.select(tab)
            vm.tabs.showHome(tab)
        }
        Thread.sleep(300)
        main { assertTrue(tab.isHome); assertNull(tab.webView) }
    }

    @Test fun popupKeepsOpenerAndCanClose() {
        HttpFixture().use { server ->
            main { vm.navigate(server.origin + "/a"); vm.setSiteSetting(vm.tabs.selected!!, app.eddy.browser.privacy.SiteFeature.POPUPS, app.eddy.browser.data.database.SiteSettings.ALLOW) }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            var parent = ""
            main { parent = vm.tabs.selectedId!! }
            javascript("window.open('/b')")
            await { vm.tabs.selected!!.title == "B" && !vm.tabs.selected!!.isLoading }
            main { assertEquals(parent, vm.tabs.selected!!.openerId) }
            javascript("window.close()")
            await { vm.tabs.selectedId == parent }
        }
    }

    @Test fun authenticatedDownloadCanPauseAndResume() = runBlocking {
        val dao = (instrumentation.targetContext.applicationContext as EddyApp).container.database.downloads()
        HttpFixture().use { server ->
            val id = vm.downloads.enqueue(DownloadRequest(server.origin + "/slow-auth", "", "", "attachment; filename=resume.bin", "application/octet-stream", -1, cookie = "auth=yes"))
            try {
                kotlinx.coroutines.withTimeout(15000) { while ((vm.downloads.live.value[id]?.downloaded ?: 0) == 0L) kotlinx.coroutines.delay(50) }
                vm.downloads.pause(id)
                kotlinx.coroutines.withTimeout(15000) { while (vm.downloads.active.value != 0) kotlinx.coroutines.delay(50) }
                assertEquals(app.eddy.browser.data.database.DownloadStatus.PAUSED, dao.get(id)!!.status)
                vm.downloads.resume(id)
                kotlinx.coroutines.withTimeout(15000) { while (vm.downloads.active.value != 0) kotlinx.coroutines.delay(50) }
                val result = dao.get(id)!!
                assertEquals(result.error, app.eddy.browser.data.database.DownloadStatus.COMPLETED, result.status)
                assertEquals(2L * 1024 * 1024, result.downloadedBytes)
            } finally { vm.downloads.delete(id, true) }
        }
    }

    @Test fun queuedDownloadCancellationUpdatesDatabase() = runBlocking {
        val dao = (instrumentation.targetContext.applicationContext as EddyApp).container.database.downloads()
        HttpFixture().use { server ->
            val ids = (1..4).map { vm.downloads.enqueue(DownloadRequest(server.origin + "/slow", "", "", "", "application/octet-stream", -1)) }
            try {
                vm.downloads.cancel(ids.last())
                kotlinx.coroutines.withTimeout(15000) {
                    while (dao.get(ids.last())!!.status != app.eddy.browser.data.database.DownloadStatus.CANCELED) kotlinx.coroutines.delay(50)
                }
            } finally { ids.forEach { vm.downloads.delete(it, true) } }
        }
    }

    @Test fun invalidResumeRangeIsNotPublished() = runBlocking {
        val dao = (instrumentation.targetContext.applicationContext as EddyApp).container.database.downloads()
        HttpFixture().use { server ->
            val id = dao.insert(app.eddy.browser.data.database.DownloadEntity(url = server.origin + "/bad-range", fileName = "range.bin", mimeType = "application/octet-stream", resumable = true))
            File(instrumentation.targetContext.filesDir, "downloads/$id.part").writeText("abc")
            try {
                vm.downloads.resume(id)
                kotlinx.coroutines.withTimeout(15000) { while (vm.downloads.active.value != 0) kotlinx.coroutines.delay(50) }
                assertEquals(app.eddy.browser.data.database.DownloadStatus.FAILED, dao.get(id)!!.status)
                assertEquals("", dao.get(id)!!.contentUri)
            } finally { vm.downloads.delete(id, true) }
        }
    }

    @Test fun unavailableExternalAppUsesFallback() {
        HttpFixture().use { server ->
            main { vm.launchSettings { it.copy(externalLinks = app.eddy.browser.data.models.ExternalLinks.ALWAYS) } }
            await { vm.settings.externalLinks == app.eddy.browser.data.models.ExternalLinks.ALWAYS }
            main { vm.handleExternalIntent(vm.tabs.selected!!, Intent(Intent.ACTION_VIEW, Uri.parse("eddy-audit-missing://test")), true, server.origin + "/a") }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
        }
    }

    @Test fun rememberedPermissionDoesNotCrossOrigins() {
        val request = object : android.webkit.PermissionRequest() {
            var granted = false
            override fun getOrigin() = Uri.parse("https://www.audit-permission.example:8443")
            override fun getResources() = arrayOf(RESOURCE_VIDEO_CAPTURE)
            override fun grant(resources: Array<out String>) { granted = true }
            override fun deny() {}
        }
        main {
            vm.sites.set("audit-permission.example", app.eddy.browser.privacy.SiteFeature.CAMERA, app.eddy.browser.data.database.SiteSettings.ALLOW)
            vm.sites.set("https://www.audit-permission.example", app.eddy.browser.privacy.SiteFeature.CAMERA, app.eddy.browser.data.database.SiteSettings.ALLOW)
            vm.requestWebPermission(vm.tabs.selected!!, request)
            assertTrue(vm.prompts.any { it is app.eddy.browser.browser.Prompt.Permission && it.host == "https://www.audit-permission.example:8443" })
            vm.cancelWebPermission(request)
            assertFalse(request.granted)
        }
    }

    @Test fun recreationDetachesBackgroundActivityAndExitsFullscreen() {
        lateinit var background: app.eddy.browser.browser.BrowserTab
        var hidden = false
        main {
            background = vm.tabs.newTab("about:blank")
            vm.tabs.newTab("about:blank")
            vm.showCustomView(android.widget.FrameLayout(instrumentation.targetContext), object : android.webkit.WebChromeClient.CustomViewCallback {
                override fun onCustomViewHidden() { hidden = true }
            })
        }
        await { vm.customViewActive }
        scenario.recreate()
        main {
            assertTrue(hidden)
            assertFalse(vm.customViewActive)
            assertFalse((background.webView!!.context as android.content.MutableContextWrapper).baseContext is android.app.Activity)
        }
    }

    @Test fun rotationKeepsPageAndTypedInput() {
        HttpFixture().use { server ->
            main { vm.navigate(server.origin + "/a") }
            await { vm.tabs.selected!!.title == "A" && !vm.tabs.selected!!.isLoading }
            javascript("document.querySelector('#text').value='retained'")
            var id = ""
            main { id = vm.tabs.selectedId!! }
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            Thread.sleep(400)
            main { assertEquals(id, vm.tabs.selectedId) }
            assertEquals("\"retained\"", javascript("document.querySelector('#text').value"))
            scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        }
    }

    @Test fun filePickerCancellationCompletesCallback() {
        var answered = false
        var result: Array<Uri>? = arrayOf(Uri.EMPTY)
        val params = object : android.webkit.WebChromeClient.FileChooserParams() {
            override fun getMode() = MODE_OPEN_MULTIPLE
            override fun getAcceptTypes() = arrayOf(".pdf,image/*")
            override fun isCaptureEnabled() = false
            override fun getTitle(): CharSequence = "Audit upload"
            override fun getFilenameHint(): String? = null
            override fun createIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT)
        }
        main { vm.requestFileChooser(android.webkit.ValueCallback { result = it; answered = true }, params) }
        Thread.sleep(500)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
        await { answered }
        assertNull(result)
    }

    @Test fun malformedTabIndexDoesNotCrashRestore() = runBlocking {
        val file = File(instrumentation.targetContext.filesDir, "tabs.json")
        file.writeText("{\"tabs\":[null,7,{\"id\":\"../bad\"}]}")
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            vm.tabs.restore()
            assertTrue(vm.tabs.tabs.none { it.id == "../bad" })
        }
    }

}
