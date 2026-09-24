package fr.vueconfort.app.equalizer

import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import org.junit.Assert.*
import org.junit.Test

class EqualizerStateReconciliationTest {
    @Test fun unrelatedWritesAfterDeletionNeverReuseRevisionZero() {
        val draft = EqualizerProfile(revision = 23)
        var state = EqualizerUiState(draft = draft, loaded = true)
        repeat(10) { state = state.withPersistedSnapshot(null, null) }
        assertEquals(23L, state.draft.revision)
        assertSame(draft, state.draft)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test fun cancelledCleanDraftKeepsItsNewRevisionAcrossRepeatedSavedProfileEmissions() {
        val saved = EqualizerProfile(revision = 4, preferences = EqualizerPreferences(sharpness = 0.2f))
        val cancelled = saved.copy(revision = 11)
        val current = EqualizerUiState(draft = cancelled, savedProfile = saved, loaded = true)
        val next = current.withPersistedSnapshot(saved, null)
        assertSame(cancelled, next.draft)
        assertEquals(11L, next.draft.revision)
        assertFalse(next.hasUnsavedChanges)
    }

    @Test fun genuinelyDifferentStoredInputsInvalidateReceiptAndAdvanceBeyondBothRevisions() {
        val initial = EqualizerProfile(revision = 19, applied = EqualizerRenderRecord("test", EqualizerRenderDecision.IDENTITY, emptyMap(), sourceRevision = 19))
        val saved = initial.copy(preferences = EqualizerPreferences(sharpness = 0.7f), revision = 25, applied = null)
        val next = EqualizerUiState(draft = initial, savedProfile = initial, loaded = true).withPersistedSnapshot(saved, null)
        assertEquals(26L, next.draft.revision)
        assertEquals(0.7f, next.draft.preferences.sharpness, 0f)
        assertNull(next.draft.applied)
        assertFalse(next.hasUnsavedChanges)
        assertEquals(26L, next.withPersistedSnapshot(saved, null).draft.revision)
    }

    @Test fun incomingBilanInvalidatesCurrentReceiptWithoutChangingPerceptualChoices() {
        val draft = EqualizerProfile(revision = 8, preferences = EqualizerPreferences(sharpness = 0.6f),
            applied = EqualizerRenderRecord("test", EqualizerRenderDecision.APPLY, emptyMap(), sourceRevision = 8))
        val bilan = OpticalPrescription(rightEye = EyePrescription(sphere = -1f), updatedAtMillis = 123)
        val next = EqualizerUiState(draft = draft, loaded = true).withPersistedSnapshot(null, bilan)
        assertEquals(9L, next.draft.revision)
        assertEquals(draft.preferences, next.draft.preferences)
        assertNull(next.draft.applied)
        assertEquals(ConfirmedBilanReference.from(bilan), next.draft.confirmedBilan)
        assertTrue(next.hasUnsavedChanges)
    }

    @Test fun saveEmissionBeforeCompletionPreservesInFlightDraftAndDoesNotCreateFalseDirtyState() {
        val draft = EqualizerProfile(revision = 12, preferences = EqualizerPreferences(contrast = 1.2f))
        val saved = draft.copy(provenance = draft.provenance.copy(updatedAtMillis = draft.provenance.updatedAtMillis + 1))
        val pending = EqualizerUiState(draft = draft, loaded = true, busy = true).withPersistedSnapshot(saved, null)
        assertSame(draft, pending.draft)
        assertFalse(pending.hasUnsavedChanges)
        val afterCompletion = pending.copy(busy = false).withPersistedSnapshot(saved, null)
        assertSame(draft, afterCompletion.draft)
        assertEquals(12L, afterCompletion.draft.revision)
        assertFalse(afterCompletion.hasUnsavedChanges)
    }
}
