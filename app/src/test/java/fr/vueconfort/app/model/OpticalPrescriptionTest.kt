package fr.vueconfort.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpticalPrescriptionTest {
    @Test
    fun validPrescription_keepsOdAndOgDistinct_andSupportsSignedDecimals() {
        val prescription = samplePrescription()

        assertTrue(prescription.isValid)
        assertEquals(-2.25f, prescription.rightEye.sphere)
        assertEquals(1.5f, prescription.leftEye.sphere)
        assertEquals(-0.75f, prescription.rightEye.cylinder)
        assertEquals(180, prescription.leftEye.axisDegrees)
        assertEquals(2.25f, prescription.leftEye.addition)
    }

    @Test
    fun missingOpticalValues_areRejected() {
        val prescription = OpticalPrescription(
            pupillaryDistanceMm = 63f,
            prescriptionDate = "2026-08-11"
        )

        assertFalse(prescription.isValid)
        assertTrue(prescription.validationErrors().any { it.contains("au moins une valeur") })
    }

    @Test
    fun invalidRangesAndDate_areReported() {
        val prescription = OpticalPrescription(
            rightEye = EyePrescription(sphere = 31f, cylinder = -13f, axisDegrees = -1),
            leftEye = EyePrescription(addition = 6.25f, axisDegrees = 181),
            pupillaryDistanceMm = 39f,
            recommendedReadingDistanceCm = 101,
            prescriptionDate = "11/08/2026"
        )

        val errors = prescription.validationErrors()
        assertFalse(prescription.isValid)
        assertTrue(errors.any { it.contains("sphère") })
        assertTrue(errors.any { it.contains("cylindre") })
        assertTrue(errors.count { it.contains("axe") } == 2)
        assertTrue(errors.any { it.contains("addition") })
        assertTrue(errors.any { it.contains("écart pupillaire") })
        assertTrue(errors.any { it.contains("distance de lecture") })
        assertTrue(errors.any { it.contains("AAAA-MM-JJ") })
    }

    @Test
    fun unconfirmedImportedValues_cannotBeUsed() {
        val prescription = OpticalPrescription(
            rightEye = EyePrescription(sphere = -1f),
            source = PrescriptionSource.PDF,
            confirmedByUser = false
        )

        assertFalse(prescription.isValid)
        assertTrue(prescription.validationErrors().any { it.contains("confirmées") })
    }

    @Test
    fun codec_roundTripsSavedValues_andNullRepresentsDeletion() {
        val original = samplePrescription()
        val saved = OpticalPrescriptionCodec.encode(original)
        val reloaded = OpticalPrescriptionCodec.decode(saved)

        assertEquals(original, reloaded)
        assertNull(OpticalPrescriptionCodec.decode(null))
        assertNull(OpticalPrescriptionCodec.decode(""))
        assertNull(OpticalPrescriptionCodec.decode("future|invalid"))
    }

    @Test
    fun codec_roundTripsDocumentMetadataAndDeclaredAcuity() {
        val original = samplePrescription().copy(
            rightEye = samplePrescription().rightEye.copy(visualAcuity = "10/10"),
            correctionType = CorrectionType.ASTIGMATISM,
            source = PrescriptionSource.PDF,
            documentUri = "content://provider/document/42",
            documentName = "ordonnance.pdf"
        )
        assertEquals(original, OpticalPrescriptionCodec.decode(OpticalPrescriptionCodec.encode(original)))
    }

    private fun samplePrescription() = OpticalPrescription(
        rightEye = EyePrescription(
            sphere = -2.25f,
            cylinder = -0.75f,
            axisDegrees = 15,
            addition = 2f
        ),
        leftEye = EyePrescription(
            sphere = 1.5f,
            cylinder = 0.5f,
            axisDegrees = 180,
            addition = 2.25f
        ),
        pupillaryDistanceMm = 63.5f,
        recommendedReadingDistanceCm = 45,
        prescriptionDate = "2026-08-11",
        notes = "Lecture écran — OD/OG distincts",
        updatedAtMillis = 1234L
    )
}
