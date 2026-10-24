package app.eddy.browser.ui.animation

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import app.eddy.browser.ui.theme.LocalReducedMotion

/** Spring for things that move or resize: bouncy enough to feel alive, settles quickly. */
@Composable
fun <T> spatialSpring(bouncy: Boolean = true): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap()
    else spring(if (bouncy) 0.72f else Spring.DampingRatioNoBouncy, 380f)

/** Spring for color/alpha: never overshoots. */
@Composable
fun <T> effectSpring(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(Spring.DampingRatioNoBouncy, 700f)

/** Follows the system "Remove animations" switch, including changes made while Eddy keeps running. */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val reduced = remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced.value = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced.value
}
