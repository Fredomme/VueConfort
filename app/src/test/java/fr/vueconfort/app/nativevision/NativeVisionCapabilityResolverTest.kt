package fr.vueconfort.app.nativevision

import org.junit.Assert.*
import org.junit.Test

class NativeVisionCapabilityResolverTest {
    private val resolver = NativeVisionCapabilityResolver()
    private val device = NativeVisionDevice("samsung", "SM-S931B", 36, "80500", "synthetic-build")
    private val relumino = NativeVisionCapability.RELUMINO
    private val knownRelumino = NativeVisionCapabilityObservation(NativeVisionPresence.PRESENT,
        "FIRMWARE_ATTESTATION_AND_EXPORTED_ROUTE", readable = true, settingsRouteAvailable = true,
        labCommandAttested = true, labPermissionGranted = true)

    private fun snapshot(variant: NativeVisionVariant = NativeVisionVariant.COMMERCIAL,
                         observation: NativeVisionCapabilityObservation = knownRelumino) =
        NativeVisionRuntimeSnapshot(device, variant, 100, mapOf(relumino to observation))

    @Test fun samsungNameAndVersionAloneNeverProveReluminoPresence() {
        val resolved = resolver.resolve(snapshot().copy(observations = emptyMap()))[relumino]!!
        assertEquals(NativeVisionPresence.UNKNOWN, resolved.presence)
        assertEquals(NativeVisionAvailability.UNAVAILABLE, resolved.availability)
        assertFalse(resolved.canApplyAutomatically)
    }

    @Test fun commercialNeverUsesLabGrantEvenWhenItIsAccidentallyPresent() {
        val resolved = resolver.resolve(snapshot())[relumino]!!
        assertEquals(NativeVisionAvailability.AVAILABLE_USER_ACTION, resolved.availability)
        assertEquals(NativeVisionEngine.SAMSUNG_SETTINGS, resolved.engine)
        assertFalse(resolved.canApplyAutomatically)
        assertTrue(resolved.readable)
    }

    @Test fun labRequiresAttestedCommandAndActualGrantAndLiveContext() {
        val permitted = resolver.resolve(snapshot(NativeVisionVariant.LAB))[relumino]!!
        assertTrue(permitted.canApplyAutomatically)
        assertEquals(NativeVisionEngine.SAMSUNG_LAB, permitted.engine)
        for (observation in listOf(knownRelumino.copy(labCommandAttested = false),
            knownRelumino.copy(labPermissionGranted = false), knownRelumino.copy(contextAllowsControl = false))) {
            assertFalse(resolver.resolve(snapshot(NativeVisionVariant.LAB, observation))[relumino]!!.canApplyAutomatically)
        }
    }

    @Test fun permissionWithdrawalIsReevaluatedInsteadOfUsingAnEarlierSnapshot() {
        assertTrue(resolver.resolve(snapshot(NativeVisionVariant.LAB))[relumino]!!.canApplyAutomatically)
        val withdrawn = resolver.resolve(snapshot(NativeVisionVariant.LAB,
            knownRelumino.copy(labPermissionGranted = false)).copy(observedAtMillis = 200))
        assertFalse(withdrawn[relumino]!!.canApplyAutomatically)
        assertEquals(200, withdrawn.observedAtMillis)
    }

    @Test fun nonSamsungStaysSupportedForAndroidMagnificationOnly() {
        val mag = NativeVisionCapability.MAGNIFICATION
        val observations = mapOf(relumino to knownRelumino, mag to NativeVisionCapabilityObservation(
            NativeVisionPresence.PRESENT, "PUBLIC_CONTROLLER", publicApiAvailable = true, publicPermissionGranted = true))
        val result = resolver.resolve(snapshot().copy(device = device.copy(manufacturer = "Google", model = "Pixel"), observations = observations))
        assertEquals(NativeVisionAvailability.UNSUPPORTED_DEVICE, result[relumino]!!.availability)
        assertEquals(NativeVisionAvailability.AVAILABLE_PUBLIC, result[mag]!!.availability)
        assertTrue(result[mag]!!.canApplyAutomatically)
    }

    @Test fun apiFloorStillAppliesToInjectedPresenceAndSamsungManufacturerIsCaseInsensitive() {
        assertEquals(NativeVisionAvailability.UNSUPPORTED_DEVICE,
            resolver.resolve(snapshot().copy(device = device.copy(sdkInt = 33)))[relumino]!!.availability)
        assertEquals(NativeVisionAvailability.AVAILABLE_USER_ACTION,
            resolver.resolve(snapshot().copy(device = device.copy(manufacturer = "SAMSUNG")))[relumino]!!.availability)
    }

    @Test fun publicMagnificationDistinguishesUserPermissionFromCurrentCommandability() {
        val mag = NativeVisionCapability.MAGNIFICATION
        val observation = NativeVisionCapabilityObservation(NativeVisionPresence.PRESENT, "PUBLIC_CONTROLLER",
            publicApiAvailable = true, publicPermissionRequestable = true)
        val base = snapshot().copy(observations = mapOf(mag to observation))
        assertEquals(NativeVisionAvailability.AVAILABLE_WITH_USER_PERMISSION, resolver.resolve(base)[mag]!!.availability)
        assertFalse(resolver.resolve(base)[mag]!!.canApplyAutomatically)
        assertTrue(resolver.resolve(base.copy(observations = mapOf(mag to observation.copy(publicPermissionGranted = true))))[mag]!!.canApplyAutomatically)
    }

    @Test fun missingSettingIsNotEvidenceOfMissingFunctionButAbsentFunctionRemainsUnavailable() {
        // Missing stored keys may mean defaults: presence comes from attested feature evidence.
        assertEquals(NativeVisionAvailability.AVAILABLE_USER_ACTION, resolver.resolve(snapshot())[relumino]!!.availability)
        assertEquals(NativeVisionAvailability.UNAVAILABLE,
            resolver.resolve(snapshot(observation = knownRelumino.copy(presence = NativeVisionPresence.ABSENT)))[relumino]!!.availability)
    }

    @Test fun mereReadabilityNeverGrantsControl() {
        val result = resolver.resolve(snapshot(observation = knownRelumino.copy(settingsRouteAvailable = false,
            labCommandAttested = false, publicApiAvailable = false)))[relumino]!!
        assertTrue(result.readable)
        assertFalse(result.canApplyAutomatically)
        assertEquals(NativeVisionAvailability.UNAVAILABLE, result.availability)
    }

    @Test fun allCapabilitiesGetAnExplicitStateWithDeviceAndProvenance() {
        val result = resolver.resolve(snapshot())
        assertEquals(NativeVisionCapability.entries.toSet(), result.capabilities.keys)
        assertEquals(device, result.device)
        assertEquals(knownRelumino.provenance, result[relumino]!!.provenance)
        assertEquals(NativeVisionCapabilities.CURRENT_SCHEMA_VERSION, result.schemaVersion)
    }
}
