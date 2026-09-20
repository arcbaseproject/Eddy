package app.eddy.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import app.eddy.browser.data.models.MotionPref
import app.eddy.browser.data.models.Settings
import app.eddy.browser.data.models.ThemeMode
import app.eddy.browser.ui.animation.rememberSystemReducedMotion

val EddyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** True when animations should snap instead of spring (system setting or in-app preference). */
val LocalReducedMotion = staticCompositionLocalOf { false }
val LocalHapticsEnabled = staticCompositionLocalOf { true }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EddyTheme(settings: Settings, incognito: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    } || incognito
    val context = LocalContext.current
    val amoled = settings.amoled && dark
    val scheme = when {
        incognito -> paletteScheme(Palette.SLATE, dark = true, amoled = settings.amoled)
        settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val s = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (amoled) s.toAmoled() else s
        }
        else -> paletteScheme(Palette.of(settings.palette), dark, amoled)
    }
    val reduced = settings.motion == MotionPref.REDUCED || rememberSystemReducedMotion()
    CompositionLocalProvider(
        LocalReducedMotion provides reduced,
        LocalHapticsEnabled provides settings.hapticsEnabled,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = EddyShapes,
            typography = EddyTypography,
            content = content,
        )
    }
}
