package fr.vueconfort.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.vueconfort.app.core.VueConfortCoreState
import fr.vueconfort.app.magnifier.ScreenMagnifierService
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreStatusScreen(onBack: () -> Unit, onContinue: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            refresh++
        }
    }
    @Suppress("UNUSED_EXPRESSION") refresh
    val accessibility = VueConfortCoreState.isAccessibilityEnabled(context)
    val notifications = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("État de VueConfort") },
                navigationIcon = { OutlinedButton(onClick = onBack) { Text("Retour") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusLine("Accessibilité", accessibility, if (accessibility) null else "Autoriser") {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            StatusLine("Overlay", VueConfortCoreState.overlayActive, null) {}
            StatusLine("Grossissement", VueConfortCoreState.magnificationActive, null) {}
            StatusLine("Extraction", VueConfortCoreState.extractionAvailable, null) {}
            StatusLine("Lecteur", VueConfortCoreState.readerActive, null) {}
            StatusLine("Profils automatiques", VueConfortCoreState.automaticProfilesActive, null) {}
            StatusLine("Notifications", notifications, if (notifications) null else "Autoriser") {
                if (Build.VERSION.SDK_INT >= 33) {
                    activity?.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4107)
                }
            }
            OutlinedButton(
                onClick = { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Réglages batterie") }
            Button(
                onClick = { ScreenMagnifierService.handleExternalAction(ScreenMagnifierService.ACTION_READ) },
                enabled = accessibility,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Test réel : ouvrir le lecteur") }
            if (accessibility && notifications) {
                Text("Installation indispensable terminée. Choisissez un profil puis utilisez VueConfort dans vos applications.")
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuer vers VueConfort")
                }
            } else {
                Text("L’assistant reste incomplet tant que l’accessibilité et les notifications requises ne sont pas actives.")
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, active: Boolean, actionLabel: String?, action: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column { Text(label); Text(if (active) "Actif" else "Inactif") }
            if (actionLabel != null) OutlinedButton(onClick = action) { Text(actionLabel) }
        }
    }
}
