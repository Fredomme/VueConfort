package fr.vueconfort.app.nativevision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File

/** The tests use the target application's own UID. No shell identity or screen capture is used. */
internal class NativeVisionLabDeviceFixture(private val testName: String) {
    val context: Context = ApplicationProvider.getApplicationContext<Context>()
    val events = JSONArray()
    private val baseline: Map<String, String?>
    private var finalized = false
    private var lastReadStatuses: Map<String, String> = emptyMap()

    init {
        assertEquals("Never exercise a commercial or historical package", "fr.vueconfort.app.lab", context.packageName)
        assertEquals("Commands must execute as the target APK", context.applicationInfo.uid, Process.myUid())
        assertTrue("No system/root/shell UID", Process.myUid() >= 10_000)
        baseline = readRaw().filterKeys { it in restorationKeys }
        assertEquals("All Relumino keys must be readable, including absence", restorationKeys.toSet(), baseline.keys)
        assertFalse("An earlier rollback journal must be resolved before testing", adapter().hasPendingRestoration())
        event("BASELINE", null, baseline)
        save()
    }

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    fun capabilities() = NativeVisionCapabilityResolver().resolve(
        NativeVisionEnvironment(context).snapshot(NativeVisionVariant.LAB, hasPermission())
    )
    fun adapter() = SamsungNativeVisionLabAdapter(context, ::capabilities)
    fun request(value: NativeVisionValue.Relumino, revision: Long) = NativeVisionRequestedState(
        mapOf(NativeVisionCapability.RELUMINO to value), revision, System.currentTimeMillis(), "SYNTHETIC_DEVICE_RECIPE"
    )

    fun applyAndCheck(adapter: SamsungNativeVisionLabAdapter, request: NativeVisionRequestedState): List<NativeVisionApplicationResult> {
        val started = SystemClock.elapsedRealtimeNanos()
        val results = adapter.apply(request)
        val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        event("COMMAND", JSONObject().put("revision", request.revision).put("commandToReadbackMillis", elapsed)
            .put("results", JSONArray().apply {
                results.forEach { result -> put(JSONObject().put("capability", result.capability.name)
                    .put("requested", result.requested.toString()).put("applied", result.applied?.toString() ?: JSONObject.NULL)
                    .put("status", result.status.name).put("confirmation", result.confirmation.name)
                    .put("reason", result.reason).put("restorationAvailable", result.restorationAvailable)) }
            }), readRaw())
        save()
        assertEquals(request.values.size, results.size)
        results.forEach { result ->
            assertEquals(result.reason, NativeVisionApplicationStatus.APPLIED_LAB, result.status)
            assertEquals(result.requested, result.applied)
            assertEquals(NativeVisionConfirmation.READ_BACK_CONFIRMED, result.confirmation)
        }
        return results
    }

    fun assertBaseline() { assertEquals("Every Relumino key must regain its exact value or absence", baseline,
        readRaw().filterKeys { it in restorationKeys }) }

    /** Keeps a failed journal; never forcibly overwrites a detected external change for test cleanup. */
    fun restoreFinally(adapter: SamsungNativeVisionLabAdapter, revision: Long = 1000) {
        val outcome = adapter.restorePreviousState(revision)
        event("RESTORE", JSONObject().put("success", outcome.success).put("pending", outcome.pending)
            .put("reasons", JSONArray(outcome.results.map { it.reason })), readRaw())
        finalized = outcome.success && !outcome.pending && baseline == readRaw().filterKeys { it in restorationKeys }
        save()
        assertTrue("Rollback failed. Journal retained: ${outcome.results.map { it.reason }}", outcome.success)
        assertFalse("No rollback entry should remain after success", outcome.pending)
        assertBaseline()
    }

    fun finishReadOnly() { assertBaseline(); finalized = true; event("READ_ONLY_COMPLETE", null, readRaw()); save() }

    /** Unreadable is omitted from values and explicitly reported, never converted to absence. */
    fun readRaw(): Map<String, String?> {
        val values = linkedMapOf<String, String?>()
        val statuses = linkedMapOf<String, String>()
        for (key in keys) {
            try {
                val value = Settings.Secure.getString(context.contentResolver, key)
                values[key] = value
                statuses[key] = if (value == null) "ABSENT" else "PRESENT"
            } catch (_: SecurityException) { statuses[key] = "UNREADABLE" }
            catch (_: Exception) { statuses[key] = "ERROR" }
        }
        lastReadStatuses = statuses
        return values
    }
    fun event(stage: String, details: JSONObject?, settings: Map<String, String?> = readRaw()) {
        events.put(JSONObject().put("stage", stage).put("timestampMillis", System.currentTimeMillis())
            .put("uid", Process.myUid()).put("developmentPermissionGranted", hasPermission())
            .put("settings", JSONObject().apply { settings.forEach { (key, value) -> put(key, value ?: JSONObject.NULL) } })
            .put("settingReadStatus", JSONObject(lastReadStatuses))
            .put("details", details ?: JSONObject.NULL))
    }
    fun save() {
        File(context.filesDir, "native-vision-$testName.json").writeText(JSONObject()
            .put("schemaVersion", 1).put("test", testName).put("package", context.packageName)
            .put("restoredOrUnchanged", finalized).put("restorationVerificationKeys", JSONArray(restorationKeys))
            .put("extraDimVerification", "NO_WRITES; OPTIONAL_READS_MAY_BE_UNREADABLE; EXTERNAL_BASELINE_REQUIRED")
            .put("events", events).toString(2))
    }

    fun observationPause() {
        // Optional bounded dwell requested externally for a visible, synthetic test session.
        val seconds = InstrumentationRegistry.getArguments().getString("nativeVisionDwellSeconds")?.toLongOrNull()?.coerceIn(0, 10) ?: 0
        if (seconds > 0) SystemClock.sleep(seconds * 1000)
    }

    companion object {
        val restorationKeys = listOf("relumino_switch", "relumino_edge_thickness", "relumino_type")
        val keys = restorationKeys + listOf("reduce_bright_colors_activated", "reduce_bright_colors_level")
    }
}
