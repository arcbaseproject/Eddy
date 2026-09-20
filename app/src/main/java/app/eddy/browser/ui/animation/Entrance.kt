package app.eddy.browser.ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import app.eddy.browser.ui.theme.LocalReducedMotion
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Fade-and-rise entrance, staggered by [index]. Runs once per composition and is skipped with reduced motion. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index * 45L)
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 320f))
        }
    }
    return graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

