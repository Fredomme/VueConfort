package fr.vueconfort.app.nativevision

import android.content.Context
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.app.UiAutomation
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.magnifier.ScreenMagnifierService
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** No screenshots, personal content, shell identity or permission adoption. Runs in the preview UID. */
@RunWith(AndroidJUnit4::class)
class NativeVisionCommercialDeviceTest {
    private lateinit var context: Context

    @Before fun requireCommercialPreview() {
        context = ApplicationProvider.getApplicationContext()
        check(context.packageName == "fr.vueconfort.app.preview")
        check(!BuildConfig.NATIVE_VISION_LAB)
    }

    @Test fun commercialManifestAndFactoryCannotRequestOrUseLabPrivilege() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toSet().orEmpty()
        val forbidden = setOf("android.permission.WRITE_SECURE_SETTINGS", "android.permission.WRITE_SETTINGS",
            "android.permission.READ_LOGS", "android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.CAPTURE_VIDEO_OUTPUT", "android.permission.READ_FRAME_BUFFER",
            "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION")
        assertEquals(emptySet<String>(), requested.intersect(forbidden))
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS"))
        assertFalse(VariantNativeVisionFactory.hasLabAccess(context))
        assertNull(VariantNativeVisionFactory.create(context) {
            NativeVisionCapabilityResolver().resolve(NativeVisionEnvironment(context).snapshot(NativeVisionVariant.COMMERCIAL))
        })
        receipt("permissions", JSONObject().put("result", "PASS")
            .put("requestedPermissions", JSONArray(requested.sorted()))
            .put("writeSecureSettingsGranted", false).put("labFactoryAvailable", false))
    }

    @Test fun s25CapabilitiesSeparateNativePresenceFromOrdinaryAppControl() {
        val adapter = SamsungNativeVisionAdapter(context)
        if (!adapter.isAttestedS25) receipt("capabilities", JSONObject().put("result", "SKIPPED")
            .put("reason", "Ce contrôle ciblé exige le firmware S25 attesté."))
        assumeTrue("S25 SM-S931B API 36 S931BXXSCCZH1 requis pour ce contrôle ciblé", adapter.isAttestedS25)
        val snapshot = NativeVisionEnvironment(context).snapshot(NativeVisionVariant.COMMERCIAL)
        val capabilities = NativeVisionCapabilityResolver().resolve(snapshot)
        assertEquals("SM-S931B", snapshot.device.model)
        assertEquals(36, snapshot.device.sdkInt)
        assertNotNull(snapshot.device.oneUiVersion)
        val rows = JSONArray()
        for (capability in NativeVisionCapability.entries) {
            val state = capabilities[capability]!!
            if (capability != NativeVisionCapability.MAGNIFICATION) {
                assertEquals("S25 function $capability is established", NativeVisionPresence.PRESENT, state.presence)
                assertFalse("Commercial protected/display commands must stay guided: $capability", state.canApplyAutomatically)
                assertEquals(NativeVisionAvailability.AVAILABLE_USER_ACTION, state.availability)
            }
            rows.put(JSONObject().put("capability", capability.name).put("presence", state.presence.name)
                .put("availability", state.availability.name).put("automatic", state.canApplyAutomatically)
                .put("readable", state.readable).put("provenance", state.provenance))
        }
        receipt("capabilities", JSONObject().put("result", "PASS").put("capabilities", rows)
            .put("reluminoReadback", readbackJson(adapter.read(NativeVisionCapability.RELUMINO))))
    }

    @Test fun reluminoGuidanceUsesInspectedAccessibleSystemActivityWithoutShortcutPermission() {
        val adapter = SamsungNativeVisionAdapter(context)
        assumeTrue("Contrôle de navigation Samsung uniquement", adapter.isSamsung)
        val intent = adapter.guidanceIntent(NativeVisionCapability.RELUMINO)
        assertNotNull("A public accessibility landing page must be available", intent)
        val component = intent!!.component!!
        @Suppress("DEPRECATION")
        val info = context.packageManager.getActivityInfo(component, 0)
        assertTrue(info.exported)
        assertTrue(info.enabled && info.applicationInfo.enabled)
        assertTrue(info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
        assertTrue(info.permission.isNullOrEmpty() || context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertFalse(component.className.contains("ReluminoShortcut"))
        assertFalse(adapter.specificSettingsRouteAvailable(NativeVisionCapability.RELUMINO))
        receipt("navigation", JSONObject().put("result", "PASS").put("action", intent.action)
            .put("component", component.flattenToString()).put("exported", info.exported)
            .put("requiredPermission", info.permission ?: JSONObject.NULL)
            .put("launched", false).put("guidance", adapter.guidanceText(NativeVisionCapability.RELUMINO)))
    }

    @Test fun commercialApplyAndReadbackNeverAlterProtectedSettingsOrTheStoredProfile() {
        val adapter = SamsungNativeVisionAdapter(context)
        val before = settingsSnapshot(adapter)
        val profileBefore = storedProfileFingerprint()
        val requested = NativeVisionRequestedState(
            values = mapOf(
                NativeVisionCapability.RELUMINO to NativeVisionValue.Relumino(true, ReluminoThickness.MAX, ReluminoColor.GREEN),
                NativeVisionCapability.EXTRA_DIM to NativeVisionValue.ExtraDim(true, 65),
                NativeVisionCapability.COLOR_FILTER to NativeVisionValue.ColorFilter(true, 3, 45),
                NativeVisionCapability.COLOR_CORRECTION to NativeVisionValue.ColorCorrection(true, 12),
                NativeVisionCapability.COLOR_INVERSION to NativeVisionValue.Toggle(true),
                NativeVisionCapability.HIGH_CONTRAST_TEXT to NativeVisionValue.Toggle(true),
                NativeVisionCapability.EYE_COMFORT to NativeVisionValue.Toggle(true),
                NativeVisionCapability.SYSTEM_BRIGHTNESS to NativeVisionValue.Brightness(110),
                NativeVisionCapability.FONT_SCALE to NativeVisionValue.FontScale(1.2f),
                NativeVisionCapability.SCREEN_ZOOM to NativeVisionValue.ScreenZoom(2),
            ), revision = 7, updatedAtMillis = 7, provenance = "SYNTHETIC_READ_ONLY_DEVICE_TEST",
        )
        val applied = adapter.apply(requested)
        val verified = adapter.verify(requested)
        assertEquals(requested.values.size, applied.size)
        (applied + verified).forEach {
            assertFalse(it.status == NativeVisionApplicationStatus.APPLIED_AUTO)
            assertFalse(it.status == NativeVisionApplicationStatus.APPLIED_LAB)
            assertFalse(it.restorationAvailable)
            it.validated()
        }
        val after = settingsSnapshot(adapter)
        assertEquals("Commercial adapter changed a settings value", before, after)
        assertEquals("Read/guidance must not persist a personal profile", profileBefore, storedProfileFingerprint())
        receipt("read-only", JSONObject().put("result", "PASS").put("settingsUnchanged", true)
            .put("profileUnchanged", true).put("settings", settingsJson(after))
            .put("resultStatuses", JSONArray(applied.map { "${it.capability}:${it.status}" })))
    }

    @Test fun publicMagnificationSessionRestoresExactObservedStateWhenPreviewServiceIsReady() = runBlocking {
        // Instrumentation may restart the app process; an already user-enabled service must have
        // time to bind again. This public flag prevents the test's automation connection from
        // suppressing that service. No shell permissions are adopted and no service is enabled here.
        InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val adapter = AndroidNativeVisionAdapter()
        val session = NativeMagnificationSession(context)
        val connectionTimeoutMillis = magnificationServiceWaitMillis()
        val waitStarted = SystemClock.elapsedRealtime()
        while (!adapter.isControllerConnected() && SystemClock.elapsedRealtime() - waitStarted < connectionTimeoutMillis) delay(100)
        if (adapter.isControllerConnected()) delay(500)
        val ownService = ComponentName(context, ScreenMagnifierService::class.java)
        val accessibility = context.getSystemService(AccessibilityManager::class.java)
        val ownServiceDeclaredEnabled = accessibility.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info -> info.resolveInfo?.serviceInfo?.let {
                ComponentName(it.packageName, it.name) == ownService
            } == true }
        val binding = JSONObject().put("ownService", ownService.flattenToShortString())
            .put("ownServiceDeclaredEnabled", ownServiceDeclaredEnabled)
            .put("controllerConnected", adapter.isControllerConnected())
            .put("connectionTimeoutMillis", connectionTimeoutMillis)
            .put("connectionWaitMillis", SystemClock.elapsedRealtime() - waitStarted)
            .put("automationDoesNotSuppressAccessibilityServices", true)
        val baseline = adapter.readMagnificationState()
        binding.put("snapshot", magnificationJson(baseline))
        val skip = when {
            !adapter.isControllerConnected() -> "Service VueConfort Aperçu non connecté ; aucune activation de service n’est effectuée par le test."
            baseline?.restorable != true -> "Le contrôleur ne fournit pas une géométrie et une activation entièrement restaurables."
            session.hasPendingRestoration() -> "Une session existante appartient à l’utilisateur ; le test la préserve."
            else -> null
        }
        if (skip != null) receipt("magnification", JSONObject().put("result", "SKIPPED").put("reason", skip)
            .put("binding", binding))
        assumeTrue(skip ?: "Controller snapshot is restorable", skip == null)
        val before = requireNotNull(baseline)
        val beforeSettings = settingsSnapshot(SamsungNativeVisionAdapter(context))
        val profileBefore = storedProfileFingerprint()
        val request = NativeVisionRequestedState(mapOf(NativeVisionCapability.MAGNIFICATION to
            NativeVisionValue.Magnification(enabled = true,
                scale = if (before.scale!! < 3f) before.scale + .25f else before.scale - .25f,
                centerX = before.centerX, centerY = before.centerY, mode = before.mode!!)),
            revision = 1, updatedAtMillis = 1, provenance = "SYNTHETIC_PUBLIC_API_DEVICE_TEST")
        var restored = false
        try {
            val result = session.apply(request).single()
            assertEquals(NativeVisionApplicationStatus.APPLIED_AUTO, result.status)
            assertEquals(NativeVisionConfirmation.READ_BACK_CONFIRMED, result.confirmation)
            assertNotNull(result.applied)
            assertTrue(session.hasPendingRestoration())
            // A fresh owner must recover the same journal after the original owner is discarded.
            val reopened = NativeMagnificationSession(context)
            assertTrue(reopened.hasPendingRestoration())
            val outcome = reopened.restore(1)
            assertTrue("${outcome.results.map { it.reason }}", outcome.success)
            assertFalse(outcome.pending)
            restored = before.matches(requireNotNull(adapter.readMagnificationState()))
            assertTrue("All original controller fields must be restored", restored)
            assertFalse(reopened.hasPendingRestoration())
            assertEquals(profileBefore, storedProfileFingerprint())
            assertEquals(beforeSettings, settingsSnapshot(SamsungNativeVisionAdapter(context)))
            receipt("magnification", JSONObject().put("result", "PASS").put("publicApiApplied", true)
                .put("binding", binding)
                .put("journalReopened", true).put("originalControllerStateRestored", true)
                .put("storedProfileUnchanged", true).put("protectedSettingsUnchanged", true))
        } finally {
            withContext(NonCancellable) {
                if (!restored && session.hasPendingRestoration()) {
                    val outcome = session.restore(1)
                    receipt("magnification-finally", JSONObject().put("restored", outcome.success).put("pending", outcome.pending)
                        .put("reasons", JSONArray(outcome.results.map { it.reason })))
                    assertTrue("Magnification cleanup failed; private recovery journal is retained", outcome.success)
                }
            }
        }
    }

    /** Exercises the unchanged historical activation when an inactive controller has no geometry. */
    @Test fun legacyMagnifierAndNativeSessionReturnToKnownInactiveStateWithoutChangingLoupeProfile() = runBlocking {
        InstrumentationRegistry.getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val adapter = AndroidNativeVisionAdapter()
        val session = NativeMagnificationSession(context)
        val connectionTimeoutMillis = magnificationServiceWaitMillis()
        val started = SystemClock.elapsedRealtime()
        while (!adapter.isControllerConnected() && SystemClock.elapsedRealtime() - started < connectionTimeoutMillis) delay(100)
        // Let the historical service finish reading its own saved loupe preferences after binding.
        if (adapter.isControllerConnected()) delay(500)
        val initial = adapter.readMagnificationState()
        val skip = when {
            !adapter.isControllerConnected() -> "Service Aperçu non connecté après l’attente autorisée (${connectionTimeoutMillis} ms)."
            initial == null || !initial.activationExact || initial.enabled -> "Ce contrôle exige une activation initiale OFF connue."
            initial.restorable -> "La recette publique normale peut déjà restaurer toute la géométrie."
            session.hasPendingRestoration() -> "Un journal existant appartient à l’utilisateur ; il est préservé."
            else -> null
        }
        if (skip != null) receipt("magnification-legacy", JSONObject().put("result", "SKIPPED")
            .put("reason", skip).put("connectionTimeoutMillis", connectionTimeoutMillis)
            .put("connectionWaitMillis", SystemClock.elapsedRealtime() - started)
            .put("initialSnapshot", magnificationJson(initial)))
        assumeTrue(skip ?: "Known inactive state required", skip == null)
        val profileBefore = storedProfileFingerprint()
        val protectedBefore = settingsSnapshot(SamsungNativeVisionAdapter(context))
        var legacyActivationRequested = false
        var activeBaseline: NativeMagnificationSnapshot? = null
        var applied = false
        var activeSessionRestored = false
        try {
            legacyActivationRequested = true
            withContext(Dispatchers.Main) { ScreenMagnifierService.handleExternalAction(ScreenMagnifierService.ACTION_MAGNIFIER_ENABLE) }
            val activationStarted = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - activationStarted < 5_000) {
                val observed = adapter.readMagnificationState()
                if (observed?.enabled == true && observed.restorable) { activeBaseline = observed; break }
                delay(100)
            }
            assertNotNull("Historical magnifier activation must yield a readable active geometry", activeBaseline)
            val before = requireNotNull(activeBaseline)
            val request = NativeVisionRequestedState(mapOf(NativeVisionCapability.MAGNIFICATION to
                NativeVisionValue.Magnification(true,
                    if (before.scale!! < 3f) before.scale + .25f else before.scale - .25f,
                    before.centerX, before.centerY, before.mode!!)), revision = 1,
                updatedAtMillis = 1, provenance = "SYNTHETIC_LEGACY_AND_PUBLIC_API_TEST")
            val result = session.apply(request).single()
            applied = result.status == NativeVisionApplicationStatus.APPLIED_AUTO
            assertTrue(result.reason, applied)
            val reopened = NativeMagnificationSession(context)
            val restore = reopened.restore(1)
            activeSessionRestored = restore.success && !restore.pending &&
                before.matches(requireNotNull(adapter.readMagnificationState()))
            assertTrue("Native session must restore the active state created by historical activation", activeSessionRestored)
        } finally {
            withContext(NonCancellable) {
                var cleanupCanReset = true
                if (session.hasPendingRestoration()) {
                    val outcome = session.restore(1)
                    cleanupCanReset = outcome.success && !outcome.pending
                    receipt("magnification-legacy-session-cleanup", JSONObject().put("restored", outcome.success)
                        .put("pending", outcome.pending).put("reasons", JSONArray(outcome.results.map { it.reason })))
                }
                // Pause uses the historical disableMagnification path. It never changes the saved scale
                // or disables the accessibility service; the user-enabled service remains for the host to restore.
                // ACTION_PAUSE is a toggle: do not invoke it if the phone is already inactive.
                if (legacyActivationRequested && cleanupCanReset && adapter.readMagnificationState()?.enabled == true) {
                    withContext(Dispatchers.Main) { ScreenMagnifierService.handleExternalAction(ScreenMagnifierService.ACTION_PAUSE) }
                    val cleanupStarted = SystemClock.elapsedRealtime()
                    while (SystemClock.elapsedRealtime() - cleanupStarted < 5_000 && adapter.readMagnificationState()?.enabled != false) delay(100)
                }
                val final = adapter.readMagnificationState()
                val inactiveRestored = final?.activationExact == true && !final.enabled
                val profileUnchanged = profileBefore == storedProfileFingerprint()
                val protectedUnchanged = protectedBefore == settingsSnapshot(SamsungNativeVisionAdapter(context))
                receipt("magnification-legacy", JSONObject()
                    .put("result", if (applied && activeSessionRestored && cleanupCanReset && inactiveRestored && profileUnchanged && protectedUnchanged) "PASS" else "FAILED")
                    .put("historicalActivationRequested", legacyActivationRequested).put("nativeSessionApplied", applied)
                    .put("activeSessionExactlyRestored", activeSessionRestored).put("initialActivationRestored", inactiveRestored)
                    .put("initialUnknownGeometryRestored", false).put("initialUnknownGeometryNotClaimed", true)
                    .put("storedProfileUnchanged", profileUnchanged).put("protectedSettingsUnchanged", protectedUnchanged)
                    .put("initialSnapshot", magnificationJson(initial)).put("activeSnapshot", magnificationJson(activeBaseline))
                    .put("finalSnapshot", magnificationJson(final)).put("journalPending", session.hasPendingRestoration()))
                assertTrue("Unresolved native journal retained; no forced legacy reset", cleanupCanReset)
                assertTrue("Historical pause must restore the known initial inactive state", inactiveRestored)
                assertTrue("Historical activation/pause must preserve the saved loupe/profile", profileUnchanged)
                assertTrue("Protected Samsung settings must be unchanged", protectedUnchanged)
            }
        }
    }

    private fun magnificationJson(value: NativeMagnificationSnapshot?): Any = value?.let {
        JSONObject().put("enabled", it.enabled).put("activationExact", it.activationExact)
            .put("scale", it.scale ?: JSONObject.NULL).put("centerX", it.centerX ?: JSONObject.NULL)
            .put("centerY", it.centerY ?: JSONObject.NULL).put("mode", it.mode?.name ?: JSONObject.NULL)
            .put("restorable", it.restorable)
    } ?: JSONObject.NULL

    /** Leaves time for a host/user OFF → ON action in Android Settings after instrumentation
     * restarts the process. The test never enables a service or writes accessibility settings. */
    private fun magnificationServiceWaitMillis(): Long =
        InstrumentationRegistry.getArguments().getString("nativeVisionServiceWaitMillis")
            ?.toLongOrNull()?.coerceIn(0L, 60_000L) ?: 5_000L

    private fun settingsSnapshot(adapter: SamsungNativeVisionAdapter): Map<String, NativeSettingRead> =
        NativeVisionCapability.entries.filter { it != NativeVisionCapability.MAGNIFICATION }
            .flatMap { capability -> adapter.read(capability).settings.map { (key, value) -> "${capability.name}/$key" to value } }.toMap()

    private fun settingsJson(values: Map<String, NativeSettingRead>) = JSONObject().also { root ->
        values.forEach { (key, value) -> root.put(key, JSONObject().put("status", value.status.name)
            .put("value", value.rawValue ?: JSONObject.NULL)) }
    }

    private fun readbackJson(value: NativeVisionReadback) = JSONObject().put("readable", value.readable)
        .put("knownValue", value.value?.toString() ?: JSONObject.NULL).put("provenance", value.provenance)
        .put("settings", settingsJson(value.settings))

    private fun storedProfileFingerprint(): String? {
        val file = File(context.filesDir, "datastore/vueconfort_settings.preferences_pb")
        if (!file.exists()) return null
        return MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    }

    private fun receipt(name: String, content: JSONObject) {
        content.put("packageName", context.packageName).put("uid", Process.myUid())
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("sdk", Build.VERSION.SDK_INT).put("buildIncremental", Build.VERSION.INCREMENTAL)
            .put("timestampMillis", System.currentTimeMillis())
        File(context.filesDir, "native-vision-commercial-$name.json").writeText(content.toString(2))
    }
}
