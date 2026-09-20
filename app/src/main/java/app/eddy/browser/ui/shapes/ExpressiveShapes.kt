package app.eddy.browser.ui.shapes

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import app.eddy.browser.data.models.ShortcutStyle

/** The tasteful set of silhouettes used for shortcuts and accents. Every entry is roughly square. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object EddyPolygons {
    val squircle: RoundedPolygon = RoundedPolygon.rectangle(
        width = 1f, height = 1f, rounding = CornerRounding(radius = 0.38f, smoothing = 0.9f),
    ).normalized()

    val all: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided,
            squircle,
            MaterialShapes.Clover4Leaf,
            MaterialShapes.Diamond,
            MaterialShapes.Flower,
            MaterialShapes.SoftBurst,
            MaterialShapes.Circle,
        )
    }

    fun forShortcut(style: ShortcutStyle, index: Int): RoundedPolygon = when (style) {
        ShortcutStyle.MIXED -> all[index.mod(all.size)]
        ShortcutStyle.SQUIRCLE -> squircle
        ShortcutStyle.CIRCLE -> MaterialShapes.Circle
        ShortcutStyle.FLOWER -> MaterialShapes.Cookie9Sided
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
/** A [Shape] that morphs between two polygons; [progress] 0 is [Morph.start], 1 is the target. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress)
        val b = path.getBounds()
        val m = Matrix().apply {
            scale(size.width / b.width, size.height / b.height)
            translate(-b.left, -b.top)
        }
        path.transform(m)
        return Outline.Generic(path)
    }
}

@Composable
fun rememberMorph(from: RoundedPolygon, to: RoundedPolygon): Morph = remember(from, to) { Morph(from, to) }
