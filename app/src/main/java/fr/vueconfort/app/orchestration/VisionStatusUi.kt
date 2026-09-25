package fr.vueconfort.app.orchestration

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.vueconfort.app.data.VisualProfileRepository
import fr.vueconfort.app.equalizer.*
import fr.vueconfort.app.nativevision.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.util.UUID

/** A read-only aggregate. Existing repositories remain the only owners of user data. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VisionStatusViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VisualProfileRepository(application)
    private val sources = combine(repository.equalizerProfile, repository.opticalPrescription,
        repository.profile, repository.userContext) { equalizer, prescription, visual, context ->
        VisionProfileSnapshot(equalizer ?: EqualizerProfile(), prescription,
            visualProfile = visual, declaredUserContext = context)
    }
    private val refresh = MutableStateFlow(0)
    private val loadError = MutableStateFlow<String?>(null)
    val error = loadError.asStateFlow()
    fun retry() { refresh.value++ }
    val snapshot = refresh.flatMapLatest { combine(sources, repository.visualAssessments, repository.standardizedAssessments) { profile, comfort, standard ->
        profile.withCalibration(VisionCalibrationSnapshot(
            comfortAssessment = comfort.maxByOrNull { it.createdAtMillis },
            standardizedAssessment = standard.maxByOrNull { it.createdAtMillis }))
    }.map<VisionProfileSnapshot, VisionProfileSnapshot?> { it }
        .onStart { loadError.value = null }
        .catch { loadError.value = "Votre profil n’a pas pu être lu. Vos données sont conservées."; emit(null) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
private fun currentCapabilities(): NativeVisionCapabilities? {
    val app = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var capabilities by remember { mutableStateOf<NativeVisionCapabilities?>(null) }
    LaunchedEffect(app, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                while (true) {
                    capabilities = NativeVisionCapabilityResolver().resolve(
                        NativeVisionEnvironment(app).snapshot(NativeVisionVariant.COMMERCIAL, false))
                    delay(2_000)
                }
            } finally { capabilities = null }
        }
    }
    return capabilities
}

/** Normal UI shows effects and their scope, never scientific or transport internals. */
@Composable
fun VisionStatusCard(model: VisionStatusViewModel = viewModel()) {
    val source by model.snapshot.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val capabilities = currentCapabilities()
    val app = LocalContext.current
    val session = remember { UUID.randomUUID().toString() }
    val snapshot = source
    val observed = remember(snapshot, capabilities) {
        if (snapshot == null || capabilities == null) null else
            VisionRuntime.observe(app, VisionRuntime.plan(snapshot, capabilities, session))
    }
    val loupeActive = remember(capabilities) { AndroidNativeVisionAdapter().readMagnificationState()?.enabled == true }
    val opticalState = remember(snapshot, capabilities) {
        if (snapshot == null || capabilities == null) null else {
            val context = VisionExecutionContext(session, capabilities.observedAtMillis, capabilities.observedAtMillis,
                setOf(VisionTransport.INTERNAL_PREVIEW), capabilities)
            VisionRuntime.orchestrator().plan(snapshot.profileId, snapshot.sourceRevision, context,
                listOf(snapshot.opticalReadinessRequest())).transformations.singleOrNull()
        }
    }
    Card(Modifier.fillMaxWidth().testTag("vision_status")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (observed?.anyActive == true || loupeActive) "VueConfort · aide active" else "VueConfort · vos aides visuelles",
                style = MaterialTheme.typography.titleMedium)
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = model::retry) { Text("Réessayer") }
            } else if (observed == null) Text("Vérification des aides disponibles…")
            else if (observed.transformations.isEmpty()) Text("Aucune aide du téléphone n’est activée par ce profil.")
            else observed.transformations.forEach { item -> Text(visionEffectSummary(item),
                style = MaterialTheme.typography.bodyMedium) }
            if (loupeActive && observed?.transformations?.none { it.active &&
                    (it.planned.request.payload as? EnginePayload.Native)?.capability == NativeVisionCapability.MAGNIFICATION } != false)
                Text("Loupe Android : active, état vérifié")
            Text("Égaliseur : disponible dans son aperçu.", style = MaterialTheme.typography.bodySmall)
            if (opticalState != null) Text("Correction avancée : indisponible avec les informations actuelles.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The existing renderer remains intact; the central plan chooses whether to dispatch its input. */
@Composable
fun OrchestratedEqualizerPreview(profile: EqualizerProfile, original: Boolean, commandId: Long,
                                modifier: Modifier, onApplied: (EqualizerRenderRecord) -> Unit,
                                nativeProfileOverride: NativeVisionProfile? = null) {
    val capabilities = currentCapabilities()
    val session = remember { UUID.randomUUID().toString() }
    val app = LocalContext.current
    val nativeProfile = if (nativeProfileOverride != null) nativeProfileOverride else {
        val native by NativeVisionController.get(app).state.collectAsStateWithLifecycle()
        native.profile
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var surfaceEpoch by remember { mutableLongStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_START) surfaceEpoch++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val current = profile.copy(nativeVision = nativeProfile)
    val requestedPlan = remember(current, capabilities) {
        capabilities?.let { VisionRuntime.plan(VisionProfileSnapshot(current), it, session, includePreview = true,
            occupiedDisplayDomains = VisionRuntime.occupiedDisplayDomains(app, it)) }
    }
    val requestedStep = requestedPlan?.transformations?.firstOrNull { it.request.payload is EnginePayload.Perceptual }
    val geometryFallback = capabilities != null && capabilities.device.sdkInt < 33 &&
        requestedStep?.disposition == VisionPlanDisposition.UNAVAILABLE
    val renderedProfile = if (geometryFallback) current.copy(preferences = current.preferences.copy(
        sharpness = 0f, contrast = 1f, lightComfort = 0f)) else current
    val plan = if (geometryFallback && capabilities != null) VisionRuntime.plan(VisionProfileSnapshot(renderedProfile),
        capabilities, session, includePreview = true, occupiedDisplayDomains = requestedPlan!!.context.occupiedDisplayDomains)
        else requestedPlan
    val step = plan?.transformations?.firstOrNull { it.request.payload is EnginePayload.Perceptual }
    val permitted = step?.disposition in setOf(VisionPlanDisposition.APPLICABLE, VisionPlanDisposition.IDENTITY)
    var draw by remember(profile.revision, profile.preferences, profile.scene, permitted, original, geometryFallback, surfaceEpoch) { mutableStateOf<EqualizerRenderRecord?>(null) }
    Column {
        key(surfaceEpoch, permitted, original, geometryFallback) {
            EqualizerPreview(if (permitted) renderedProfile.preferences else EqualizerPreferences.Neutral,
                profile.scene, original, commandId, modifier, { record ->
                    if (permitted) { draw = record; if (!geometryFallback) onApplied(record) }
                }, profile.revision)
        }
        val state = remember(plan, draw, original) { plan?.let { VisionRuntime.observe(app, it, draw, original) } }
        val rendered = state?.transformations?.firstOrNull { it.planned.request.payload is EnginePayload.Perceptual }
        Text(when {
            original -> "Original affiché · aides du téléphone conservées"
            geometryFallback && permitted -> "Aperçu : taille et épaisseur disponibles ; effets d’image indisponibles"
            rendered?.active == true -> "Confort visuel : actif dans cet aperçu"
            step?.disposition == VisionPlanDisposition.IDENTITY -> "Aperçu : réglages neutres"
            !permitted && plan != null -> "Aperçu sans traitement · ${step?.reason.orEmpty()}"
            else -> "Aperçu : vérification du rendu"
        }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("vision_preview_status"))
    }
}

internal fun visionEffectSummary(item: VisionObservedTransformation): String {
    val payload = item.planned.request.payload
    val label = when (payload) {
        is EnginePayload.Native -> when (payload.capability) {
            NativeVisionCapability.MAGNIFICATION -> "Agrandissement"
            NativeVisionCapability.RELUMINO -> "Contours Samsung"
            NativeVisionCapability.EXTRA_DIM -> "Atténuation supplémentaire"
            NativeVisionCapability.COLOR_FILTER -> "Filtre de couleur"
            NativeVisionCapability.COLOR_CORRECTION -> "Correction des couleurs"
            NativeVisionCapability.COLOR_INVERSION -> "Inversion des couleurs"
            NativeVisionCapability.HIGH_CONTRAST_TEXT -> "Texte contrasté"
            NativeVisionCapability.EYE_COMFORT -> "Confort des yeux Samsung"
            NativeVisionCapability.SYSTEM_BRIGHTNESS -> "Luminosité du téléphone"
            NativeVisionCapability.FONT_SCALE -> "Taille des caractères"
            NativeVisionCapability.SCREEN_ZOOM -> "Zoom du téléphone"
        }
        is EnginePayload.Perceptual -> "Confort de l’aperçu"
        is EnginePayload.Optical -> "Correction avancée"
    }
    val status = when {
        item.active -> "actif, état vérifié"
        item.planned.disposition == VisionPlanDisposition.GUIDED -> "à vérifier dans les réglages du téléphone"
        item.planned.disposition == VisionPlanDisposition.PERMISSION_REQUIRED -> "autorisation nécessaire"
        item.planned.disposition == VisionPlanDisposition.CONFLICT -> "combinaison non validée"
        item.planned.disposition in setOf(VisionPlanDisposition.UNAVAILABLE, VisionPlanDisposition.REJECTED, VisionPlanDisposition.UNQUALIFIED) -> "indisponible dans ce contexte"
        else -> "inactif ou non confirmé"
    }
    return "$label : $status"
}
