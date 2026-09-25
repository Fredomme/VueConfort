package fr.vueconfort.app.nativevision

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.vueconfort.app.data.VisualProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeVisionLabControllerDeviceTest {
    @Test fun rapidChangesAreCoalescedAndDisableCancelsPendingCommands() = runBlocking {
        val fixture = NativeVisionLabDeviceFixture("lab-controller-coalescence")
        assertTrue("The explicit external development grant is required", fixture.hasPermission())
        val repository = VisualProfileRepository(fixture.context)
        val originalEqualizer = repository.equalizerProfile.first()
        val originalNative = originalEqualizer?.nativeVision ?: NativeVisionProfile()
        assertFalse("Do not replace an existing active Lab profile for a test", originalNative.enabled)
        assertFalse("An existing magnification session must not be owned by this test",
            NativeMagnificationSession(fixture.context).hasPendingRestoration())
        // Seed before constructing the singleton; its collector deliberately rejects stale emissions.
        repository.updateNativeVision { NativeVisionProfile() }
        val controller = NativeVisionController.get(fixture.context)
        try {
            withTimeout(15_000) { controller.state.first { it.loaded && !it.saving && !it.restoring } }
            assertFalse("Resolve any previous magnification/native rollback before this test", controller.state.value.pendingRestoration)
            // Only the native subset is synthetic; bilan, equalizer choices and loupe are never changed.
            withTimeout(15_000) { controller.state.first { it.profile.requested.values.isEmpty() && !it.profile.enabled } }
            val submittedBefore = controller.state.value.submittedCommands
            val executedBefore = controller.state.value.executedBatches
            val last = NativeVisionValue.Relumino(true, ReluminoThickness.MAX, ReluminoColor.GREEN)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repeat(100) { index ->
                    controller.update(NativeVisionCapability.RELUMINO, NativeVisionValue.Relumino()) { current ->
                        if (index % 2 == 0) current.copy(enabled = true,
                            thickness = ReluminoThickness.entries[(index / 2) % 5])
                        else current.copy(enabled = true,
                            color = if (index == 99) ReluminoColor.GREEN else ReluminoColor.entries[(index / 2) % 4])
                    }
                }
            }
            val settled = withTimeout(20_000) { controller.state.first { state ->
                !state.saving && state.profile.applied.results[NativeVisionCapability.RELUMINO]?.let {
                    it.status == NativeVisionApplicationStatus.APPLIED_LAB && it.applied == last
                } == true
            } }
            val submissions = settled.submittedCommands - submittedBefore
            val executions = settled.executedBatches - executedBefore
            fixture.event("COALESCED", JSONObject().put("submittedCommands", submissions)
                .put("executedBatches", executions).put("lastCommandReadbackMillis", settled.lastCommandReadbackMillis)
                .put("lastRequested", last.toString()))
            fixture.save()
            assertEquals(100L, submissions)
            assertTrue("The actual controller must combine repeated requests into fewer application batches: $executions", executions in 1..49)
            assertEquals("4.99", fixture.readRaw()["relumino_edge_thickness"])
            assertEquals("3", fixture.readRaw()["relumino_type"])

            // Queue another burst, then invalidate it synchronously before the background consumer can replay it.
            lateinit var disabling: kotlinx.coroutines.Job
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                repeat(100) { index -> controller.change(NativeVisionCapability.RELUMINO,
                    NativeVisionValue.Relumino(true, ReluminoThickness.entries[index % 5], ReluminoColor.BLACK)) }
                disabling = controller.disable(restore = true)
            }
            withTimeout(20_000) { disabling.join() }
            delay(500) // More than seven coalescing windows: old values must not reappear after disable.
            val disabled = controller.state.value
            assertFalse(disabled.profile.enabled)
            assertFalse(disabled.restoring)
            assertFalse(disabled.saving)
            assertFalse(disabled.pendingRestoration)
            assertNull(disabled.error)
            fixture.assertBaseline()
            fixture.event("DISABLED_WITHOUT_STALE_REPLAY", JSONObject().put("profileEnabled", disabled.profile.enabled)
                .put("pendingRestoration", disabled.pendingRestoration))
            fixture.save()
        } finally {
            // A cancelled test must still attempt both the journal cleanup and the profile cleanup.
            // A controller timeout must not skip the following finally blocks.
            withContext(NonCancellable) {
                try { withTimeout(20_000) { controller.disable(restore = true).join() } }
                finally {
                    try { fixture.restoreFinally(fixture.adapter()) }
                    finally {
                        if (originalEqualizer == null) {
                            controller.deletePersonalProfile()
                            controller.refresh().join()
                            assertNull("Refresh must not resurrect a deleted synthetic profile", repository.equalizerProfile.first())
                        }
                        else {
                            repository.updateNativeVision { originalNative }
                            val restored = repository.equalizerProfile.first()!!
                            assertEquals(originalNative, restored.nativeVision)
                            // updateNativeVision may update provenance time; it must not change equalizer content.
                            assertEquals(originalEqualizer.copy(nativeVision = restored.nativeVision,
                                provenance = restored.provenance), restored)
                        }
                    }
                }
            }
        }
    }
}
