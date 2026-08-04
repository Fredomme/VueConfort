package fr.vueconfort.app.assessment

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun AmslerGridTest(
    eye: StandardEye,
    onCompleted: (AmslerResult) -> Unit
) {
    var straight by remember { mutableStateOf(true) }
    var wavy by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var blurred by remember { mutableStateOf(false) }
    var centerVisible by remember { mutableStateOf(true) }
    var recent by remember { mutableStateOf(false) }
    var mark by remember { mutableStateOf<Offset?>(null) }
    Text("${eye.name} — 30 à 40 cm. Fixez le point central, sans zoom.")
    Box {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f)
                .pointerInput(Unit) { detectTapGestures { mark = it } }
        ) {
            val step = size.width / 20f
            repeat(21) { index ->
                drawLine(Color.Black, Offset(index * step, 0f), Offset(index * step, size.height), 1f)
                drawLine(Color.Black, Offset(0f, index * step), Offset(size.width, index * step), 1f)
            }
            drawCircle(Color.Black, 6f, center)
            mark?.let { drawCircle(Color.Red, 12f, it, style = androidx.compose.ui.graphics.drawscope.Stroke(3f)) }
        }
    }
    AmslerSwitch("Toutes les lignes sont droites", straight) { straight = it }
    AmslerSwitch("Certaines lignes paraissent ondulées", wavy) { wavy = it }
    AmslerSwitch("Une zone manque", missing) { missing = it }
    AmslerSwitch("Une zone paraît sombre", dark) { dark = it }
    AmslerSwitch("Une zone paraît floue", blurred) { blurred = it }
    AmslerSwitch("Le point central reste visible", centerVisible) { centerVisible = it }
    AmslerSwitch("Le changement est récent ou soudain", recent) { recent = it }
    Button(onClick = {
        onCompleted(
            AmslerResult(
                eye, straight, wavy, missing, dark, blurred, centerVisible,
                recent, mark?.x, mark?.y
            )
        )
    }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer pour cet œil") }
}

@Composable
private fun AmslerSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Switch(value, onChange)
    }
}
