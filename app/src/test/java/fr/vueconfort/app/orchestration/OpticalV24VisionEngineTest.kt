package fr.vueconfort.app.orchestration

import fr.vueconfort.app.nativevision.NativeVisionCapabilities
import fr.vueconfort.app.nativevision.NativeVisionDevice
import fr.vueconfort.app.nativevision.NativeVisionVariant
import fr.vueconfort.app.precompensation.v24.*
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class OpticalV24VisionEngineTest {
    private val engine = OpticalV24VisionEngine()
    private val context = VisionExecutionContext("preview", 7, 100,
        setOf(VisionTransport.INTERNAL_PREVIEW),
        NativeVisionCapabilities(device = NativeVisionDevice("samsung", "SM-S931B", 36),
            variant = NativeVisionVariant.COMMERCIAL, observedAtMillis = 100, capabilities = emptyMap()),
        allowExperimental = true)
    private val contract = OpticalContractV24(residual = ResidualOpticalDefectV24(0.0, 0.0, 0.0,
        ResidualOriginV24.SYNTHETIC_DIRECT, "recorded-synthetic-reference"))
    private val pixels = ScientificStimuliV24.create("TEXT")
    private val surface = OpticalV24PreviewSurface(
        DisplayObservationV24(128, 128, 1.0, true, 1080, 2340, 1080, 2340, true),
        "SM-S931B", 512, 512, 192, 192,
        LuminanceV24.decode(LuminanceV24.code8(ScientificRendererV24.BACKGROUND) / 255.0), true,
        context.sessionId, context.runtimeRevision, context.observedAtMillis)

    private fun input(c: OpticalContractV24 = contract, f: DoubleArray = pixels,
                      s: OpticalV24PreviewSurface = surface, use: OpticalV24Use = OpticalV24Use.SYNTHETIC_REFERENCE) =
        OpticalV24Input(3, c, f, s, use, "fixed-text-frame")
    private fun request(i: OpticalV24Input = input(), transports: Set<VisionTransport> = context.availableTransports) =
        VisionRequest("optical.v2.4", EnginePayload.Optical(i), transports, sourceRevision = 3)
    private fun plan(r: VisionRequest = request(), ctx: VisionExecutionContext = context) =
        VisionOrchestrator(VisionEngineCatalog.defaultEngines()).plan("profile", 3, ctx, listOf(r))

    @Test fun bundledScientificSourcesHaveTheExactRecordedBytes() {
        val directory = File("src/opticalReference")
        val lines = File(directory, "SHA256SUMS").readLines()
        assertEquals(4, lines.size)
        lines.forEach { line ->
            val parts = line.split("  ", limit = 2)
            val bytes = File(directory, "java/fr/vueconfort/app/precompensation/${parts[1]}").readBytes()
            val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(parts[1], parts[0], actual)
        }
    }

    @Test fun ordinaryProfileHasExplicitUnavailableOpticalInputsAndNoSyntheticModel() {
        val profile = VisionProfileSnapshot()
        val req = profile.opticalReadinessRequest()
        val input = (req.payload as EnginePayload.Optical).input as OpticalV24Input
        assertNull(input.contract)
        assertNull(input.linearFrame())
        val result = engine.assess(req, context)!!
        assertEquals(VisionEngineAvailability.UNAVAILABLE, result.availability)
        assertTrue(result.guardRails.contains("NEAR_RESIDUAL_UNKNOWN"))
        assertTrue(result.guardRails.contains("QUALIFIED_1_TO_1_SURFACE_UNAVAILABLE"))
    }

    @Test fun commercialContextCannotExecuteEvenCompleteSyntheticInputs() {
        val plan = plan(ctx = context.copy(allowExperimental = false))
        assertEquals(VisionPlanDisposition.UNQUALIFIED, plan.transformations.single().disposition)
        assertTrue(plan.executable.isEmpty())
        assertNull(engine.render(plan, "optical.v2.4", plan.context).linearPixels())
    }

    @Test fun syntheticModelNeverBecomesAPersonalOpticalModel() {
        val result = engine.assess(request(input(use = OpticalV24Use.PERSONAL_PREVIEW)), context)!!
        assertEquals(VisionEngineAvailability.REJECTED, result.availability)
        assertTrue(result.guardRails.contains("SYNTHETIC_MODEL_IS_NOT_PERSONAL"))
    }

    @Test fun absentNearResidualIsUnavailableEvenWithAnOrdinaryPrescription() {
        val c = contract.copy(residual = null, prescription = PrescriptionV24(-2.0, -.5, 90.0, 1.5))
        assertEquals(VisionEngineAvailability.UNAVAILABLE, engine.assess(request(input(c)), context)!!.availability)
        assertNull(engine.render(plan(request(input(c))), "optical.v2.4", context).linearPixels())
    }

    @Test fun impossibleRasterOrOpticalParametersAreRejectedWithoutSilentClamping() {
        val badContracts = listOf(contract.copy(geometry = contract.geometry.copy(viewingDistanceMm = 200.0)),
            contract.copy(pupilDiameterMm = 6.0), contract.copy(residual = contract.residual!!.copy(sphereD = 2.0)))
        badContracts.forEach { assertEquals(VisionEngineAvailability.REJECTED,
            engine.assess(request(input(it)), context)!!.availability) }
        listOf(DoubleArray(127 * 128), pixels.copyOf().also { it[1] = Double.NaN },
            pixels.copyOf().also { it[1] = 1.1 }).forEach { bad ->
            assertEquals(VisionEngineAvailability.REJECTED, engine.assess(request(input(f = bad)), context)!!.availability)
        }
    }

    @Test fun scaledClippedStaleAndUnknownSurfacesCannotQualify() {
        listOf(surface.copy(observation = surface.observation.copy(canvasDensityScale = 2.0)),
            surface.copy(observation = surface.observation.copy(canvasDensityScale = Double.NaN)),
            surface.copy(observation = surface.observation.copy(fullyVisible = false)),
            surface.copy(surroundingWidthPx = 128), surface.copy(stimulusOffsetXPx = 191),
            surface.copy(stimulusOffsetYPx = 193), surface.copy(surroundingLinearGray = .5),
            surface.copy(surroundingUniformAndVisible = false), surface.copy(runtimeRevision = 6),
            surface.copy(deviceModel = "another-device")).forEach { bad ->
            assertEquals(VisionEngineAvailability.REJECTED, engine.assess(request(input(s = bad)), context)!!.availability)
        }
        assertEquals(VisionEngineAvailability.REJECTED,
            engine.assess(request(), context.copy(nativeCapabilities = null))!!.availability)
    }

    @Test fun nativeEffectsAndGlobalTransportsCannotBeMixedIntoThisFixedPreview() {
        listOf("system-magnification", "image-color-processing", "luminance-processing", "text-weight", "layout-scaling")
            .forEach { occupied ->
                assertEquals(VisionPlanDisposition.CONFLICT,
                    plan(ctx = context.copy(occupiedDisplayDomains = setOf(occupied))).transformations.single().disposition)
            }
        val global = request(transports = setOf(VisionTransport.FUTURE_DISPLAY_LENS))
        assertEquals(VisionPlanDisposition.UNAVAILABLE, plan(global).transformations.single().disposition)
    }

    @Test fun inputPixelsAreImmutableAndIdealResidualUsesOriginalEngineExactIdentity() {
        val source = pixels.copyOf()
        val i = input(f = source)
        source.fill(0.0)
        i.linearFrame()!!.fill(1.0)
        val plan = plan(request(i))
        assertEquals(VisionPlanDisposition.APPLICABLE, plan.transformations.single().disposition)
        val frame = engine.render(plan, "optical.v2.4", context)
        assertEquals(OpticalDecisionV24.IDENTITY, frame.decision)
        assertEquals(listOf("IDEAL_EYE_IDENTITY_POLICY"), frame.reasons)
        assertArrayEquals(pixels, frame.linearPixels()!!, 0.0)
        frame.linearPixels()!!.fill(1.0)
        assertArrayEquals(pixels, frame.linearPixels()!!, 0.0)
        assertFalse(VisionOrchestrator(VisionEngineCatalog.defaultEngines()).observe(plan, emptyList(), 100).anyActive)
    }

    @Test fun changedProfileOrContextInvalidatesPendingOutput() {
        val plan = plan()
        val frame = engine.render(plan, "optical.v2.4", context)
        assertTrue(frame.mayPresent(context, "profile", 3))
        assertFalse(frame.mayPresent(context, "another-profile", 3))
        assertFalse(frame.mayPresent(context, "profile", 4))
        assertFalse(frame.mayPresent(context.copy(runtimeRevision = 8), "profile", 3))
        val refused = engine.render(plan, "optical.v2.4", context.copy(runtimeRevision = 8))
        assertEquals(OpticalDecisionV24.REJECTED, refused.decision)
        assertNull(refused.linearPixels())
    }

    @Test fun staleInputRevisionIsRejectedBeforeRendering() {
        val i = OpticalV24Input(2, contract, pixels, surface, OpticalV24Use.SYNTHETIC_REFERENCE, "frame")
        assertEquals(VisionPlanDisposition.REJECTED, plan(request(i)).transformations.single().disposition)
    }

    @Test fun boundedNonzeroCaseRetainsTheExistingRendererDecisionReasonsMetricsAndPixels() {
        val c = contract.copy(residual = contract.residual!!.copy(cylinderD = -.25, axisDegrees = 90.0))
        val direct = ScientificRendererV24(c).render(pixels, ScientificConditionV24.P)
        val frame = engine.render(plan(request(input(c))), "optical.v2.4", context)
        assertEquals(direct.decision, frame.decision)
        assertEquals(direct.reasons, frame.reasons)
        assertEquals(direct.usedResidual, frame.diagnostics!!.usedResidual)
        assertEquals(direct.mseNormal, frame.diagnostics.mseNormal, 0.0)
        assertEquals(direct.mseOutput, frame.diagnostics.mseOutput, 0.0)
        if (direct.output == null) assertNull(frame.linearPixels())
        else assertArrayEquals(direct.output, frame.linearPixels()!!, 0.0)
    }
}
