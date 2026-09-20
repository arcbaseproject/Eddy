package app.eddy.browser.ui.animation

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import app.eddy.browser.ui.theme.LocalHapticsEnabled

/** Thin wrapper so call sites say what happened ("confirm", "tick") instead of picking constants. */
@Stable
class Haptics(private val view: View, private val enabled: Boolean) {
    fun tick() = fire(HapticFeedbackConstants.CLOCK_TICK)
    fun confirm() = fire(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK)
    fun longPress() = fire(HapticFeedbackConstants.LONG_PRESS)
    fun reject() = fire(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)

    private fun fire(type: Int) {
        if (enabled) view.performHapticFeedback(type)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { Haptics(view, enabled) }
}
