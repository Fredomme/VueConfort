package fr.vueconfort.app.prescription

import fr.vueconfort.app.model.PrescriptionSource
import org.junit.Assert.*
import org.junit.Test

class PrescriptionDocumentParserTest {
    @Test fun explicitLabels_keepEyesSignsAndFieldsSeparate() {
        val draft = PrescriptionDocumentParser.parse("OD SPH −2,25 CYL −0,50 AXE 90° ADD +1,50\nOG SPH +1,25 CYL +0,75 AXE 180 ADD +2,00")
        assertTrue(draft.warnings.toString(), draft.canPrefill)
        assertEquals(-2.25f, draft.rightEye.sphere)
        assertEquals(1.25f, draft.leftEye.sphere)
        assertEquals(-0.5f, draft.rightEye.cylinder)
        assertEquals(0.75f, draft.leftEye.cylinder)
        assertEquals(180, draft.leftEye.axisDegrees)
        assertEquals(1.5f, draft.rightEye.addition)
        assertTrue(draft.evidence["OD.sphere"]!!.contains("−2,25"))
    }

    @Test fun unambiguousTable_usesHeaderOrderRatherThanAssumedOrder() {
        val draft = PrescriptionDocumentParser.parse("Œil ADD AXE CYL SPH\nOD +2,00 15 −0,75 −1,25\nOG +1,50 180 +0,50 +0,75")
        assertTrue(draft.warnings.toString(), draft.canPrefill)
        assertEquals(-1.25f, draft.rightEye.sphere)
        assertEquals(2f, draft.rightEye.addition)
        assertEquals(0.75f, draft.leftEye.sphere)
    }

    @Test fun eyeHeadingAndLabelledLines_areSupported() {
        val draft = PrescriptionDocumentParser.parse("Œil   droit\nSphère −1,00\nCylindre −0,25\nAxe 80\nŒil gauche\nSphère +0,50")
        assertTrue(draft.warnings.toString(), draft.canPrefill)
        assertEquals(-1f, draft.rightEye.sphere)
        assertEquals(0.5f, draft.leftEye.sphere)
    }

    @Test fun twoEyesOnOneExplicitlyLabelledLine_areSupported() {
        val draft = PrescriptionDocumentParser.parse("OD SPH -1,25 ; OG SPH +0,75")
        assertTrue(draft.warnings.toString(), draft.canPrefill)
        assertEquals(-1.25f, draft.rightEye.sphere)
        assertEquals(0.75f, draft.leftEye.sphere)
    }

    @Test fun createdPrescriptionIsAlwaysUnconfirmed() {
        val draft = PrescriptionDocumentParser.parse("OD SPH -1")
        val candidate = draft.toUnconfirmedPrescription(PrescriptionSource.PDF, "bilan.pdf")
        assertFalse(candidate.confirmedByUser)
        assertFalse(candidate.isValid)
        assertNull(candidate.documentUri)
    }

    @Test fun multipleNearFarContexts_doNotPickOne() {
        assertRejected("VL\nOD SPH -1\nOG SPH -2\nVP\nOD SPH +1\nOG SPH +2")
    }

    @Test fun singleContextIsRetained_withoutDerivingResidual() {
        val draft = PrescriptionDocumentParser.parse("Vision de près\nOD SPH +1,25 ADD +2,00")
        assertTrue(draft.canPrefill)
        assertEquals("Vision de près", draft.nearFarContext)
        assertEquals(1.25f, draft.rightEye.sphere)
        assertEquals(2f, draft.rightEye.addition)
    }

    @Test fun duplicateEvenEqualFields_areAmbiguous() {
        assertRejected("OD SPH -1\nOD SPH -1")
        assertRejected("OD SPH -1 SPH +1")
    }

    @Test fun conflictingOrExtraTableColumns_areRejected() {
        assertRejected("SPH CYL AXE ADD\nOD -1 -0.5 90 1 2")
        assertRejected("SPH SPH AXE\nOD -1 1 90")
    }

    @Test fun unlabelledNumbersNeverBecomePrescription() {
        assertRejected("OD -1,25 (-0,50) 90\nOG +1,00")
        assertRejected("SPH -1 CYL -0.5 AXE 90")
        assertRejected("OD OG\nSPH -1 -2\nCYL -0.5 -0.75")
    }

    @Test fun octAndAcuityReports_areNotReinterpreted() {
        assertRejected("OCT maculaire\nOD épaisseur 250\nOG épaisseur 248\nAV OD 10/10 OG 8/10")
        assertRejected("Acuité visuelle\nOD 10/10\nOG 8/10")
        assertRejected("Champ visuel\nOD SPH -1")
    }

    @Test fun contactLensPrescription_isNotTreatedAsGlasses() {
        assertRejected("Lentilles de contact\nOD SPH -2 CYL -0.75 AXE 90")
    }

    @Test fun unreadableSignAndMalformedNumber_blockEntireDraft() {
        assertRejected("OD SPH ?1,25\nOG SPH +1,25")
        assertRejected("OD SPH 1,2,3\nOG SPH +1,25")
        assertRejected("OD SPH —1,25\nOG SPH +1,25")
    }

    @Test fun cylinderWithoutAxis_andAxisWithoutCylinder_areRejected() {
        assertRejected("OD SPH -1 CYL -0.5")
        assertRejected("OD AXE 90\nOG SPH -1")
    }

    @Test fun unrelatedParagraph_doesNotInheritPreviousEye() {
        assertRejected("OD SPH -1\nObservations\nADD +2")
    }

    @Test fun outOfRangeDoesNotClampValues() {
        assertRejected("OD SPH -99\nOG SPH 1")
    }

    @Test fun missingEyeRemainsUnknown_ratherThanDuplicated() {
        val draft = PrescriptionDocumentParser.parse("OD SPH -1")
        assertTrue(draft.canPrefill)
        assertNull(draft.leftEye.sphere)
    }

    @Test fun blankAndOversizedText_failClosed() {
        assertRejected("")
        assertRejected("x".repeat(100_001))
    }

    private fun assertRejected(text: String) {
        val draft = PrescriptionDocumentParser.parse(text)
        assertFalse(text, draft.canPrefill)
        assertFalse(draft.rightEye.hasValues)
        assertFalse(draft.leftEye.hasValues)
        assertTrue(draft.warnings.isNotEmpty())
    }
}
