package fr.vueconfort.app.nativevision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NativeVisionScreen(onBack: () -> Unit, model: NativeVisionViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    NativeResumeObserver(model)
    Scaffold(modifier = Modifier.semantics { testTagsAsResourceId = true }, topBar = {
        TopAppBar(title = { Text("Mon affichage", fontWeight = FontWeight.SemiBold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Votre confort, au-delà de l’aperçu", style = MaterialTheme.typography.headlineSmall)
            Text("Choisissez les aides du téléphone. Vos préférences restent enregistrées dans votre profil VueConfort.")
            if (model.isLab) Text("Laboratoire · les réglages Samsung automatiques restent actifs après fermeture, jusqu’à votre prochaine modification.",
                style = MaterialTheme.typography.bodyMedium)
            if (!state.loaded) {
                if (state.error == null) CircularProgressIndicator() else Text(state.error!!)
            } else {
                fr.vueconfort.app.orchestration.VisionStatusCard()
                ReluminoControls(state, model)
                NativeCard("Grossissement", "L’agrandissement Android de votre loupe, avec ses commandes habituelles.") {
                    val value = (state.profile.requested.values[NativeVisionCapability.MAGNIFICATION] as? NativeVisionValue.Magnification)
                        ?: NativeVisionValue.Magnification(false, 2f)
                    NativeToggle("Agrandir l’affichage", value.enabled, "native_magnification_enabled", !state.restoring) {
                        model.update(NativeVisionCapability.MAGNIFICATION, value) { current -> current.copy(enabled = it) }
                    }
                    Text("Taille : ${"%.1f".format(value.scale)}×")
                    Slider(value.scale, { model.update(NativeVisionCapability.MAGNIFICATION, value) { current -> current.copy(scale = it) } },
                        valueRange = 1f..8f, enabled = !state.restoring,
                        modifier = Modifier.testTag("native_magnification_scale").semantics { contentDescription = "Taille du grossissement" })
                    NativeChoices(NativeMagnificationMode.entries, value.mode, { if (it == NativeMagnificationMode.FULLSCREEN) "Plein écran" else "Fenêtre" }, enabled = !state.restoring) {
                        model.update(NativeVisionCapability.MAGNIFICATION, value) { current -> current.copy(mode = it) }
                    }
                    ResultText(NativeVisionCapability.MAGNIFICATION, state)
                    Text("Pour agrandir l’écran, activez volontairement le service de la loupe dans Android. Ce service utilise aussi le nom de l’application affichée pour vos règles et, uniquement avec le bouton Lire, son texte accessible. Ces informations restent sur le téléphone ; le texte lu n’est pas enregistré.", style = MaterialTheme.typography.bodySmall)
                    ConfigureButton(NativeVisionCapability.MAGNIFICATION, model, "Autoriser ou configurer la loupe")
                }
                NativeCard("Atténuation supplémentaire", "Réduire la luminosité des couleurs affichées avec l’aide native du téléphone.") {
                    val cap = NativeVisionCapability.EXTRA_DIM
                    val v = state.profile.requested.values[cap] as? NativeVisionValue.ExtraDim ?: NativeVisionValue.ExtraDim(false, 38)
                    NativeToggle("Utiliser l’atténuation", v.enabled, "native_dim_enabled", !state.restoring) { model.update(cap, v) { current -> current.copy(enabled = it) } }
                    Text("Ajustez l’intensité directement dans les réglages du téléphone. Cette version ne peut pas la vérifier automatiquement.", style = MaterialTheme.typography.bodySmall)
                    ResultText(cap, state); ConfigureButton(cap, model)
                }
                var details by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { details = !details }, modifier = Modifier.fillMaxWidth().testTag("native_more")) {
                    Text(if (details) "Masquer les autres aides" else "Autres aides de l’affichage")
                }
                if (details) {
                    OtherNativeControls(state, model)
                }
                NativeFooter(state, model)
            }
        }
    }
}

/** Embedded beneath the existing equalizer controls; its Netteté equation is unchanged. */
@Composable
fun NativeEqualizerControls(model: NativeVisionViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    NativeResumeObserver(model)
    if (state.loaded) {
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        ReluminoControls(state, model)
        Text("Le bouton Original compare uniquement l’aperçu VueConfort. Les aides Samsung restent actives.", style = MaterialTheme.typography.bodySmall)
        NativeFooter(state, model)
    }
}

@Composable
private fun NativeResumeObserver(model: NativeVisionViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        model.refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun ReluminoControls(state: NativeVisionUiState, model: NativeVisionViewModel) {
    val cap = NativeVisionCapability.RELUMINO
    val support = state.capabilities?.get(cap)
    val v = state.profile.requested.values[cap] as? NativeVisionValue.Relumino ?: NativeVisionValue.Relumino()
    NativeCard("Contours Samsung · Relumino", "Renforcer les contours sur l’écran. Ce réglage est indépendant de la Netteté de l’aperçu.") {
        val supported = support?.presence == NativeVisionPresence.PRESENT && support.availability != NativeVisionAvailability.UNSUPPORTED_DEVICE
        if (!supported) {
            Text(if (support?.availability == NativeVisionAvailability.UNSUPPORTED_DEVICE) "Cette aide Samsung n’est pas disponible sur cet appareil."
                else "La présence de cette aide n’a pas été confirmée sur ce téléphone.")
        } else {
            if (model.isLab && support?.canApplyAutomatically != true) Text("Contrôle automatique Samsung indisponible dans cette version de laboratoire.")
            NativeToggle("Utiliser les contours", v.enabled, "native_relumino_enabled", !state.restoring) {
                model.update(cap, v) { current -> current.copy(enabled = it) }
            }
            Text("Épaisseur demandée : ${v.thickness.ordinal + 1} / 5", fontWeight = FontWeight.Medium)
            Slider(v.thickness.ordinal.toFloat(), { position ->
                model.update(cap, v) { current -> current.copy(thickness = ReluminoThickness.entries[position.roundToInt().coerceIn(0, 4)]) }
            }, valueRange = 0f..4f, steps = 3, enabled = !state.restoring,
                modifier = Modifier.testTag("native_relumino_thickness").semantics { contentDescription = "Épaisseur des contours Samsung" })
            NativeChoices(ReluminoColor.entries, v.color, { colorLabel(it) }, enabled = !state.restoring) { model.update(cap, v) { current -> current.copy(color = it) } }
            Text("Ces cinq positions correspondent aux crans Samsung. Aucun réglage optique n’est calculé.", style = MaterialTheme.typography.bodySmall)
            ResultText(cap, state)
            if (support?.canApplyAutomatically != true) {
                ConfigureButton(cap, model, "Configurer les contours Samsung")
                Text("Dans Samsung : Améliorations pour la vision → Contour Relumino. Appliquez l’activation, l’épaisseur et la couleur indiquées, puis revenez ici.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (state.profile.requested.values.containsKey(cap) && state.profile.currentResult(cap)?.applied == null && !state.saving) {
                TextButton(onClick = { model.confirm(cap) }, modifier = Modifier.testTag("native_confirm_manual")) {
                    Text("J’ai appliqué ces réglages")
                }
            }
        }
    }
}

@Composable
private fun OtherNativeControls(state: NativeVisionUiState, model: NativeVisionViewModel) {
    NativeCard("Filtre de couleur", "Une teinte sur l’affichage, différente de la correction des couleurs.") {
        val cap = NativeVisionCapability.COLOR_FILTER
        val v = state.profile.requested.values[cap] as? NativeVisionValue.ColorFilter ?: NativeVisionValue.ColorFilter(false, 0, 20)
        NativeToggle("Utiliser le filtre", v.enabled, "native_filter_enabled", !state.restoring) { model.update(cap, v) { current -> current.copy(enabled = it) } }
        val names = listOf("Bleu", "Azur", "Cyan", "Vert printemps", "Vert", "Chartreuse", "Jaune", "Orange", "Rouge", "Rose", "Magenta", "Violet")
        Text("Couleur demandée : ${names[v.color]}")
        Slider(v.color.toFloat(), { model.update(cap, v) { current -> current.copy(color = it.roundToInt()) } }, valueRange = 0f..11f, steps = 10, enabled = !state.restoring)
        Text("Intensité demandée : ${v.opacityPercent} %")
        Slider(v.opacityPercent.toFloat(), { model.update(cap, v) { current -> current.copy(opacityPercent = (it / 5).roundToInt() * 5) } },
            valueRange = 20f..60f, steps = 7, enabled = !state.restoring)
        ResultText(cap, state); ConfigureButton(cap, model)
    }
    NativeCard("Correction des couleurs", "Une aide spécifique pour distinguer certaines couleurs.") {
        val cap = NativeVisionCapability.COLOR_CORRECTION
        val v = state.profile.requested.values[cap] as? NativeVisionValue.ColorCorrection ?: NativeVisionValue.ColorCorrection(false, 12)
        NativeToggle("Utiliser la correction des couleurs", v.enabled, "native_color_correction", !state.restoring) { model.update(cap, v) { current -> current.copy(enabled = it) } }
        val names = mapOf(12 to "Vert–rouge", 11 to "Rouge–vert", 13 to "Bleu–jaune", 0 to "Gris")
        NativeChoices(names.keys.toList(), v.mode, { names.getValue(it) }, enabled = !state.restoring) { model.update(cap, v) { current -> current.copy(mode = it) } }
        ResultText(cap, state); ConfigureButton(cap, model)
    }
    for ((cap, name, description) in listOf(
        Triple(NativeVisionCapability.COLOR_INVERSION, "Inversion des couleurs", "Inverser les couleurs est un choix distinct du contraste."),
        Triple(NativeVisionCapability.HIGH_CONTRAST_TEXT, "Texte à contraste élevé", "Renforcer le texte compatible, sans changer les images."),
        Triple(NativeVisionCapability.EYE_COMFORT, "Confort visuel Samsung", "Configurer les couleurs chaudes et leur programmation dans Samsung.")
    )) NativeCard(name, description) {
        val v = state.profile.requested.values[cap] as? NativeVisionValue.Toggle ?: NativeVisionValue.Toggle(false)
        NativeToggle("Utiliser cette aide", v.enabled, "native_${cap.name.lowercase()}", !state.restoring) { model.update(cap, v) { current -> current.copy(enabled = it) } }
        ResultText(cap, state); ConfigureButton(cap, model)
    }
    for ((cap, label) in listOf(NativeVisionCapability.SYSTEM_BRIGHTNESS to "Luminosité du téléphone",
        NativeVisionCapability.FONT_SCALE to "Taille de police", NativeVisionCapability.SCREEN_ZOOM to "Zoom écran")) {
        NativeCard(label, "Ce réglage reste dans les paramètres Android. Aucune autorisation supplémentaire n’est demandée par VueConfort.") {
            ConfigureButton(cap, model)
        }
    }
}

@Composable private fun NativeCard(title: String, description: String, body: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium)
            body()
        }
    }
}
@Composable private fun NativeToggle(label: String, checked: Boolean, tag: String, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked, change, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = label })
    }
}
@Composable private fun <T> NativeChoices(values: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, change: (T) -> Unit) {
    Column {
        values.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { value -> FilterChip(selected == value, { change(value) }, label = { Text(label(value)) }, enabled = enabled,
                modifier = Modifier.weight(1f).testTag("native_choice_${value.toString().lowercase()}")) }
        } }
    }
}
@Composable private fun ResultText(cap: NativeVisionCapability, state: NativeVisionUiState) {
    val result = state.profile.currentResult(cap)
    val label = when {
        state.saving -> "Enregistrement et vérification…"
        !state.profile.requested.values.containsKey(cap) -> "Non utilisé dans votre profil"
        !state.profile.enabled -> "Pilotage désactivé · état du téléphone à vérifier"
        result?.confirmation == NativeVisionConfirmation.USER_CONFIRMED -> "Confirmé par vous · non vérifié automatiquement"
        result?.confirmation == NativeVisionConfirmation.READ_BACK_CONFIRMED && (result.status == NativeVisionApplicationStatus.APPLIED_AUTO || result.applied == result.requested) -> "Configuration terminée · état vérifié"
        result?.status == NativeVisionApplicationStatus.UNSUPPORTED -> "Non pris en charge sur cet appareil"
        result?.status == NativeVisionApplicationStatus.UNAVAILABLE -> "Indisponible actuellement"
        result?.status == NativeVisionApplicationStatus.REJECTED || result?.status == NativeVisionApplicationStatus.ERROR -> "Réglage non appliqué"
        else -> "Action requise dans les réglages du téléphone"
    }
    Text(label, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("native_status_${cap.name.lowercase()}"))
    result?.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
@Composable private fun ConfigureButton(cap: NativeVisionCapability, model: NativeVisionViewModel, text: String = "Configurer sur le téléphone") {
    var failed by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { failed = !model.configure(cap) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("native_configure_${cap.name.lowercase()}")) { Text(text) }
    if (failed) Text("Ouvrez les réglages Android de l’affichage ou de l’accessibilité ; cette page ne peut pas être ouverte directement.")
}
@Composable private fun NativeFooter(state: NativeVisionUiState, model: NativeVisionViewModel) {
    var deactivate by remember { mutableStateOf(false) }
    state.message?.let { Text(it, modifier = Modifier.testTag("native_message")) }
    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("native_error")) }
    if (state.profile.enabled || state.pendingRestoration) OutlinedButton(onClick = { deactivate = true }, enabled = !state.restoring,
        modifier = Modifier.fillMaxWidth().testTag("native_deactivate")) { Text("Désactiver / restaurer") }
    Text("Des aides à l’affichage, sans capture d’écran. Elles ne remplacent pas une correction visuelle.", style = MaterialTheme.typography.bodySmall)
    if (deactivate) AlertDialog(onDismissRequest = { deactivate = false }, title = { Text("Que conserver sur le téléphone ?") },
        text = { Text("Vous pouvez garder les réglages actuels ou restaurer ceux que VueConfort avait mémorisés avant ses commandes automatiques. Les réglages effectués manuellement dans Samsung restent sous votre contrôle.") },
        confirmButton = { TextButton(onClick = { deactivate = false; model.disable(true) }, modifier = Modifier.testTag("native_restore")) { Text("Restaurer les précédents") } },
        dismissButton = { TextButton(onClick = { deactivate = false; model.disable(false) }, modifier = Modifier.testTag("native_keep")) { Text("Conserver les actuels") } })
}
private fun colorLabel(color: ReluminoColor) = when (color) {
    ReluminoColor.ADAPTIVE -> "Adaptatif"; ReluminoColor.BLACK -> "Noir"; ReluminoColor.WHITE -> "Blanc"; ReluminoColor.GREEN -> "Vert"
}
