package app.eddy.browser.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.eddy.browser.browser.FaviconCache

val LocalFavicons = staticCompositionLocalOf<FaviconCache?> { null }

/**
 * A site's favicon, or a coloured initial while none is known. Pass [live] for the currently loaded
 * page's icon, and [fetch] to let the cache request /favicon.ico from the site itself.
 */
@Composable
fun SiteIcon(
    host: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    live: Bitmap? = null,
    fetch: Boolean = false,
    shape: Shape = CircleShape,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val cache = LocalFavicons.current
    var bitmap by remember(host) { mutableStateOf(cache?.peek(host)) }
    LaunchedEffect(host, live) {
        if (live == null && bitmap == null && cache != null) {
            bitmap = cache.load(host) ?: if (fetch) cache.fetch(host) else null
        }
    }
    val shown = live ?: bitmap
    Box(modifier.size(size).clip(shape).background(background), contentAlignment = Alignment.Center) {
        if (shown != null) {
            Image(shown.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(size * 0.66f))
        } else {
            Text(
                host.removePrefix("www.").firstOrNull()?.uppercase() ?: "•",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
