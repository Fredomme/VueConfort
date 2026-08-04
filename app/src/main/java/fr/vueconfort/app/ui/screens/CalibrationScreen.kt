package fr.vueconfort.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.vueconfort.app.calibration.CalibrationChoice
import fr.vueconfort.app.calibration.CalibrationParameter
import fr.vueconfort.app.calibration.CalibrationTrial
import fr.vueconfort.app.calibration.CalibrationViewModel
import fr.vueconfort.app.model.VisualProfile

private const val CALIBRATION_SAMPLE =
    "Lire devrait rester confortable. Choisissez la version qui demande le moins d’effort visuel, sans chercher celle qui paraît simplement la plus nette."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    baseProfile: VisualProfile,
    viewModel: CalibrationViewModel,
    onCalibrationCompleted: (VisualProfile) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(baseProfile.id) {
        if (!uiState.started) {
            viewModel.start(baseProfile)
        }
    }

    LaunchedEffect(uiState.completed) {
        if (uiState.completed) {
            onCalibrationCompleted(uiState.currentProfile)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text("Calibration visuelle")
                },
                navigationIcon = {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            !uiState.started -> {
                LoadingCalibrationContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            uiState.completed -> {
                CalibrationCompletedContent(
                    profile = uiState.currentProfile,
                    confidence = uiState.session.confidenceScore,
                    onRestart = {
                        viewModel.restartFrom(baseProfile)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }

            else -> {
                val trial = uiState.currentTrial

                if (trial != null) {
                    CalibrationTrialContent(
                        trial = trial,
                        currentIndex = uiState.currentTrialIndex,
                        totalCount = uiState.totalTrialCount,
                        progress = uiState.progress,
                        onChoice = { choice, responseTime ->
                            viewModel.choose(
                                choice = choice,
                                responseTimeMillis = responseTime
                            )
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingCalibrationContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Préparation de la calibration…",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CalibrationTrialContent(
    trial: CalibrationTrial,
    currentIndex: Int,
    totalCount: Int,
    progress: Float,
    onChoice: (CalibrationChoice, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val trialStartedAt = remember(currentIndex) {
        System.currentTimeMillis()
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Étape ${currentIndex + 1} sur $totalCount",
            style = MaterialTheme.typography.titleMedium
        )

        LinearProgressIndicator(
            progress = {
                ((currentIndex + 1).toFloat() /
                    totalCount.coerceAtLeast(1).toFloat())
                    .coerceIn(0f, 1f)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = parameterInstruction(trial.parameter),
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            text = "Ne cherchez pas la version la plus nette. Choisissez celle qui nécessite le moins d’effort pour lire.",
            style = MaterialTheme.typography.bodyMedium
        )

        ProfileOptionCard(
            label = "Version A",
            profile = trial.optionA,
            onClick = {
                onChoice(
                    CalibrationChoice.OPTION_A,
                    System.currentTimeMillis() - trialStartedAt
                )
            }
        )

        ProfileOptionCard(
            label = "Version B",
            profile = trial.optionB,
            onClick = {
                onChoice(
                    CalibrationChoice.OPTION_B,
                    System.currentTimeMillis() - trialStartedAt
                )
            }
        )

        OutlinedButton(
            onClick = {
                onChoice(
                    CalibrationChoice.NO_DIFFERENCE,
                    System.currentTimeMillis() - trialStartedAt
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Aucune différence notable")
        }

        Text(
            text = "Progression enregistrée : ${(progress * 100f).toInt()} %",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ProfileOptionCard(
    label: String,
    profile: VisualProfile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(profile.backgroundArgb.toInt())
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Color(profile.foregroundArgb.toInt())
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(
                        (profile.columnWidthPercent / 100f)
                            .coerceIn(0.60f, 1f)
                    )
                    .background(
                        color = Color(
                            profile.backgroundArgb.toInt()
                        ),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(
                        horizontal = profile.horizontalMarginDp.dp,
                        vertical = 18.dp
                    )
            ) {
                Text(
                    text = CALIBRATION_SAMPLE,
                    color = Color(
                        profile.foregroundArgb.toInt()
                    ),
                    fontSize = profile.fontSizeSp.sp,
                    fontWeight = FontWeight(
                        profile.fontWeight.coerceIn(100, 900)
                    ),
                    letterSpacing = profile.letterSpacingSp.sp,
                    lineHeight = (
                        profile.fontSizeSp *
                            profile.lineHeightMultiplier
                        ).sp,
                    textAlign = TextAlign.Start
                )
            }

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 180.dp)
            ) {
                Text("Cette version demande moins d’effort")
            }
        }
    }
}

@Composable
private fun CalibrationCompletedContent(
    profile: VisualProfile,
    confidence: Float,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Calibration terminée",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "Le profil a été adapté selon les versions qui vous ont demandé le moins d’effort.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProfileResultRow(
                    label = "Taille du texte",
                    value = "${profile.fontSizeSp.toInt()} sp"
                )

                ProfileResultRow(
                    label = "Épaisseur",
                    value = profile.fontWeight.toString()
                )

                ProfileResultRow(
                    label = "Espacement des lettres",
                    value = "%.2f sp".format(
                        profile.letterSpacingSp
                    )
                )

                ProfileResultRow(
                    label = "Hauteur de ligne",
                    value = "%.2f".format(
                        profile.lineHeightMultiplier
                    )
                )

                ProfileResultRow(
                    label = "Marge horizontale",
                    value = "${profile.horizontalMarginDp.toInt()} dp"
                )

                ProfileResultRow(
                    label = "Confiance",
                    value = "${(confidence * 100f).toInt()} %"
                )
            }
        }

        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Recommencer la calibration")
        }
    }
}

@Composable
private fun ProfileResultRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun parameterInstruction(
    parameter: CalibrationParameter
): String {
    return when (parameter) {
        CalibrationParameter.FONT_SIZE ->
            "Quelle taille est la plus reposante ?"

        CalibrationParameter.FONT_WEIGHT ->
            "Quelle épaisseur est la plus facile à lire ?"

        CalibrationParameter.LETTER_SPACING ->
            "Quel espacement réduit le mieux l’effort ?"

        CalibrationParameter.LINE_HEIGHT ->
            "Quelle hauteur de ligne facilite le suivi ?"

        CalibrationParameter.CONTRAST ->
            "Quel contraste est le plus confortable ?"

        CalibrationParameter.BACKGROUND ->
            "Quel fond fatigue le moins vos yeux ?"

        CalibrationParameter.COLUMN_WIDTH ->
            "Quelle largeur facilite la lecture ?"

        CalibrationParameter.MARGINS ->
            "Quelles marges rendent le texte plus confortable ?"

        CalibrationParameter.WARMTH ->
            "Quelle température de couleur est la plus douce ?"

        CalibrationParameter.DESATURATION ->
            "Quel niveau de couleur est le moins fatigant ?"
    }
}
