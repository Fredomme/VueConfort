package fr.vueconfort.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.optical.opticalRender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    profiles: List<AssistProfile>,
    activeProfile: AssistProfile,
    onActivate: (String) -> Unit,
    onCreate: () -> Unit,
    onDuplicate: (AssistProfile) -> Unit,
    onSave: (AssistProfile) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    onOpticalSettings: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profils visuels") },
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
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                    Text("Créer un profil utilisateur")
                }
                OutlinedButton(onClick = onOpticalSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("Réglages optiques expérimentaux")
                }
            }
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    active = profile.id == activeProfile.id,
                    onActivate = { onActivate(profile.id) },
                    onDuplicate = { onDuplicate(profile) },
                    onSave = onSave,
                    onDelete = { onDelete(profile.id) },
                    onRestore = { onRestore(profile.id) }
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: AssistProfile,
    active: Boolean,
    onActivate: () -> Unit,
    onDuplicate: () -> Unit,
    onSave: (AssistProfile) -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (profile.predefined) {
                Text("${if (active) "✓ " else ""}${profile.name}")
            } else {
                OutlinedTextField(
                    value = profile.name,
                    onValueChange = { onSave(profile.copy(name = it.take(40))) },
                    label = { Text(if (active) "Nom — profil actif" else "Nom") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            Text(profile.description)
            Text(
                "Aperçu VueConfort — Aa 123 Il1 O0",
                modifier = Modifier.fillMaxWidth().opticalRender(profile.optical)
            )
            Text(if (profile.optical.enabled) "Effets locaux actifs" else "Effets locaux neutres")
            Text("Grossissement : ${profile.magnificationScale}×")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        onSave(
                            profile.copy(
                                magnificationScale =
                                    (profile.magnificationScale - 0.5f).coerceAtLeast(1f)
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("−") }
                OutlinedButton(
                    onClick = {
                        onSave(
                            profile.copy(
                                magnificationScale =
                                    (profile.magnificationScale + 0.5f).coerceAtMost(8f)
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("+") }
            }
            SettingSwitch("Grossissement actif", profile.magnificationEnabled) {
                onSave(profile.copy(magnificationEnabled = it))
            }
            SettingSwitch("Panneau verrouillé", profile.locked) {
                onSave(profile.copy(locked = it))
            }
            Text("Visibilité du panneau : ${(profile.overlayAlpha * 100).toInt()} %")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.62f, 0.82f, 1f).forEach { alpha ->
                    OutlinedButton(
                        onClick = { onSave(profile.copy(overlayAlpha = alpha)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("${(alpha * 100).toInt()} %") }
                }
            }
            if (!active) {
                Button(onClick = onActivate, modifier = Modifier.fillMaxWidth()) {
                    Text("Activer ce profil")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                    Text("Dupliquer")
                }
                if (profile.predefined) {
                    OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                        Text("Restaurer")
                    }
                } else {
                    OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text("Supprimer")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
