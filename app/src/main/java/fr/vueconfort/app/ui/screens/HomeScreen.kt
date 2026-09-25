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
    onNativeVision: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    ProductPage("VueConfort", null) {
        Text("Votre confort visuel", style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        Text("Retrouvez vos outils, avec ou sans bilan visuel.")
        ProductAction("Améliorer mon affichage", "home_configure", primary = true, action = onQuestionnaire)
        ProductCard {
            Text("Affiner mon confort", style = MaterialTheme.typography.titleLarge)
            ProductAction("Égaliseur visuel", "eq_open", primary = true, action = onEqualizer)
            ProductAction("Mon profil", "home_profile", action = onProfile)
            ProductAction("Mon bilan visuel", "home_bilan", action = onOpticalPrescription)
        }
        fr.vueconfort.app.orchestration.VisionStatusCard()
        ProductCard {
            Text("Sur mon téléphone", style = MaterialTheme.typography.titleLarge)
            ProductAction("Aides de l’affichage", "native_open", action = onNativeVision)
            ProductAction("Ouvrir la loupe", "home_magnifier") {
                if (VueConfortCoreState.isAccessibilityEnabled(context)) {
                    ScreenMagnifierService.handleExternalAction(ScreenMagnifierService.ACTION_MAGNIFIER_ENABLE)
                } else onMagnifierSetup()
            }
            ProductAction("Lecteur confortable", "home_reading", action = onReading)
            Text("L’égaliseur agit dans son aperçu. Les aides Android et Samsung ont la portée indiquée dans leurs réglages.",
                style = MaterialTheme.typography.bodySmall)
        }
        ProductAction("Réglages et autorisations", "home_settings", action = onSettings)
        ProductAction("Aide", "home_help", action = onHelp)
    }
}
