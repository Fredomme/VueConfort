package fr.vueconfort.app.orchestration

import fr.vueconfort.app.nativevision.NativeMagnificationMode
import fr.vueconfort.app.nativevision.NativeVisionValue
import fr.vueconfort.app.nativevision.ReluminoColor
import fr.vueconfort.app.nativevision.ReluminoThickness
import fr.vueconfort.app.nativevision.NativeVisionCapability
import fr.vueconfort.app.nativevision.NativeVisionCapabilities
import fr.vueconfort.app.nativevision.NativeVisionCapabilityState
import fr.vueconfort.app.nativevision.NativeVisionAvailability
import fr.vueconfort.app.nativevision.NativeVisionDevice
import fr.vueconfort.app.nativevision.NativeVisionEngine
import fr.vueconfort.app.nativevision.NativeVisionPresence
import fr.vueconfort.app.nativevision.NativeVisionVariant
import org.junit.Assert.*
import org.junit.Test

/** Pure readback comparisons: no Android process, permission or system mutation. */
class VisionRuntimeMatchingTest {
    private val request = NativeVisionValue.Magnification(true, 2f, 200f, 300f)

    @Test fun magnificationAllowsOnlyTheDocumentedSmallReadbackTolerance() {
        assertTrue(VisionRuntime.nativeValuesMatch(request, request.copy(scale = 2.0005f, centerX = 200.5f, centerY = 299.5f)))
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(scale = 2.01f)))
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(centerX = 202f)))
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(centerY = 302f)))
    }

    @Test fun unspecifiedCentersDoNotPretendThatAnExplicitCenterWasRequested() {
        assertTrue(VisionRuntime.nativeValuesMatch(request.copy(centerX = null, centerY = null), request))
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(centerX = null, centerY = null)))
    }

    @Test fun activationModeMissingAndNonFiniteReadbackCannotConfirmMagnification() {
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(enabled = false)))
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(mode = NativeMagnificationMode.WINDOW)))
        assertFalse(VisionRuntime.nativeValuesMatch(request, null))
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(scale = Float.NaN)))
        assertFalse(VisionRuntime.nativeValuesMatch(request, request.copy(centerX = Float.POSITIVE_INFINITY)))
    }

    @Test fun reluminoRequiresItsExactActivationThicknessAndColor() {
        val relumino = NativeVisionValue.Relumino(true, ReluminoThickness.THREE, ReluminoColor.GREEN)
        assertTrue(VisionRuntime.nativeValuesMatch(relumino, relumino.copy()))
        assertFalse(VisionRuntime.nativeValuesMatch(relumino, relumino.copy(enabled = false)))
        assertFalse(VisionRuntime.nativeValuesMatch(relumino, relumino.copy(thickness = ReluminoThickness.TWO)))
        assertFalse(VisionRuntime.nativeValuesMatch(relumino, relumino.copy(color = ReluminoColor.ADAPTIVE)))
    }

    @Test fun anotherValueFamilyCannotConfirmAnEquivalentLookingEffect() {
        assertFalse(VisionRuntime.nativeValuesMatch(NativeVisionValue.Relumino(true), NativeVisionValue.Toggle(true)))
        assertFalse(VisionRuntime.nativeValuesMatch(NativeVisionValue.ExtraDim(true, 30), NativeVisionValue.ExtraDim(true, 31)))
        assertFalse(VisionRuntime.nativeValuesMatch(request, NativeVisionValue.FontScale(2f)))
    }

    @Test fun offReadbackConfirmsOnlyTheInactiveUnitScaleAndLeavesPreferencesDormant() {
        val dormant = request.copy(enabled = false, mode = NativeMagnificationMode.WINDOW)
        val inactive = NativeVisionValue.Magnification(false, 1f, null, null, NativeMagnificationMode.FULLSCREEN)
        assertTrue(VisionRuntime.nativeValuesMatch(dormant, inactive))
        assertTrue(VisionRuntime.nativeValuesMatch(dormant, inactive.copy(centerX = 540f, centerY = 1098f)))
        assertFalse(VisionRuntime.nativeValuesMatch(dormant, inactive.copy(scale = 2f)))
        assertFalse(VisionRuntime.nativeValuesMatch(dormant, inactive.copy(scale = 1.0001f)))
        assertFalse(VisionRuntime.nativeValuesMatch(dormant, inactive.copy(enabled = true)))
        assertFalse(VisionRuntime.nativeValuesMatch(dormant, inactive.copy(centerX = 540f)))
        assertFalse(VisionRuntime.nativeValuesMatch(dormant.copy(scale = Float.NaN), inactive))
        assertEquals(2f, dormant.scale, 0f)
        assertEquals(NativeMagnificationMode.WINDOW, dormant.mode)
    }

    @Test fun reenablingRequiresTheSavedFactorModeAndExplicitCenterAgain() {
        val dormant = request.copy(enabled = false, mode = NativeMagnificationMode.WINDOW)
        val on = dormant.copy(enabled = true)
        assertTrue(VisionRuntime.nativeValuesMatch(on, on.copy()))
        assertFalse(VisionRuntime.nativeValuesMatch(on, NativeVisionValue.Magnification(false, 1f)))
        assertFalse(VisionRuntime.nativeValuesMatch(on, on.copy(mode = NativeMagnificationMode.FULLSCREEN)))
        assertFalse(VisionRuntime.nativeValuesMatch(on, on.copy(scale = 1f)))
        assertFalse(VisionRuntime.nativeValuesMatch(on, on.copy(centerX = null, centerY = null)))
    }

    @Test fun aVerifiedOffReceiptNeverBecomesAnActiveEffectInTheOrchestrator() {
        val capability = NativeVisionCapability.MAGNIFICATION
        val dormant = request.copy(enabled = false, mode = NativeMagnificationMode.WINDOW)
        assertTrue(VisionRuntime.nativeValuesMatch(dormant, NativeVisionValue.Magnification(false, 1f)))
        val capabilities = NativeVisionCapabilities(device = NativeVisionDevice("samsung", "test", 36),
            variant = NativeVisionVariant.COMMERCIAL, observedAtMillis = 100,
            capabilities = mapOf(capability to NativeVisionCapabilityState(capability, NativeVisionPresence.PRESENT,
                NativeVisionAvailability.AVAILABLE_PUBLIC, true, true, NativeVisionEngine.ANDROID_PUBLIC, "test", "available")))
        val context = VisionExecutionContext("test", 1, 100, setOf(VisionTransport.ANDROID_NATIVE), capabilities)
        val command = VisionRequest("off", EnginePayload.Native(capability, dormant), setOf(VisionTransport.ANDROID_NATIVE))
        val engine = AndroidPublicVisionEngine()
        val orchestrator = VisionOrchestrator(listOf(engine))
        val plan = orchestrator.plan("profile", 1, context, listOf(command))
        assertEquals(VisionPlanDisposition.APPLICABLE, plan.transformations.single().disposition)
        val receipt = VisionApplicationReceipt("profile", 1, context, command, engine.descriptor.engineId,
            engine.descriptor.version, VisionTransport.ANDROID_NATIVE, VisionReceiptStatus.VERIFIED_EXISTING,
            VisionReceiptConfirmation.READ_BACK_CONFIRMED, 100, command.payload, "test off readback", "Grossissement désactivé.")
        assertFalse(orchestrator.observe(plan, listOf(receipt), 100).anyActive)
    }
}
