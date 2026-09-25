package fr.vueconfort.app.equalizer

import fr.vueconfort.app.optical.renderPlan
import org.junit.Assert.*
import org.junit.Test

class EqualizerRenderingTest {
    @Test fun neutralAndZeroIntensityBypassAllPixelAndTextTreatments() {
        assertTrue(EqualizerPreferences.Neutral.toOpticalSettings().renderPlan().isIdentity)
        val requested = EqualizerPreferences(1.4f, .8f, 1.5f, 1f, 800, 0f)
        val record = requested.renderRecord(EqualizerScene.TEXT, 3)
        assertEquals(EqualizerRenderDecision.IDENTITY, record.decision)
        assertEquals(1.4f, record.parameters.getValue("viewportScale"), 0f)
        assertEquals(0f, record.parameters.getValue("textStrokeDp"), 0f)
        assertTrue(requested.toOpticalSettings().renderPlan().isIdentity)
    }
    @Test fun comfortIsMonotoneAndBoundedWithoutAnyGeometryOrClinicalInference() {
        val settings = (0..100).map { EqualizerPreferences(lightComfort = it / 100f).toOpticalSettings() }
        settings.zipWithNext().forEach { (a,b) -> assertTrue(b.brightness <= a.brightness); assertTrue(b.whiteReduction >= a.whiteReduction) }
        settings.forEach { assertEquals(1f, it.horizontalStretch, 0f); assertEquals(1f, it.verticalStretch, 0f); assertEquals(0f, it.cylindricalDistortion, 0f) }
        assertEquals(.82f, settings.last().brightness, .000001f)
        assertEquals(.3f, settings.last().whiteReduction, 0f)
    }
    @Test fun unsupportedPixelsReportRejectionAndActualIdentityParameters() {
        val record = EqualizerPreferences(sharpness = .5f, contrast = 1.3f).renderRecord(EqualizerScene.PHOTO, 7, false)
        assertEquals(EqualizerRenderDecision.REJECTED, record.decision)
        assertEquals(0f, record.parameters.getValue("sharpness"), 0f)
        assertEquals(1f, record.parameters.getValue("contrast"), 0f)
        assertEquals(7, record.sourceRevision)
    }
    @Test fun nonTextScenePreservesThePreferenceButDoesNotClaimTextTreatment() {
        val preferences = EqualizerPreferences(fontWeight = 750)
        assertTrue(preferences.renderRecord(EqualizerScene.TEXT, 2).parameters.getValue("textStrokeDp") > 0f)
        assertEquals(0f, preferences.renderRecord(EqualizerScene.PHOTO, 2).parameters.getValue("textStrokeDp"), 0f)
        assertEquals(750, preferences.fontWeight)
    }
}
