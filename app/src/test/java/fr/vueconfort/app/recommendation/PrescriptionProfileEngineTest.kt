package fr.vueconfort.app.recommendation

import fr.vueconfort.app.assessment.EyeComfortResult
import fr.vueconfort.app.assessment.ResultReliability
import fr.vueconfort.app.assessment.TestedEye
import fr.vueconfort.app.assessment.VisualComfortAssessment
import fr.vueconfort.app.calibration.CalibrationChoice
import fr.vueconfort.app.calibration.CalibrationEngine
import fr.vueconfort.app.calibration.CalibrationParameter
import fr.vueconfort.app.calibration.CalibrationSession
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.model.VisualProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionProfileEngineTest {
    @Test
    fun prescriptionOnly_createsEditableMyVisionStartingProfile() {
        val recommendation = PrescriptionProfileEngine.recommend(
            prescription = prescription(),
            currentAssistProfile = AssistProfile.defaults(10L).first(),
            currentVisualProfile = VisualProfile(fontSizeSp = 19f),
            nowMillis = 42L
        )

        assertEquals(AssistProfile.MY_VISION_ID, recommendation.assistProfile.id)
        assertEquals("Ma vue", recommendation.assistProfile.name)
        assertFalse(recommendation.assistProfile.predefined)
        assertTrue(recommendation.assistProfile.magnificationEnabled)
        assertEquals(2.25f, recommendation.assistProfile.magnificationScale, 0.001f)
        assertEquals(27f, recommendation.visualProfile.fontSizeSp, 0.001f)
        assertFalse(recommendation.visualProfile.calibrated)
        assertTrue(recommendation.usedPrescription)
        assertFalse(recommendation.usedVueConfortTests)
    }

    @Test
    fun odOgDifference_isNotAveraged_andSelectsEyeByEyeCalibration() {
        val recommendation = PrescriptionProfileEngine.recommend(
            prescription = OpticalPrescription(
                rightEye = EyePrescription(sphere = -4f, cylinder = -1f),
                leftEye = EyePrescription(sphere = 1f, cylinder = 0f)
            ),
            currentAssistProfile = AssistProfile.defaults(10L).first(),
            currentVisualProfile = VisualProfile()
        )

        assertTrue(recommendation.reasons.any { it.contains("aucune moyenne") })
        assertTrue(CalibrationParameter.CONTRAST in recommendation.calibrationPriorities)
        assertTrue(CalibrationParameter.LETTER_SPACING in recommendation.calibrationPriorities)
    }

    @Test
    fun prescriptionAndVueConfortTest_keepMostDemandingMeasuredRequirement() {
        val current = AssistProfile.defaults(10L).first()
        val recommendation = PrescriptionProfileEngine.recommend(
            prescription = prescription(),
            currentAssistProfile = current,
            currentVisualProfile = VisualProfile(),
            legacyAssessment = assessment(preferredMagnification = 4f, comfortableTextSp = 30)
        )

        assertTrue(recommendation.usedVueConfortTests)
        assertEquals(4f, recommendation.assistProfile.magnificationScale, 0.001f)
        assertEquals(30f, recommendation.visualProfile.fontSizeSp, 0.001f)
        assertTrue(recommendation.reasons.any { it.contains("bilan VueConfort") })
    }

    @Test
    fun generatedProfile_canBeCompletedByExistingCalibrationEngine() {
        val recommendation = PrescriptionProfileEngine.recommend(
            prescription = prescription(),
            currentAssistProfile = AssistProfile.defaults(10L).first(),
            currentVisualProfile = VisualProfile()
        )
        val answeredTrials = CalibrationEngine.createTrials(recommendation.visualProfile)
            .map { it.copy(choice = CalibrationChoice.OPTION_B) }
        val session = CalibrationEngine.completeSession(
            CalibrationSession(trials = answeredTrials),
            recommendation.visualProfile
        )

        assertEquals(8, answeredTrials.size)
        assertTrue(session.resultingProfile!!.calibrated)
        assertEquals(1f, session.confidenceScore, 0.001f)
        assertEquals(AssistProfile.MY_VISION_ID, session.resultingProfile!!.id)
    }

    @Test
    fun recommendation_doesNotMutateExistingProfilesOrDefaultCatalog() {
        val currentAssist = AssistProfile.defaults(10L).first()
        val currentVisual = VisualProfile(id = "existing", name = "Existant", fontSizeSp = 18f)
        val defaultsBefore = AssistProfile.defaults(10L)

        PrescriptionProfileEngine.recommend(
            prescription = prescription(),
            currentAssistProfile = currentAssist,
            currentVisualProfile = currentVisual
        )

        assertEquals(AssistProfile.STANDARD_ID, currentAssist.id)
        assertEquals("existing", currentVisual.id)
        assertEquals(defaultsBefore, AssistProfile.defaults(10L))
    }

    private fun prescription() = OpticalPrescription(
        rightEye = EyePrescription(sphere = -1f, addition = 2f),
        leftEye = EyePrescription(sphere = -1.5f, addition = 2.25f),
        recommendedReadingDistanceCm = 45,
        updatedAtMillis = 10L
    )

    private fun assessment(
        preferredMagnification: Float,
        comfortableTextSp: Int
    ): VisualComfortAssessment {
        val right = EyeComfortResult(
            eye = TestedEye.RIGHT,
            distanceCm = 40,
            withCorrection = true,
            smallestOptotypeMm = 3.5f,
            acuityScore = 60,
            errorRatePercent = 10,
            contrastScore = 80,
            overloadScore = 20,
            minimumReadableSp = 24,
            comfortableTextSp = comfortableTextSp,
            preferredMagnification = preferredMagnification,
            trialCount = 12
        )
        return VisualComfortAssessment(
            id = "test",
            createdAtMillis = 1L,
            ageRange = "",
            wearsCorrection = true,
            testedWithCorrection = true,
            usualDistanceCm = 40,
            physicalCalibrationFactor = 1f,
            physicalCalibrationConfirmed = true,
            distanceConfirmed = true,
            interruptedCount = 0,
            right = right,
            left = right.copy(eye = TestedEye.LEFT),
            both = null,
            reliability = ResultReliability.HIGH,
            doubleVision = false,
            recentDistortion = false,
            missingOrDarkArea = false
        )
    }
}
