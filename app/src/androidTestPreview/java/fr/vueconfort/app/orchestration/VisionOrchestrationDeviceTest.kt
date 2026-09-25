package fr.vueconfort.app.orchestration

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.MainActivity
import fr.vueconfort.app.data.VisualProfileRepository
import fr.vueconfort.app.equalizer.*
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.nativevision.*
import fr.vueconfort.app.ui.screens.HomeScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Real preview process and Compose draw callbacks; all requests are synthetic, never persisted. */
class VisionOrchestrationDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Before fun requireOrdinaryCommercialProcess() {
        check(rule.activity.packageName == "fr.vueconfort.app.preview")
        check(!BuildConfig.NATIVE_VISION_LAB)
        assertEquals(PackageManager.PERMISSION_DENIED,
            rule.activity.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS"))
    }

    @Test fun freshCommercialContextIgnoresAnUnrelatedSavedLabSuccess() {
        val repository = VisualProfileRepository(rule.activity)
        val storedBefore = runBlocking { repository.equalizerProfile.first() }
        val settingsBefore = settingsSnapshot()
        val adapter = SamsungNativeVisionAdapter(rule.activity)
        val current = adapter.read(NativeVisionCapability.RELUMINO).value as? NativeVisionValue.Relumino
        // Choose a known mismatch when readback is possible. Unknown remains unconfirmed.
        val requested = current?.copy(enabled = !current.enabled) ?: NativeVisionValue.Relumino(true)
        val fakeSavedReceipt = NativeVisionApplicationResult(NativeVisionCapability.RELUMINO, requested,
            NativeVisionApplicationStatus.APPLIED_LAB, requested, NativeVisionEngine.SAMSUNG_LAB,
            "SYNTHETIC_SAVED_RECEIPT_NOT_DEVICE_PROOF", "Synthetic historical record only", 1, 9,
            confirmation = NativeVisionConfirmation.READ_BACK_CONFIRMED)
        val source = EqualizerProfile(revision = 4, nativeVision = NativeVisionProfile(enabled = true,
            requested = NativeVisionRequestedState(mapOf(NativeVisionCapability.RELUMINO to requested), revision = 9),
            applied = NativeVisionAppliedState(mapOf(NativeVisionCapability.RELUMINO to fakeSavedReceipt))))
        val capabilities = actualCapabilities()
        val plan = VisionRuntime.plan(VisionProfileSnapshot(source), capabilities, "device-commercial-read-only")
        assertFalse("The ordinary app must never get an automatic Relumino route", plan.transformations.single().canExecuteAutomatically)
        if (capabilities[NativeVisionCapability.RELUMINO]?.availability == NativeVisionAvailability.AVAILABLE_USER_ACTION) {
            assertEquals(VisionPlanDisposition.GUIDED, plan.transformations.single().disposition)
        }
        assertFalse("A stored Lab receipt cannot assert current activity", VisionRuntime.observe(rule.activity, plan).anyActive)
        assertEquals(settingsBefore, settingsSnapshot())
        assertEquals(storedBefore, runBlocking { repository.equalizerProfile.first() })
        receipt("fresh-readback", "PASS", "Fresh ordinary-app context; stored synthetic Lab receipt ignored")
    }

    @Test fun injectedPermissionWithdrawalInvalidatesReceiptWithoutChangingDevicePermission() {
        val settingsBefore = settingsSnapshot()
        val cap = NativeVisionCapability.MAGNIFICATION
        val observation = NativeVisionCapabilityObservation(NativeVisionPresence.PRESENT,
            "SYNTHETIC_PERMISSION_STATE_ONLY", publicApiAvailable = true, publicPermissionGranted = true,
            publicPermissionRequestable = true)
        val at = System.currentTimeMillis()
        val environment = NativeVisionRuntimeSnapshot(NativeVisionDevice(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT),
            NativeVisionVariant.COMMERCIAL, at, mapOf(cap to observation))
        val requested = NativeVisionValue.Magnification(true, 2f)
        val snapshot = VisionProfileSnapshot(EqualizerProfile(nativeVision = NativeVisionProfile(enabled = true,
            requested = NativeVisionRequestedState(mapOf(cap to requested), revision = 1))))
        val resolver = NativeVisionCapabilityResolver()
        val plan = VisionRuntime.plan(snapshot, resolver.resolve(environment), "synthetic-permission-contract")
        val step = plan.transformations.single()
        assertEquals(VisionPlanDisposition.APPLICABLE, step.disposition)
        val engine = requireNotNull(step.engine)
        val contractReceipt = VisionApplicationReceipt(plan.profileId, plan.profileRevision, plan.context, step.request,
            engine.engineId, engine.version, requireNotNull(step.transport), VisionReceiptStatus.APPLIED,
            VisionReceiptConfirmation.READ_BACK_CONFIRMED, at, step.request.payload,
            "SYNTHETIC_CONTRACT_TEST_NOT_A_PHYSICAL_OBSERVATION", "Synthetic matching receipt")
        assertTrue(VisionRuntime.orchestrator().observe(plan, listOf(contractReceipt), at).anyActive)
        val withdrawn = environment.copy(observedAtMillis = at + 1,
            observations = mapOf(cap to observation.copy(publicPermissionGranted = false)))
        val refreshed = VisionRuntime.plan(snapshot, resolver.resolve(withdrawn), "synthetic-permission-contract")
        assertEquals(VisionPlanDisposition.PERMISSION_REQUIRED, refreshed.transformations.single().disposition)
        assertFalse(VisionRuntime.orchestrator().observe(refreshed, listOf(contractReceipt), at + 1).anyActive)
        assertEquals(settingsBefore, settingsSnapshot())
        assertEquals(PackageManager.PERMISSION_DENIED,
            rule.activity.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS"))
        receipt("permission-contract", "PASS", "Injected permission withdrawal only; no grant, revocation or system navigation")
    }

    @Test fun actualHomeContainsReadOnlyOrchestrationStatusCard() {
        val repository = VisualProfileRepository(rule.activity)
        val storedBefore = runBlocking { repository.equalizerProfile.first() }
        val settingsBefore = settingsSnapshot()
        rule.runOnUiThread {
            rule.activity.setContent {
                MaterialTheme {
                    HomeScreen(profile = VisualProfile(), onQuestionnaire = {}, onCalibration = {}, onEqualizer = {},
                        onVisualAssessment = {}, onReading = {}, onProfile = {}, onSettings = {}, onCoreStatus = {},
                        onHelp = {}, onMagnifierSetup = {}, onOpticalPrescription = {}, onNativeVision = {})
                }
            }
        }
        rule.onNodeWithTag("vision_status").performScrollTo().assertIsDisplayed()
        rule.waitForIdle()
        assertEquals(storedBefore, runBlocking { repository.equalizerProfile.first() })
        assertEquals(settingsBefore, settingsSnapshot())
        receipt("home-status", "PASS", "Actual Home composable contains the read-only orchestration status card")
    }

    @Test fun orchestratedPreviewRequiresItsRealDrawCallbackAndKeepsOriginalComparisonLocal() {
        assumeTrue("The existing AGSL preview requires Android 13", Build.VERSION.SDK_INT >= 33)
        val repository = VisualProfileRepository(rule.activity)
        val storedBefore = runBlocking { repository.equalizerProfile.first() }
        val settingsBefore = settingsSnapshot()
        val capabilities = actualCapabilities()
        val occupied = VisionRuntime.occupiedDisplayDomains(rule.activity, capabilities)
        // Keep existing phone effects untouched; exercise a transformation whose domain is free.
        val candidates = listOf(
            EqualizerPreferences(sharpness = .35f) to EqualizerPreferences(sharpness = .55f),
            EqualizerPreferences(fontWeight = 600) to EqualizerPreferences(fontWeight = 700),
            EqualizerPreferences(sizeScale = 1.2f) to EqualizerPreferences(sizeScale = 1.4f),
            EqualizerPreferences(lightComfort = .3f) to EqualizerPreferences(lightComfort = .5f))
        val available = candidates.firstOrNull { (preferences, _) ->
            VisionRuntime.plan(VisionProfileSnapshot(EqualizerProfile(preferences = preferences)), capabilities,
                "preview-domain-check", includePreview = true, occupiedDisplayDomains = occupied)
                .transformations.single().disposition == VisionPlanDisposition.APPLICABLE
        }
        assumeTrue("An existing phone effect occupies every preview domain; leave those settings untouched", available != null)
        val (firstPreferences, nextPreferences) = requireNotNull(available)
        val source = mutableStateOf(EqualizerProfile(preferences = firstPreferences, revision = 5))
        val original = mutableStateOf(false)
        val drawn = AtomicReference<EqualizerRenderRecord?>(null)
        val initialPlan = VisionRuntime.plan(VisionProfileSnapshot(source.value), actualCapabilities(), "preview-before-draw", includePreview = true)
        assertFalse(VisionRuntime.observe(rule.activity, initialPlan).anyActive)
        rule.runOnUiThread {
            rule.activity.setContent {
                MaterialTheme {
                    OrchestratedEqualizerPreview(source.value, original.value, 0,
                        Modifier.fillMaxWidth().height(260.dp), onApplied = { drawn.set(it) },
                        nativeProfileOverride = NativeVisionProfile())
                }
            }
        }
        rule.waitUntil(15_000) { drawn.get()?.sourceRevision == 5L }
        rule.waitForIdle()
        assertEquals(source.value.preferences.renderRecord(source.value.scene, 5, true), drawn.get())
        rule.onNodeWithTag("vision_preview_status").assertTextEquals("Confort visuel : actif dans cet aperçu")
        val firstDraw = requireNotNull(drawn.get())
        val sourceBeforeOriginal = source.value
        rule.runOnUiThread { original.value = true }
        rule.waitForIdle()
        rule.onNodeWithTag("vision_preview_status").assertTextEquals("Original affiché · aides du téléphone conservées")
        assertEquals(sourceBeforeOriginal, source.value)
        assertEquals(firstDraw, drawn.get())
        rule.runOnUiThread {
            original.value = false
            source.value = source.value.copy(revision = 6, preferences = nextPreferences)
        }
        rule.waitUntil(15_000) { drawn.get()?.sourceRevision == 6L }
        rule.waitForIdle()
        assertEquals(source.value.preferences.renderRecord(source.value.scene, 6, true), drawn.get())
        val currentPlan = VisionRuntime.plan(VisionProfileSnapshot(source.value), actualCapabilities(), "preview-after-change", includePreview = true)
        assertFalse("Old draw revision cannot activate a new request", VisionRuntime.observe(rule.activity, currentPlan, firstDraw).anyActive)
        assertTrue("Current draw callback confirms only the internal preview", VisionRuntime.observe(rule.activity, currentPlan, drawn.get()).anyActive)
        assertEquals(storedBefore, runBlocking { repository.equalizerProfile.first() })
        assertEquals(settingsBefore, settingsSnapshot())
        receipt("preview-render-callback", "PASS", "Real post-draw callbacks, stale draw rejection and local Original comparison; no profile save")
    }

    private fun actualCapabilities() = NativeVisionCapabilityResolver().resolve(
        NativeVisionEnvironment(rule.activity).snapshot(NativeVisionVariant.COMMERCIAL, false))

    private fun settingsSnapshot(): Map<String, NativeSettingRead> {
        val adapter = SamsungNativeVisionAdapter(rule.activity)
        return NativeVisionCapability.entries.filter { it != NativeVisionCapability.MAGNIFICATION }.flatMap { capability ->
            adapter.read(capability).settings.map { (key, value) -> "${capability.name}/$key" to value }
        }.toMap()
    }

    private fun receipt(name: String, result: String, evidence: String) {
        File(rule.activity.filesDir, "vision-orchestration-$name.json").writeText(JSONObject()
            .put("result", result).put("evidence", evidence).put("packageName", rule.activity.packageName)
            .put("screenCaptured", false).put("protectedSettingsModified", false)
            .put("syntheticRequestsPersisted", false).put("timestampMillis", System.currentTimeMillis()).toString(2))
    }
}
