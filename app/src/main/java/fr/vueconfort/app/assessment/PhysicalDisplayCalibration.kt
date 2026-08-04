package fr.vueconfort.app.assessment

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PhysicalCalibrationControl(
    widthDp: Float,
    heightDp: Float,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit
) {
    Text("Alignez ce rectangle avec une carte bancaire réelle (85,60 × 53,98 mm).")
    Box(
        Modifier.width(widthDp.dp).height(heightDp.dp)
            .border(3.dp, Color.Black),
        contentAlignment = Alignment.Center
    ) { Text("Carte bancaire") }
    Row {
        OutlinedButton(onClick = { onWidthChange((widthDp - 2).coerceAtLeast(220f)) }) { Text("Largeur −") }
        Button(onClick = { onWidthChange((widthDp + 2).coerceAtMost(430f)) }) { Text("Largeur +") }
    }
    Row {
        OutlinedButton(onClick = { onHeightChange((heightDp - 2).coerceAtLeast(140f)) }) { Text("Hauteur −") }
        Button(onClick = { onHeightChange((heightDp + 2).coerceAtMost(300f)) }) { Text("Hauteur +") }
    }
}

fun buildPhysicalCalibration(
    widthDp: Float,
    heightDp: Float,
    xdpi: Float,
    ydpi: Float,
    density: Float,
    orientation: String,
    valid: Boolean
): PhysicalDisplayCalibration {
    val widthPx = widthDp * density
    val heightPx = heightDp * density
    val systemWidthPx = 85.60f * xdpi / 25.4f
    val systemHeightPx = 53.98f * ydpi / 25.4f
    return PhysicalDisplayCalibration(
        horizontalFactor = widthPx / systemWidthPx.coerceAtLeast(1f),
        verticalFactor = heightPx / systemHeightPx.coerceAtLeast(1f),
        xdpi = xdpi,
        ydpi = ydpi,
        density = density,
        orientation = orientation,
        calculatedWidthMm = 85.60f,
        calculatedHeightMm = 53.98f,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
        calibratedAtMillis = System.currentTimeMillis(),
        valid = valid
    )
}
