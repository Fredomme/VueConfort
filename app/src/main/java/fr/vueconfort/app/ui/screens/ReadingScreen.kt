package fr.vueconfort.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.magnifier
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.optical.opticalRender

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReadingScreen(
    profile: VisualProfile,
    assistProfile: AssistProfile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("Collez ou saisissez ici un texte à lire. Le moteur optique est appliqué uniquement dans VueConfort.") }
    var fontSize by remember { mutableFloatStateOf(profile.fontSizeSp) }
    var lineHeight by remember { mutableFloatStateOf(profile.lineHeightMultiplier) }
    var letterSpacing by remember { mutableFloatStateOf(profile.letterSpacingSp) }
    var lineWidth by remember { mutableFloatStateOf(profile.columnWidthPercent) }
    var dark by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var magnifierEnabled by remember { mutableStateOf(false) }
    var lens by remember { mutableStateOf(Offset.Unspecified) }
    val background = if (dark) Color(0xFF151515) else Color(profile.backgroundArgb.toInt())
    val foreground = if (dark) Color(0xFFF2F2F2) else Color(profile.foregroundArgb.toInt())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Lecture VueConfort") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Retour") }
                },
                actions = {
                    OutlinedButton(onClick = { controlsVisible = !controlsVisible }) {
                        Text(if (controlsVisible) "Réduire" else "Commandes")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().background(background).padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (controlsVisible) {
                Text("Moteur du profil ${assistProfile.name} — contenu local uniquement.")
                ReadingControl("Taille ${fontSize.toInt()} sp", controlsLocked, { fontSize = (fontSize - 2).coerceAtLeast(16f) }, { fontSize = (fontSize + 2).coerceAtMost(64f) })
                ReadingControl("Interligne ${"%.2f".format(lineHeight)}", controlsLocked, { lineHeight = (lineHeight - 0.1f).coerceAtLeast(1f) }, { lineHeight = (lineHeight + 0.1f).coerceAtMost(2f) })
                ReadingControl("Espacement ${"%.2f".format(letterSpacing)}", controlsLocked, { letterSpacing = (letterSpacing - 0.1f).coerceAtLeast(0f) }, { letterSpacing = (letterSpacing + 0.1f).coerceAtMost(2f) })
                ReadingControl("Largeur ${lineWidth.toInt()} %", controlsLocked, { lineWidth = (lineWidth - 5).coerceAtLeast(60f) }, { lineWidth = (lineWidth + 5).coerceAtMost(100f) })
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Mode sombre", Modifier.weight(1f)); Switch(dark, { dark = it })
                    Text("Loupe interne", Modifier.weight(1f)); Switch(magnifierEnabled, { magnifierEnabled = it })
                    Text("Verrouiller", Modifier.weight(1f)); Switch(controlsLocked, { controlsLocked = it })
                }
            }
            val lensModifier = if (magnifierEnabled) {
                Modifier
                    .magnifier(sourceCenter = { lens }, zoom = 2f)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { lens = it },
                            onDragEnd = { lens = Offset.Unspecified },
                            onDragCancel = { lens = Offset.Unspecified }
                        ) { change, drag -> change.consume(); lens += drag }
                    }
            } else Modifier
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(50_000) },
                modifier = lensModifier.fillMaxWidth(lineWidth / 100f)
                    .widthIn(max = 720.dp).opticalRender(assistProfile.optical),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = foreground, fontSize = fontSize.sp,
                    fontWeight = FontWeight(profile.fontWeight.coerceIn(100, 900)),
                    letterSpacing = letterSpacing.sp,
                    lineHeight = (fontSize * lineHeight).sp
                ),
                minLines = 16
            )
        }
    }
}

@Composable
private fun ReadingControl(
    label: String,
    locked: Boolean,
    decrease: () -> Unit,
    increase: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = decrease, enabled = !locked) { Text("−") }
        Button(onClick = increase, enabled = !locked) { Text("+") }
    }
}
