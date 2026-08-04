package fr.vueconfort.app.optical

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BeforeAfterComparison(settings: OpticalSettings, modifier: Modifier = Modifier) {
    var split by remember { mutableFloatStateOf(0.5f) }
    var width by remember { mutableFloatStateOf(1f) }
    val sample: @Composable (Modifier, String) -> Unit = { childModifier, label ->
        Box(childModifier.background(Color(0xFFF5F3ED)).padding(10.dp)) {
            Text(
                "$label\nAa 123 — Il1 O0\nLire un petit texte confortablement.",
                color = Color(0xFF181818),
                fontSize = 16.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
    Row(
        modifier = modifier.fillMaxWidth().height(150.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, drag ->
                    split = (split + drag / width).coerceIn(0.2f, 0.8f)
                }
            }
    ) {
        sample(Modifier.weight(split), "Avant")
        sample(Modifier.weight(1f - split).opticalRender(settings), "Après")
    }
}
