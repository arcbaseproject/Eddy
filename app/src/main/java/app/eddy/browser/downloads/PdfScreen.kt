package app.eddy.browser.downloads

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.eddy.browser.browser.BrowserViewModel
import app.eddy.browser.browser.Screen
import app.eddy.browser.ui.components.EddyIconButton
import app.eddy.browser.ui.components.EmptyState
import app.eddy.browser.ui.components.ScreenScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a downloaded PDF in the app instead of handing it to another one. Pages are rendered on
 * demand with the framework's [PdfRenderer] (no dependency, no network) and cached while the screen
 * is open; the renderer itself is single-threaded, so every call runs on one background dispatcher.
 */
@Composable
fun PdfScreen(vm: BrowserViewModel) {
    val context = LocalContext.current
    val open = vm.openPdf ?: return
    val document = remember(open.uri) { PdfDocument(context, Uri.parse(open.uri)) }
    DisposableEffect(document) { onDispose { document.close() } }

    ScreenScaffold(
        title = open.name.ifBlank { "PDF" },
        onBack = { vm.closePdf() },
        actions = {
            EddyIconButton(Icons.Rounded.Share, "Share ${open.name}", { vm.sharePdf() })
            EddyIconButton(Icons.Rounded.OpenInNew, "Open in another app", { vm.openPdfExternally() })
        },
    ) { padding ->
        when (val count = document.pageCount) {
            null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) { CircularProgressIndicator() }
            0 -> EmptyState(Icons.Rounded.Description, "Can't open this PDF", "The file may be damaged or password protected.", Modifier.padding(padding))
            else -> BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                LazyColumn(
                    Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items((0 until count).toList()) { index -> PdfPage(document, index, widthPx) }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(document: PdfDocument, index: Int, widthPx: Int) {
    var bitmap by remember(document, index, widthPx) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(document, index, widthPx) { bitmap = document.render(index, widthPx) }

    val image = bitmap
    if (image == null) {
        // A4 proportions keep the scroll bar honest until the real page arrives.
        Box(Modifier.fillMaxWidth().aspectRatio(0.707f).background(MaterialTheme.colorScheme.surfaceContainerHigh))
    } else {
        Image(image.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
    }
}

/** Thin wrapper that keeps the file descriptor, the renderer and the page cache together. */
private class PdfDocument(context: Context, uri: Uri) {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val cache = LinkedHashMap<Int, Bitmap>()
    var pageCount: Int? = null
        private set

    init {
        runCatching {
            val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open $uri")
            descriptor = fd
            renderer = PdfRenderer(fd).also { pageCount = it.pageCount }
        }.onFailure { pageCount = 0 }
    }

    suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        val r = renderer ?: return@withContext null
        synchronized(this@PdfDocument) {
            cache[index]?.takeIf { it.width == widthPx }?.let { return@withContext it }
            runCatching {
                r.openPage(index).use { page ->
                    val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    // PdfRenderer draws only ink, so an unpainted page would come out transparent.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache[index] = bitmap
                    while (cache.size > MAX_CACHED_PAGES) cache.remove(cache.keys.first())
                    bitmap
                }
            }.getOrNull()
        }
    }

    fun close() {
        synchronized(this) {
            cache.clear()
            runCatching { renderer?.close() }
            runCatching { descriptor?.close() }
            renderer = null
            descriptor = null
        }
    }

    private companion object {
        // ponytail: fixed page cache, swap for a size-aware cache if huge pages start churning.
        const val MAX_CACHED_PAGES = 6
    }
}
