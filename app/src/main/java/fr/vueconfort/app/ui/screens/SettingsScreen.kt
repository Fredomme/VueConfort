package fr.vueconfort.app.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import fr.vueconfort.app.R
import fr.vueconfort.app.magnifier.ScreenMagnifierService
import fr.vueconfort.app.model.AmbientLightLevel
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.AutomationRule
import fr.vueconfort.app.model.AutomationStatus
import fr.vueconfort.app.model.AutomationTrigger
import java.text.DateFormat
import java.util.Date
import java.util.UUID

data class LaunchableApp(val label: String, val packageName: String)

fun isMagnifierServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ScreenMagnifierService::class.java).flattenToString()
    return Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty().split(':').any { it.equals(expected, ignoreCase = true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profiles: List<AssistProfile>,
    rules: List<AutomationRule>,
    status: AutomationStatus,
    onSaveRule: (AutomationRule) -> Unit,
    onDeleteRule: (String) -> Unit,
    onPauseAutomation: (Long?) -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onPrivacy: () -> Unit,
    onRedoSetup: () -> Unit,
    onResetProfiles: () -> Unit,
    onClearHistory: () -> Unit,
    onClearRules: () -> Unit,
    onResetAll: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val apps = remember {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        context.packageManager.queryIntentActivities(intent, 0)
            .map {
                LaunchableApp(
                    it.loadLabel(context.packageManager).toString(),
                    it.activityInfo.packageName
                )
            }
            .filterNot { it.packageName == context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    val lightSensorAvailable = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_LIGHT) != null
    }
    var trigger by remember { mutableStateOf(AutomationTrigger.APPLICATION) }
    var appIndex by remember { mutableIntStateOf(0) }
    var profileIndex by remember { mutableIntStateOf(0) }
    var startHour by remember { mutableIntStateOf(21) }
    var endHour by remember { mutableIntStateOf(7) }
    var daysMask by remember { mutableIntStateOf(0b1111111) }
    var light by remember { mutableStateOf(AmbientLightLevel.LOW) }
    var pendingReset by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val resetProfilesConfirmation = stringResource(R.string.reset_profiles_confirm)
    val deleteHistoryConfirmation = stringResource(R.string.delete_history_confirm)
    val deleteRulesConfirmation = stringResource(R.string.delete_rules_confirm)
    val resetAllConfirmation = stringResource(R.string.reset_all_confirm)

    pendingReset?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingReset = null },
            title = { Text(stringResource(R.string.confirm_reset_title)) },
            text = { Text(pending.first) },
            confirmButton = { Button(onClick = { pending.second(); pendingReset = null }) { Text(stringResource(R.string.confirm)) } },
            dismissButton = { OutlinedButton(onClick = { pendingReset = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automatisation") },
                navigationIcon = {
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) {
                        Text("Retour")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Profil appliqué : ${profiles.firstOrNull { it.id == status.profileId }?.name ?: "Standard"}")
                        Text("${status.source}${status.reason.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
                        if (status.lastAppliedMillis > 0) {
                            Text("Dernière application : ${DateFormat.getDateTimeInstance().format(Date(status.lastAppliedMillis))}")
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { onPauseAutomation(15 * 60_000L) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("15 min", fontSize = 14.sp, maxLines = 1) }
                            OutlinedButton(onClick = { onPauseAutomation(60 * 60_000L) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("1 heure", fontSize = 14.sp, maxLines = 1) }
                            OutlinedButton(onClick = { onPauseAutomation(null) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Manuel", fontSize = 14.sp, maxLines = 1) }
                        }
                        Button(onClick = { onPauseAutomation(0L) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Reprendre les automatismes")
                        }
                    }
                }
            }
            if (!lightSensorAvailable) {
                item {
                    Text("La détection de luminosité n’est pas disponible sur cet appareil.")
                }
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Nouvelle règle")
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            AutomationTrigger.entries.forEach { value ->
                                OutlinedButton(
                                    onClick = { trigger = value },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) { Text(when (value) {
                                    AutomationTrigger.APPLICATION -> "Application"
                                    AutomationTrigger.TIME_RANGE -> "Heure"
                                    AutomationTrigger.AMBIENT_LIGHT -> "Lumière"
                                }, fontSize = 14.sp, maxLines = 1) }
                            }
                        }
                        if (trigger == AutomationTrigger.APPLICATION && apps.isNotEmpty()) {
                            SelectorRow(
                                "Application : ${apps[appIndex.coerceIn(apps.indices)].label}",
                                { appIndex = (appIndex - 1 + apps.size) % apps.size },
                                { appIndex = (appIndex + 1) % apps.size }
                            )
                        }
                        if (trigger == AutomationTrigger.TIME_RANGE) {
                            SelectorRow("Début : %02d:00".format(startHour), { startHour = (startHour + 23) % 24 }, { startHour = (startHour + 1) % 24 })
                            SelectorRow("Fin : %02d:00".format(endHour), { endHour = (endHour + 23) % 24 }, { endHour = (endHour + 1) % 24 })
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf("L","M","M","J","V","S","D").forEachIndexed { index, day ->
                                    OutlinedButton(
                                        onClick = { daysMask = daysMask xor (1 shl index) },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(if (daysMask and (1 shl index) != 0) "✓$day" else day) }
                                }
                            }
                        }
                        if (trigger == AutomationTrigger.AMBIENT_LIGHT) {
                            SelectorRow(
                                "Condition : ${lightLabel(light)}",
                                { light = AmbientLightLevel.entries[(light.ordinal + 2) % 3] },
                                { light = AmbientLightLevel.entries[(light.ordinal + 1) % 3] }
                            )
                        }
                        if (profiles.isNotEmpty()) {
                            SelectorRow(
                                "Profil : ${profiles[profileIndex.coerceIn(profiles.indices)].name}",
                                { profileIndex = (profileIndex - 1 + profiles.size) % profiles.size },
                                { profileIndex = (profileIndex + 1) % profiles.size }
                            )
                        }
                        Button(
                            enabled = profiles.isNotEmpty() &&
                                (trigger != AutomationTrigger.APPLICATION || apps.isNotEmpty()) &&
                                (trigger != AutomationTrigger.AMBIENT_LIGHT || lightSensorAvailable),
                            onClick = {
                                val app = apps.getOrNull(appIndex)
                                val profile = profiles[profileIndex.coerceIn(profiles.indices)]
                                val readableTrigger = when (trigger) {
                                    AutomationTrigger.APPLICATION -> app?.label ?: "Application"
                                    AutomationTrigger.TIME_RANGE -> "%02d:00–%02d:00".format(startHour, endHour)
                                    AutomationTrigger.AMBIENT_LIGHT -> lightLabel(light)
                                }
                                onSaveRule(
                                    AutomationRule(
                                        id = UUID.randomUUID().toString(),
                                        name = readableTrigger,
                                        enabled = true,
                                        priority = 50,
                                        profileId = profile.id,
                                        trigger = trigger,
                                        packageName = app?.packageName.orEmpty(),
                                        startMinutes = startHour * 60,
                                        endMinutes = endHour * 60,
                                        daysMask = daysMask,
                                        lightLevel = light
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Ajouter la règle") }
                    }
                }
            }
            items(rules, key = { it.id }) { rule ->
                RuleCard(rule, profiles, onSaveRule, onDeleteRule)
            }
            item {
                Button(
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Ouvrir les réglages d’accessibilité") }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.information), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.help)) }
                        OutlinedButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about)) }
                        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_policy)) }
                        OutlinedButton(onClick = onRedoSetup, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.redo_setup)) }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.reset_section), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        ResetButton(stringResource(R.string.reset_profiles)) { pendingReset = resetProfilesConfirmation to onResetProfiles }
                        ResetButton(stringResource(R.string.delete_history)) { pendingReset = deleteHistoryConfirmation to onClearHistory }
                        ResetButton(stringResource(R.string.delete_rules)) { pendingReset = deleteRulesConfirmation to onClearRules }
                        ResetButton(stringResource(R.string.reset_all)) { pendingReset = resetAllConfirmation to onResetAll }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResetButton(label: String, action: () -> Unit) {
    OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
}

@Composable
private fun SelectorRow(text: String, previous: () -> Unit, next: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = previous) { Text("‹") }
        Text(text, modifier = Modifier.weight(1f).padding(top = 12.dp))
        OutlinedButton(onClick = next) { Text("›") }
    }
}

@Composable
private fun RuleCard(
    rule: AutomationRule,
    profiles: List<AssistProfile>,
    onSave: (AutomationRule) -> Unit,
    onDelete: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(rule.name)
            Text("Profil : ${profiles.firstOrNull { it.id == rule.profileId }?.name ?: "Indisponible"}")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (rule.enabled) "Règle active" else "Règle désactivée")
                Switch(checked = rule.enabled, onCheckedChange = { onSave(rule.copy(enabled = it)) })
            }
            SelectorRow(
                "Priorité : ${rule.priority}",
                { onSave(rule.copy(priority = (rule.priority - 5).coerceAtLeast(0))) },
                { onSave(rule.copy(priority = (rule.priority + 5).coerceAtMost(100))) }
            )
            OutlinedButton(onClick = { onDelete(rule.id) }, modifier = Modifier.fillMaxWidth()) {
                Text("Supprimer")
            }
        }
    }
}

private fun lightLabel(value: AmbientLightLevel) = when (value) {
    AmbientLightLevel.LOW -> "Faible luminosité"
    AmbientLightLevel.MEDIUM -> "Luminosité moyenne"
    AmbientLightLevel.HIGH -> "Forte luminosité"
}
