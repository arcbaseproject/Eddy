package app.eddy.browser.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Seed palettes for devices without dynamic color, or users who prefer a fixed look. */
enum class Palette(val label: String, val hue: Float, val saturation: Float = 1f) {
    ORCHID("Orchid", 268f),
    OCEAN("Ocean", 208f),
    MEADOW("Meadow", 142f, 0.85f),
    EMBER("Ember", 18f),
    ROSE("Rose", 338f),
    SLATE("Slate", 215f, 0.25f),
    ;

    val swatch: Color get() = Color.hsl(hue, 0.6f * saturation, 0.5f)

    companion object {
        fun of(index: Int) = entries.getOrElse(index) { ORCHID }
    }
}

private fun tone(hue: Float, s: Float, l: Float) = Color.hsl(hue.mod(360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))

/** Builds a Material-style tonal scheme from a hue using HSL tones; small and dependency free. */
fun paletteScheme(p: Palette, dark: Boolean, amoled: Boolean): ColorScheme {
    val h = p.hue
    val k = p.saturation
    val scheme = if (!dark) {
        lightColorScheme(
            primary = tone(h, .55f * k + .1f, .40f),
            onPrimary = Color.White,
            primaryContainer = tone(h, .80f * k, .90f),
            onPrimaryContainer = tone(h, .70f * k, .14f),
            inversePrimary = tone(h, .85f * k, .80f),
            secondary = tone(h + 12, .30f * k + .04f, .40f),
            onSecondary = Color.White,
            secondaryContainer = tone(h + 12, .42f * k, .90f),
            onSecondaryContainer = tone(h + 12, .40f * k, .14f),
            tertiary = tone(h + 62, .42f * k + .05f, .40f),
            onTertiary = Color.White,
            tertiaryContainer = tone(h + 62, .62f * k, .90f),
            onTertiaryContainer = tone(h + 62, .60f * k, .14f),
            background = tone(h, .30f * k, .98f),
            onBackground = tone(h, .15f * k, .10f),
            surface = tone(h, .30f * k, .98f),
            onSurface = tone(h, .15f * k, .10f),
            surfaceVariant = tone(h, .22f * k, .91f),
            onSurfaceVariant = tone(h, .14f * k, .30f),
            surfaceTint = tone(h, .55f * k + .1f, .40f),
            inverseSurface = tone(h, .10f * k, .20f),
            inverseOnSurface = tone(h, .20f * k, .95f),
            outline = tone(h, .10f * k, .48f),
            outlineVariant = tone(h, .16f * k, .80f),
            surfaceBright = tone(h, .30f * k, .98f),
            surfaceDim = tone(h, .18f * k, .87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(h, .30f * k, .96f),
            surfaceContainer = tone(h, .26f * k, .94f),
            surfaceContainerHigh = tone(h, .24f * k, .92f),
            surfaceContainerHighest = tone(h, .22f * k, .90f),
        )
    } else {
        darkColorScheme(
            primary = tone(h, .85f * k + .05f, .80f),
            onPrimary = tone(h, .70f * k, .16f),
            primaryContainer = tone(h, .48f * k, .30f),
            onPrimaryContainer = tone(h, .80f * k, .90f),
            inversePrimary = tone(h, .55f * k + .1f, .40f),
            secondary = tone(h + 12, .36f * k, .78f),
            onSecondary = tone(h + 12, .30f * k, .18f),
            secondaryContainer = tone(h + 12, .26f * k, .28f),
            onSecondaryContainer = tone(h + 12, .40f * k, .90f),
            tertiary = tone(h + 62, .55f * k, .78f),
            onTertiary = tone(h + 62, .40f * k, .18f),
            tertiaryContainer = tone(h + 62, .35f * k, .28f),
            onTertiaryContainer = tone(h + 62, .60f * k, .90f),
            background = tone(h, .14f * k, .08f),
            onBackground = tone(h, .10f * k, .91f),
            surface = tone(h, .14f * k, .08f),
            onSurface = tone(h, .10f * k, .91f),
            surfaceVariant = tone(h, .12f * k, .27f),
            onSurfaceVariant = tone(h, .12f * k, .80f),
            surfaceTint = tone(h, .85f * k + .05f, .80f),
            inverseSurface = tone(h, .10f * k, .91f),
            inverseOnSurface = tone(h, .14f * k, .18f),
            outline = tone(h, .08f * k, .58f),
            outlineVariant = tone(h, .12f * k, .27f),
            surfaceBright = tone(h, .12f * k, .24f),
            surfaceDim = tone(h, .14f * k, .08f),
            surfaceContainerLowest = tone(h, .14f * k, .05f),
            surfaceContainerLow = tone(h, .14f * k, .10f),
            surfaceContainer = tone(h, .13f * k, .13f),
            surfaceContainerHigh = tone(h, .12f * k, .17f),
            surfaceContainerHighest = tone(h, .12f * k, .21f),
        )
    }
    return if (dark && amoled) scheme.toAmoled() else scheme
}

/** True black surfaces with slightly lifted containers so cards stay distinguishable. */
fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    surfaceBright = Color(0xFF262626),
)
