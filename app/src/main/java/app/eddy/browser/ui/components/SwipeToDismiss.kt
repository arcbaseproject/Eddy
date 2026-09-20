package app.eddy.browser.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import app.eddy.browser.ui.animation.rememberHaptics
import app.eddy.browser.ui.animation.spatialSpring
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** Horizontal fling-away with a spring-back when released early. The card follows the finger 1:1. */
@Composable
fun Modifier.swipeToDismiss(onDismiss: () -> Unit): Modifier {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var width by remember { mutableFloatStateOf(1f) }
    val settle = spatialSpring<Float>()
    return this
        .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            translationX = offset.value
            alpha = 1f - (abs(offset.value) / width).coerceIn(0f, 1f) * 0.7f
            rotationZ = offset.value / width * 6f
        }
        .draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta -> scope.launch { offset.snapTo(offset.value + delta) } },
            onDragStopped = { velocity ->
                val flung = abs(velocity) > 1800f
                val far = abs(offset.value) > width * 0.4f
                if (flung || far) {
                    val dir = if (flung) sign(velocity) else sign(offset.value)
                    haptics.confirm()
                    offset.animateTo(dir * width * 1.3f, spring(stiffness = 500f))
                    onDismiss()
                } else {
                    offset.animateTo(0f, settle)
                }
            },
        )
}
