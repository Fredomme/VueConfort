package fr.vueconfort.app.assessment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssessmentHistoryScreen(
    standardized: List<StandardizedAssessmentReport>,
    legacy: List<VisualComfortAssessment>,
    onDeleteStandardized: (String) -> Unit,
    onDeleteLegacy: (String) -> Unit,
    onRepeat: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des dépistages") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("Retour") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                compatibleEvolution(standardized)?.let { Text(it) }
                Text("Seuls les résultats de même protocole, œil, distance et correction sont comparés.")
            }
            items(standardized, key = { it.id }) { report ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("STANDARDIZED_V2 · ${DateFormat.getDateTimeInstance().format(Date(report.createdAtMillis))}")
                        report.acuityResults.forEach { value ->
                            Text("${value.eye} ${value.method} ${distanceShort(value.distance)} : ${value.logMar?.let { String.format("%.1f logMAR", it) } ?: "non mesurable"} · ${value.reliability}")
                        }
                        report.contrastResults.forEach { Text("${it.eye} contraste écran : ${it.logContrastSensitivity ?: "non mesurable"}") }
                        report.amslerResults.forEach { Text("${it.eye} Amsler : réponse subjective enregistrée") }
                        OutlinedButton(onClick = { onDeleteStandardized(report.id) }, modifier = Modifier.fillMaxWidth()) { Text("Supprimer") }
                    }
                }
            }
            if (legacy.isNotEmpty()) {
                item { Text("Anciens résultats") }
            }
            items(legacy, key = { it.id }) { value ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Ancien protocole non standardisé")
                        Text(DateFormat.getDateTimeInstance().format(Date(value.createdAtMillis)))
                        Text("Les anciens scores sur 100 ne sont pas comparés au logMAR.")
                        OutlinedButton(onClick = { onDeleteLegacy(value.id) }, modifier = Modifier.fillMaxWidth()) { Text("Supprimer") }
                    }
                }
            }
            item {
                OutlinedButton(onClick = onRepeat, modifier = Modifier.fillMaxWidth()) { Text("Nouveau test de dépistage") }
            }
        }
    }
}

private fun compatibleEvolution(values: List<StandardizedAssessmentReport>): String? {
    val all = values.filter { it.calibration.valid }.flatMap { it.acuityResults }
    val latest = all.firstOrNull { it.logMar != null } ?: return null
    val previous = all.drop(1).firstOrNull {
        it.logMar != null && it.method == latest.method && it.eye == latest.eye &&
            it.distance == latest.distance && it.withCorrection == latest.withCorrection
    } ?: return null
    val change = latest.logMar!! - previous.logMar!!
    return when {
        change <= -0.2f -> "Évolution compatible : amélioration apparente."
        change >= 0.2f -> "Évolution compatible : baisse apparente. Si elle se répète, un contrôle professionnel est recommandé."
        else -> "Évolution compatible : résultat globalement stable."
    }
}

private fun distanceShort(value: TestDistance) = "${value.centimeters} cm"
