package fr.vueconfort.app.equalizer

import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.model.PrescriptionSource
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class EqualizerProfileTest {
    @Test fun completeProfileRoundTripsExactlyIncludingSmallFloatingValuesAndIndependentLayers() {
        val profile = EqualizerProfile(
            preferences = EqualizerPreferences(1.2345678f, 0.31415927f, 0.8765432f, 0.456789f, 653, 0.654321f),
            scene = EqualizerScene.PHOTO, revision = 17,
            confirmedBilan = ConfirmedBilanReference(9123L, "PDF"),
            context = EqualizerContext(37.5f, false),
            calculated = EqualizerCalculatedParameters("test-engine-1", 17, mapOf("example" to 0.0000000001f), "SYNTHETIC_TEST_ONLY"),
            applied = EqualizerRenderRecord("agsl-perceptual-1", EqualizerRenderDecision.APPLY,
                mapOf("contrast" to 0.8765432f, "sharpness" to 0.31415927f), listOf("Valeur|avec\naccents é et = séparateurs"), 17),
            provenance = EqualizerProvenance("USER_PERCEPTUAL", 123L, 456L),
            extensions = mapOf("future.example" to "opaque|é\nno inferred diopters")
        )
        val encoded = EqualizerProfileCodec.encode(profile)
        assertEquals(profile, EqualizerProfileCodec.decode(encoded))
        assertEquals(encoded, EqualizerProfileCodec.encode(EqualizerProfileCodec.decode(encoded)!!))
        assertFalse(encoded.contains("no inferred diopters"))
    }

    @Test fun preferenceChangesNeverCreateClinicalOrCalculatedParameters() {
        val profile = EqualizerProfile(preferences = EqualizerPreferences(2f, 0.8f, 1.5f, 1f, 800, 1f))
        val restored = EqualizerProfileCodec.decode(EqualizerProfileCodec.encode(profile))!!
        assertNull(restored.confirmedBilan)
        assertNull(restored.context.declaredDistanceCm)
        assertNull(restored.context.correctionWorn)
        assertNull(restored.calculated)
        assertNull(restored.applied)
    }

    @Test fun unconfirmedOrInvalidBilanCannotBeReferenced() {
        val value = OpticalPrescription(rightEye = EyePrescription(sphere = -1.25f), source = PrescriptionSource.PDF, updatedAtMillis = 1234L)
        assertEquals(ConfirmedBilanReference(1234L, "PDF"), ConfirmedBilanReference.from(value))
        assertNull(ConfirmedBilanReference.from(value.copy(confirmedByUser = false)))
        assertNull(ConfirmedBilanReference.from(value.copy(rightEye = EyePrescription(sphere = Float.NaN))))
        assertNull(ConfirmedBilanReference.from(null))
    }

    @Test fun nonFinitePreferencesFallBackAndFiniteOutOfBoundsAreClamped() {
        assertEquals(EqualizerPreferences.Neutral, EqualizerPreferences(
            Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN, 0, Float.NaN
        ).validated())
        assertEquals(EqualizerPreferences(2f, 0.8f, 0.7f, 1f, 800, 0f),
            EqualizerPreferences(100f, 20f, -1f, 4f, 2000, -4f).validated())
    }

    @Test fun codecRejectsTruncationDuplicateKeysAndUnknownSchemaWithoutGuessing() {
        val encoded = EqualizerProfileCodec.encode(EqualizerProfile(provenance = EqualizerProvenance(createdAtMillis = 0)))
        assertNull(EqualizerProfileCodec.decode(null))
        assertNull(EqualizerProfileCodec.decode(encoded.substringBeforeLast('\n')))
        assertNull(EqualizerProfileCodec.decode(encoded + "\n" + encoded.lineSequence().first()))
        val unknown = encoded.lineSequence().map {
            if (it.startsWith(base64("schema") + "=")) base64("schema") + "=" + base64("999") else it
        }.joinToString("\n")
        assertNull(EqualizerProfileCodec.decode(unknown))
        assertNull(EqualizerProfileCodec.decode("!not-base64!=broken"))
    }

    @Test fun staleRenderAndComputedRecordsCannotMasqueradeAsCurrentRevision() {
        val base = EqualizerProfile(revision = 4)
        assertTrue(runCatching { base.copy(applied = EqualizerRenderRecord("test", EqualizerRenderDecision.APPLY, emptyMap(), sourceRevision = 3)).validated() }.isFailure)
        assertTrue(runCatching { base.copy(calculated = EqualizerCalculatedParameters("test", 3, emptyMap(), "test")).validated() }.isFailure)
        assertTrue(runCatching { base.copy(applied = EqualizerRenderRecord("test", EqualizerRenderDecision.APPLY, mapOf("bad" to Float.NaN), sourceRevision = 4)).validated() }.isFailure)
    }

    @Test fun allRenderDecisionsStayDistinctAndOnlyUserChoicesDefineUnsavedChanges() {
        for (decision in EqualizerRenderDecision.entries) {
            val profile = EqualizerProfile(applied = EqualizerRenderRecord("test", decision, emptyMap(), sourceRevision = 0))
            assertEquals(decision, EqualizerProfileCodec.decode(EqualizerProfileCodec.encode(profile))!!.applied!!.decision)
            assertTrue(profile.sameUserChoices(profile.copy(revision = 1, applied = null)))
            assertFalse(profile.sameUserChoices(profile.copy(scene = EqualizerScene.PHOTO)))
        }
    }

    @Test fun emptyVersusSavedProfileProducesExplicitDirtyState() {
        val draft = EqualizerProfile(provenance = EqualizerProvenance(createdAtMillis = 123))
        assertFalse(EqualizerUiState(draft = draft).hasUnsavedChanges)
        assertTrue(EqualizerUiState(draft = draft.copy(preferences = draft.preferences.copy(sharpness = 0.25f))).hasUnsavedChanges)
        assertFalse(EqualizerUiState(draft = draft, savedProfile = draft).hasUnsavedChanges)
    }

    private fun base64(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
