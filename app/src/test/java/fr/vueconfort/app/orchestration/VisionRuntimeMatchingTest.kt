package fr.vueconfort.app.orchestration

import fr.vueconfort.app.nativevision.NativeMagnificationMode
import fr.vueconfort.app.nativevision.NativeVisionValue
import fr.vueconfort.app.nativevision.ReluminoColor
import fr.vueconfort.app.nativevision.ReluminoThickness
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
}
