package fr.vueconfort.app.nativevision

import fr.vueconfort.app.equalizer.*
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class NativeVisionProfileTest {
    private val cap = NativeVisionCapability.RELUMINO
    private val value = NativeVisionValue.Relumino(true, ReluminoThickness.MAX, ReluminoColor.GREEN)

    @Test fun fiveThicknessStopsAndFourColorsHaveNoOutOfRangeRepresentation() {
        assertEquals(listOf(1f, 2f, 3f, 4f, 4.99f), ReluminoThickness.entries.map { it.value })
        assertEquals(listOf(0, 1, 2, 3), ReluminoColor.entries.map { it.value })
        for (invalid in listOf(Float.NaN, Float.NEGATIVE_INFINITY, 0f, 1.5f, 5f, 100f)) assertNull(ReluminoThickness.fromValue(invalid))
        for (invalid in listOf(-1, 4, 100)) assertNull(ReluminoColor.fromValue(invalid))
    }

    @Test fun discreteColorAndCorrectionBoundsRejectInvalidRequestsWithoutClamping() {
        for (mode in listOf(0, 11, 12, 13)) assertTrue(NativeVisionValue.ColorCorrection(true, mode).isValidFor(NativeVisionCapability.COLOR_CORRECTION))
        assertFalse(NativeVisionValue.ColorCorrection(true, 1).isValidFor(NativeVisionCapability.COLOR_CORRECTION))
        assertFalse(NativeVisionValue.ColorFilter(true, 12, 30).isValidFor(NativeVisionCapability.COLOR_FILTER))
        assertFalse(NativeVisionValue.ColorFilter(true, 0, 31).isValidFor(NativeVisionCapability.COLOR_FILTER))
        assertFalse(NativeVisionValue.ExtraDim(true, 101).isValidFor(NativeVisionCapability.EXTRA_DIM))
        assertFalse(NativeVisionValue.Magnification(true, Float.NaN).isValidFor(NativeVisionCapability.MAGNIFICATION))
        assertFalse(NativeVisionValue.Magnification(true, 2f, 20f, null).isValidFor(NativeVisionCapability.MAGNIFICATION))
    }

    @Test fun transformationsCannotBeReassignedToAnUnrelatedCapability() {
        assertFalse(value.isValidFor(NativeVisionCapability.HIGH_CONTRAST_TEXT))
        assertFalse(NativeVisionValue.Toggle(true).isValidFor(cap))
        assertFalse(NativeVisionValue.ExtraDim(true, 50).isValidFor(NativeVisionCapability.EYE_COMFORT))
        assertTrue(runCatching { NativeVisionRequestedState(mapOf(NativeVisionCapability.EXTRA_DIM to value)).validated() }.isFailure)
    }

    @Test fun absentRawSettingRemainsDifferentFromExplicitDefaultOnRollback() {
        assertEquals(NativeVisionStoredValue.Absent, NativeVisionStoredValue.fromRaw(null))
        val zero = NativeVisionStoredValue.fromRaw("0")
        assertTrue(zero.present); assertEquals("0", zero.rawValue)
        assertNotEquals(NativeVisionStoredValue.Absent, zero)
        assertTrue(runCatching { NativeVisionStoredValue(false, "0") }.isFailure)
        assertTrue(runCatching { NativeVisionStoredValue(true, null) }.isFailure)
        assertEquals("4.990", NativeVisionStoredValue.fromRaw("4.990").rawValue)
    }

    @Test fun recommendationRequestAppliedAndManualConfirmationRoundTripAsIndependentLayers() {
        val requested = NativeVisionRequestedState(mapOf(cap to value), 7, 123, "USER")
        val manualReceipt = receipt(NativeVisionApplicationStatus.NEEDS_USER_ACTION,
            confirmation = NativeVisionConfirmation.USER_CONFIRMED)
        val profile = NativeVisionProfile(enabled = true,
            preferences = NativeVisionPreferences(NativeVisionRequestedState(mapOf(cap to value.copy(thickness = ReluminoThickness.ONE)),
                provenance = "USER_ACCEPTED_SUGGESTION"), NativeVisionDisableBehavior.RESTORE_PREVIOUS),
            requested = requested, applied = NativeVisionAppliedState(mapOf(cap to manualReceipt)),
            restorationReferences = mapOf(cap to "private-journal-transaction-1"))
        val raw = NativeVisionCodec.encode(profile)
        assertEquals(profile, NativeVisionCodec.decode(raw))
        assertEquals(raw, NativeVisionCodec.encode(NativeVisionCodec.decode(raw)!!))
        assertNull(profile.applied.results[cap]!!.applied)
    }

    @Test fun automaticApplicationNeedsKnownAppliedValueAndTechnicalReadBack() {
        val base = receipt(NativeVisionApplicationStatus.APPLIED_LAB)
        assertTrue(runCatching { base.validated() }.isFailure)
        assertTrue(runCatching { base.copy(applied = value, confirmation = NativeVisionConfirmation.USER_CONFIRMED).validated() }.isFailure)
        assertEquals(value, base.copy(applied = value, confirmation = NativeVisionConfirmation.READ_BACK_CONFIRMED).validated().applied)
    }

    @Test fun capabilitiesArePersistedAsDatedSnapshotRatherThanAuthorityForFutureWrites() {
        val caps = NativeVisionCapabilityResolver().resolve(NativeVisionRuntimeSnapshot(
            NativeVisionDevice("samsung", "SM-S931B", 36, "80500", "synthetic-build"), NativeVisionVariant.LAB, 40,
            mapOf(cap to NativeVisionCapabilityObservation(NativeVisionPresence.PRESENT, "TEST", labCommandAttested = true, labPermissionGranted = true))))
        val profile = NativeVisionProfile(capabilities = caps)
        assertEquals(profile, NativeVisionCodec.decode(NativeVisionCodec.encode(profile)))
    }

    @Test fun allTypedValuesRoundTripIncludingExactCenterAndScale() {
        val values = mapOf(
            NativeVisionCapability.MAGNIFICATION to NativeVisionValue.Magnification(true, 2.1234567f, 200.125f, 900.5f, NativeMagnificationMode.WINDOW),
            cap to value, NativeVisionCapability.EXTRA_DIM to NativeVisionValue.ExtraDim(true, 38),
            NativeVisionCapability.COLOR_FILTER to NativeVisionValue.ColorFilter(true, 11, 60),
            NativeVisionCapability.COLOR_CORRECTION to NativeVisionValue.ColorCorrection(true, 12),
            NativeVisionCapability.COLOR_INVERSION to NativeVisionValue.Toggle(false),
            NativeVisionCapability.HIGH_CONTRAST_TEXT to NativeVisionValue.Toggle(true),
            NativeVisionCapability.EYE_COMFORT to NativeVisionValue.Toggle(false),
            NativeVisionCapability.SYSTEM_BRIGHTNESS to NativeVisionValue.Brightness(128),
            NativeVisionCapability.FONT_SCALE to NativeVisionValue.FontScale(1.2f),
            NativeVisionCapability.SCREEN_ZOOM to NativeVisionValue.ScreenZoom(2))
        val profile = NativeVisionProfile(requested = NativeVisionRequestedState(values))
        assertEquals(profile, NativeVisionCodec.decode(NativeVisionCodec.encode(profile)))
    }

    @Test fun migrationPreservesEqualizerBilanAndOpaqueExtensionsWithoutInventingNativeRequests() {
        val original = EqualizerProfile(revision = 7, preferences = EqualizerPreferences(sharpness = 0.6f),
            confirmedBilan = ConfirmedBilanReference(123, "PDF"), extensions = mapOf("future.foo" to "bar"))
        val legacy = EqualizerProfileCodec.encode(original).lineSequence().filterNot { it.startsWith(b64("nativeVision") + "=") }
            .map { if (it.startsWith(b64("schema") + "=")) b64("schema") + "=" + b64("1") else it }.joinToString("\n")
        val migrated = EqualizerProfileCodec.decode(legacy)!!
        assertEquals(original, migrated)
        assertEquals(2, migrated.schemaVersion)
        assertTrue(migrated.nativeVision.requested.values.isEmpty())
        assertFalse(migrated.nativeVision.enabled)
    }

    @Test fun malformedNativeSectionNeverSilentlyDropsStoredConfiguration() {
        val profile = EqualizerProfile(nativeVision = NativeVisionProfile(requested = NativeVisionRequestedState(mapOf(cap to value))))
        val corrupted = EqualizerProfileCodec.encode(profile).lineSequence().map {
            if (it.startsWith(b64("nativeVision") + "=")) b64("nativeVision") + "=" + b64("broken") else it
        }.joinToString("\n")
        assertNull(EqualizerProfileCodec.decode(corrupted))
        val raw = NativeVisionCodec.encode(profile.nativeVision)
        assertNull(NativeVisionCodec.decode(raw.substringBeforeLast('\n')))
        assertNull(NativeVisionCodec.decode(raw + "\n" + raw.lineSequence().first()))
    }

    @Test fun oldReceiptCanRemainHistoricalButCannotBelongToAFutureRevision() {
        val record = receipt(NativeVisionApplicationStatus.NEEDS_USER_ACTION)
        assertTrue(runCatching { NativeVisionProfile(applied = NativeVisionAppliedState(mapOf(cap to record))).validated() }.isFailure)
        assertTrue(runCatching { NativeVisionProfile(requested = NativeVisionRequestedState(revision = 8),
            applied = NativeVisionAppliedState(mapOf(cap to record))).validated() }.isSuccess)
        val old = NativeVisionProfile(requested = NativeVisionRequestedState(mapOf(cap to value), revision = 8),
            applied = NativeVisionAppliedState(mapOf(cap to record)))
        assertNull(old.currentResult(cap))
        assertEquals(record, old.copy(requested = old.requested.copy(revision = 7)).currentResult(cap))
        assertNull(old.copy(requested = old.requested.copy(revision = 7, values = mapOf(cap to value.copy(enabled = false)))).currentResult(cap))
    }

    private fun receipt(status: NativeVisionApplicationStatus, confirmation: NativeVisionConfirmation = NativeVisionConfirmation.UNCONFIRMED) =
        NativeVisionApplicationResult(cap, value, status, engine = NativeVisionEngine.SAMSUNG_LAB,
            provenance = "TEST", reason = "Test de modèle, pas d’effet observé.", timestampMillis = 500, profileRevision = 7,
            confirmation = confirmation)
    private fun b64(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
}
