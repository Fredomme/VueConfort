package fr.vueconfort.app.assessment

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.UUID

private enum class StandardScreen {
    MENU, CALIBRATION, SETUP, CONDITIONS, ACUITY, CONTRAST, AMSLER, RESULT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardizedAssessmentScreen(
    onSaveReport: (StandardizedAssessmentReport) -> Unit,
    onSaveProfile: (fr.vueconfort.app.model.AssistProfile) -> Unit,
    onTryProfile: (String) -> Unit,
    onAdjustProfile: () -> Unit,
    onHistory: () -> Unit,
    onMeasurementActive: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val metrics = context.resources.displayMetrics
    val configuration = LocalConfiguration.current
    var screen by remember { mutableStateOf(StandardScreen.MENU) }
    var pending by remember { mutableStateOf(AcuityMethod.LANDOLT_C) }
    var contrastRequested by remember { mutableStateOf(false) }
    var amslerRequested by remember { mutableStateOf(false) }
    var widthDp by remember { mutableFloatStateOf(324f) }
    var heightDp by remember { mutableFloatStateOf(204f) }
    var calibrationValid by remember { mutableStateOf(false) }
    var calibration by remember {
        mutableStateOf(
            buildPhysicalCalibration(
                widthDp, heightDp, metrics.xdpi, metrics.ydpi, metrics.density,
                "portrait", false
            )
        )
    }
    var eye by remember { mutableStateOf(StandardEye.OD) }
    var distance by remember { mutableStateOf(TestDistance.NEAR_40) }
    var withCorrection by remember { mutableStateOf(true) }
    var conditionsConfirmed by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var interruptions by remember { mutableIntStateOf(0) }
    var levelIndex by remember { mutableIntStateOf(0) }
    var trialInLevel by remember { mutableIntStateOf(0) }
    var correctInLevel by remember { mutableIntStateOf(0) }
    var totalTrials by remember { mutableIntStateOf(0) }
    var totalCorrect by remember { mutableIntStateOf(0) }
    var confirmation by remember { mutableStateOf(false) }
    var validatedLogMar by remember { mutableStateOf<Float?>(null) }
    val acuityResults = remember { mutableStateListOf<StandardAcuityResult>() }
    val contrastResults = remember { mutableStateListOf<StandardContrastResult>() }
    val amslerResults = remember { mutableStateListOf<AmslerResult>() }
    var contrastIndex by remember { mutableIntStateOf(0) }
    var currentReport by remember { mutableStateOf<StandardizedAssessmentReport?>(null) }

    DisposableEffect(screen) {
        val measuring = screen in listOf(
            StandardScreen.CONDITIONS, StandardScreen.ACUITY,
            StandardScreen.CONTRAST, StandardScreen.AMSLER
        )
        val oldOrientation = activity?.requestedOrientation
        val oldBrightness = activity?.window?.attributes?.screenBrightness
        if (measuring) {
            onMeasurementActive(true)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            activity?.window?.attributes = activity.window.attributes.apply {
                screenBrightness = 0.7f
            }
        }
        onDispose {
            if (measuring) {
                onMeasurementActive(false)
                if (oldOrientation != null) activity.requestedOrientation = oldOrientation
                if (oldBrightness != null) {
                    activity?.window?.attributes = activity.window.attributes.apply {
                        screenBrightness = oldBrightness
                    }
                }
            }
        }
    }

    fun resetSeries() {
        levelIndex = 0; trialInLevel = 0; correctInLevel = 0
        totalTrials = 0; totalCorrect = 0; confirmation = false; validatedLogMar = null
    }

    fun buildReport(): StandardizedAssessmentReport =
        StandardizedAssessmentReport(
            id = UUID.randomUUID().toString(),
            protocol = StandardProtocol.STANDARDIZED_V2,
            createdAtMillis = System.currentTimeMillis(),
            calibration = calibration,
            conditionsConfirmed = conditionsConfirmed,
            acuityResults = acuityResults.toList(),
            contrastResults = contrastResults.toList(),
            amslerResults = amslerResults.toList()
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Test de dépistage visuel") },
                navigationIcon = {
                    OutlinedButton(
                        onClick = {
                            if (screen == StandardScreen.MENU) onBack()
                            else screen = StandardScreen.MENU
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("Quitter") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (paused) {
                Text("Test en pause")
                Button(onClick = {
                    paused = false
                    trialInLevel = 0
                    correctInLevel = 0
                    confirmation = false
                }, modifier = Modifier.fillMaxWidth()) { Text("Reprendre au début du niveau") }
                return@Column
            }
            if (screen in listOf(StandardScreen.ACUITY, StandardScreen.CONTRAST, StandardScreen.AMSLER)) {
                Text("${eye.name} · ${distanceLabel(distance)} · ${if (withCorrection) "avec correction" else "sans correction"}")
                OutlinedButton(onClick = { paused = true; interruptions++ }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pause")
                }
            }
            when (screen) {
                StandardScreen.MENU -> StandardMenu(
                    onLandolt = { amslerRequested = false; contrastRequested = false; pending = AcuityMethod.LANDOLT_C; screen = StandardScreen.CALIBRATION },
                    onE = { amslerRequested = false; contrastRequested = false; pending = AcuityMethod.TUMBLING_E; distance = TestDistance.NEAR_40; screen = StandardScreen.CALIBRATION },
                    onContrast = { amslerRequested = false; contrastRequested = true; pending = AcuityMethod.LANDOLT_C; screen = StandardScreen.CALIBRATION },
                    onAmsler = {
                        amslerRequested = true
                        contrastRequested = false
                        eye = StandardEye.OD
                        distance = TestDistance.NEAR_40
                        screen = StandardScreen.CONDITIONS
                    },
                    onHistory = onHistory
                )
                StandardScreen.CALIBRATION -> {
                    Text("Calibration physique obligatoire pour un résultat chiffré")
                    PhysicalCalibrationControl(widthDp, heightDp, { widthDp = it }, { heightDp = it })
                    StandardSwitch("La carte est parfaitement alignée", calibrationValid) { calibrationValid = it }
                    Button(onClick = {
                        calibration = buildPhysicalCalibration(
                            widthDp, heightDp, metrics.xdpi, metrics.ydpi, metrics.density,
                            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) "portrait" else "paysage",
                            calibrationValid
                        )
                        screen = StandardScreen.SETUP
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (calibrationValid) "Valider la calibration" else "Mode démonstration sans résultat chiffré")
                    }
                }
                StandardScreen.SETUP -> StandardSetup(
                    pending, eye, distance, withCorrection,
                    { eye = it }, { distance = it }, { withCorrection = it }
                ) {
                    screen = StandardScreen.CONDITIONS
                }
                StandardScreen.CONDITIONS -> ConditionsChecklist(conditionsConfirmed, {
                    conditionsConfirmed = it
                }) {
                    resetSeries()
                    screen = when {
                        amslerRequested -> StandardScreen.AMSLER
                        contrastRequested -> StandardScreen.CONTRAST
                        else -> StandardScreen.ACUITY
                    }
                }
                StandardScreen.ACUITY -> {
                    val levels = LogMarTestEngine.displayableLevels(distance, calibration)
                        .ifEmpty { listOf(1f) }
                    val logMar = levels[levelIndex.coerceIn(levels.indices)]
                    val geometry = VisualAngleCalculator.landoltGeometry(logMar, distance.centimeters)
                    val diameterPx = VisualAngleCalculator.millimetersToPixels(
                        geometry.outerDiameterMm, calibration.xdpi, calibration.horizontalFactor
                    )
                    val strokePx = VisualAngleCalculator.millimetersToPixels(
                        geometry.strokeMm, calibration.xdpi, calibration.horizontalFactor
                    )
                    val direction = remember(levelIndex, trialInLevel, confirmation) {
                        OptotypeDirection.entries.random()
                    }
                    Text("${String.format("%.1f", logMar)} logMAR · essai ${trialInLevel + 1}/5")
                    if (pending == AcuityMethod.LANDOLT_C) {
                        LandoltOptotype(
                            (diameterPx / metrics.density).dp,
                            (strokePx / metrics.density).dp,
                            direction
                        )
                    } else {
                        TumblingEOptotype((diameterPx / metrics.density).dp, direction)
                    }
                    DirectionButtons { selected ->
                        val correct = selected == direction
                        totalTrials++; trialInLevel++
                        if (correct) { totalCorrect++; correctInLevel++ }
                        if (trialInLevel == 5) {
                            if (LogMarTestEngine.passes(correctInLevel)) {
                                validatedLogMar = logMar
                                if (levelIndex < levels.lastIndex) {
                                    levelIndex++; trialInLevel = 0; correctInLevel = 0; confirmation = false
                                } else {
                                    val result = LogMarTestEngine.buildResult(
                                        pending, eye, distance, withCorrection, validatedLogMar,
                                        levels.last(), totalCorrect, totalTrials,
                                        calibration.valid && conditionsConfirmed, interruptions
                                    )
                                    acuityResults += result
                                    currentReport = buildReport()
                                    screen = StandardScreen.RESULT
                                }
                            } else if (!confirmation) {
                                confirmation = true; trialInLevel = 0; correctInLevel = 0
                            } else {
                                val result = LogMarTestEngine.buildResult(
                                    pending, eye, distance, withCorrection, validatedLogMar,
                                    levels.last(), totalCorrect, totalTrials,
                                    calibration.valid && conditionsConfirmed, interruptions
                                )
                                acuityResults += result
                                currentReport = buildReport()
                                screen = StandardScreen.RESULT
                            }
                        }
                    }
                }
                StandardScreen.CONTRAST -> {
                    val contrast = ScreenContrastTestEngine.contrastLevels[
                        contrastIndex.coerceIn(ScreenContrastTestEngine.contrastLevels.indices)
                    ]
                    val direction = remember(contrastIndex, trialInLevel) {
                        OptotypeDirection.entries.random()
                    }
                    Text("Sensibilité au contraste sur écran · série ${contrastIndex + 1}")
                    LandoltOptotype(80.dp, 16.dp, direction, Color.Black.copy(alpha = contrast))
                    DirectionButtons { selected ->
                        totalTrials++; trialInLevel++
                        if (selected == direction) { totalCorrect++; correctInLevel++ }
                        if (trialInLevel == 5) {
                            if (
                                ScreenContrastTestEngine.passes(correctInLevel) &&
                                contrastIndex < ScreenContrastTestEngine.contrastLevels.lastIndex
                            ) {
                                contrastIndex++; trialInLevel = 0; correctInLevel = 0
                            } else {
                                val minimum = if (ScreenContrastTestEngine.passes(correctInLevel)) contrast
                                    else ScreenContrastTestEngine.contrastLevels.getOrNull(contrastIndex - 1)
                                contrastResults += StandardContrastResult(
                                    eye, minimum, minimum?.let(ScreenContrastTestEngine::sensitivity),
                                    minimum?.let(ScreenContrastTestEngine::logSensitivity),
                                    totalCorrect, totalTrials,
                                    if (totalTrials == 0) 1f else (totalTrials - totalCorrect).toFloat() / totalTrials,
                                    conditionsConfirmed,
                                    if (calibration.valid && totalTrials >= 10) ResultReliability.MEDIUM else ResultReliability.LOW
                                )
                                currentReport = buildReport()
                                screen = StandardScreen.RESULT
                            }
                        }
                    }
                }
                StandardScreen.AMSLER -> {
                    Row {
                        listOf(StandardEye.OD, StandardEye.OG).forEach { value ->
                            OutlinedButton(
                                onClick = { eye = value },
                                modifier = Modifier.weight(1f)
                            ) { Text(if (eye == value) "✓ ${value.name}" else value.name) }
                        }
                    }
                    AmslerGridTest(eye) {
                        amslerResults.removeAll { old -> old.eye == it.eye }
                        amslerResults += it
                        currentReport = buildReport()
                        screen = StandardScreen.RESULT
                    }
                }
                StandardScreen.RESULT -> currentReport?.let { report ->
                    StandardResult(report, onSaveReport, onSaveProfile, onTryProfile, onAdjustProfile) {
                        screen = StandardScreen.MENU
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardMenu(
    onLandolt: () -> Unit, onE: () -> Unit, onContrast: () -> Unit,
    onAmsler: () -> Unit, onHistory: () -> Unit
) {
    Text("Protocole fondé sur des principes standardisés")
    MenuButton("Mesurer mon acuité", "Test principal avec anneau de Landolt", onLandolt)
    MenuButton("Test simplifié", "E directionnel à 40 cm", onE)
    MenuButton("Sensibilité au contraste", "Symboles de moins en moins contrastés", onContrast)
    MenuButton("Vision centrale", "Grille d’Amsler, un œil à la fois", onAmsler)
    MenuButton("Historique", "Consulter les résultats compatibles", onHistory)
}

@Composable
private fun MenuButton(title: String, subtitle: String, action: () -> Unit) {
    Button(onClick = action, modifier = Modifier.fillMaxWidth().height(76.dp)) {
        Column { Text(title); Text(subtitle) }
    }
}

@Composable
private fun StandardSetup(
    method: AcuityMethod,
    eye: StandardEye,
    distance: TestDistance,
    correction: Boolean,
    onEye: (StandardEye) -> Unit,
    onDistance: (TestDistance) -> Unit,
    onCorrection: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    Text(if (method == AcuityMethod.LANDOLT_C) "Anneau de Landolt" else "Test E directionnel")
    Row { StandardEye.entries.forEach { value ->
        OutlinedButton(onClick = { onEye(value) }, modifier = Modifier.weight(1f)) {
            Text(if (eye == value) "✓ ${value.name}" else value.name)
        }
    } }
    val distances = if (method == AcuityMethod.TUMBLING_E) listOf(TestDistance.NEAR_40)
    else TestDistance.entries
    distances.forEach { value ->
        OutlinedButton(onClick = { onDistance(value) }, modifier = Modifier.fillMaxWidth()) {
            Text("${if (distance == value) "✓ " else ""}${distanceLabel(value)}")
        }
    }
    if (distance.centimeters >= 200) {
        Text("Mode accompagné obligatoire : une autre personne valide les réponses. Ne revenez pas toucher l’écran entre les symboles.")
    }
    StandardSwitch("Correction habituelle portée", correction, onCorrection)
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continuer") }
}

@Composable
private fun ConditionsChecklist(value: Boolean, onValue: (Boolean) -> Unit, onNext: () -> Unit) {
    Text("Avant la série")
    Text("Correction conforme au parcours · écran propre · distance maintenue · aucun reflet.")
    Text("Désactivez luminosité automatique, Confort visuel Samsung, Extra dim et filtres de couleurs.")
    Text("VueConfort fixe une luminosité stable et désactive ses effets optiques pendant la mesure.")
    StandardSwitch("Toutes les conditions sont confirmées", value, onValue)
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Commencer") }
}

@Composable
private fun DirectionButtons(onDirection: (OptotypeDirection) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            OptotypeDirection.LEFT to "←", OptotypeDirection.UP to "↑",
            OptotypeDirection.DOWN to "↓", OptotypeDirection.RIGHT to "→"
        ).forEach { (direction, symbol) ->
            Button(onClick = { onDirection(direction) }, modifier = Modifier.weight(1f).height(70.dp)) {
                Text(symbol)
            }
        }
    }
}

@Composable
private fun StandardResult(
    report: StandardizedAssessmentReport,
    onSaveReport: (StandardizedAssessmentReport) -> Unit,
    onSaveProfile: (fr.vueconfort.app.model.AssistProfile) -> Unit,
    onTryProfile: (String) -> Unit,
    onAdjustProfile: () -> Unit,
    onDone: () -> Unit
) {
    Text("Rapport STANDARDIZED_V2")
    report.acuityResults.forEach {
        Text("${it.eye} ${it.method} — ${distanceLabel(it.distance)}")
        if (it.logMar == null) {
            Text("Résultat non mesurable sans calibration physique")
        } else {
            Text("${String.format("%.1f", it.logMar)} logMAR — environ ${String.format("%.1f", it.tenths)}/10")
            Text("${it.correctAnswers} réponses correctes sur ${it.totalTrials} · fiabilité ${it.reliability.name.lowercase()}")
        }
    }
    report.contrastResults.forEach {
        Text("${it.eye} — sensibilité au contraste sur écran : ${it.logContrastSensitivity?.let { value -> String.format("%.2f log", value) } ?: "non mesurable"}")
    }
    report.amslerResults.forEach {
        val anomaly = it.wavyLines || it.missingArea || it.darkArea || !it.centralPointVisible
        Text("${it.eye} — grille d’Amsler : ${if (anomaly) "anomalie subjective signalée" else "aucune anomalie déclarée"}")
        if (anomaly) Text("Un contrôle professionnel est recommandé.")
        if (anomaly && it.recentOrSudden) Text("Une modification récente ou soudaine doit être évaluée rapidement.")
    }
    val monocular = report.acuityResults.filter {
        it.eye != StandardEye.OU && it.logMar != null
    }
    val od = monocular.firstOrNull { it.eye == StandardEye.OD }
    val og = monocular.firstOrNull {
        it.eye == StandardEye.OG && it.method == od?.method &&
            it.distance == od?.distance && it.withCorrection == od?.withCorrection
    }
    if (od?.logMar != null && og?.logMar != null &&
        kotlin.math.abs(od.logMar - og.logMar) >= 0.2f
    ) {
        Text("Une différence notable entre OD et OG a été mesurée. Un contrôle professionnel est recommandé.")
    }
    if (report.acuityResults.any { (it.logMar ?: 0f) >= 1f }) {
        Text("Le résultat mesuré est très faible. Un contrôle professionnel est recommandé.")
    }
    if (report.acuityResults.any { it.logMar == null && it.totalTrials >= 10 }) {
        Text("Le test reste non mesurable malgré plusieurs tentatives. Un contrôle professionnel est recommandé.")
    }
    Text("Estimation de dépistage sur écran, non équivalente à une ordonnance.")
    val profile = StandardizedProfileEngine.create(report)
    if (report.acuityResults.any {
            it.logMar != null && it.reliability != ResultReliability.LOW
        }
    ) {
        Text("Grossissement proposé : ${String.format("%.1f", profile.magnificationScale)}×, fondé sur le résultat le moins favorable avec marge de confort.")
        Button(onClick = { onSaveProfile(profile); onTryProfile(profile.id) }, modifier = Modifier.fillMaxWidth()) { Text("Essayer") }
        OutlinedButton(onClick = { onSaveProfile(profile) }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer") }
        OutlinedButton(
            onClick = { onSaveProfile(profile); onAdjustProfile() },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Ajuster dans Profils") }
    }
    Button(onClick = { onSaveReport(report); onDone() }, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer le rapport") }
}

@Composable
private fun StandardSwitch(label: String, value: Boolean, onValue: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, onValue)
    }
}

private fun distanceLabel(value: TestDistance) = when (value) {
    TestDistance.NEAR_40 -> "près 40 cm"
    TestDistance.INTERMEDIATE_60 -> "intermédiaire 60 cm"
    TestDistance.INTERMEDIATE_70 -> "intermédiaire 70 cm"
    TestDistance.INTERMEDIATE_80 -> "intermédiaire 80 cm"
    TestDistance.FAR_200 -> "loin 2 m"
    TestDistance.FAR_300 -> "loin 3 m"
    TestDistance.FAR_400 -> "loin 4 m"
}
