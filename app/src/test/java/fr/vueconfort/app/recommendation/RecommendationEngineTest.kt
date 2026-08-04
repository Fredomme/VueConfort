package fr.vueconfort.app.recommendation

import fr.vueconfort.app.model.UserVisualContext
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.ui.screens.QuestionnaireAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {
    @Test
    fun generateProfile_appliesAgeSymptomsAndPhotophobia() {
        val result = RecommendationEngine.generateProfile(
            answers = QuestionnaireAnswers(
                age = 65,
                screenHours = 8f,
                readingDistanceCm = 35f,
                wearsGlasses = false,
                migraines = false,
                dryEye = false,
                photophobia = true,
                symptoms = listOf("Texte trop petit", "Fatigue rapide", "Lettres qui se confondent"),
                freeComment = ""
            ),
            current = VisualProfile(),
            context = UserVisualContext()
        )
        assertEquals(23f, result.fontSizeSp)
        assertEquals(500, result.fontWeight)
        assertTrue(result.lineHeightMultiplier > 1.5f)
        assertEquals(25, result.warmthPercent)
    }
}
