package fr.vueconfort.app.assessment

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import fr.vueconfort.app.optical.OpticalSettings
import fr.vueconfort.app.optical.opticalRender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisualAssessmentScreen(
    activeOptical: OpticalSettings,
    onSaveAssessment: (VisualComfortAssessment) -> Unit,
    onSaveProfile: (fr.vueconfort.app.model.AssistProfile) -> Unit,
    onTryProfile: (String) -> Unit,
    onHistory: () -> Unit,
    onBack: () -> Unit
) {
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (previous != null) activity.requestedOrientation = previous
        }
    }

    var stage by remember { mutableIntStateOf(0) }
    var ageRange by remember { mutableStateOf("40 à 59 ans") }
    var wearsCorrection by remember { mutableStateOf(false) }
    var withCorrection by remember { mutableStateOf(false) }
    var distance by remember { mutableStateOf(AssessmentDistance.NEAR) }
    var cardWidthDp by remember { mutableFloatStateOf(324f) }
    var calibrated by remember { mutableStateOf(false) }
    var distanceConfirmed by remember { mutableStateOf(false) }
    var eyeIndex by remember { mutableIntStateOf(0) }
    var trial by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var interruptions by remember { mutableIntStateOf(0) }
    val acuityAnswers = remember { mutableStateListOf<Boolean>() }
    val contrastAnswers = remember { mutableStateListOf<Boolean>() }
    val results = remember { mutableStateMapOf<TestedEye, EyeComfortResult>() }
    var comfortableSp by remember { mutableIntStateOf(34) }
    var overloadScore by remember { mutableIntStateOf(30) }
    var doubleVision by remember { mutableStateOf(false) }
    var distortion by remember { mutableStateOf(false) }
    var darkArea by remember { mutableStateOf(false) }
    var finalAssessment by remember { mutableStateOf<VisualComfortAssessment?>(null) }
    val eyes = listOf(TestedEye.RIGHT, TestedEye.LEFT)
    val metrics = LocalContext.current.resources.displayMetrics
    val physicalFactor = (cardWidthDp * metrics.density) /
        ((85.60f / 25.4f) * metrics.xdpi).coerceAtLeast(1f)

    fun finishEyePhase() {
        val eye = eyes[eyeIndex]
        val base = AcuityTestEngine.result(
            eye, distance.centimeters, withCorrection, acuityAnswers.toList()
        )
        results[eye] = base.copy(
            contrastScore = ContrastTestEngine.score(contrastAnswers),
            overloadScore = overloadScore,
            minimumReadableSp = (comfortableSp - 6).coerceAtLeast(18),
            comfortableTextSp = comfortableSp,
            preferredMagnification = (comfortableSp / 18f).coerceIn(1f, 8f),
            trialCount = base.trialCount + contrastAnswers.size + 4
        )
        acuityAnswers.clear()
        contrastAnswers.clear()
        trial = 0
        if (eyeIndex == 0) {
            eyeIndex = 1
            stage = 2
        } else {
            val eyeResults = results.values.toList()
            val reliability = ReliabilityEngine.calculate(
                calibrated, distanceConfirmed, eyeResults, interruptions
            )
            val assessment = VisualComfortAssessment(
                UUID.randomUUID().toString(), System.currentTimeMillis(), ageRange,
                wearsCorrection, withCorrection, distance.centimeters, physicalFactor,
                calibrated, distanceConfirmed, interruptions,
                results[TestedEye.RIGHT], results[TestedEye.LEFT], null,
                reliability, doubleVision, distortion, darkArea
            )
            finalAssessment = assessment
            onSaveAssessment(assessment)
            stage = 7
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bilan visuel de confort") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Annuler")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(18.dp)
                .opticalRender(activeOptical),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (paused) {
                Text("Bilan en pause", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { paused = false }, modifier = Modifier.fillMaxWidth()) {
                    Text("Reprendre")
                }
                return@Column
            }
            if (stage in 2..6) {
                OutlinedButton(
                    onClick = { paused = true; interruptions++ },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Mettre en pause") }
            }
            when (stage) {
                0 -> PreparationStep(
                    ageRange, wearsCorrection, withCorrection,
                    { ageRange = it }, { wearsCorrection = it }, { withCorrection = it },
                    { stage = 1 }, onHistory
                )
                1 -> PhysicalCalibrationStep(
                    cardWidthDp, { cardWidthDp = it }, calibrated,
                    { calibrated = it }, { stage = 2 }
                )
                2 -> EyeAndDistanceStep(eyes[eyeIndex], distance, {
                    distance = it
                }) {
                    distanceConfirmed = true
                    stage = 3
                }
                3 -> AcuityStep(
                    eye = eyes[eyeIndex],
                    trial = trial,
                    physicalFactor = physicalFactor,
                    onAnswer = { answer ->
                        acuityAnswers += answer
                        trial++
                        if (trial >= 18 || acuityAnswers.takeLast(6).count { !it } >= 4) {
                            trial = 0
                            stage = 4
                        }
                    }
                )
                4 -> ReadingComfortStep(
                    comfortableSp,
                    { comfortableSp = it },
                    { stage = 5; trial = 0 }
                )
                5 -> ContrastStep(trial) { answer ->
                    contrastAnswers += answer
                    trial++
                    if (trial >= 8) {
                        trial = 0
                        stage = 6
                    }
                }
                6 -> OverloadStep(
                    overloadScore,
                    { overloadScore = it },
                    doubleVision, distortion, darkArea,
                    { doubleVision = it }, { distortion = it }, { darkArea = it },
                    { finishEyePhase() }
                )
                7 -> finalAssessment?.let { assessment ->
                    ResultStep(
                        assessment,
                        AssessmentProfileEngine.recommend(assessment),
                        onSaveProfile,
                        onTryProfile,
                        onHistory
                    )
                }
            }
        }
    }
}

@Composable
private fun PreparationStep(
    age: String, wears: Boolean, testedWith: Boolean,
    onAge: (String) -> Unit, onWears: (Boolean) -> Unit,
    onTestedWith: (Boolean) -> Unit, onNext: () -> Unit, onHistory: () -> Unit
) {
    Text("Avant de commencer", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("Testez un œil à la fois. Couvrez l’autre sans appuyer. Évitez les reflets et choisissez une luminosité confortable.")
    Text("Interrompez en cas de douleur, vertige, vision double ou malaise.")
    Text("Ce bilan est un outil de confort et de dépistage. Il ne constitue ni un diagnostic ni une ordonnance.")
    listOf("Moins de 40 ans", "40 à 59 ans", "60 ans ou plus").forEach {
        OutlinedButton(onClick = { onAge(it) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (age == it) "✓ $it" else it)
        }
    }
    LabeledSwitch("Je porte des lunettes ou lentilles", wears, onWears)
    LabeledSwitch("Je fais le bilan avec ma correction habituelle", testedWith, onTestedWith)
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Continuer") }
    OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Historique des bilans") }
}

@Composable
private fun PhysicalCalibrationStep(
    width: Float, onWidth: (Float) -> Unit, confirmed: Boolean,
    onConfirmed: (Boolean) -> Unit, onNext: () -> Unit
) {
    Text("Calibration physique", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("Placez une carte bancaire contre l’écran et alignez le rectangle avec sa largeur de 85,60 mm.")
    Box(Modifier.width(width.dp).height(80.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.fillMaxSize()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Largeur de la carte") } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = { onWidth((width - 4).coerceAtLeast(220f)) }) { Text("−") }
        OutlinedButton(onClick = { onWidth((width + 4).coerceAtMost(420f)) }) { Text("+") }
    }
    LabeledSwitch("La largeur est alignée", confirmed, onConfirmed)
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
        Text(if (confirmed) "Utiliser cette calibration" else "Continuer sans calibration manuelle")
    }
}

@Composable
private fun EyeAndDistanceStep(
    eye: TestedEye, distance: AssessmentDistance,
    onDistance: (AssessmentDistance) -> Unit, onConfirmed: () -> Unit
) {
    Text(if (eye == TestedEye.RIGHT) "Œil droit" else "Œil gauche", fontSize = 34.sp, fontWeight = FontWeight.Bold)
    Text(if (eye == TestedEye.RIGHT) "Couvrez doucement l’œil gauche." else "Couvrez doucement l’œil droit.")
    AssessmentDistance.entries.forEach {
        OutlinedButton(onClick = { onDistance(it) }, modifier = Modifier.fillMaxWidth()) {
            Text("${if (distance == it) "✓ " else ""}${if (it == AssessmentDistance.NEAR) "Vision de près — 40 cm" else "Vision intermédiaire — 70 cm"}")
        }
    }
    Text("Placez le téléphone à la distance choisie. L’application ne peut pas mesurer cette distance sans caméra.")
    Text("3… 2… 1…", fontSize = 30.sp)
    Button(onClick = onConfirmed, modifier = Modifier.fillMaxWidth()) { Text("Le téléphone est à la bonne distance") }
}

@Composable
private fun AcuityStep(
    eye: TestedEye, trial: Int, physicalFactor: Float, onAnswer: (Boolean) -> Unit
) {
    val orientation = (trial * 3 + eye.ordinal) % 4
    val level = (trial / 3).coerceIn(AcuityTestEngine.optotypeSizesMm.indices)
    val mm = AcuityTestEngine.optotypeSizesMm[level]
    val sizeSp = (mm * 12f * physicalFactor).coerceIn(24f, 110f)
    Text("Indiquez l’orientation du E", fontSize = 26.sp, fontWeight = FontWeight.Bold)
    Text(
        "E", fontSize = sizeSp.sp, fontWeight = FontWeight.Black,
        modifier = Modifier.graphicsLayer { rotationZ = orientation * 90f }
    )
    val arrows = listOf("→", "↓", "←", "↑")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        arrows.forEachIndexed { index, arrow ->
            Button(onClick = { onAnswer(index == orientation) }, modifier = Modifier.weight(1f).height(72.dp)) {
                Text(arrow, fontSize = 30.sp)
            }
        }
    }
    Text("Essai ${trial + 1}")
}

@Composable
private fun ReadingComfortStep(size: Int, onSize: (Int) -> Unit, onNext: () -> Unit) {
    Text("Lecture réelle", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("VueConfort recherche une présentation confortable pour lire des messages et des documents.", fontSize = size.sp, lineHeight = (size * 1.4f).sp)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { onSize((size - 2).coerceAtLeast(18)) }, modifier = Modifier.weight(1f)) { Text("Plus petit") }
        OutlinedButton(onClick = { onSize((size + 2).coerceAtMost(56)) }, modifier = Modifier.weight(1f)) { Text("Plus grand") }
    }
    listOf("Lisible sans effort", "Lisible avec effort", "Difficile", "Illisible").forEachIndexed { index, label ->
        Button(
            onClick = { onSize(size + index * 2); onNext() },
            modifier = Modifier.fillMaxWidth()
        ) { Text(label) }
    }
}

@Composable
private fun ContrastStep(trial: Int, onAnswer: (Boolean) -> Unit) {
    val orientation = (trial * 5 + 1) % 4
    val alpha = (1f - trial * 0.1f).coerceAtLeast(0.25f)
    Text("Contraste de confort", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("E", fontSize = 70.sp, color = Color.Black.copy(alpha = alpha), modifier = Modifier.graphicsLayer { rotationZ = orientation * 90f })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("→", "↓", "←", "↑").forEachIndexed { index, arrow ->
            Button(onClick = { onAnswer(index == orientation) }, modifier = Modifier.weight(1f).height(72.dp)) { Text(arrow, fontSize = 30.sp) }
        }
    }
}

@Composable
private fun OverloadStep(
    score: Int, onScore: (Int) -> Unit,
    doubleVision: Boolean, distortion: Boolean, darkArea: Boolean,
    onDouble: (Boolean) -> Unit, onDistortion: (Boolean) -> Unit,
    onDark: (Boolean) -> Unit, onFinish: () -> Unit
) {
    Text("Surcharge visuelle", fontSize = 30.sp, fontWeight = FontWeight.Bold)
    Text("E  ○  □  E  △  ○  □  E", fontSize = 34.sp, textAlign = TextAlign.Center)
    Text("Un élément entouré de formes est-il plus difficile à reconnaître qu’un élément isolé ?")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onScore(25) }, modifier = Modifier.weight(1f)) { Text("Non") }
        Button(onClick = { onScore(70) }, modifier = Modifier.weight(1f)) { Text("Oui") }
    }
    Text("Score actuel : $score / 100")
    LabeledSwitch("Vision double récente", doubleVision, onDouble)
    LabeledSwitch("Déformation récente", distortion, onDistortion)
    LabeledSwitch("Zone sombre ou manquante", darkArea, onDark)
    Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Terminer cet œil") }
}

@Composable
private fun ResultStep(
    assessment: VisualComfortAssessment,
    recommendation: AssessmentRecommendation,
    onSaveProfile: (fr.vueconfort.app.model.AssistProfile) -> Unit,
    onTryProfile: (String) -> Unit,
    onHistory: () -> Unit
) {
    Text("Résultat du bilan", fontSize = 32.sp, fontWeight = FontWeight.Bold)
    EyeResultCard("Œil droit", assessment.right)
    EyeResultCard("Œil gauche", assessment.left)
    Text("Différence entre les yeux : ${recommendation.eyeDifference} points")
    Text("Fiabilité : ${when (assessment.reliability) { ResultReliability.LOW -> "faible"; ResultReliability.MEDIUM -> "moyenne"; ResultReliability.HIGH -> "élevée" }}")
    if (recommendation.professionalCheckRecommended) {
        Text("Une différence notable ou une performance faible a été observée. Un contrôle auprès d’un professionnel de la vision est recommandé.", fontWeight = FontWeight.Bold)
    }
    if (recommendation.urgentAdvice) {
        Text("Pour un symptôme soudain ou important, consultez rapidement un professionnel.", fontWeight = FontWeight.Bold)
    }
    Text("Profil proposé : ${recommendation.profile.magnificationScale}× — panneau ${(recommendation.profile.overlayAlpha * 100).toInt()} %")
    recommendation.reasons.forEach { Text("• $it") }
    Button(
        onClick = {
            onSaveProfile(recommendation.profile)
            onTryProfile(recommendation.profile.id)
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Essayer maintenant") }
    OutlinedButton(onClick = { onSaveProfile(recommendation.profile) }, modifier = Modifier.fillMaxWidth()) {
        Text("Enregistrer comme profil")
    }
    OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("Voir l’historique") }
    Text("Estimation de dépistage — non diagnostique")
}

@Composable
private fun EyeResultCard(label: String, result: EyeComfortResult?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontWeight = FontWeight.Bold)
            if (result == null) Text("Non réalisé") else {
                Text("Performance interne : ${result.acuityScore} / 100")
                Text("Plus petit symbole : ${result.smallestOptotypeMm} mm")
                Text("Erreurs : ${result.errorRatePercent} %")
                Text("Contraste : ${result.contrastScore} / 100")
                Text("Surcharge : ${result.overloadScore} / 100")
                Text("Taille confortable : ${result.comfortableTextSp} sp")
            }
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
