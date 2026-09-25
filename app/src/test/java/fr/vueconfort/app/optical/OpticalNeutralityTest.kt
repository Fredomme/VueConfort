package fr.vueconfort.app.optical

import org.junit.Assert.*
import org.junit.Test

class OpticalNeutralityTest {
    private val distorted = OpticalSettings(
        enabled = true, sharpness = 0.8f, localContrast = 1.5f, gamma = 1.3f,
        brightness = 0.8f, saturation = 0.5f, temperature = 0.2f, whiteReduction = 0.3f,
        edgeEnhancement = 0.4f, horizontalStretch = 1.1f, verticalStretch = 0.9f,
        cylindricalDistortion = 0.08f, distortionAxisDegrees = 47f,
        quality = OpticalQuality.QUALITY
    )

    @Test fun disabledIgnoresStoredGeometryAndPixelTreatments() {
        assertTrue(distorted.copy(enabled = false).renderPlan().isIdentity)
    }

    @Test fun zeroIntensityIgnoresStoredGeometryAndPixelTreatments() {
        assertTrue(distorted.copy(globalIntensity = 0f).renderPlan().isIdentity)
    }

    @Test fun enabledNeutralIsAlsoIdentity() {
        assertTrue(OpticalSettings(enabled = true).renderPlan().isIdentity)
        assertTrue(OpticalSettings(enabled = true, distortionAxisDegrees = 71f).renderPlan().isIdentity)
    }

    @Test fun originalComparisonPreservesGeometryWithoutMutatingTheSettings() {
        val held = distorted.renderPlan(bypass = true)
        val adjusted = distorted.renderPlan()
        assertEquals(adjusted.horizontalScale, held.horizontalScale, 0f)
        assertEquals(adjusted.verticalScale, held.verticalScale, 0f)
        assertEquals(adjusted.distortion, held.distortion, 0f)
        assertFalse(held.hasPixelTreatments)
        assertTrue(adjusted.hasPixelTreatments)
        assertEquals(1f, distorted.globalIntensity, 0f)
    }

    @Test fun equalizerOriginalHasNoHiddenLayerOrWarp() {
        assertTrue(distorted.copy(horizontalStretch = 1f, verticalStretch = 1f,
            cylindricalDistortion = 0f).renderPlan(bypass = true).isIdentity)
    }

    @Test fun intensityInterpolatesLegacyGeometryContinuouslyToIdentity() {
        val half = distorted.copy(globalIntensity = 0.5f).renderPlan()
        assertEquals(1.05f, half.horizontalScale, 0.00001f)
        assertEquals(0.95f, half.verticalScale, 0.00001f)
        assertEquals(0.04f, half.distortion, 0.00001f)
    }

    @Test fun corruptNumbersFallBackToNeutralBeforeRendering() {
        val clean = OpticalSettings(
            enabled = true, sharpness = Float.NaN, localContrast = Float.POSITIVE_INFINITY,
            gamma = Float.NEGATIVE_INFINITY, brightness = Float.NaN,
            saturation = Float.NaN, temperature = Float.NaN, whiteReduction = Float.NaN,
            edgeEnhancement = Float.NaN, horizontalStretch = Float.NaN,
            verticalStretch = Float.NaN, cylindricalDistortion = Float.NaN,
            distortionAxisDegrees = Float.NaN, globalIntensity = Float.NaN
        ).sanitized()
        assertTrue(clean.renderPlan().isIdentity)
        assertEquals(0f, clean.sharpness, 0f)
        assertEquals(1f, clean.localContrast, 0f)
        assertEquals(1f, clean.gamma, 0f)
        assertEquals(1f, clean.horizontalStretch, 0f)
        assertEquals(0f, clean.globalIntensity, 0f)
    }

    @Test fun unsupportedEconomyOperationsDoNotPretendToChangePixels() {
        assertTrue(OpticalSettings(enabled = true, sharpness = 0.8f,
            cylindricalDistortion = 0.08f, quality = OpticalQuality.ECONOMY)
            .renderPlan().isIdentity)
    }
}
