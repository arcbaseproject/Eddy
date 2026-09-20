package app.eddy.browser.ui.animation

import android.provider.Settings
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

@Composable
fun rememberSystemReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
