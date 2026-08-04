package fr.vueconfort.app.recommendation

import fr.vueconfort.app.ui.screens.ComfortCalibrationAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    @Test
    fun generateAssistProfile_appliesSmallTextAndOutdoorPreferences() {
        val result = RecommendationEngine.generateAssistProfile(
            answers = ComfortCalibrationAnswers(
                preferredScale = 2f,
                difficulty = "Très petits textes",
                overlayAlpha = 0.9f,
                usages = listOf("Extérieur")
            )
        )
        assertEquals(2.5f, result.magnificationScale)
        assertEquals(0.9f, result.overlayAlpha)
        assertEquals("Extérieur", result.description)
        assertTrue(result.magnificationEnabled)
        assertTrue(result.expanded)
    }
}
