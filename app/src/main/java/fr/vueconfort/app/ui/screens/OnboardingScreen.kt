package fr.vueconfort.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.vueconfort.app.R
import fr.vueconfort.app.core.VueConfortCoreState
import fr.vueconfort.app.model.AssistProfile

@Composable
fun WelcomeScreen(
    onStart: () -> Unit,
    onDiscover: () -> Unit,
    onPrivacy: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(stringResource(R.string.app_name), style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        Text(stringResource(R.string.welcome_subtitle), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.welcome_summary))
        Card { Text(stringResource(R.string.welcome_limits), Modifier.padding(16.dp)) }
        Card { Text(stringResource(R.string.welcome_local_data), Modifier.padding(16.dp)) }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start)) }
        OutlinedButton(onClick = onDiscover, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.discover_features)) }
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_policy)) }
    }
}

private enum class SetupStep { PRESENTATION, ACCESSIBILITY, NOTIFICATIONS, BATTERY, PROFILE, OVERLAY, PRACTICE, SUMMARY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidedSetupScreen(
    profiles: List<AssistProfile>,
    activeProfile: AssistProfile,
    onActivateProfile: (String) -> Unit,
    onComplete: () -> Unit,
    onTemporaryExit: () -> Unit,
    onBackFromFirst: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var stepIndex by remember { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    var practiceDone by remember { mutableStateOf(false) }
    val steps = SetupStep.entries
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    @Suppress("UNUSED_EXPRESSION") refresh
    val accessibility = VueConfortCoreState.isAccessibilityEnabled(context)
    val notifications = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val power = context.getSystemService(PowerManager::class.java)
    val batteryOptimized = !power.isIgnoringBatteryOptimizations(context.packageName)
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val current = steps[stepIndex]

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.setup_title)) }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.setup_progress, stepIndex + 1, steps.size))
            when (current) {
                SetupStep.PRESENTATION -> SetupCard(stringResource(R.string.setup_presentation_title), stringResource(R.string.setup_presentation_body), true)
                SetupStep.ACCESSIBILITY -> SetupCard(
                    stringResource(R.string.setup_accessibility_title), stringResource(R.string.setup_accessibility_body), accessibility,
                    stringResource(R.string.open_accessibility_settings)
                ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                SetupStep.NOTIFICATIONS -> SetupCard(
                    stringResource(R.string.setup_notifications_title), stringResource(R.string.setup_notifications_body), notifications,
                    stringResource(R.string.authorize)
                ) {
                    if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                SetupStep.BATTERY -> SetupCard(
                    stringResource(R.string.setup_battery_title), stringResource(R.string.setup_battery_body), !batteryOptimized,
                    stringResource(R.string.open_battery_settings)
                ) {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    }.getOrElse { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
                }
                SetupStep.PROFILE -> {
                    SetupCard(stringResource(R.string.setup_profile_title), stringResource(R.string.setup_profile_body), activeProfile.id.isNotBlank())
                    profiles.filter { it.id in setOf(AssistProfile.STANDARD_ID, AssistProfile.SMALL_TEXT_ID, AssistProfile.READING_ID, AssistProfile.LONG_READING_ID, AssistProfile.OUTDOOR_ID, AssistProfile.CUSTOM_ID) }
                        .forEach { profile ->
                            OutlinedButton(onClick = { onActivateProfile(profile.id) }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (profile.id == activeProfile.id) "✓ ${profile.name} — ${profile.description}" else "${profile.name} — ${profile.description}")
                            }
                        }
                }
                SetupStep.OVERLAY -> SetupCard(
                    stringResource(R.string.setup_overlay_title), stringResource(R.string.setup_overlay_body), VueConfortCoreState.overlayActive,
                    if (accessibility) null else stringResource(R.string.open_accessibility_settings)
                ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                SetupStep.PRACTICE -> {
                    SetupCard(stringResource(R.string.setup_practice_title), stringResource(R.string.setup_practice_body), practiceDone)
                    Button(onClick = { practiceDone = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.practice_done)) }
                }
                SetupStep.SUMMARY -> {
                    SetupCard(stringResource(R.string.setup_summary_title), stringResource(R.string.setup_summary_body), true)
                    StatusText(stringResource(R.string.accessibility), accessibility)
                    StatusText(stringResource(R.string.notifications), notifications)
                    Text(stringResource(R.string.battery_status, if (batteryOptimized) stringResource(R.string.recommended_action_pending) else stringResource(R.string.applied)))
                    Text(stringResource(R.string.active_profile_format, activeProfile.name))
                    StatusText(stringResource(R.string.floating_bar), VueConfortCoreState.overlayActive)
                    Text(stringResource(R.string.quick_tile_unknown))
                    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.finish)) }
                }
            }
            if (current != SetupStep.SUMMARY) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { if (stepIndex == 0) onBackFromFirst() else stepIndex-- },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.previous)) }
                    Button(onClick = { stepIndex++ }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.next)) }
                }
                OutlinedButton(onClick = onTemporaryExit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.exit_temporarily)) }
            }
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, valid: Boolean, actionLabel: String? = null, action: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text(body)
            Text(if (valid) stringResource(R.string.status_validated) else stringResource(R.string.status_not_validated))
            if (actionLabel != null && !valid) Button(onClick = action) { Text(actionLabel) }
        }
    }
}

@Composable
private fun StatusText(label: String, active: Boolean) {
    Text("$label : ${stringResource(if (active) R.string.active else R.string.inactive)}")
}
