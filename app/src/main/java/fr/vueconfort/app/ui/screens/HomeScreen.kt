package fr.vueconfort.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
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
    onVisualAssessment: () -> Unit,
    onReading: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onCoreStatus: () -> Unit,
    onHelp: () -> Unit,
    onMagnifierSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.home_summary),
                style = MaterialTheme.typography.bodyLarge
            )

            ProfileStatusCard(
                profile = profile
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Button(
                onClick = {
                    if (
                        VueConfortCoreState
                            .isAccessibilityEnabled(context)
                    ) {
                        ScreenMagnifierService
                            .handleExternalAction(
                                ScreenMagnifierService
                                    .ACTION_MAGNIFIER_ENABLE
                            )
                    } else {
                        onMagnifierSetup()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(
                        R.string.home_magnifier_primary
                    )
                )
            }

            Button(
                onClick = onVisualAssessment,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.visual_assessment))
            }

            Button(
                onClick = onCalibration,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (profile.calibrated) {
                        stringResource(R.string.redo_calibration)
                    } else {
                        stringResource(R.string.start_calibration)
                    }
                )
            }

            OutlinedButton(
                onClick = onQuestionnaire,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.visual_questionnaire))
            }

            OutlinedButton(
                onClick = onReading,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.optimized_reading))
            }

            OutlinedButton(
                onClick = onProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.my_profile))
            }

            OutlinedButton(
                onClick = onCoreStatus,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.vueconfort_status))
            }

            OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(R.string.help))
            }

            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.settings))
            }

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = stringResource(R.string.medical_limits),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProfileStatusCard(
    profile: VisualProfile
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (profile.calibrated) {
                    stringResource(R.string.calibrated_profile)
                } else {
                    stringResource(R.string.default_profile)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = stringResource(R.string.text_size_format, profile.fontSizeSp.toInt()),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.weight_format, profile.fontWeight),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = stringResource(R.string.line_spacing_format, profile.lineHeightMultiplier),
                style = MaterialTheme.typography.bodyMedium
            )

            if (profile.calibrated) {
                Text(
                    text = stringResource(R.string.confidence_format, (profile.calibrationConfidence * 100f).toInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
