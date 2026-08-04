package fr.vueconfort.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    InfoScaffold(stringResource(R.string.help), onBack) {
        HelpSection(stringResource(R.string.help_getting_started), stringResource(R.string.help_getting_started_body))
        HelpSection(stringResource(R.string.help_floating_bar), stringResource(R.string.help_floating_bar_body))
        HelpSection(stringResource(R.string.help_read_other_app), stringResource(R.string.help_read_other_app_body))
        HelpSection(stringResource(R.string.help_automatic_profiles), stringResource(R.string.help_automatic_profiles_body))
        HelpSection(stringResource(R.string.help_visual_tests), stringResource(R.string.help_visual_tests_body))
        HelpSection(stringResource(R.string.issue_bar_missing), stringResource(R.string.issue_bar_missing_body))
        HelpSection(stringResource(R.string.issue_zoom), stringResource(R.string.issue_zoom_body))
        HelpSection(stringResource(R.string.issue_no_text), stringResource(R.string.issue_no_text_body))
        HelpSection(stringResource(R.string.issue_service_stops), stringResource(R.string.issue_service_stops_body))
        HelpSection(stringResource(R.string.issue_notifications), stringResource(R.string.issue_notifications_body))
        HelpSection(stringResource(R.string.issue_samsung), stringResource(R.string.issue_samsung_body))
        HelpSection(stringResource(R.string.issue_tile), stringResource(R.string.issue_tile_body))
        HelpSection(stringResource(R.string.issue_automation), stringResource(R.string.issue_automation_body))
        Button(
            onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.open_accessibility_settings)) }
        OutlinedButton(
            onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.open_settings)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, onHelp: () -> Unit, onPrivacy: () -> Unit, onResetWelcome: () -> Unit) {
    val context = LocalContext.current
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    val version = packageInfo.versionName ?: "?"
    val code = if (Build.VERSION.SDK_INT >= 28) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
    InfoScaffold(stringResource(R.string.about), onBack) {
        HelpSection(stringResource(R.string.app_name), stringResource(R.string.about_description))
        Text(stringResource(R.string.version_format, version, code))
        Text(stringResource(R.string.platform_format, "Android ${Build.VERSION.RELEASE}"))
        Text(stringResource(R.string.medical_limits))
        Text(stringResource(R.string.about_privacy))
        Text(stringResource(R.string.open_source_licenses))
        Text(stringResource(R.string.contact_configurable))
        if (BuildConfig.DEBUG) Text("Debug · ${BuildConfig.BUILD_TYPE} · SDK ${Build.VERSION.SDK_INT}")
        Button(onClick = {
            val manager = context.getSystemService(ClipboardManager::class.java)
            manager.setPrimaryClip(ClipData.newPlainText("VueConfort", "VueConfort $version ($code) · Android ${Build.VERSION.SDK_INT}"))
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.copy_technical_info)) }
        OutlinedButton(onClick = onHelp, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.help)) }
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.privacy_policy)) }
        OutlinedButton(onClick = onResetWelcome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset_welcome)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    InfoScaffold(stringResource(R.string.privacy_policy), onBack) {
        HelpSection(stringResource(R.string.privacy_data_title), stringResource(R.string.privacy_data_body))
        HelpSection(stringResource(R.string.privacy_storage_title), stringResource(R.string.privacy_storage_body))
        HelpSection(stringResource(R.string.privacy_accessibility_title), stringResource(R.string.privacy_accessibility_body))
        HelpSection(stringResource(R.string.privacy_tests_title), stringResource(R.string.privacy_tests_body))
        HelpSection(stringResource(R.string.privacy_rights_title), stringResource(R.string.privacy_rights_body))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }, navigationIcon = { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() } } }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium); Text(body) } }
}
