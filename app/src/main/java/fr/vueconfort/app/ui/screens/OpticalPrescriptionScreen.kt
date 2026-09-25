package fr.vueconfort.app.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.vueconfort.app.assessment.StandardizedAssessmentReport
import fr.vueconfort.app.assessment.VisualComfortAssessment
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.CorrectionType
import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.model.PrescriptionSource
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.prescription.DocumentReadResult
import fr.vueconfort.app.prescription.LocalDocumentReader
import fr.vueconfort.app.prescription.PrescriptionDocumentParser
import fr.vueconfort.app.prescription.PrescriptionEyeInput
import fr.vueconfort.app.prescription.PrescriptionInputValidation
import fr.vueconfort.app.recommendation.PrescriptionProfileEngine
import fr.vueconfort.app.recommendation.PrescriptionProfileRecommendation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class PrescriptionEntry { CHOOSE, MANUAL, DOCUMENT }

/** Drafts, source images and OCR stay in memory, including across configuration changes. */
class PrescriptionDraftViewModel : ViewModel() {
    var right by mutableStateOf(PrescriptionEyeInput())
    var left by mutableStateOf(PrescriptionEyeInput())
    var pupillaryDistance by mutableStateOf("")
    var readingDistance by mutableStateOf("")
    var date by mutableStateOf("")
    var notes by mutableStateOf("")
    var correctionType by mutableStateOf(CorrectionType.NOT_SPECIFIED)
    var source by mutableStateOf(PrescriptionSource.MANUAL)
    var confirmed by mutableStateOf(false)
    var step by mutableStateOf(0)
    var showErrors by mutableStateOf(false)
    var showDetails by mutableStateOf(false)
    var showExtractedText by mutableStateOf(false)
    var showPreview by mutableStateOf(false)
    var document by mutableStateOf<DocumentReadResult?>(null)
    var loading by mutableStateOf(false)
    var readError by mutableStateOf<String?>(null)
    var importNotice by mutableStateOf<String?>(null)
    private var selectedUri: Uri? = null
    private var readJob: Job? = null
    private var reader: LocalDocumentReader? = null
    private var request = 0L
    private var seeded = false
    private var seedVersion: Long? = null
    private var entryApplied = false
    private var hasEdits = false

    fun enter(entry: PrescriptionEntry, saved: OpticalPrescription?) {
        if (entryApplied) return
        entryApplied = true
        seed(saved)
        if (entry == PrescriptionEntry.MANUAL) manualEntry(saved)
    }

    fun seed(saved: OpticalPrescription?) {
        if (seeded && seedVersion == saved?.updatedAtMillis) return
        // A late repository emission must not overwrite a draft already being edited.
        if (seeded && (hasEdits || document != null || loading || (seedVersion == null && step != 0))) return
        seeded = true
        seedVersion = saved?.updatedAtMillis
        right = inputFrom(saved?.rightEye)
        left = inputFrom(saved?.leftEye)
        pupillaryDistance = saved?.pupillaryDistanceMm.display()
        readingDistance = saved?.recommendedReadingDistanceCm?.toString().orEmpty()
        date = saved?.prescriptionDate.orEmpty()
        notes = saved?.notes.orEmpty()
        correctionType = saved?.correctionType ?: CorrectionType.NOT_SPECIFIED
        source = saved?.source ?: PrescriptionSource.MANUAL
        confirmed = false
    }

    fun edited() {
        hasEdits = true
        confirmed = false
        if (step == 2) step = 1
    }

    fun manualEntry(saved: OpticalPrescription?) {
        cancelRead()
        document = null
        selectedUri = null
        right = inputFrom(saved?.rightEye)
        left = inputFrom(saved?.leftEye)
        pupillaryDistance = saved?.pupillaryDistanceMm.display()
        readingDistance = saved?.recommendedReadingDistanceCm?.toString().orEmpty()
        date = saved?.prescriptionDate.orEmpty()
        notes = saved?.notes.orEmpty()
        correctionType = saved?.correctionType ?: CorrectionType.NOT_SPECIFIED
        source = saved?.source ?: PrescriptionSource.MANUAL
        importNotice = null
        readError = null
        showErrors = false
        hasEdits = false
        confirmed = false
        step = 1
    }

    fun read(context: Context, uri: Uri, pageIndex: Int = 0) {
        readJob?.cancel()
        val token = ++request
        selectedUri = uri
        source = PrescriptionSource.MANUAL
        document = null
        loading = true
        hasEdits = true
        readError = null
        importNotice = null
        confirmed = false
        showExtractedText = false
        showPreview = false
        right = PrescriptionEyeInput()
        left = PrescriptionEyeInput()
        pupillaryDistance = ""
        readingDistance = ""
        date = ""
        notes = ""
        correctionType = CorrectionType.NOT_SPECIFIED
        step = 1
        val localReader = reader ?: LocalDocumentReader(context.applicationContext).also { reader = it }
        readJob = viewModelScope.launch {
            try {
                val result = localReader.read(uri, pageIndex)
                if (token == request) {
                    document = result
                    source = result.source
                    importNotice = "Vérifiez la page, puis choisissez les valeurs à reprendre. Votre profil n’a pas été modifié."
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token == request) readError = error.message ?: "Ce document n’a pas pu être lu. Vous pouvez réessayer ou saisir ses valeurs."
            } finally {
                if (token == request) loading = false
            }
        }
    }

    fun page(context: Context, pageIndex: Int) {
        selectedUri?.let { read(context, it, pageIndex) }
    }

    fun cancelRead() {
        request++
        readJob?.cancel()
        loading = false
    }

    fun adopt(rightEye: EyePrescription, leftEye: EyePrescription, nearFarContext: String?) {
        right = inputFrom(rightEye)
        left = inputFrom(leftEye)
        nearFarContext?.let { context ->
            val remaining = notes.lineSequence().filterNot { it.startsWith("Contexte du document : ") }.joinToString("\n").trim()
            notes = ("Contexte du document : $context" + if (remaining.isBlank()) "" else "\n$remaining").take(500)
            showDetails = true
        }
        edited()
        showErrors = true
        importNotice = "Valeurs reconnues reportées ci-dessous. Vérifiez notamment les signes + et −, les yeux OD/OG et les colonnes près/loin."
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpticalPrescriptionScreen(
    saved: OpticalPrescription?,
    currentAssistProfile: AssistProfile,
    currentVisualProfile: VisualProfile,
    latestLegacyAssessment: VisualComfortAssessment?,
    latestStandardizedAssessment: StandardizedAssessmentReport?,
    onApplyAndCalibrate: (OpticalPrescription, PrescriptionProfileRecommendation) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    saving: Boolean = false,
    operationError: String? = null,
    initialEntry: PrescriptionEntry = PrescriptionEntry.CHOOSE
) {
    val context = LocalContext.current
    BackHandler(enabled = saving) { /* Keep the active save and its navigation in this screen. */ }
    val draft: PrescriptionDraftViewModel = viewModel()
    LaunchedEffect(initialEntry) { draft.enter(initialEntry, saved) }
    LaunchedEffect(saved?.updatedAtMillis) { draft.seed(saved) }
    var confirmDelete by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) draft.read(context, uri)
    }
    val validation = PrescriptionInputValidation.validate(
        draft.right, draft.left, draft.pupillaryDistance, draft.readingDistance)
    val candidate = OpticalPrescription(
        rightEye = validation.rightEye,
        leftEye = validation.leftEye,
        pupillaryDistanceMm = validation.pupillaryDistanceMm,
        recommendedReadingDistanceCm = validation.recommendedReadingDistanceCm,
        prescriptionDate = draft.date.trim().ifBlank { null },
        notes = draft.notes.trim(),
        correctionType = draft.correctionType,
        documentUri = null,
        documentName = when (draft.source) {
            PrescriptionSource.PDF -> "PDF importé"
            PrescriptionSource.PHOTO, PrescriptionSource.IMAGE -> "Photo importée"
            PrescriptionSource.MANUAL -> null
        },
        source = draft.source,
        confirmedByUser = draft.confirmed,
        updatedAtMillis = saved?.updatedAtMillis ?: 0L
    )
    val errors = (validation.errors + candidate.validationErrors()).distinct()
    val recommendation = remember(candidate, validation.isValid, currentAssistProfile, currentVisualProfile,
        latestLegacyAssessment, latestStandardizedAssessment) {
        if (validation.isValid && candidate.isValid) PrescriptionProfileEngine.recommend(
            candidate, currentAssistProfile, currentVisualProfile, latestLegacyAssessment, latestStandardizedAssessment)
        else null
    }
    val document = draft.document
    val recognized = remember(document?.text) { document?.let { PrescriptionDocumentParser.parse(it.text) } }
    val scroll = rememberScrollState()
    LaunchedEffect(draft.step) { scroll.scrollTo(0) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Mon bilan visuel") }, navigationIcon = {
            TextButton(onClick = { draft.cancelRead(); onBack() }, enabled = !saving) { Text("Retour") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().testTag("bilan_screen").padding(padding).verticalScroll(scroll).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(when (draft.step) { 0 -> "Votre bilan, votre point de départ"; 1 -> "Vérifions vos informations"; else -> "Essayez votre affichage" },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${draft.step + 1} / 3 · ${listOf("Importer ou saisir", "Vérifier", "Essayer")[draft.step]}",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            LinearProgressIndicator(progress = { (draft.step + 1) / 3f }, modifier = Modifier.fillMaxWidth())

            operationError?.let { BilanCard { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Enregistrement sur ce téléphone…")
            }

            if (draft.step == 0) {
                BilanCard(highlighted = true) {
                    Text(if (initialEntry == PrescriptionEntry.DOCUMENT) "Importez votre bilan" else "Une photo, un PDF ou une saisie", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold)
                    Text("Ajoutez le document de votre professionnel de la vue. VueConfort essaiera de repérer les valeurs utiles ; vous les vérifierez avant toute utilisation.")
                    Text("La lecture du document se fait sur ce téléphone. La photo et le texte extrait ne sont ni envoyés ni conservés par VueConfort. Seules les valeurs que vous confirmez seront enregistrées.",
                        style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) }, enabled = !saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Importer une photo ou un PDF")
                    }
                    OutlinedButton(onClick = { draft.manualEntry(saved) }, enabled = !saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp)) {
                        Text(if (saved == null) "Commencer une saisie manuelle" else "Consulter mon bilan enregistré")
                    }
                }
                if (saved != null) BilanCard {
                    Text("Un bilan est déjà enregistré", style = MaterialTheme.typography.titleMedium)
                    Text(saved.prescriptionDate?.let { "Date du document : $it" } ?: "Date du document non renseignée")
                    Text("Votre profil reste identique jusqu’à la dernière étape.", style = MaterialTheme.typography.bodySmall)
                }
                Text("Vous n’avez pas de bilan ? Les réglages de confort de lecture restent accessibles depuis l’accueil.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (draft.step == 1) {
                if (draft.loading) BilanCard {
                    Text("Lecture du document sur votre téléphone…", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Les valeurs ne sont pas encore ajoutées à votre profil.")
                    TextButton(onClick = { draft.cancelRead(); draft.readError = "Lecture annulée. Vous pouvez choisir un document ou saisir les valeurs." }) {
                        Text("Annuler la lecture")
                    }
                }
                draft.readError?.let { BilanCard { Text(it, color = MaterialTheme.colorScheme.error) } }
                if (document != null) {
                    BilanCard {
                        Text(if (document.source == PrescriptionSource.PDF) "Votre PDF · page ${document.pageIndex + 1} sur ${document.pageCount}" else "Votre photo",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Image(document.preview.asImageBitmap(), contentDescription = "Document original à vérifier",
                            modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 280.dp), contentScale = ContentScale.Fit)
                        TextButton(onClick = { draft.showPreview = true }) { Text("Agrandir le document") }
                        if (document.pageCount > 1) {
                            Text("Seule la page affichée est lue. Choisissez celle de la prescription. Les pages ne sont jamais mélangées.",
                                style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { draft.page(context, document.pageIndex - 1) },
                                    enabled = document.pageIndex > 0, modifier = Modifier.weight(1f)) { Text("Page précédente") }
                                OutlinedButton(onClick = { draft.page(context, document.pageIndex + 1) },
                                    enabled = document.pageIndex + 1 < document.pageCount, modifier = Modifier.weight(1f)) { Text("Page suivante") }
                            }
                            Text("Changer de page efface le brouillon ci-dessous, sans modifier le bilan enregistré.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        recognized?.nearFarContext?.takeIf { it.isNotBlank() }?.let {
                            Text("Contexte repéré : $it. Vérifiez cette indication sur le document.", fontWeight = FontWeight.Medium)
                        }
                        recognized?.warnings?.forEach { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton(onClick = { draft.showExtractedText = !draft.showExtractedText }) {
                            Text(if (draft.showExtractedText) "Masquer le texte reconnu" else "Voir le texte reconnu")
                        }
                        if (draft.showExtractedText) Text(document.text.ifBlank { "Aucun texte reconnu. Recopiez les valeurs depuis l’original." },
                            style = MaterialTheme.typography.bodyMedium)
                        if (recognized?.canPrefill == true) {
                            Button(onClick = { draft.adopt(recognized.rightEye, recognized.leftEye, recognized.nearFarContext) }, modifier = Modifier.fillMaxWidth()) {
                                Text("Reprendre les valeurs de cette page")
                            }
                        } else {
                            Text("Aucune proposition assez claire à reprendre automatiquement. Utilisez l’original pour saisir les valeurs utiles ci-dessous.",
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
                draft.importNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (!draft.loading) {
                    Text("Recopiez les signes et les nombres tels qu’ils figurent sur votre document. Laissez les informations absentes vides.")
                    Text("SPH : sphère · CYL : cylindre · AXE : orientation · ADD : addition",
                        style = MaterialTheme.typography.bodySmall)
                    BilanEyeCard("Œil droit · OD", "OD", draft.right, validation.fieldErrors, draft.showErrors) {
                        draft.right = it; draft.edited()
                    }
                    BilanEyeCard("Œil gauche · OG", "OG", draft.left, validation.fieldErrors, draft.showErrors) {
                        draft.left = it; draft.edited()
                    }
                    BilanField("Distance de lecture indiquée (cm), facultative", draft.readingDistance,
                        error = if (draft.showErrors) validation.fieldErrors["readingDistance"] else null,
                        onChange = { draft.readingDistance = it; draft.edited() })
                    TextButton(onClick = { draft.showDetails = !draft.showDetails }) {
                        Text(if (draft.showDetails) "Masquer les informations complémentaires" else "Date, écart pupillaire et autres informations")
                    }
                    if (draft.showDetails) BilanCard {
                        BilanField("Écart pupillaire (mm), facultatif", draft.pupillaryDistance,
                            error = if (draft.showErrors) validation.fieldErrors["pupillaryDistance"] else null,
                            onChange = { draft.pupillaryDistance = it; draft.edited() })
                        BilanField("Date du document · AAAA-MM-JJ", draft.date, keyboard = KeyboardType.Text,
                            onChange = { draft.date = it; draft.edited() })
                        Text("Type de correction indiqué, facultatif", style = MaterialTheme.typography.titleSmall)
                        CorrectionType.entries.forEach { type ->
                            FilterChip(selected = draft.correctionType == type,
                                onClick = { draft.correctionType = type; draft.edited() }, label = { Text(type.displayName()) })
                        }
                        OutlinedTextField(value = draft.notes, onValueChange = { draft.notes = it.take(500); draft.edited() },
                            label = { Text("Mes notes, facultatives") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    }
                    BilanCard(highlighted = true) {
                        Row(Modifier.fillMaxWidth().toggleable(value = draft.confirmed, role = Role.Checkbox,
                            onValueChange = { draft.confirmed = it }), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = draft.confirmed, onCheckedChange = null)
                            Text(if (draft.source == PrescriptionSource.MANUAL)
                                "J’ai vérifié chaque valeur, son signe et l’œil concerné dans ma correction connue."
                            else "J’ai vérifié chaque valeur, son signe et l’œil concerné sur mon document.",
                                modifier = Modifier.weight(1f))
                        }
                        Text("Les valeurs ne sont utilisées qu’après votre vérification. Si une information est incertaine, laissez-la vide.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (draft.showErrors && errors.isNotEmpty()) BilanCard {
                        Text("À vérifier avant de continuer", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error)
                        errors.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                    Button(onClick = {
                        draft.showErrors = true
                        if (validation.isValid && candidate.isValid) draft.step = 2
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Vérifier et continuer")
                    }
                    TextButton(onClick = { draft.step = 0 }) { Text("Choisir un autre document ou mode de saisie") }
                }
            }

            if (draft.step == 2) {
                BilanCard(highlighted = true) {
                    Text("Votre bilan est prêt", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Vos valeurs confirmées restent sur ce téléphone, séparées de vos préférences de lecture.")
                    Text("Le bilan sera relié à votre profil. Vous retrouverez vos réglages dans l’égaliseur pour affiner votre confort. Un bilan seul ne garantit pas une correction optique de l’écran.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                recommendation?.let { value ->
                    Button(onClick = { onApplyAndCalibrate(candidate, value) }, enabled = !saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("Enregistrer et ouvrir l’égaliseur")
                    }
                }
                OutlinedButton(onClick = { draft.step = 1 }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Revoir les valeurs") }
                Text("Le document original n’est pas conservé dans VueConfort. Vous pourrez modifier ou supprimer les valeurs enregistrées.",
                    style = MaterialTheme.typography.bodySmall)
            }

            if (saved != null && draft.step == 0) TextButton(onClick = { confirmDelete = true }, enabled = !saving) {
                Text("Supprimer mes bilans enregistrés", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (draft.showPreview && document != null) DocumentPreview(document) { draft.showPreview = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, title = { Text("Supprimer mes bilans ?") },
        text = { Text("Les valeurs du bilan actuel et l’historique des bilans seront supprimés de VueConfort. Vos documents d’origine restent dans leur emplacement. Votre profil d’égaliseur et vos réglages de loupe sont conservés.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }, enabled = !saving) { Text("Supprimer les bilans") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
    )
}

@Composable
private fun DocumentPreview(document: DocumentReadResult, close: () -> Unit) {
    var scale by remember(document) { mutableStateOf(1f) }
    var offset by remember(document) { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Document original", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = close) { Text("Fermer") }
                }
                Text("Écartez deux doigts pour agrandir. Faites glisser pour déplacer.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { scale = (scale / 1.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero }, modifier = Modifier.weight(1f)) { Text("Réduire") }
                    OutlinedButton(onClick = { scale = (scale * 1.5f).coerceAtMost(5f) }, modifier = Modifier.weight(1f)) { Text("Agrandir") }
                    TextButton(onClick = { scale = 1f; offset = Offset.Zero }, modifier = Modifier.weight(1f)) { Text("Recentrer") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(document) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offset = if (scale == 1f) Offset.Zero else Offset(
                            (offset.x + pan.x).coerceIn(-size.width * scale / 2f, size.width * scale / 2f),
                            (offset.y + pan.y).coerceIn(-size.height * scale / 2f, size.height * scale / 2f))
                    }
                }) {
                    Image(document.preview.asImageBitmap(), "Document original, page ${document.pageIndex + 1}",
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale,
                            translationX = offset.x, translationY = offset.y), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun BilanCard(highlighted: Boolean = false, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(
        containerColor = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun BilanEyeCard(title: String, eye: String, value: PrescriptionEyeInput,
    fieldErrors: Map<String, String>, showErrors: Boolean, onChange: (PrescriptionEyeInput) -> Unit) {
    BilanCard {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BilanField("SPH", value.sphere, Modifier.weight(1f), if (showErrors) fieldErrors["$eye.sphere"] else null) { onChange(value.copy(sphere = it)) }
            BilanField("CYL", value.cylinder, Modifier.weight(1f), if (showErrors) fieldErrors["$eye.cylinder"] else null) { onChange(value.copy(cylinder = it)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BilanField("AXE (°)", value.axis, Modifier.weight(1f), if (showErrors) fieldErrors["$eye.axis"] else null) { onChange(value.copy(axis = it)) }
            BilanField("ADD", value.addition, Modifier.weight(1f), if (showErrors) fieldErrors["$eye.addition"] else null) { onChange(value.copy(addition = it)) }
        }
        BilanField("Acuité si indiquée, facultative", value.visualAcuity,
            error = if (showErrors) fieldErrors["$eye.visualAcuity"] else null, keyboard = KeyboardType.Text) {
            onChange(value.copy(visualAcuity = it))
        }
    }
}

@Composable
private fun BilanField(label: String, value: String, modifier: Modifier = Modifier, error: String? = null,
    keyboard: KeyboardType = KeyboardType.Decimal, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        singleLine = true, isError = error != null, supportingText = error?.let { { Text(it) } })
}

@Composable
private fun ReadingBeforeAfter(before: VisualProfile, after: VisualProfile) {
    listOf("Votre affichage actuel" to before, "Point de départ proposé" to after).forEach { (label, profile) ->
        BilanCard {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Lire un message, retrouver une information, profiter d’une pause.", fontSize = profile.fontSizeSp.sp,
                lineHeight = (profile.fontSizeSp * profile.lineHeightMultiplier).sp)
        }
    }
}

private fun inputFrom(value: EyePrescription?) = PrescriptionEyeInput(value?.sphere.display(), value?.cylinder.display(),
    value?.axisDegrees?.toString().orEmpty(), value?.addition.display(), value?.visualAcuity.orEmpty())
private fun Float?.display() = this?.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }.orEmpty()
private fun CorrectionType.displayName() = when (this) {
    CorrectionType.MYOPIA -> "Myopie"
    CorrectionType.HYPERMETROPIA -> "Hypermétropie"
    CorrectionType.ASTIGMATISM -> "Astigmatisme"
    CorrectionType.PRESBYOPIA -> "Presbytie"
    CorrectionType.MULTIFOCAL_PROGRESSIVE -> "Multifocale / progressive"
    CorrectionType.OTHER -> "Autre"
    CorrectionType.NOT_SPECIFIED -> "Non renseigné"
}
