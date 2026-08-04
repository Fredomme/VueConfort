package fr.vueconfort.app.assessment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp

enum class OptotypeDirection(val degrees: Float) {
    RIGHT(0f), DOWN(90f), LEFT(180f), UP(270f)
}

@Composable
fun LandoltOptotype(
    diameter: Dp,
    strokeWidth: Dp,
    direction: OptotypeDirection,
    color: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(diameter)) {
        val stroke = strokeWidth.toPx().coerceAtLeast(1f)
        val inset = stroke / 2f
            // Chord of one MAR on the four-MAR centreline diameter: about 29°.
            val gapAngle = 29f
        val gapCenter = direction.degrees
        drawArc(
            color = color,
            startAngle = gapCenter + gapAngle / 2f,
            sweepAngle = 360f - gapAngle,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke, cap = StrokeCap.Butt)
        )
    }
}

@Composable
fun TumblingEOptotype(
    diameter: Dp,
    direction: OptotypeDirection,
    color: Color = Color.Black,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.size(diameter)) {
        rotate(direction.degrees, pivot = center) {
            val unit = size.width / 5f
            drawRect(color, Offset(0f, 0f), Size(unit, size.height))
            drawRect(color, Offset(0f, 0f), Size(size.width, unit))
            drawRect(color, Offset(0f, 2f * unit), Size(4f * unit, unit))
            drawRect(color, Offset(0f, 4f * unit), Size(size.width, unit))
        }
    }
}
