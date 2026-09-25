package fr.vueconfort.app.nativevision

import org.junit.Assert.*
import org.junit.Test

class NativePlatformPolicyTest {
    @Test fun samsungManufacturerAloneDoesNotAttestFirmware() {
        assertFalse(SamsungNativeVisionAdapter.isAttestedFirmware("Samsung", "SM-S931B", 36, "new-build"))
        assertFalse(SamsungNativeVisionAdapter.isAttestedFirmware("Samsung", "SM-S921B", 36, "S931BXXSCCZH1"))
        assertFalse(SamsungNativeVisionAdapter.isAttestedFirmware("Samsung", "SM-S931B", 35, "S931BXXSCCZH1"))
        assertFalse(SamsungNativeVisionAdapter.isAttestedFirmware("Google", "SM-S931B", 36, "S931BXXSCCZH1"))
        assertTrue(SamsungNativeVisionAdapter.isAttestedFirmware("SAMSUNG", "SM-S931B", 36, "S931BXXSCCZH1"))
    }

    @Test fun missingSecureRowIsReadableButNotAStoredZero() {
        val missing = SamsungNativeVisionAdapter.readScalar { null }
        val zero = SamsungNativeVisionAdapter.readScalar { "0" }
        assertTrue(missing.readable)
        assertEquals(NativeSettingReadStatus.ABSENT, missing.status)
        assertNull(missing.rawValue)
        assertEquals(NativeSettingReadStatus.PRESENT, zero.status)
        assertEquals("0", zero.rawValue)
    }

    @Test fun refusedReadDoesNotBecomeMissingOrDisabled() {
        val result = SamsungNativeVisionAdapter.readScalar { throw SecurityException("not readable") }
        assertFalse(result.readable)
        assertEquals(NativeSettingReadStatus.INACCESSIBLE, result.status)
        assertNull(result.rawValue)
    }

    @Test fun providerFailureDoesNotBecomeAbsent() {
        val result = SamsungNativeVisionAdapter.readScalar { throw IllegalStateException("provider unavailable") }
        assertEquals(NativeSettingReadStatus.ERROR, result.status)
        assertFalse(result.readable)
    }

    @Test fun systemSettingsRouteMustPassEveryGate() {
        assertTrue(NativeSettingsActivityPolicy.isLaunchable(true, true, true, true, true))
        assertFalse(NativeSettingsActivityPolicy.isLaunchable(false, true, true, true, true))
        assertFalse(NativeSettingsActivityPolicy.isLaunchable(true, false, true, true, true))
        assertFalse(NativeSettingsActivityPolicy.isLaunchable(true, true, false, true, true))
        assertFalse(NativeSettingsActivityPolicy.isLaunchable(true, true, true, false, true))
        assertFalse(NativeSettingsActivityPolicy.isLaunchable(true, true, true, true, false))
    }

    private val baseline = NativeMagnificationSnapshot(
        enabled = true, scale = 2f, centerX = 300f, centerY = 700f,
        mode = NativeMagnificationMode.WINDOW, activationExact = true,
    )

    @Test fun magnificationSnapshotCarriesAllRestorableFields() {
        assertTrue(baseline.restorable)
        assertEquals(NativeVisionValue.Magnification(true, 2f, 300f, 700f, NativeMagnificationMode.WINDOW), baseline.asValue())
        assertTrue(baseline.copy(enabled = false).restorable)
        assertEquals(false, baseline.copy(enabled = false).asValue()!!.enabled)
    }

    @Test fun legacyInferredActivationMustNotPromiseExactRestoration() {
        assertFalse(baseline.copy(activationExact = false).restorable)
        assertNull(baseline.copy(activationExact = false).asValue())
    }

    @Test fun absentOrNonfiniteGeometryRequiresGuidanceInsteadOfInventingCenter() {
        assertFalse(baseline.copy(centerX = null).restorable)
        assertFalse(baseline.copy(centerY = Float.NaN).restorable)
        assertFalse(baseline.copy(scale = Float.POSITIVE_INFINITY).restorable)
        assertFalse(baseline.copy(mode = null).restorable)
        assertFalse(baseline.copy(centerX = -1f).restorable)
    }

    @Test fun rollbackComparisonDetectsUserChangesToEachControllerDimension() {
        assertTrue(baseline.matches(baseline.copy()))
        assertFalse(baseline.matches(baseline.copy(enabled = false)))
        assertFalse(baseline.matches(baseline.copy(scale = 3f)))
        assertFalse(baseline.matches(baseline.copy(centerX = 305f)))
        assertFalse(baseline.matches(baseline.copy(centerY = 705f)))
        assertFalse(baseline.matches(baseline.copy(mode = NativeMagnificationMode.FULLSCREEN)))
    }

    @Test fun rollbackToleranceAllowsOnlyControllerRounding() {
        assertTrue(baseline.matches(baseline.copy(scale = 2.0001f, centerX = 300.5f)))
        assertFalse(baseline.matches(baseline.copy(scale = 2.01f)))
        assertFalse(baseline.matches(baseline.copy(centerX = 301.1f)))
    }

    @Test fun baselineOutsideProfileRangeDoesNotOfferARollbackThatCannotBeRecorded() {
        assertFalse(baseline.copy(scale = 9f).restorable)
        assertNull(baseline.copy(scale = 9f).asValue())
    }

    private val inactiveWithoutViewport = NativeMagnificationSnapshot(
        enabled = false, scale = 1f, centerX = null, centerY = null,
        mode = NativeMagnificationMode.WINDOW, activationExact = true,
    )

    @Test fun exactInactiveStateWithoutViewportIsRestorableWithoutInventingACenter() {
        assertTrue(inactiveWithoutViewport.restorable)
        assertEquals(NativeVisionValue.Magnification(false, 1f, null, null, NativeMagnificationMode.WINDOW),
            inactiveWithoutViewport.asValue())
        assertFalse(inactiveWithoutViewport.copy(enabled = true).restorable)
        assertFalse(inactiveWithoutViewport.copy(activationExact = false).restorable)
    }

    @Test fun partialOrInvalidInactiveGeometryRemainsUnrestorable() {
        assertFalse(inactiveWithoutViewport.copy(centerX = 20f).restorable)
        assertFalse(inactiveWithoutViewport.copy(centerY = 20f).restorable)
        assertFalse(inactiveWithoutViewport.copy(centerX = Float.NaN, centerY = Float.NaN).restorable)
        assertFalse(inactiveWithoutViewport.copy(scale = null).restorable)
        assertFalse(inactiveWithoutViewport.copy(mode = null).restorable)
    }

    @Test fun inactiveMatchingKeepsModeAndScaleExactWithoutFabricatingRawCoordinates() {
        assertTrue(inactiveWithoutViewport.matches(inactiveWithoutViewport.copy()))
        val inactiveWithRetainedCoordinates = inactiveWithoutViewport.copy(centerX = 20f, centerY = 30f)
        assertTrue(inactiveWithoutViewport.matches(inactiveWithRetainedCoordinates))
        assertTrue(inactiveWithRetainedCoordinates.matches(inactiveWithoutViewport))
        assertNotEquals(inactiveWithoutViewport, inactiveWithRetainedCoordinates)
        assertTrue(inactiveWithoutViewport.inactiveCenterUnobservable)
        assertNull(inactiveWithoutViewport.centerX)
        assertNull(inactiveWithoutViewport.centerY)
        assertFalse(inactiveWithoutViewport.matches(inactiveWithoutViewport.copy(scale = 1.0001f)))
        assertFalse(inactiveWithoutViewport.matches(inactiveWithoutViewport.copy(mode = NativeMagnificationMode.FULLSCREEN)))
        assertFalse(inactiveWithoutViewport.matches(inactiveWithoutViewport.copy(enabled = true)))
    }

    @Test fun firstActivationUsesOnlyAProvidedPublicViewportCenter() {
        val request = NativeVisionValue.Magnification(true, 1.5f, mode = NativeMagnificationMode.WINDOW)
        val target = inactiveWithoutViewport.targetFor(request, viewportCenter = 540f to 1200f)!!
        assertEquals(540f, target.centerX!!, 0f)
        assertEquals(1200f, target.centerY!!, 0f)
        assertTrue(target.enabled && target.restorable)
        assertEquals(1.5f, target.scale!!, 0f)
        assertNull(inactiveWithoutViewport.targetFor(request))
        assertNull(inactiveWithoutViewport.targetFor(request, Float.NaN to 10f))
        assertNull(inactiveWithoutViewport.targetFor(request, -1f to 10f))
    }

    @Test fun explicitCenterAndAnExistingViewportTakePriorityOverFallbackGeometry() {
        val noCenter = NativeVisionValue.Magnification(true, 1.5f, mode = NativeMagnificationMode.WINDOW)
        val reused = baseline.targetFor(noCenter, 1f to 2f)!!
        assertEquals(baseline.centerX, reused.centerX)
        assertEquals(baseline.centerY, reused.centerY)
        val explicit = baseline.targetFor(noCenter.copy(centerX = 100f, centerY = 200f), 1f to 2f)!!
        assertEquals(100f, explicit.centerX!!, 0f)
        assertEquals(200f, explicit.centerY!!, 0f)
    }

    @Test fun inactiveTargetNeverAddsAWindowCenterFromFallback() {
        val target = inactiveWithoutViewport.targetFor(NativeVisionValue.Magnification(false, 1f,
            mode = NativeMagnificationMode.WINDOW), 540f to 1200f)!!
        assertEquals(inactiveWithoutViewport, target)
        assertNull(target.centerX)
        assertNull(target.centerY)
    }

    @Test fun journalCodecRoundTripsExplicitNullsAndPreviouslySupportedActiveGeometry() {
        val inactiveFields = NativeMagnificationSnapshotCodec.encode(inactiveWithoutViewport)
        assertTrue(inactiveFields.containsKey("centerX") && inactiveFields.containsKey("centerY"))
        assertNull(inactiveFields.getValue("centerX"))
        assertNull(inactiveFields.getValue("centerY"))
        assertEquals(inactiveWithoutViewport, NativeMagnificationSnapshotCodec.decode(inactiveFields))
        assertEquals(baseline, NativeMagnificationSnapshotCodec.decode(NativeMagnificationSnapshotCodec.encode(baseline)))
        // JSONObject returns Number implementations; the codec accepts exact numeric JSON values.
        assertEquals(inactiveWithoutViewport, NativeMagnificationSnapshotCodec.decode(inactiveFields + ("scale" to 1.0)))
    }

    @Test fun journalCodecDoesNotTurnMissingFieldsOrAnActiveUnknownCenterIntoAnInactiveState() {
        val fields = NativeMagnificationSnapshotCodec.encode(inactiveWithoutViewport)
        assertTrue(runCatching { NativeMagnificationSnapshotCodec.decode(fields - "centerX") }.isFailure)
        assertTrue(runCatching { NativeMagnificationSnapshotCodec.decode(fields + ("enabled" to true)) }.isFailure)
        assertTrue(runCatching { NativeMagnificationSnapshotCodec.decode(fields + ("centerX" to 20f)) }.isFailure)
        assertTrue(runCatching { NativeMagnificationSnapshotCodec.encode(inactiveWithoutViewport.copy(enabled = true)) }.isFailure)
    }

    @Test fun switchingOffCanLoseViewportWhileSwitchingBackOnStillRequiresAndRestoresTheFullCenter() {
        val originalFields = NativeMagnificationSnapshotCodec.encode(baseline)
        val offTarget = baseline.targetFor(NativeVisionValue.Magnification(false, 1f,
            mode = NativeMagnificationMode.WINDOW))!!
        assertTrue(offTarget.matches(inactiveWithoutViewport))
        assertTrue(inactiveWithoutViewport.inactiveCenterUnobservable)
        assertEquals(baseline, NativeMagnificationSnapshotCodec.decode(originalFields))
        val onTarget = inactiveWithoutViewport.targetFor(baseline.asValue()!!)!!
        assertEquals(baseline, onTarget)
        assertFalse(onTarget.matches(onTarget.copy(centerX = null, centerY = null)))
        assertFalse(offTarget.matches(inactiveWithoutViewport.copy(centerX = 20f)))
        assertFalse(offTarget.matches(inactiveWithoutViewport.copy(scale = 1.0001f)))
    }

    private val appliedFullscreen = NativeMagnificationSnapshot(true, 1.8f, 540f, 1170f,
        NativeMagnificationMode.FULLSCREEN, true)
    private val interruptedInactiveFullscreen = NativeMagnificationSnapshot(false, 1f, 540f, 1098f,
        NativeMagnificationMode.FULLSCREEN, true)

    @Test fun interruptedOldOffIsNotSilentlyAdoptedAsAnOwnedState() {
        assertFalse(NativeMagnificationRestorePolicy.owns(interruptedInactiveFullscreen,
            appliedFullscreen, inactiveWithoutViewport))
        assertTrue(NativeMagnificationRestorePolicy.canResumeInactive(inactiveWithoutViewport,
            appliedFullscreen, inactiveWithoutViewport, interruptedInactiveFullscreen, interruptedInactiveFullscreen))
        assertFalse(NativeMagnificationRestorePolicy.canResumeInactive(inactiveWithoutViewport,
            appliedFullscreen, inactiveWithoutViewport, interruptedInactiveFullscreen,
            interruptedInactiveFullscreen.copy(centerY = 1099f)))
        assertFalse(NativeMagnificationRestorePolicy.canResumeInactive(inactiveWithoutViewport,
            appliedFullscreen, inactiveWithoutViewport, interruptedInactiveFullscreen.copy(scale = 2f),
            interruptedInactiveFullscreen.copy(scale = 2f)))
    }

    @Test fun restoreModeTransitionUsesTheRecordedFactorAndObservedCenterBeforeFinalOff() {
        assertTrue(NativeMagnificationRestorePolicy.requiresModeTransition(inactiveWithoutViewport, interruptedInactiveFullscreen))
        val transition = NativeMagnificationRestorePolicy.modeTransitionTarget(inactiveWithoutViewport,
            interruptedInactiveFullscreen, listOf(appliedFullscreen, inactiveWithoutViewport))!!
        assertEquals(NativeMagnificationMode.WINDOW, transition.mode)
        assertEquals(1.8f, transition.scale!!, 0f)
        assertEquals(540f, transition.centerX!!, 0f)
        assertEquals(1098f, transition.centerY!!, 0f)
        assertTrue(transition.enabled)
        assertFalse(NativeMagnificationRestorePolicy.requiresModeTransition(inactiveWithoutViewport, transition))
        // Each WAL step owns only its target and its actual predecessor, with the original baseline intact.
        assertTrue(NativeMagnificationRestorePolicy.owns(transition, transition, interruptedInactiveFullscreen))
        assertTrue(NativeMagnificationRestorePolicy.owns(interruptedInactiveFullscreen, transition, interruptedInactiveFullscreen))
        assertFalse(NativeMagnificationRestorePolicy.owns(transition.copy(scale = 3f), transition, interruptedInactiveFullscreen))
        val finalObserved = transition.copy(enabled = false, scale = 1f, centerX = null, centerY = null)
        assertTrue(inactiveWithoutViewport.matches(finalObserved))
        assertTrue(NativeMagnificationRestorePolicy.owns(finalObserved, inactiveWithoutViewport, transition))
        assertFalse(inactiveWithoutViewport.matches(finalObserved.copy(mode = NativeMagnificationMode.FULLSCREEN)))
    }

    @Test fun normalActiveRestorePreparesModeWithoutAnArbitraryFactorOrGeometry() {
        val transition = NativeMagnificationRestorePolicy.modeTransitionTarget(inactiveWithoutViewport,
            appliedFullscreen, emptyList())!!
        assertEquals(appliedFullscreen.copy(mode = NativeMagnificationMode.WINDOW), transition)
        assertNull(NativeMagnificationRestorePolicy.modeTransitionTarget(inactiveWithoutViewport,
            interruptedInactiveFullscreen, listOf(inactiveWithoutViewport)))
        assertNull(NativeMagnificationRestorePolicy.modeTransitionTarget(inactiveWithoutViewport,
            appliedFullscreen.copy(centerY = null), listOf(appliedFullscreen)))
        assertFalse(NativeMagnificationRestorePolicy.requiresModeTransition(baseline, appliedFullscreen))
    }
}
