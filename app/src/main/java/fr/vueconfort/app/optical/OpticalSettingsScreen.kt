package fr.vueconfort.app.optical

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.vueconfort.app.model.AssistProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpticalSettingsScreen(
    profile: AssistProfile,
    onSave: (AssistProfile) -> Unit,
    onBack: () -> Unit
) {
    var value by remember(profile.id, profile.optical) { mutableStateOf(profile.optical) }
    fun update(next: OpticalSettings) { value = next.sanitized() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages optiques expérimentaux") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Applicable dans VueConfort — non appliqué globalement aux autres applications.")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Text("Sur cette version Android, seules les transformations de taille sont disponibles.")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Activer le moteur")
                Switch(checked = value.enabled, onCheckedChange = { update(value.copy(enabled = it)) })
            }
            BeforeAfterComparison(value)
            OpticalSlider("Netteté", value.sharpness, 0f..0.8f) { update(value.copy(sharpness = it)) }
            OpticalSlider("Contraste local", value.localContrast, 0.7f..1.5f) { update(value.copy(localContrast = it)) }
            OpticalSlider("Gamma", value.gamma, 0.7f..1.4f) { update(value.copy(gamma = it)) }
            OpticalSlider("Luminosité", value.brightness, 0.7f..1.25f) { update(value.copy(brightness = it)) }
            OpticalSlider("Saturation", value.saturation, 0f..1.4f) { update(value.copy(saturation = it)) }
            OpticalSlider("Température", value.temperature, -0.25f..0.25f) { update(value.copy(temperature = it)) }
            OpticalSlider("Réduction des blancs", value.whiteReduction, 0f..0.4f) { update(value.copy(whiteReduction = it)) }
            OpticalSlider("Contours", value.edgeEnhancement, 0f..0.6f) { update(value.copy(edgeEnhancement = it)) }

            Text("Assistant guidé")
            Text("Quelle version est la plus nette sans produire de halos ?")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Neutre", "Légère", "Moyenne", "Forte").forEachIndexed { index, label ->
                    OutlinedButton(
                        onClick = {
                            val guided = OpticalGuidanceEngine.sharpness(index)
                            update(value.copy(enabled = guided.enabled, sharpness = guided.sharpness, edgeEnhancement = guided.edgeEnhancement))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(label) }
                }
            }
            Text("Contraste et gamma")
            listOf("Doux", "Équilibré", "Renforcé", "Plus clair", "Plus sombre").forEachIndexed { index, label ->
                OutlinedButton(
                    onClick = {
                        val guided = OpticalGuidanceEngine.contrastGamma(index)
                        update(value.copy(enabled = true, localContrast = guided.localContrast, gamma = guided.gamma))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
            }
            Text("Température et blancs")
            listOf("Neutre", "Légèrement chaud", "Chaud", "Légèrement froid", "Blanc réduit").forEachIndexed { index, label ->
                OutlinedButton(
                    onClick = {
                        val guided = OpticalGuidanceEngine.color(index)
                        update(value.copy(enabled = true, temperature = guided.temperature, whiteReduction = guided.whiteReduction, brightness = guided.brightness))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(label) }
            }

            Text("Correction géométrique expérimentale")
            Text("Cette fonction ne reproduit pas une correction optique médicale et ne remplace pas des lunettes.")
            OpticalSlider("Étirement horizontal", value.horizontalStretch, 0.9f..1.1f) { update(value.copy(horizontalStretch = it)) }
            OpticalSlider("Étirement vertical", value.verticalStretch, 0.9f..1.1f) { update(value.copy(verticalStretch = it)) }
            OpticalSlider("Distorsion légère", value.cylindricalDistortion, -0.08f..0.08f) { update(value.copy(cylindricalDistortion = it)) }
            OpticalSlider("Axe expérimental", value.distortionAxisDegrees, 0f..180f) { update(value.copy(distortionAxisDegrees = it)) }
            OpticalSlider("Intensité globale", value.globalIntensity, 0f..1f) { update(value.copy(globalIntensity = it)) }

            Text("Qualité")
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                OpticalQuality.entries.forEach { quality ->
                    OutlinedButton(
                        onClick = { update(value.copy(quality = quality)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(when (quality) {
                            OpticalQuality.ECONOMY -> "Économie"
                            OpticalQuality.BALANCED -> "Équilibré"
                            OpticalQuality.QUALITY -> "Qualité"
                        })
                    }
                }
            }
            Button(
                onClick = { onSave(profile.copy(optical = value, updatedAtMillis = System.currentTimeMillis())) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Appliquer au profil") }
            OutlinedButton(onClick = { update(OpticalSettings.Neutral) }, modifier = Modifier.fillMaxWidth()) {
                Text("Tout réinitialiser")
            }
        }
    }
}

@Composable
private fun OpticalSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column {
        Text("$label : ${"%.2f".format(value)}")
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
