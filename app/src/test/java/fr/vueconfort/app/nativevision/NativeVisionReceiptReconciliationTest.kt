package fr.vueconfort.app.nativevision

import org.junit.Assert.*
import org.junit.Test

class NativeVisionReceiptReconciliationTest {
    private val value = NativeVisionValue.Relumino(true, ReluminoThickness.FOUR, ReluminoColor.GREEN)
    private fun receipt(confirmation: NativeVisionConfirmation = NativeVisionConfirmation.UNCONFIRMED) =
        NativeVisionApplicationResult(NativeVisionCapability.RELUMINO, value, NativeVisionApplicationStatus.NEEDS_USER_ACTION,
            engine = NativeVisionEngine.SAMSUNG_SETTINGS, provenance = "TEST", reason = "Observation inconnue",
            timestampMillis = 20, profileRevision = 2, confirmation = confirmation)

    @Test fun unchangedUnknownReadbackPreservesUserConfirmationWithoutInventingAppliedValue() {
        val previous = receipt(NativeVisionConfirmation.USER_CONFIRMED).copy(reason = "Confirmé par vous", provenance = "USER")
        val result = reconcileNativeVerification(previous, receipt(), true, false, false)
        assertEquals(NativeVisionConfirmation.USER_CONFIRMED, result.confirmation)
        assertEquals(NativeVisionApplicationStatus.NEEDS_USER_ACTION, result.status)
        assertEquals("USER", result.provenance)
        assertNull(result.applied)
    }

    @Test fun knownContradictoryObservationRevokesManualConfirmation() {
        val previous = receipt(NativeVisionConfirmation.USER_CONFIRMED)
        val observed = receipt().copy(applied = value.copy(enabled = false))
        assertEquals(observed, reconcileNativeVerification(previous, observed, true, false, false))
    }

    @Test fun changedRevisionOrValueNeverReusesAnEarlierManualConfirmation() {
        val previous = receipt(NativeVisionConfirmation.USER_CONFIRMED)
        for (observed in listOf(receipt().copy(profileRevision = 3), receipt().copy(requested = value.copy(thickness = ReluminoThickness.ONE)))) {
            assertEquals(observed, reconcileNativeVerification(previous, observed, true, false, false))
        }
    }

    @Test fun disappearedFeatureDoesNotRemainManuallyConfirmed() {
        val previous = receipt(NativeVisionConfirmation.USER_CONFIRMED)
        val observed = receipt().copy(status = NativeVisionApplicationStatus.UNAVAILABLE)
        assertEquals(observed, reconcileNativeVerification(previous, observed, true, false, false))
    }

    @Test fun labReceiptKeepsItsCausalOriginOnlyAfterCurrentMatchingTechnicalReadback() {
        val previous = receipt(NativeVisionConfirmation.READ_BACK_CONFIRMED).copy(status = NativeVisionApplicationStatus.APPLIED_LAB,
            engine = NativeVisionEngine.SAMSUNG_LAB, applied = value)
        val observed = receipt(NativeVisionConfirmation.READ_BACK_CONFIRMED).copy(applied = value)
        val result = reconcileNativeVerification(previous, observed, true, true, true)
        assertEquals(NativeVisionApplicationStatus.APPLIED_LAB, result.status)
        assertEquals(NativeVisionEngine.SAMSUNG_LAB, result.engine)
        assertTrue(result.restorationAvailable)
        assertEquals(observed, reconcileNativeVerification(previous, observed, true, false, true))
        assertEquals(observed, reconcileNativeVerification(previous, observed, false, true, true))
        assertEquals(receipt(), reconcileNativeVerification(previous, receipt(), true, true, true))
    }
}
