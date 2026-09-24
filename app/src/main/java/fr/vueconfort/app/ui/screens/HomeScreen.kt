package fr.vueconfort.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.vueconfort.app.R
import fr.vueconfort.app.core.VueConfortCoreState
import fr.vueconfort.app.magnifier.ScreenMagnifierService
import fr.vueconfort.app.model.VisualProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    profile: VisualProfile,
    onQuestionnaire: () -> Unit,
    onCalibration: () -> Unit,
    onEqualizer: () -> Unit,
    onVisualAssessment: () -> Unit,
    onReading: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onCoreStatus: () -> Unit,
    onHelp: () -> Unit,
    onMagnifierSetup: () -> Unit,
    onOpticalPrescription: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showMore by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = { TextButton(onClick = onHelp) { Text(stringResource(R.string.help)) } }
            )
        }
    ) { innerPadding ->
        Column(
            Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Votre lecture, à votre rythme", style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold)
            Text("Retrouvez vos outils de lecture et un affichage qui vous ressemble.",
                style = MaterialTheme.typography.bodyLarge)

            HomeSection(highlighted = true) {
                Text("Votre égaliseur visuel", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("Regardez, réglez, comparez. Trouvez votre confort sur un texte ou une image, avec ou sans bilan.")
                Button(onClick = onEqualizer, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("eq_open"),
                    shape = RoundedCornerShape(16.dp)) { Text("Ouvrir l’égaliseur") }
                TextButton(onClick = onEqualizer) { Text("Essayer immédiatement · sans correction connue") }
            }

            HomeSection {
                Text("Mon bilan visuel", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("Vous avez une ordonnance ou un compte rendu ? Importez une photo ou un PDF, vérifiez les valeurs, puis essayez un point de départ pour lire.")
                OutlinedButton(onClick = onOpticalPrescription,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("J’ai un bilan / je connais ma correction")
                }
                Text("Sans bilan, vous pouvez aussi régler votre confort de lecture.",
                    style = MaterialTheme.typography.bodySmall)
            }

            Text("Au quotidien", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            HomeSection {
                OutlinedButton(onClick = {
                    if (VueConfortCoreState.isAccessibilityEnabled(context)) {
                        ScreenMagnifierService.handleExternalAction(ScreenMagnifierService.ACTION_MAGNIFIER_ENABLE)
                    } else onMagnifierSetup()
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text(stringResource(R.string.home_magnifier_primary))
                }
                OutlinedButton(onClick = onReading, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp)) { Text(stringResource(R.string.optimized_reading)) }
                Text("La loupe agrandit avec Android. Le lecteur applique vos réglages au texte disponible.",
                    style = MaterialTheme.typography.bodySmall)
            }

            HomeSection {
                Text("Profils du lecteur et de la loupe",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Retrouvez les réglages de vos autres outils de lecture.")
                TextButton(onClick = onProfile) { Text("Voir mon profil") }
            }

            TextButton(onClick = { showMore = !showMore }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (showMore) "Masquer les autres outils" else "Autres outils et réglages")
            }
            if (showMore) {
                HomeSection {
                    HomeSecondaryAction(stringResource(R.string.visual_questionnaire), onQuestionnaire)
                    HomeSecondaryAction(if (profile.calibrated) stringResource(R.string.redo_calibration)
                        else stringResource(R.string.start_calibration), onCalibration)
                    HomeSecondaryAction(stringResource(R.string.visual_assessment), onVisualAssessment)
                    HomeSecondaryAction(stringResource(R.string.vueconfort_status), onCoreStatus)
                    HomeSecondaryAction(stringResource(R.string.settings), onSettings)
                }
            }
            Text("Vos réglages restent sur ce téléphone. VueConfort aide à lire sur écran et ne remplace pas des lunettes ni un bilan professionnel.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeSection(highlighted: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun HomeSecondaryAction(label: String, action: () -> Unit) {
    TextButton(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
}
