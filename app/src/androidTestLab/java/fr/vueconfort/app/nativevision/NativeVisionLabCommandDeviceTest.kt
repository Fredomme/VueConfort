package fr.vueconfort.app.nativevision

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeVisionLabCommandDeviceTest {
    /** Grant WRITE_SECURE_SETTINGS to the Lab APK externally, never from instrumentation. */
    @Test fun reluminoFiveStopsFourTypesRestoreExactlyAndExtraDimRemainsGuided() {
        val fixture = NativeVisionLabDeviceFixture("lab-relumino-extra-dim")
        assertTrue("The explicit external development grant is required", fixture.hasPermission())
        val adapter = fixture.adapter()
        var revision = 1L
        try {
            assertEquals(NativeVisionAvailability.AVAILABLE_LAB_ONLY,
                fixture.capabilities()[NativeVisionCapability.RELUMINO]!!.availability)
            fixture.applyAndCheck(adapter, fixture.request(NativeVisionValue.Relumino(false), revision++))
            assertEquals("0", fixture.readRaw()["relumino_switch"])
            for (thickness in ReluminoThickness.entries) {
                fixture.applyAndCheck(adapter, fixture.request(NativeVisionValue.Relumino(true, thickness), revision++))
                assertEquals(thickness.value.toString(), fixture.readRaw()["relumino_edge_thickness"])
                assertEquals("1", fixture.readRaw()["relumino_switch"])
                fixture.observationPause()
            }
            for (color in ReluminoColor.entries) {
                fixture.applyAndCheck(adapter, fixture.request(NativeVisionValue.Relumino(true, ReluminoThickness.THREE, color), revision++))
                assertEquals(color.value.toString(), fixture.readRaw()["relumino_type"])
                fixture.observationPause()
            }
            // A new adapter instance must recover the original journal, not snapshot the already changed phone.
            val reopened = fixture.adapter()
            assertTrue(reopened.hasPendingRestoration())
            fixture.applyAndCheck(reopened, fixture.request(NativeVisionValue.Relumino(false, ReluminoThickness.TWO), revision++))
            val extraDimRequest = NativeVisionRequestedState(mapOf(
                NativeVisionCapability.EXTRA_DIM to NativeVisionValue.ExtraDim(true, 20)
            ), revision++, System.currentTimeMillis(), "SYNTHETIC_DEVICE_RECIPE")
            assertFalse("Shell-only proof must not be reported as APK control",
                fixture.capabilities()[NativeVisionCapability.EXTRA_DIM]!!.canApplyAutomatically)
            val beforeExtraDim = fixture.readRaw()
            val rejected = reopened.apply(extraDimRequest).single()
            fixture.event("EXTRA_DIM_REQUIRES_SYSTEM_SETTINGS", org.json.JSONObject()
                .put("status", rejected.status.name).put("reason", rejected.reason)
                .put("expectedBehavior", "GUIDED_ONLY"))
            fixture.save()
            assertEquals(NativeVisionApplicationStatus.UNSUPPORTED, rejected.status)
            assertNull(rejected.applied)
            assertEquals("An unavailable Extra Dim command must not change readable settings", beforeExtraDim, fixture.readRaw())
        } finally { fixture.restoreFinally(fixture.adapter(), revision + 1) }
    }
}
