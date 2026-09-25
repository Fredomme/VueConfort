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
}
