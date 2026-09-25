package fr.vueconfort.app.prescription

import org.junit.Assert.*
import org.junit.Test

class PrescriptionInputValidationTest {
    private val empty = PrescriptionEyeInput()

    @Test fun unicodeMinusAndDecimalComma_keepNegativeSign() {
        val result = PrescriptionInputValidation.validate(PrescriptionEyeInput(sphere = "−2,25"), empty)
        assertTrue(result.isValid)
        assertEquals(-2.25f, result.rightEye.sphere)
    }

    @Test fun signSeparatedBySpace_isPreserved() {
        val result = PrescriptionInputValidation.validate(PrescriptionEyeInput(sphere = "− 2,25"), empty)
        assertTrue(result.isValid)
        assertEquals(-2.25f, result.rightEye.sphere)
    }

    @Test fun malformedNonemptyNumbers_areNeverSilentlyDropped() {
        listOf("1,2,3", "1.2.3", "--2", "+-2", "1e2", "NaN", "Infinity", "2 D", "?", "—2").forEach { bad ->
            val result = PrescriptionInputValidation.validate(PrescriptionEyeInput(sphere = bad), PrescriptionEyeInput(sphere = "1"))
            assertFalse(bad, result.isValid)
            assertTrue(bad, "OD.sphere" in result.fieldErrors)
        }
    }

    @Test fun negativeAxis_isRejectedRatherThanConvertedToPositive() {
        val result = PrescriptionInputValidation.validate(PrescriptionEyeInput(cylinder = "-0.5", axis = "-10"), empty)
        assertFalse(result.isValid)
        assertTrue("OD.axis" in result.fieldErrors)
    }

    @Test fun cylinderRequiresAxis_butZeroCylinderDoesNot() {
        assertFalse(PrescriptionInputValidation.validate(PrescriptionEyeInput(cylinder = "-0.5"), empty).isValid)
        assertTrue(PrescriptionInputValidation.validate(PrescriptionEyeInput(cylinder = "0"), empty).isValid)
    }

    @Test fun axisAloneAndAcuityAlone_doNotDefineCorrection() {
        assertFalse(PrescriptionInputValidation.validate(PrescriptionEyeInput(axis = "90"), empty).isValid)
        assertFalse(PrescriptionInputValidation.validate(PrescriptionEyeInput(visualAcuity = "10/10"), empty).isValid)
    }

    @Test fun positiveCylinderAndBothAxisEndpoints_arePreserved() {
        val result = PrescriptionInputValidation.validate(
            PrescriptionEyeInput(sphere = "+1,25", cylinder = "+0,50", axis = "0"),
            PrescriptionEyeInput(sphere = "-2", cylinder = "-0,75", axis = "180")
        )
        assertTrue(result.isValid)
        assertEquals(0.5f, result.rightEye.cylinder)
        assertEquals(0, result.rightEye.axisDegrees)
        assertEquals(180, result.leftEye.axisDegrees)
    }

    @Test fun outOfRangeAndNonfiniteValues_areBlocked() {
        val result = PrescriptionInputValidation.validate(
            PrescriptionEyeInput(sphere = "31", cylinder = "-13", axis = "181", addition = "-1"), empty,
            pupillaryDistance = "39", readingDistance = "101"
        )
        assertFalse(result.isValid)
        assertEquals(6, result.fieldErrors.size)
    }

    @Test fun invalidAuxiliaryField_isNotIgnored() {
        val result = PrescriptionInputValidation.validate(PrescriptionEyeInput(sphere = "-1"), empty, "6x", "4.0")
        assertFalse(result.isValid)
        assertTrue("pupillaryDistance" in result.fieldErrors)
        assertTrue("readingDistance" in result.fieldErrors)
    }

    @Test fun emptyAndZero_areDistinct() {
        val result = PrescriptionInputValidation.validate(PrescriptionEyeInput(sphere = "0", addition = "0"), empty)
        assertTrue(result.isValid)
        assertEquals(0f, result.rightEye.sphere)
        assertNull(result.leftEye.sphere)
    }
}
