package fr.vueconfort.app.orchestration

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import fr.vueconfort.app.data.VisualProfileRepository
import fr.vueconfort.app.nativevision.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.File

/** Runs inside the ordinary preview process: instrumentation must not restart its accessibility service. */
class PublicMagnificationRecipeActivity : ComponentActivity() {
    private lateinit var status: TextView
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "fr.vueconfort.app.preview")
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 80, 36, 40)
        }
        status = TextView(this).apply { textSize = 20f }
        layout.addView(TextView(this).apply {
            text = "VueConfort · essai public\n\nChaque détail compte.\n0123456789\n\nAgrandissement bref puis retour au réglage initial."
            textSize = 24f
        })
        layout.addView(status)
        layout.addView(Button(this).apply {
            text = "Ouvrir l’accessibilité"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        layout.addView(Button(this).apply {
            text = "Vérifier l’agrandissement"
            setOnClickListener { if (!running) lifecycleScope.launch { runRecipe() } }
        })
        layout.addView(Button(this).apply {
            text = "Vérifier les commandes habituelles"
            setOnClickListener { if (!running) lifecycleScope.launch { runLegacyRecipe() } }
        })
        layout.addView(Button(this).apply {
            text = "Reprendre la restauration de l’essai"
            setOnClickListener { if (!running) lifecycleScope.launch { resumeRecipeRestoration() } }
        })
        layout.addView(Button(this).apply {
            text = "Terminer et désactiver le service d’essai"
            setOnClickListener { if (!running && !NativeMagnificationSession(this@PublicMagnificationRecipeActivity).hasPendingRestoration()) {
                fr.vueconfort.app.magnifier.ScreenMagnifierService.handleExternalAction(
                    fr.vueconfort.app.magnifier.ScreenMagnifierService.ACTION_CLOSE)
                finish()
            } }
        })
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        if (!running) status.text = if (AndroidNativeVisionAdapter().isControllerConnected())
            "Service connecté. L’essai est disponible." else "Activez la loupe VueConfort Aperçu dans Android, puis revenez."
    }

    private suspend fun runRecipe() {
        running = true
        val adapter = AndroidNativeVisionAdapter()
        val session = NativeMagnificationSession(this)
        val before = adapter.readMagnificationState()
        val report = JSONObject().put("schema", 1).put("recipe", "PUBLIC_APP_PROCESS")
            .put("before", before?.toString()).put("instrumentation", false)
            .put("developmentPermissionGranted", checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            .put("physicalEffectObserved", "NOT_RECORDED")
        var owned = false
        try {
            check(!session.hasPendingRestoration()) { "Une restauration précédente est en attente ; essai refusé." }
            check(before?.restorable == true) { "État Android incomplet ou service non connecté ; aucune modification." }
            val repository = VisualProfileRepository(this)
            val profileBefore = repository.equalizerProfile.first()
            val requested = NativeVisionRequestedState(mapOf(NativeVisionCapability.MAGNIFICATION to
                NativeVisionValue.Magnification(true, if (before.scale == 1.8f) 2f else 1.8f,
                    before.centerX, before.centerY, NativeMagnificationMode.FULLSCREEN)),
                1, System.currentTimeMillis(), "PUBLIC_PHYSICAL_RECIPE")
            val capabilities = NativeVisionCapabilityResolver().resolve(
                NativeVisionEnvironment(this).snapshot(NativeVisionVariant.COMMERCIAL, false))
            val profile = (profileBefore ?: fr.vueconfort.app.equalizer.EqualizerProfile()).copy(
                nativeVision = NativeVisionProfile(enabled = true, requested = requested))
            val renderPlan = VisionRuntime.plan(VisionProfileSnapshot(profile), capabilities,
                "public-physical-recipe", pendingNativeCommand = true)
            val step = renderPlan.transformations.single()
            report.put("planDisposition", step.disposition.name).put("engine", step.engine?.engineId)
            check(step.canExecuteAutomatically) { step.reason }
            owned = true
            val result = session.apply(requested).single()
            report.put("orchestratorObservedActive", VisionRuntime.observe(this, renderPlan).anyActive)
            report.put("result", result.status.name).put("reason", result.reason)
                .put("applied", result.applied?.toString()).put("confirmation", result.confirmation.name)
            status.text = "Agrandissement : ${result.status}. Retour au réglage initial dans quelques secondes."
            delay(4_000)
            report.put("profileUnchanged", profileBefore == repository.equalizerProfile.first())
        } catch (failure: Exception) {
            report.put("error", failure.message ?: failure.javaClass.simpleName)
        } finally {
            withContext(NonCancellable) {
                if (owned) {
                    val restored = session.restore(2)
                    val actual = adapter.readMagnificationState()
                    report.put("restored", restored.success && !restored.pending && actual != null && before?.matches(actual) == true)
                        .put("pendingRestoration", restored.pending).put("after", actual?.toString())
                } else report.put("noCommandSent", true)
                File(filesDir, "orchestrator-public-magnification.json").writeText(report.toString(2))
                status.text = if (report.optBoolean("restored") && report.optString("result") == "APPLIED_AUTO")
                    "Essai terminé. Agrandissement confirmé et réglage initial restauré." else
                    "Essai non validé. ${report.optString("error", report.optString("reason"))}"
                running = false
            }
        }
    }

    private suspend fun runLegacyRecipe() {
        running = true
        val adapter = AndroidNativeVisionAdapter()
        val repository = VisualProfileRepository(this)
        val before = adapter.readMagnificationState()
        val report = JSONObject().put("recipe", "EXISTING_LOUPE_COMMANDS").put("before", before?.toString())
        var started = false
        try {
            check(before?.enabled == false && before.restorable) { "Un état initial éteint et connu est nécessaire." }
            check(!NativeMagnificationSession(this).hasPendingRestoration()) { "Une restauration est en attente." }
            val loupe = repository.activeAssistProfile.first()
            val equalizer = repository.equalizerProfile.first()
            started = true
            fr.vueconfort.app.magnifier.ScreenMagnifierService.handleExternalAction(
                fr.vueconfort.app.magnifier.ScreenMagnifierService.ACTION_MAGNIFIER_ENABLE)
            delay(1_500)
            val active = adapter.readMagnificationState()
            report.put("active", active?.enabled == true).put("activeSnapshot", active?.toString())
            status.text = "Commandes habituelles de la loupe · retour imminent au réglage initial."
            delay(2_000)
            report.put("loupeProfileUnchanged", loupe == repository.activeAssistProfile.first())
                .put("equalizerProfileUnchanged", equalizer == repository.equalizerProfile.first())
        } catch (failure: Exception) { report.put("error", failure.message) }
        finally {
            withContext(NonCancellable) {
                if (started && adapter.readMagnificationState()?.enabled == true) {
                    fr.vueconfort.app.magnifier.ScreenMagnifierService.handleExternalAction(
                        fr.vueconfort.app.magnifier.ScreenMagnifierService.ACTION_PAUSE)
                    delay(1_000)
                }
                val after = adapter.readMagnificationState()
                report.put("after", after?.toString()).put("restored", after != null && before?.matches(after) == true)
                File(filesDir, "orchestrator-legacy-loupe.json").writeText(report.toString(2))
                status.text = if (report.optBoolean("active") && report.optBoolean("restored"))
                    "Loupe habituelle vérifiée, état initial restauré." else "Vérification de la loupe non validée."
                running = false
            }
        }
    }

    /** Explicit recovery is restricted to the exact partial state recorded by this preview recipe. */
    private suspend fun resumeRecipeRestoration() {
        running = true
        val report = JSONObject().put("recipe", "EXPLICIT_PUBLIC_RECIPE_RECOVERY")
        try {
            val interrupted = JSONObject(File(filesDir, "orchestrator-public-magnification.json").readText())
            check(interrupted.optBoolean("pendingRestoration")) { "Aucune restauration interrompue enregistrée." }
            val expected = requireNotNull(AndroidNativeVisionAdapter().readMagnificationState())
            check(interrupted.getString("after") == expected.toString()) { "Le réglage a changé depuis l’essai ; reprise refusée." }
            val outcome = withContext(NonCancellable) { NativeMagnificationSession(this@PublicMagnificationRecipeActivity)
                .resumeRestoration(3, expected) }
            report.put("success", outcome.success).put("pending", outcome.pending)
                .put("after", AndroidNativeVisionAdapter().readMagnificationState()?.toString())
                .put("reason", outcome.results.joinToString { it.reason })
            status.text = if (outcome.success && !outcome.pending) "Restauration terminée, état initial retrouvé."
                else "Restauration encore en attente."
        } catch (failure: Exception) {
            report.put("error", failure.message)
            status.text = "Reprise refusée : ${failure.message}"
        } finally {
            File(filesDir, "orchestrator-public-recovery.json").writeText(report.toString(2))
            running = false
        }
    }
}
