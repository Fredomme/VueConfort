package fr.vueconfort.app.calibration

import fr.vueconfort.app.model.VisualProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationEngineTest {
    @Test
    fun createTrials_buildsEightBoundedComparisons() {
        val trials = CalibrationEngine.createTrials(VisualProfile(fontSizeSp = 14f))
        assertEquals(8, trials.size)
        assertTrue(trials.all { it.optionA.fontSizeSp >= 14f })
        assertTrue(trials.all { it.optionB.fontSizeSp <= 34f })
    }

    @Test
    fun noDifference_preservesVisualValuesAndIdentity() {
        val profile = VisualProfile(id = "p", name = "Test", fontSizeSp = 21f)
        val trial = CalibrationEngine.createTrials(profile).first()
        val result = CalibrationEngine.applyChoice(
            profile,
            trial,
            CalibrationChoice.NO_DIFFERENCE
        )
        assertEquals("p", result.id)
        assertEquals("Test", result.name)
        assertEquals(21f, result.fontSizeSp)
    }

    @Test
    fun confidence_combinesCompletionAndDecisiveness() {
        val trials = CalibrationEngine.createTrials(VisualProfile()).mapIndexed { index, trial ->
            when (index) {
                0 -> trial.copy(choice = CalibrationChoice.OPTION_A)
                1 -> trial.copy(choice = CalibrationChoice.NO_DIFFERENCE)
                else -> trial
            }
        }
        assertEquals(0.325f, CalibrationEngine.confidenceScore(trials), 0.0001f)
    }
}
