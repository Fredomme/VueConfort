package fr.vueconfort.app.prescription

import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.ui.screens.PrescriptionDraftViewModel
import fr.vueconfort.app.ui.screens.PrescriptionEntry
import org.junit.Assert.*
import org.junit.Test

class PrescriptionDraftStateTest {
    @Test fun aNewSavedBilanDoesNotOverwriteValuesAlreadyBeingEdited() {
        val draft = PrescriptionDraftViewModel()
        draft.seed(OpticalPrescription(rightEye = EyePrescription(sphere = -1f), updatedAtMillis = 10))
        draft.right = draft.right.copy(sphere = "-1.75")
        draft.edited()
        draft.seed(OpticalPrescription(rightEye = EyePrescription(sphere = 2f), updatedAtMillis = 11))
        assertEquals("-1.75", draft.right.sphere)
        assertFalse(draft.confirmed)
    }

    @Test fun enteringManualRouteSeedsOnceWithoutDiscardingRotatedDraft() {
        val draft = PrescriptionDraftViewModel()
        val saved = OpticalPrescription(rightEye = EyePrescription(sphere = -1.25f), updatedAtMillis = 10)
        draft.enter(PrescriptionEntry.MANUAL, saved)
        assertEquals(1, draft.step)
        assertEquals("-1.25", draft.right.sphere)
        draft.right = draft.right.copy(sphere = "-2.25")
        draft.edited()
        draft.enter(PrescriptionEntry.MANUAL, saved)
        assertEquals("-2.25", draft.right.sphere)
    }

    @Test fun documentEntryDoesNotStartReadingOrConfirmAnyValues() {
        val draft = PrescriptionDraftViewModel()
        draft.enter(PrescriptionEntry.DOCUMENT, null)
        assertEquals(0, draft.step)
        assertFalse(draft.loading)
        assertFalse(draft.confirmed)
        assertNull(draft.document)
        assertEquals(PrescriptionEyeInput(), draft.right)
    }

    @Test fun everyEditReturnsToVerificationAndRevokesConfirmation() {
        val draft = PrescriptionDraftViewModel()
        draft.confirmed = true
        draft.step = 2
        draft.edited()
        assertEquals(1, draft.step)
        assertFalse(draft.confirmed)
    }
}
