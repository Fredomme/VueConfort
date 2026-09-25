package fr.vueconfort.app.orchestration

import fr.vueconfort.app.equalizer.EqualizerPreferences
import fr.vueconfort.app.equalizer.EqualizerScene
import fr.vueconfort.app.equalizer.renderRecord
import fr.vueconfort.app.nativevision.*
import org.junit.Assert.*
import org.junit.Test

class VisionEngineAdaptersTest {
    @Test fun allElevenNativeIdentitiesAreRoutedExactlyOnceWithoutParameterSubstitution() {
        val context = context()
        NativeVisionCapability.entries.forEach { capability ->
            val request = nativeRequest(capability)
            val candidates = VisionEngineCatalog.defaultEngines().mapNotNull { it.assess(request, context) }
            assertEquals(capability.name, 1, candidates.size)
            val candidate = candidates.single()
            assertEquals("native.${capability.name}", candidate.transformationId)
            assertEquals(if (capability == NativeVisionCapability.MAGNIFICATION) VisionEngineAvailability.READY
                else VisionEngineAvailability.NEEDS_USER_ACTION, candidate.availability)
            assertEquals(values.getValue(capability), (request.payload as EnginePayload.Native).value)
            assertFalse(candidate.identity)
        }
    }

    @Test fun androidSettingsStayDistinctFromSamsungOnlyFeatures() {
        val context = context(manufacturer = "Other")
        val engines = VisionEngineCatalog.defaultEngines()
        listOf(NativeVisionCapability.RELUMINO, NativeVisionCapability.COLOR_FILTER, NativeVisionCapability.EYE_COMFORT).forEach {
            assertEquals(VisionEngineAvailability.UNAVAILABLE,
                engines.mapNotNull { engine -> engine.assess(nativeRequest(it), context) }.single().availability)
        }
        val extraDim = AndroidSettingsGuidedVisionEngine().assess(nativeRequest(NativeVisionCapability.EXTRA_DIM), context)!!
        assertEquals(VisionTransport.ANDROID_NATIVE, extraDim.transport)
        assertEquals(VisionEngineAvailability.NEEDS_USER_ACTION, extraDim.availability)
    }

    @Test fun missingObservationsAndRevokedPublicPermissionNeverProduceAnAutomaticRoute() {
        val engine = AndroidPublicVisionEngine()
        val request = nativeRequest(NativeVisionCapability.MAGNIFICATION)
        assertEquals(VisionEngineAvailability.UNAVAILABLE, engine.assess(request, context().copy(nativeCapabilities = null))!!.availability)
        val revoked = context(publicPermission = false)
        assertEquals(VisionEngineAvailability.PERMISSION_REQUIRED, engine.assess(request, revoked)!!.availability)
        val state = revoked.nativeCapabilities!!.get(NativeVisionCapability.MAGNIFICATION)!!.copy(presence = NativeVisionPresence.UNKNOWN)
        val incoherent = revoked.copy(nativeCapabilities = revoked.nativeCapabilities.copy(capabilities = mapOf(state.capability to state)))
        assertEquals(VisionEngineAvailability.UNAVAILABLE, engine.assess(request, incoherent)!!.availability)
    }

    @Test fun aLabOnlyObservationCannotEnterTheNewCommercialOrchestration() {
        val ctx = context()
        val relumino = ctx.nativeCapabilities!!.get(NativeVisionCapability.RELUMINO)!!.copy(
            availability = NativeVisionAvailability.AVAILABLE_LAB_ONLY, canApplyAutomatically = true, engine = NativeVisionEngine.SAMSUNG_LAB)
        val laboratory = ctx.copy(nativeCapabilities = ctx.nativeCapabilities.copy(variant = NativeVisionVariant.LAB,
            capabilities = mapOf(relumino.capability to relumino)))
        val result = SamsungGuidedVisionEngine().assess(nativeRequest(NativeVisionCapability.RELUMINO), laboratory)!!
        assertEquals(VisionEngineAvailability.INACCESSIBLE, result.availability)
    }

    @Test fun requestedDisableRemainsAnActionAndDoesNotReserveAnExclusiveDomain() {
        val request = nativeRequest(NativeVisionCapability.RELUMINO).copy(
            payload = EnginePayload.Native(NativeVisionCapability.RELUMINO, NativeVisionValue.Relumino(enabled = false)))
        val result = SamsungGuidedVisionEngine().assess(request, context())!!
        assertFalse(result.identity)
        assertEquals(VisionEngineAvailability.NEEDS_USER_ACTION, result.availability)
        assertTrue(result.composition.exclusiveGroups.isEmpty())
        assertEquals(VisionEngineCategory.entries.toSet(), result.composition.compatibleCategories)
    }

    @Test fun previewMappingReusesTheExistingRendererWithoutInventingAFrameObservation() {
        val payload = EnginePayload.Perceptual(EqualizerPreferences(sizeScale = 1.4f, sharpness = .5f,
            contrast = 1.2f, lightComfort = .4f, fontWeight = 650, intensity = .7f), EqualizerScene.TEXT)
        val expected = payload.preferences.renderRecord(payload.scene, 17)
        val record = PerceptualPreviewVisionEngine().renderMapping(payload, 17, true)
        assertEquals(expected.parameters, record.parameters)
        assertEquals(expected.decision, record.decision)
        assertEquals(expected.engineVersion, record.engineVersion)
        assertEquals(17L, record.sourceRevision)
        assertFalse(record.reasons.contains("PREVIEW_DRAW_OBSERVED"))
        assertTrue(record.reasons.contains("PLAN_ONLY_NO_RENDER_RECEIPT"))
    }

    @Test fun geometryDoesNotDisappearWhenPixelIntensityIsZero() {
        val result = PerceptualPreviewVisionEngine().assess(previewRequest(EqualizerPreferences(
            sizeScale = 1.5f, sharpness = .8f, intensity = 0f)), context())!!
        assertEquals(VisionEngineAvailability.READY, result.availability)
        assertFalse(result.identity)
        assertEquals(setOf("layout-scaling"), result.composition.exclusiveGroups)
        assertTrue(PerceptualPreviewVisionEngine().assess(previewRequest(EqualizerPreferences.Neutral), context())!!.identity)
    }

    @Test fun unknownOrOldApiCannotClaimPixelEffectsAndInvalidPreferencesAreRejected() {
        val engine = PerceptualPreviewVisionEngine()
        val pixels = previewRequest(EqualizerPreferences(sharpness = .5f))
        assertEquals(VisionEngineAvailability.UNAVAILABLE, engine.assess(pixels, context(sdk = 32))!!.availability)
        assertEquals(VisionEngineAvailability.UNAVAILABLE, engine.assess(pixels, context().copy(nativeCapabilities = null))!!.availability)
        assertEquals(VisionEngineAvailability.READY, engine.assess(previewRequest(EqualizerPreferences(sizeScale = 1.5f)), context(sdk = 32))!!.availability)
        assertEquals(VisionEngineAvailability.REJECTED, engine.assess(previewRequest(EqualizerPreferences(sharpness = Float.NaN)), context())!!.availability)
        assertEquals(VisionEngineAvailability.REJECTED, engine.assess(previewRequest(EqualizerPreferences(sizeScale = 4f)), context())!!.availability)
    }

    @Test fun previewAndPublicMagnificationHaveDeclaredStageOrderButOverlappingColorEffectsConflict() {
        val orchestrator = VisionOrchestrator(VisionEngineCatalog.defaultEngines())
        val preview = previewRequest(EqualizerPreferences(sharpness = .4f))
        val plan = orchestrator.plan("profile", 1, context(), listOf(nativeRequest(NativeVisionCapability.MAGNIFICATION), preview))
        assertTrue(plan.transformations.all { it.disposition == VisionPlanDisposition.APPLICABLE })
        assertEquals("preview", plan.transformations.first().request.id)
        assertFalse(orchestrator.observe(plan, emptyList(), context().observedAtMillis).anyActive)
        val overlapping = orchestrator.plan("profile", 1, context(), listOf(preview, nativeRequest(NativeVisionCapability.RELUMINO)))
        assertEquals(VisionPlanDisposition.CONFLICT, overlapping.transformations.single { it.request.id == "RELUMINO" }.disposition)
    }

    @Test fun changingTransportDoesNotPromoteThePreviewToGlobalPixels() {
        val request = previewRequest(EqualizerPreferences(sharpness = .4f)).copy(allowedTransports = setOf(VisionTransport.FUTURE_DISPLAY_LENS))
        val plan = VisionOrchestrator(VisionEngineCatalog.defaultEngines()).plan("profile", 1, context(), listOf(request))
        assertEquals(VisionPlanDisposition.UNAVAILABLE, plan.transformations.single().disposition)
        assertEquals(VisionFallback.IDENTITY, plan.transformations.single().fallback)
    }

    private fun context(sdk: Int = 36, manufacturer: String = "samsung", publicPermission: Boolean = true): VisionExecutionContext {
        val native = NativeVisionCapabilityResolver().resolve(NativeVisionRuntimeSnapshot(
            device = NativeVisionDevice(manufacturer, "test-device", sdk), variant = NativeVisionVariant.COMMERCIAL, observedAtMillis = 100,
            observations = NativeVisionCapability.entries.associateWith { capability -> NativeVisionCapabilityObservation(
                presence = NativeVisionPresence.PRESENT, provenance = "TEST_OBSERVATION", readable = capability != NativeVisionCapability.EXTRA_DIM,
                settingsRouteAvailable = true, publicApiAvailable = capability == NativeVisionCapability.MAGNIFICATION,
                publicPermissionGranted = capability == NativeVisionCapability.MAGNIFICATION && publicPermission,
                publicPermissionRequestable = capability == NativeVisionCapability.MAGNIFICATION) }))
        return VisionExecutionContext("test", 1, 100, VisionTransport.entries.toSet(), native)
    }

    private fun nativeRequest(capability: NativeVisionCapability) = VisionRequest(capability.name,
        EnginePayload.Native(capability, values.getValue(capability)), setOf(VisionTransport.ANDROID_NATIVE, VisionTransport.SAMSUNG_NATIVE))

    private fun previewRequest(preferences: EqualizerPreferences) = VisionRequest("preview",
        EnginePayload.Perceptual(preferences, EqualizerScene.TEXT), setOf(VisionTransport.INTERNAL_PREVIEW))

    private val values = mapOf(
        NativeVisionCapability.MAGNIFICATION to NativeVisionValue.Magnification(true, 1.5f),
        NativeVisionCapability.RELUMINO to NativeVisionValue.Relumino(true, ReluminoThickness.MAX, ReluminoColor.GREEN),
        NativeVisionCapability.EXTRA_DIM to NativeVisionValue.ExtraDim(true, 30),
        NativeVisionCapability.COLOR_FILTER to NativeVisionValue.ColorFilter(true, 4, 35),
        NativeVisionCapability.COLOR_CORRECTION to NativeVisionValue.ColorCorrection(true, 12),
        NativeVisionCapability.COLOR_INVERSION to NativeVisionValue.Toggle(true),
        NativeVisionCapability.HIGH_CONTRAST_TEXT to NativeVisionValue.Toggle(true),
        NativeVisionCapability.EYE_COMFORT to NativeVisionValue.Toggle(true),
        NativeVisionCapability.SYSTEM_BRIGHTNESS to NativeVisionValue.Brightness(100),
        NativeVisionCapability.FONT_SCALE to NativeVisionValue.FontScale(1.2f),
        NativeVisionCapability.SCREEN_ZOOM to NativeVisionValue.ScreenZoom(2))
}
