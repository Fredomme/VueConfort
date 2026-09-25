package fr.vueconfort.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.vueconfort.app.core.VueConfortCoreState
import fr.vueconfort.app.orchestration.VisionStatusCard
import fr.vueconfort.app.orchestration.VisionStatusViewModel

val VueConfortColors = lightColorScheme(
    primary = Color(0xFF176B5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEE6), onPrimaryContainer = Color(0xFF12473D),
    secondary = Color(0xFF4C7160), secondaryContainer = Color(0xFFDCE9DE), onSecondaryContainer = Color(0xFF203D30),
    surface = Color(0xFFFAFAF5), onSurface = Color(0xFF203B34),
    background = Color(0xFFFAFAF5), onBackground = Color(0xFF203B34),
    surfaceVariant = Color(0xFFEDF1EA), onSurfaceVariant = Color(0xFF52665C), outline = Color(0xFF74867A)
)

enum class VisionEntry { TRY, UNKNOWN, KNOWN, DOCUMENT }

@Composable
fun ProductWelcomeScreen(busy: Boolean, error: String?, onChoose: (VisionEntry) -> Unit,
                         onPrivacy: () -> Unit, onBack: (() -> Unit)? = null) {
    BackHandler(enabled = busy) { }
    ProductPage("Bienvenue dans VueConfort", if (busy) null else onBack) {
        Text("Un affichage à votre mesure", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Commencez comme vous le souhaitez. Vous pourrez revenir affiner vos réglages à tout moment.")
        ProductCard {
            Text("Sans bilan", style = MaterialTheme.typography.titleLarge)
            ProductAction("Essayer immédiatement", "welcome_try", !busy, true) { onChoose(VisionEntry.TRY) }
            ProductAction("Je ne connais pas ma correction", "welcome_unknown", !busy) { onChoose(VisionEntry.UNKNOWN) }
            Text("L’essai ouvre l’Égaliseur. Le parcours guidé vous aide à choisir un texte confortable.",
                style = MaterialTheme.typography.bodySmall)
        }
        ProductCard {
            Text("Avec des informations visuelles", style = MaterialTheme.typography.titleLarge)
            ProductAction("Je connais ma correction", "welcome_known", !busy) { onChoose(VisionEntry.KNOWN) }
            ProductAction("J’ai un bilan visuel", "welcome_document", !busy) { onChoose(VisionEntry.DOCUMENT) }
            Text("Saisie ou import photo/PDF, sur ce téléphone. Vous confirmez toujours les valeurs avant de les utiliser.",
                style = MaterialTheme.typography.bodySmall)
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("welcome_error")) }
        Text("Aucun bilan, compte ou accès système n’est nécessaire pour essayer. Les aides de lecture ne remplacent pas une correction visuelle.",
            style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onPrivacy, enabled = !busy) { Text("Confidentialité de mes données") }
    }
}

@Composable
fun PersonalProfileScreen(onEqualizer: () -> Unit, onBilan: () -> Unit, onCalibration: () -> Unit,
                          onNativeVision: () -> Unit, onTools: () -> Unit, onBack: () -> Unit,
                          model: VisionStatusViewModel = viewModel(),
                          onExercises: () -> Unit = {}, onHistory: () -> Unit = {}) {
    val snapshot by model.snapshot.collectAsStateWithLifecycle()
    ProductPage("Mon profil", onBack) {
        Text("Mes réglages, au même endroit", style = MaterialTheme.typography.headlineSmall)
        Text("Vos préférences, votre bilan facultatif et vos choix de lecture restent enregistrés sur ce téléphone.")
        VisionStatusCard(model)
        ProductCard {
            Text("Mon confort de lecture", style = MaterialTheme.typography.titleLarge)
            snapshot?.let { Text("Taille enregistrée : ${"%.2f".format(java.util.Locale.FRANCE, it.equalizerPreferences.sizeScale)}× · réglages disponibles dans l’Égaliseur") }
            Text("Affinez la taille, les détails, le contraste et la lumière. Enregistrez dans l’Égaliseur pour retrouver vos choix.")
            ProductAction("Affiner avec l’Égaliseur", "profile_equalizer", primary = true, action = onEqualizer)
            ProductAction("Ajustement guidé de la lecture", "profile_calibration", action = onCalibration)
        }
        ProductCard {
            Text("Mon bilan visuel", style = MaterialTheme.typography.titleLarge)
            Text(if (snapshot?.confirmedPrescription != null) "Bilan confirmé et lié à votre profil." else "Aucun bilan confirmé requis pour continuer.")
            Text("Ajoutez, vérifiez ou supprimez vos informations. Votre profil reste utilisable sans bilan.")
            ProductAction("Consulter ou modifier mon bilan", "profile_bilan", action = onBilan)
        }
        ProductAction("Aides sur mon téléphone", "profile_native", action = onNativeVision)
        ProductAction("Profils du lecteur et de la loupe", "profile_tools", action = onTools)
        ProductCard {
            Text("Mes exercices visuels", style = MaterialTheme.typography.titleMedium)
            Text("Facultatifs : exercices sur écran et suivi de vos résultats. Ils ne constituent pas un bilan professionnel.")
            ProductAction("Retrouver mes résultats", "profile_history", action = onHistory)
            ProductAction("Faire un exercice visuel", "profile_exercises", action = onExercises)
        }
    }
}

/** Public permissions are requested at the point of use, never to enter the application. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val enabled = remember(refresh) { VueConfortCoreState.isAccessibilityEnabled(context) }
    val notifications = remember(refresh) { Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
        Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    ProductPage("Autorisations", onBack) {
        ProductCard {
            Text("Loupe et commandes de lecture", style = MaterialTheme.typography.titleLarge)
            Text(if (enabled) "Service autorisé dans Android" else "Service non autorisé", modifier = Modifier.testTag("permission_service_state"))
            Text("Pour agrandir l’écran et afficher les commandes flottantes, VueConfort utilise le service d’accessibilité Android. Il peut lire le nom de l’application affichée pour vos règles et, uniquement quand vous choisissez Lire, le texte accessible. Ce texte reste sur le téléphone et n’est pas enregistré.")
            Text("Vous choisissez d’activer ce service. Vous pouvez le désactiver à tout moment dans Android. L’Égaliseur et le bilan fonctionnent sans ce service.")
            ProductAction(if (enabled) "Gérer le service dans Android" else "Autoriser la loupe dans Android",
                "permission_accessibility", primary = !enabled) {
                error = runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                    .exceptionOrNull()?.let { "Cette page ne s’ouvre pas. Retrouvez VueConfort dans Réglages → Accessibilité → Applications installées." }
            }
        }
        ProductCard {
            Text("Raccourci dans les notifications", style = MaterialTheme.typography.titleLarge)
            Text("Facultatif : retrouver les commandes de la loupe depuis une notification. Un refus ne bloque pas votre profil.")
            if (notifications) Text("Notifications autorisées")
            else ProductAction("Autoriser les notifications", "permission_notifications") {
                if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        Text("Import de bilan : vous sélectionnez un document précis avec Android, sans autoriser l’accès à toute votre bibliothèque.",
            style = MaterialTheme.typography.bodyMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ProductAction("Revenir à VueConfort", "permission_return", primary = true, action = onBack)
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun ProductPage(title: String, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(modifier = Modifier.semantics { testTagsAsResourceId = true }, topBar = { TopAppBar(title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = { if (onBack != null) TextButton(onClick = onBack) { Text("Retour") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun ProductCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun ProductAction(label: String, tag: String, enabled: Boolean = true, primary: Boolean = false, action: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag(tag)
    if (primary) Button(onClick = action, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(16.dp)) { Text(label) }
    else OutlinedButton(onClick = action, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(16.dp)) { Text(label) }
}
