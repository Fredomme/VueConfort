package fr.vueconfort.app.optical

import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualCalibrationTest {
    @Test fun choicesNarrowTheBoundedInterval() {
        val calibration = PerceptualCalibration(0f, 0.8f, 0.02f)
        val before = calibration.step()
        val after = calibration.answer(PerceptualChoice.B)
        assertTrue(after.upper - after.lower < before.upper - before.lower)
        assertTrue(calibration.result() in after.lower..after.upper)
    }

    @Test fun calibrationTerminatesAndStaysBounded() {
        val calibration = PerceptualCalibration(0.9f, 1.1f, 0.005f, 5)
        repeat(5) { calibration.answer(PerceptualChoice.IDENTICAL) }
        assertTrue(calibration.isComplete())
        assertTrue(calibration.result() in 0.9f..1.1f)
    }
}
