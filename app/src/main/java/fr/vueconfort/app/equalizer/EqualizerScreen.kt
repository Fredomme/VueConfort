package fr.vueconfort.app.equalizer

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.vueconfort.app.R
import fr.vueconfort.app.optical.opticalRender
import java.util.Locale

private val EqualizerColors = lightColorScheme(
    primary = Color(0xFF176B5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEE6), onPrimaryContainer = Color(0xFF12473D),
    secondary = Color(0xFF4C7160), secondaryContainer = Color(0xFFDCE9DE), onSecondaryContainer = Color(0xFF203D30),
    surface = Color(0xFFFAFAF5), onSurface = Color(0xFF203B34),
    surfaceVariant = Color(0xFFEDF1EA), onSurfaceVariant = Color(0xFF52665C),
    outline = Color(0xFF74867A)
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit, onBilan: () -> Unit,
                    viewModel: EqualizerViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var original by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var commandId by remember { mutableLongStateOf(0L) }
    fun command(name: String) { commandId = EqualizerPerformance.command(name) }
    fun leave() { if (!state.busy) { if (state.hasUnsavedChanges) confirmLeave = true else onBack() } }
    BackHandler { leave() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) original = false }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    MaterialTheme(colorScheme = EqualizerColors) {
        Scaffold(modifier = Modifier.semantics { testTagsAsResourceId = true }, containerColor = MaterialTheme.colorScheme.surface, topBar = {
            TopAppBar(title = { Text("Égaliseur visuel", fontWeight = FontWeight.SemiBold, fontSize = 20.sp) },
                navigationIcon = { TextButton(onClick = { leave() }, enabled = !state.busy) { Text("Retour") } },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }, modifier = Modifier.testTag("eq_menu")) { Text("Profil") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Remettre au neutre") }, enabled = !state.busy,
                                onClick = { menu = false; original = false; command("reset"); viewModel.resetToNeutral() },
                                modifier = Modifier.testTag("eq_reset"))
                            DropdownMenuItem(text = { Text("Mon bilan visuel") }, enabled = !state.busy,
                                onClick = { menu = false; onBilan() })
                            DropdownMenuItem(text = { Text("Supprimer le profil") }, enabled = !state.busy && state.savedProfile != null,
                                onClick = { menu = false; confirmDelete = true }, modifier = Modifier.testTag("eq_delete"))
                        }
                    }
                })
        }, bottomBar = {
            Surface(shadowElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { original = false; command("cancel"); viewModel.cancelChanges() },
                        enabled = state.loaded && !state.busy && state.hasUnsavedChanges,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("eq_cancel")) { Text("Annuler") }
                    Button(onClick = { original = false; viewModel.save() }, enabled = state.loaded && !state.busy,
                        modifier = Modifier.weight(1.6f).heightIn(min = 48.dp).testTag("eq_save")) {
                        Text(if (state.busy) "Enregistrement…" else "Enregistrer")
                    }
                }
            }
        }) { padding ->
            if (!state.loaded) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    if (state.error == null) CircularProgressIndicator() else Text(state.error!!, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                }
            } else {
                val preferences = state.draft.preferences
                val scene = state.draft.scene
                val supported = Build.VERSION.SDK_INT >= 33
                BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                    val previewHeight = (maxHeight * .40f).coerceIn(144.dp, 248.dp)
                    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Triple(EqualizerScene.TEXT, "Lecture", "reading"),
                                Triple(EqualizerScene.DETAILS, "Détails", "detail"),
                                Triple(EqualizerScene.PHOTO, "Image", "image")).forEach { (value, label, tag) ->
                                FilterChip(selected = scene == value, onClick = {
                                    original = false; command("scene"); viewModel.selectScene(value)
                                }, label = { Text(label) }, modifier = Modifier.weight(1f).testTag("eq_scene_$tag"))
                            }
                        }
                        EqualizerPreview(preferences, scene, original, commandId,
                            Modifier.fillMaxWidth().height(previewHeight), onApplied = { viewModel.recordApplied(it) },
                            revision = state.draft.revision)
                        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (original) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("eq_original")
                            .semantics {
                                role = Role.Button
                                contentDescription = if (original) "Original affiché, relâcher pour retrouver mon réglage" else "Maintenir pour voir l’original"
                                onClick(label = "Comparer à l’original") { command("original_accessibility"); original = !original; true }
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = {
                                    command("original_down"); original = true
                                    try { tryAwaitRelease() } finally { command("original_up"); original = false }
                                })
                            }, contentAlignment = Alignment.Center) {
                            Text(if (original) "Original · même taille, même cadrage" else "Maintenir pour voir l’original",
                                fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Text(state.error ?: state.message ?: if (state.hasUnsavedChanges) "Modifications non enregistrées" else if (state.savedProfile != null) "Votre profil est enregistré sur ce téléphone" else "Réglez librement, aucun bilan nécessaire",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("eq_status"))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            if (!supported) Text("Sur cet appareil, taille et épaisseur restent disponibles. Les traitements d’image demandent Android 13 ou plus.", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                EqualizerControl("Taille", "${"%.2f".format(Locale.FRANCE, preferences.sizeScale)}×", preferences.sizeScale, 1f..2f,
                                    "size", Modifier.weight(1f), !state.busy) { command("size"); viewModel.updatePreferences { p -> p.copy(sizeScale = it) } }
                                EqualizerControl("Netteté", percent(preferences.sharpness / .8f), preferences.sharpness, 0f..0.8f,
                                    "sharpness", Modifier.weight(1f), supported && !state.busy) { command("sharpness"); viewModel.updatePreferences { p -> p.copy(sharpness = it) } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                EqualizerControl("Contraste", "${"%.2f".format(Locale.FRANCE, preferences.contrast)}×", preferences.contrast, .7f..1.5f,
                                    "contrast", Modifier.weight(1f), supported && !state.busy) { command("contrast"); viewModel.updatePreferences { p -> p.copy(contrast = it) } }
                                EqualizerControl("Lumière douce", percent(preferences.lightComfort), preferences.lightComfort, 0f..1f,
                                    "comfort", Modifier.weight(1f), supported && !state.busy) { command("comfort"); viewModel.updatePreferences { p -> p.copy(lightComfort = it) } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                EqualizerControl("Épaisseur", if (scene == EqualizerScene.TEXT) percent((preferences.fontWeight - 400) / 400f) else "Sur le texte", preferences.fontWeight.toFloat(), 400f..800f,
                                    "text_weight", Modifier.weight(1f), scene == EqualizerScene.TEXT && !state.busy) { command("text_weight"); viewModel.updatePreferences { p -> p.copy(fontWeight = it.toInt()) } }
                                EqualizerControl("Intensité", percent(preferences.intensity), preferences.intensity, 0f..1f,
                                    "intensity", Modifier.weight(1f), supported && !state.busy) { command("intensity"); viewModel.updatePreferences { p -> p.copy(intensity = it) } }
                            }
                            fr.vueconfort.app.nativevision.NativeEqualizerControls()
                        }
                    }
                }
            }
        }
        if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Supprimer ce profil ?") },
            text = { Text("Les réglages enregistrés de l’égaliseur seront effacés. Votre bilan et votre loupe sont conservés.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; original = false; command("delete"); viewModel.deleteProfile() },
                modifier = Modifier.testTag("eq_confirm_delete")) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Garder") } })
        if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false }, title = { Text("Quitter sans enregistrer ?") },
            text = { Text("Votre profil enregistré sera conservé. Les modifications en cours seront annulées.") },
            confirmButton = { TextButton(onClick = { viewModel.cancelChanges(); confirmLeave = false; onBack() }) { Text("Quitter") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("Continuer le réglage") } })
    }
}

private fun percent(value: Float) = "${(value * 100).toInt()} %"

@Composable
private fun EqualizerControl(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>,
                             tag: String, modifier: Modifier, enabled: Boolean, onChange: (Float) -> Unit) {
    Column(modifier.heightIn(min = 72.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(valueLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("eq_slider_$tag")
                .semantics { contentDescription = label })
    }
}

/** The geometry is outside the pixel treatment: Original never changes zoom, source or viewport. */
@Composable
fun EqualizerPreview(preferences: EqualizerPreferences, scene: EqualizerScene, original: Boolean,
                     commandId: Long = 0, modifier: Modifier = Modifier,
                     onApplied: (EqualizerRenderRecord) -> Unit = {}, revision: Long = 0L) {
    val settings = remember(preferences) { preferences.toOpticalSettings() }
    val record = remember(preferences, scene, revision) { preferences.renderRecord(scene, revision, Build.VERSION.SDK_INT >= 33) }
    // Post-draw notification is deferred: writing ViewModel state from the draw phase is avoided.
    var drawnRevision by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(drawnRevision, record) { if (drawnRevision == revision && !original) onApplied(record) }
    Box(modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, Color(0xFFCBD7CC), RoundedCornerShape(14.dp))
        .testTag("eq_preview").drawWithContent {
            drawContent()
            EqualizerPerformance.recordDraw(commandId)
            if (!original && drawnRevision != revision) drawnRevision = revision
        }) {
        Box(Modifier.fillMaxSize().opticalRender(settings, bypass = original).clip(RoundedCornerShape(0.dp))) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = preferences.sizeScale; scaleY = preferences.sizeScale
                transformOrigin = TransformOrigin(0f, 0f)
            }.background(Color(0xFFFDFBF5))) {
                when (scene) {
                    EqualizerScene.TEXT -> ReadingReference(if (original) 0f else ((preferences.fontWeight - 400) / 400f) * preferences.intensity)
                    EqualizerScene.DETAILS -> DetailReference()
                    EqualizerScene.PHOTO -> Image(painterResource(R.drawable.equalizer_reference),
                        "Illustration locale : livres, plante, verre et détails fins", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

@Composable
private fun ReadingReference(thickness: Float) {
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create("sans-serif", Typeface.NORMAL) } }
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Texte de référence : Retrouver le plaisir de lire. Petits caractères, lecture normale et faibles contrastes." }) {
        drawIntoCanvas { canvas ->
            val c = canvas.nativeCanvas
            paint.style = if (thickness <= 0f) Paint.Style.FILL else Paint.Style.FILL_AND_STROKE
            paint.strokeWidth = thickness * .65f * density
            fun line(text: String, y: Float, sp: Float, color: Int) {
                paint.textSize = sp * density
                paint.color = color
                c.drawText(text, 16 * density, y * density, paint)
            }
            line("LE PLAISIR DE LIRE", 28f, 12f, 0xff507266.toInt())
            line("Chaque détail compte.", 59f, 23f, 0xff223d34.toInt())
            line("Un moment pour ralentir,", 91f, 17f, 0xff2e3f36.toInt())
            line("regarder et retrouver les mots.", 115f, 17f, 0xff2e3f36.toInt())
            line("Petits caractères · 0123456789", 145f, 12f, 0xff5e685f.toInt())
            line("Nuances légères, contours délicats.", 169f, 13f, 0xff92968c.toInt())
            line("Réglez à votre rythme.", 199f, 17f, 0xff425d50.toInt())
        }
    }
}

@Composable
private fun DetailReference() {
    Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Contours, lignes fines, cercles et niveaux de contraste" }) {
        val w = size.width; val h = size.height
        listOf(.12f, .3f, .5f, .7f, .9f).forEachIndexed { i, gray ->
            drawRect(Color(gray, gray, gray), Offset(w * .06f + i * w * .18f, h * .09f), Size(w * .15f, h * .2f))
        }
        for (i in 0..26) {
            val x = w * .06f + i * w * .015f
            drawLine(Color(0xFF374F45), Offset(x, h * .4f), Offset(x, h * .72f), if (i % 3 == 0) 2f else 1f)
        }
        for (i in 1..7) drawCircle(Color(0xFF6D8474), i * h * .029f, Offset(w * .74f, h * .57f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(if (i % 2 == 0) 1f else 2f))
        for (i in 0..9) drawLine(Color(0xFF93A292), Offset(w * .07f + i * w * .08f, h * .84f),
            Offset(w * .15f + i * w * .08f, h * .94f), (i + 1).toFloat())
    }
}
