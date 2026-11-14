package com.nova.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import com.nova.designsystem.theme.NovaTheme

/**
 * Stylised Nova map, drawn from the same vector geometry as the design canvas (390x844 design
 * space, scaled to the real screen). It is a stand-in until the real map SDK is wired in; swap
 * this composable for the map provider's composable and keep the overlays.
 */
@Composable
fun MapBackdrop(
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    overlay: MapOverlayScope.() -> Unit = {},
) {
    val c = NovaTheme.colors
    val water = remember { svgPath("M-20 520 C 80 480 150 560 260 520 S 410 460 420 450 L420 900 L-20 900Z") }
    val minor = remember { listOf("M-10 70 L400 92", "M-10 290 L400 270", "M160 -10 L176 860", "M350 -10 L356 860").map(::svgPath) }
    val major = remember { listOf("M-10 130 L400 160", "M70 -10 L104 860", "M-10 420 L400 380", "M280 -10 L300 860").map(::svgPath) }
    Canvas(modifier.fillMaxSize()) {
        val sx = size.width / DesignWidth
        val sy = size.height / DesignHeight
        drawRect(c.mapLand)
        withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
            drawPath(water, c.mapWater)
            drawRoundRect(c.mapPark, topLeft = Offset(226f, 140f), size = Size(130f, 90f), cornerRadius = CornerRadius(20f))
            drawRoundRect(c.mapPark, topLeft = Offset(24f, 330f), size = Size(100f, 70f), cornerRadius = CornerRadius(18f))
            minor.forEach { drawPath(it, c.mapRoad, style = Stroke(4f, cap = StrokeCap.Round)) }
            major.forEach { drawPath(it, c.mapRoad, style = Stroke(11f, cap = StrokeCap.Round)) }
            MapOverlayScope(this).overlay()
        }
        if (dimmed) drawRect(Color.Black.copy(alpha = 0.4f))
    }
}

/** Draw helpers in design-space coordinates (390 x 844). */
class MapOverlayScope internal constructor(private val scope: DrawScope) {
    fun route(d: String, color: Color, width: Float = 7f, dashed: Boolean = false) {
        scope.drawPath(
            svgPath(d), color,
            style = Stroke(
                width = width,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(1f, 11f)) else null,
            ),
        )
    }

    /** Hollow-ring pickup marker. */
    fun pickup(x: Float, y: Float, ink: Color = Color(0xFF1B1B21)) {
        scope.drawCircle(Color.White, 9f, Offset(x, y))
        scope.drawCircle(ink, 9f, Offset(x, y), style = Stroke(5f))
    }

    /** Square destination marker. */
    fun destination(x: Float, y: Float, ink: Color = Color(0xFF1B1B21)) {
        scope.drawRoundRect(ink, Offset(x - 10f, y - 10f), Size(20f, 20f), CornerRadius(6f))
        scope.drawRoundRect(Color.White, Offset(x - 4f, y - 4f), Size(8f, 8f), CornerRadius(2f))
    }

    /** Top-down car. */
    fun car(x: Float, y: Float, rotation: Float = 0f, body: Color = Color(0xFF1B1B21), glass: Color = Color(0xFF7D8BFF)) {
        scope.rotate(rotation, Offset(x, y)) {
            drawRoundRect(body, Offset(x - 10f, y - 14f), Size(20f, 28f), CornerRadius(6f))
            drawRoundRect(glass, Offset(x - 6f, y - 9f), Size(12f, 7f), CornerRadius(2f))
        }
    }

    /** "You are here" dot with halo. */
    fun userDot(x: Float, y: Float, color: Color) {
        scope.drawCircle(color.copy(alpha = 0.12f), 40f, Offset(x, y))
        scope.drawCircle(Color.White, 15f, Offset(x, y))
        scope.drawCircle(color, 11f, Offset(x, y))
    }

    fun halo(x: Float, y: Float, radius: Float, color: Color, alpha: Float = 0.12f) {
        scope.drawCircle(color.copy(alpha = alpha), radius, Offset(x, y))
    }

    fun ring(x: Float, y: Float, radius: Float, color: Color, dashed: Boolean = true) {
        scope.drawCircle(
            color, radius, Offset(x, y),
            style = Stroke(2f, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f, 8f)) else null),
        )
    }

    fun shape(path: String, color: Color) = scope.drawPath(svgPath(path), color)
}

const val DesignWidth = 390f
const val DesignHeight = 844f

internal fun svgPath(d: String): Path = PathParser().parsePathString(d).toPath()
