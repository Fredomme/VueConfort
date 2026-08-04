package fr.vueconfort.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ComfortCalibrationAnswers(
    val preferredScale: Float,
    val difficulty: String,
    val overlayAlpha: Float,
    val usages: List<String>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionnaireScreen(
    onBack: () -> Unit,
    onCompleted: (ComfortCalibrationAnswers) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(0) }
    var scale by remember { mutableStateOf(2f) }
    var difficulty by remember { mutableStateOf("Textes standards") }
    var alpha by remember { mutableStateOf(0.82f) }
    val usages = remember { mutableStateListOf<String>() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Calibration de confort") },
                navigationIcon = {
                    OutlinedButton(
                        onClick = { if (step > 0) step-- else onBack() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text(if (step > 0) "Précédent" else "Retour") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            LinearProgressIndicator(
                progress = { ((step + 1) / 5f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            when (step) {
                0 -> ScaleStep { scale = it; step++ }
                1 -> ChoiceStep(
                    "Avec quels contenus avez-vous surtout besoin d’aide ?",
                    listOf(
                        "Très petits textes",
                        "Textes standards",
                        "Longues lectures",
                        "Utilisation à distance normale",
                        "Consultation rapide"
                    )
                ) { difficulty = it; step++ }
                2 -> ChoiceStep(
                    "Quelle visibilité souhaitez-vous pour les commandes ?",
                    listOf("Discrète", "Équilibrée", "Très visible")
                ) {
                    alpha = when (it) {
                        "Discrète" -> 0.62f
                        "Très visible" -> 1f
                        else -> 0.82f
                    }
                    step++
                }
                3 -> UsageStep(usages) { step++ }
                else -> {
                    Text("Votre profil Personnalisé est prêt.", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Grossissement ${scale}× — panneau ${(alpha * 100).toInt()} %")
                    Text("Usage : ${usages.ifEmpty { listOf("Usage général") }.joinToString()}")
                    Text("Cette calibration détermine uniquement vos préférences de confort. Elle ne mesure pas votre vue.")
                    Button(
                        onClick = {
                            onCompleted(
                                ComfortCalibrationAnswers(
                                    preferredScale = scale,
                                    difficulty = difficulty,
                                    overlayAlpha = alpha,
                                    usages = usages.toList()
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Enregistrer et appliquer") }
                }
            }
        }
    }
}

@Composable
private fun ScaleStep(onChoice: (Float) -> Unit) {
    Text("Quel exemple est le plus facile à lire sans effort ?", fontSize = 27.sp, fontWeight = FontWeight.Bold)
    listOf(1.5f, 2f, 2.5f, 3f).forEach { value ->
        OutlinedButton(onClick = { onChoice(value) }, modifier = Modifier.fillMaxWidth()) {
            Text("Exemple ${value}× — Aa 123", fontSize = (18f + value * 3f).sp)
        }
    }
}

@Composable
private fun ChoiceStep(title: String, choices: List<String>, onChoice: (String) -> Unit) {
    Text(title, fontSize = 27.sp, fontWeight = FontWeight.Bold)
    choices.forEach { choice ->
        Button(onClick = { onChoice(choice) }, modifier = Modifier.fillMaxWidth()) {
            Text(choice, fontSize = 20.sp)
        }
    }
}

@Composable
private fun UsageStep(selected: MutableList<String>, onNext: () -> Unit) {
    Text("Dans quels usages souhaitez-vous plus de confort ?", fontSize = 27.sp, fontWeight = FontWeight.Bold)
    listOf("Navigation web", "Messages", "Réseaux sociaux", "Documents", "Lecture longue", "Extérieur")
        .forEach { usage ->
            val active = usage in selected
            OutlinedButton(
                onClick = { if (active) selected.remove(usage) else selected.add(usage) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (active) "✓ $usage" else usage, fontSize = 20.sp) }
        }
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Voir le résultat") }
}
