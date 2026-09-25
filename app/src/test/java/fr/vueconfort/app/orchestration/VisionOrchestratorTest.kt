package fr.vueconfort.app.orchestration

import fr.vueconfort.app.equalizer.EqualizerPreferences
import fr.vueconfort.app.equalizer.EqualizerScene
import fr.vueconfort.app.nativevision.*
import org.junit.Assert.*
import org.junit.Test

class VisionOrchestratorTest {
    private val allCategories = VisionEngineCategory.entries.toSet()
    private val context = VisionExecutionContext("session-1", 1, 100, VisionTransport.entries.toSet())
    private val magnification = VisionRequest("size", EnginePayload.Native(NativeVisionCapability.MAGNIFICATION,
        NativeVisionValue.Magnification(true, 2f)), setOf(VisionTransport.ANDROID_NATIVE), sourceRevision = 3)
    private val relumino = VisionRequest("contours", EnginePayload.Native(NativeVisionCapability.RELUMINO,
        NativeVisionValue.Relumino(true)), setOf(VisionTransport.SAMSUNG_NATIVE), sourceRevision = 3)
    private val perceptual = VisionRequest("preview", EnginePayload.Perceptual(EqualizerPreferences(contrast = 1.2f),
        EqualizerScene.TEXT), setOf(VisionTransport.INTERNAL_PREVIEW), sourceRevision = 7)
    private data class OpticalInput(override val modelId: String = "D5", override val sourceRevision: Long = 7) : OpticalEngineInput
    private val optical = VisionRequest("optical", EnginePayload.Optical(OpticalInput()), setOf(VisionTransport.FUTURE_DISPLAY_LENS))

    private fun engine(
        id: String,
        request: VisionRequest,
        category: VisionEngineCategory = when (request.payload) {
            is EnginePayload.Perceptual -> VisionEngineCategory.PERCEPTUAL
            is EnginePayload.Optical -> VisionEngineCategory.OPTICAL
            is EnginePayload.Native -> if (request == relumino) VisionEngineCategory.NATIVE_SAMSUNG else VisionEngineCategory.NATIVE_ANDROID
        },
        maturity: VisionMaturity = VisionMaturity.PROTOTYPE,
        evidence: List<VisionEvidence> = emptyList(),
        availability: VisionEngineAvailability = VisionEngineAvailability.READY,
        composition: VisionCompositionPolicy = VisionCompositionPolicy(allCategories),
        quality: Int = 50,
        cost: Int = 20,
        assessmentIdentity: Boolean = false,
        transformedId: String = request.payload.transformationId,
        runtime: ((VisionExecutionContext) -> VisionEngineAvailability)? = null
    ): VisionEngine = object : VisionEngine {
        override val descriptor = VisionEngineDescriptor(id, "1", category, maturity, evidence)
        override fun assess(candidate: VisionRequest, context: VisionExecutionContext) =
            if (candidate.payload.transformationId == request.payload.transformationId)
                VisionEngineAssessment(transformedId, runtime?.invoke(context) ?: availability,
                    request.allowedTransports.first(), "Evidence for $id", composition, quality, cost,
                    identity = assessmentIdentity)
            else null
    }

    private fun plan(engines: List<VisionEngine>, requests: List<VisionRequest>, runtime: VisionExecutionContext = context,
                     revision: Long = 7) = VisionOrchestrator(engines).plan("personal", revision, runtime, requests)

    private fun receipt(plan: VisionRenderPlan, index: Int = 0,
                        status: VisionReceiptStatus = VisionReceiptStatus.APPLIED,
                        confirmation: VisionReceiptConfirmation = VisionReceiptConfirmation.READ_BACK_CONFIRMED): VisionApplicationReceipt {
        val step = plan.transformations[index]
        return VisionApplicationReceipt(plan.profileId, plan.profileRevision, plan.context, step.request,
            step.engine!!.engineId, step.engine.version, step.transport!!, status, confirmation, 110,
            step.request.payload, "live-readback", "Observed exact request")
    }

    @Test fun noEngineProducesExplicitIdentityFallbackWithoutClaimingAnAppliedEffect() {
        val result = plan(emptyList(), listOf(magnification))
        assertEquals(VisionPlanState.IDENTITY, result.state)
        assertEquals(VisionFallback.IDENTITY, result.transformations.single().fallback)
        assertEquals(VisionPlanDisposition.UNAVAILABLE, result.transformations.single().disposition)
        assertFalse(VisionOrchestrator(emptyList()).observe(result, emptyList(), 120).anyActive)
    }

    @Test fun androidOnlyPlansExactMagnificationWithoutNeedingABilan() {
        val result = plan(listOf(engine("android", magnification)), listOf(magnification))
        assertEquals(listOf(magnification), result.executable.map { it.request })
        assertEquals(VisionTransport.ANDROID_NATIVE, result.executable.single().transport)
    }

    @Test fun samsungGuidanceIsNeverAnAutomaticCommandOrAnActiveClaimByItself() {
        val engines = listOf(engine("samsung", relumino, availability = VisionEngineAvailability.NEEDS_USER_ACTION))
        val result = plan(engines, listOf(relumino))
        assertTrue(result.executable.isEmpty())
        assertEquals(VisionFallback.USER_ACTION, result.transformations.single().fallback)
        assertFalse(VisionOrchestrator(engines).observe(result, emptyList(), 120).anyActive)
    }

    @Test fun androidSamsungAndPreviewComposeOnlyWhenEveryPairExplicitlyAllowsIt() {
        val engines = listOf(engine("android", magnification), engine("samsung", relumino), engine("preview", perceptual))
        val result = plan(engines, listOf(relumino, perceptual, magnification))
        assertEquals(3, result.executable.size)
        assertEquals(VisionPlanState.PLANNED, result.state)
    }

    @Test fun implicitCompositionIsRefusedAndDoesNotDependOnRegistryOrder() {
        val engines = listOf(engine("android", magnification, composition = VisionCompositionPolicy()),
            engine("samsung", relumino, composition = VisionCompositionPolicy()))
        val forward = plan(engines, listOf(magnification, relumino))
        val reverse = plan(engines.reversed(), listOf(relumino, magnification))
        assertEquals(forward, reverse)
        assertEquals(1, forward.executable.size)
        assertEquals(1, forward.transformations.count { it.disposition == VisionPlanDisposition.CONFLICT })
        assertEquals(VisionPlanState.PARTIAL, forward.state)
    }

    @Test fun sharedExclusiveDomainOverridesDeclaredCompatibility() {
        val policy = VisionCompositionPolicy(allCategories, setOf("geometry"))
        val result = plan(listOf(engine("android", magnification, composition = policy),
            engine("preview", perceptual, composition = policy)), listOf(magnification, perceptual))
        assertEquals(1, result.executable.size)
    }

    @Test fun physicallyActiveNativeDomainsBlockAnOtherwisePreferredPreview() {
        val policy = VisionCompositionPolicy(allCategories, setOf("image-color-processing"))
        val engines = listOf(engine("preview", perceptual, composition = policy,
            maturity = VisionMaturity.DEVICE_VALIDATED,
            evidence = listOf(VisionEvidence(VisionEvidenceKind.DEVICE_EXECUTED, "device-receipt"))))
        val available = plan(engines, listOf(perceptual))
        val occupied = plan(engines, listOf(perceptual),
            context.copy(occupiedDisplayDomains = setOf("image-color-processing")))
        assertEquals(VisionPlanDisposition.APPLICABLE, available.transformations.single().disposition)
        assertEquals(VisionPlanDisposition.CONFLICT, occupied.transformations.single().disposition)
        assertFalse(VisionOrchestrator(engines).observe(occupied,
            listOf(receipt(available, confirmation = VisionReceiptConfirmation.RENDERER_CONFIRMED)), 120).anyActive)
        assertTrue(occupied.context.occupiedDisplayDomains.contains("image-color-processing"))
    }

    @Test fun occupiedDomainsDoNotPreventNativeDisableOrAnIdentityPreview() {
        val policy = VisionCompositionPolicy(allCategories, setOf("image-color-processing"))
        val off = relumino.copy(payload = EnginePayload.Native(NativeVisionCapability.RELUMINO, NativeVisionValue.Relumino(false)))
        val neutral = perceptual.copy(payload = EnginePayload.Perceptual(EqualizerPreferences.Neutral, EqualizerScene.TEXT))
        val engines = listOf(engine("samsung", off, composition = policy),
            engine("preview", neutral, composition = policy, assessmentIdentity = true))
        val occupied = plan(engines, listOf(off, neutral), context.copy(occupiedDisplayDomains = setOf("image-color-processing")))
        assertEquals(listOf(off), occupied.executable.map { it.request })
        assertEquals(VisionPlanDisposition.IDENTITY, occupied.transformations.first { it.request == neutral }.disposition)
    }

    @Test fun fallbackCanChooseAnotherQualifiedEngineForTheSameTransformation() {
        val result = plan(listOf(engine("unavailable", magnification, availability = VisionEngineAvailability.INACCESSIBLE),
            engine("public", magnification)), listOf(magnification))
        val step = result.transformations.single()
        assertEquals("public", step.engine!!.engineId)
        assertEquals(VisionPlanDisposition.UNAVAILABLE, step.alternatives.single().disposition)
        assertTrue(step.alternatives.single().reason.isNotBlank())
    }

    @Test fun opticalRequestIsNeverSubstitutedByReluminoOrPerceptualSharpness() {
        val rogue = engine("relumino-as-optics", optical, category = VisionEngineCategory.NATIVE_SAMSUNG)
        val result = plan(listOf(engine("samsung", relumino), engine("preview", perceptual), rogue), listOf(optical))
        assertEquals(VisionPlanDisposition.REJECTED, result.transformations.single().disposition)
        assertEquals(VisionFallback.IDENTITY, result.transformations.single().fallback)
        assertTrue(result.selectedEngines.isEmpty())
    }

    @Test fun wrongTransformationIdentityIsRejectedEvenFromTheCorrectFamily() {
        val result = plan(listOf(engine("android", magnification, transformedId = relumino.payload.transformationId)), listOf(magnification))
        assertEquals(VisionPlanDisposition.REJECTED, result.transformations.single().disposition)
    }

    @Test fun experimentalOpticalEngineRequiresExplicitExperimentalContext() {
        val engines = listOf(engine("optical", optical, maturity = VisionMaturity.EXPERIMENTAL))
        assertEquals(VisionPlanDisposition.UNQUALIFIED, plan(engines, listOf(optical)).transformations.single().disposition)
        assertEquals(VisionPlanDisposition.APPLICABLE, plan(engines, listOf(optical), context.copy(allowExperimental = true)).transformations.single().disposition)
    }

    @Test fun inaccessibleResearchRemainsUnavailableEvenWithExperimentalConsent() {
        val engines = listOf(engine("D5", optical, maturity = VisionMaturity.RESEARCH, availability = VisionEngineAvailability.INACCESSIBLE))
        assertEquals(VisionPlanDisposition.UNAVAILABLE, plan(engines, listOf(optical), context.copy(allowExperimental = true)).transformations.single().disposition)
    }

    @Test fun compilationCannotProveCommercialOrDeviceValidation() {
        val compiled = listOf(VisionEvidence(VisionEvidenceKind.COMPILED, "build-result"))
        for (maturity in listOf(VisionMaturity.COMMERCIAL_VALIDATED, VisionMaturity.DEVICE_VALIDATED)) {
            val result = plan(listOf(engine("false-maturity", magnification, maturity = maturity, evidence = compiled)), listOf(magnification))
            assertEquals(VisionPlanDisposition.UNQUALIFIED, result.transformations.single().disposition)
        }
    }

    @Test fun higherValidationWinsBeforePreferenceQualityOrCost() {
        val request = magnification.copy(preferredEngineIds = listOf("experimental"))
        val engines = listOf(engine("experimental", request, maturity = VisionMaturity.EXPERIMENTAL, quality = 100, cost = 0),
            engine("device", request, maturity = VisionMaturity.DEVICE_VALIDATED,
                evidence = listOf(VisionEvidence(VisionEvidenceKind.DEVICE_EXECUTED, "device-receipt")), quality = 1, cost = 100))
        assertEquals("device", plan(engines, listOf(request), context.copy(allowExperimental = true)).transformations.single().engine!!.engineId)
    }

    @Test fun preferencesBreakAnEqualMaturityTieBeforeQualityAndCost() {
        val request = magnification.copy(preferredEngineIds = listOf("preferred"))
        val engines = listOf(engine("fast", request, quality = 100, cost = 0), engine("preferred", request, quality = 1, cost = 100))
        assertEquals("preferred", plan(engines, listOf(request)).executable.single().engine!!.engineId)
    }

    @Test fun explicitEngineRejectionAndTransportWithdrawalFailClosed() {
        val rejected = plan(listOf(engine("android", magnification, availability = VisionEngineAvailability.REJECTED)), listOf(magnification))
        assertEquals(VisionPlanDisposition.REJECTED, rejected.transformations.single().disposition)
        val unavailable = plan(listOf(engine("android", magnification)), listOf(magnification), context.copy(availableTransports = emptySet()))
        assertEquals(VisionPlanDisposition.UNAVAILABLE, unavailable.transformations.single().disposition)
    }

    @Test fun capabilityAndPermissionChangesProduceANewPlanAndInvalidateEarlierReceipts() {
        val engines = listOf(engine("android", magnification, runtime = {
            if (it.runtimeRevision == 1L) VisionEngineAvailability.READY else VisionEngineAvailability.PERMISSION_REQUIRED
        }))
        val orchestrator = VisionOrchestrator(engines)
        val original = plan(engines, listOf(magnification))
        val receipt = receipt(original)
        assertTrue(orchestrator.observe(original, listOf(receipt), 120).anyActive)
        val withdrawn = plan(engines, listOf(magnification), context.copy(runtimeRevision = 2, observedAtMillis = 120))
        assertEquals(VisionFallback.REQUEST_PERMISSION, withdrawn.transformations.single().fallback)
        assertFalse(orchestrator.observe(withdrawn, listOf(receipt), 130).anyActive)
    }

    @Test fun unchangedRuntimeRevisionCannotHideADifferentNativeCapabilitySnapshot() {
        val engines = listOf(engine("android", magnification))
        val original = plan(engines, listOf(magnification))
        val capabilities = NativeVisionCapabilities(device = NativeVisionDevice("Google", "synthetic", 36),
            variant = NativeVisionVariant.COMMERCIAL, observedAtMillis = 100, capabilities = emptyMap())
        val changed = plan(engines, listOf(magnification), context.copy(nativeCapabilities = capabilities))
        assertFalse(VisionOrchestrator(engines).observe(changed, listOf(receipt(original)), 120).anyActive)
    }

    @Test fun staleProfileSessionNativeRevisionAndRequestNeverCountAsActive() {
        val engines = listOf(engine("android", magnification))
        val orchestrator = VisionOrchestrator(engines)
        val original = plan(engines, listOf(magnification))
        val receipt = receipt(original)
        val alteredPlans = listOf(
            original.copy(profileRevision = 8), original.copy(profileId = "other"),
            original.copy(context = context.copy(sessionId = "after-restart")),
            plan(engines, listOf(magnification.copy(sourceRevision = 4))),
            plan(engines, listOf(magnification.copy(payload = EnginePayload.Native(NativeVisionCapability.MAGNIFICATION, NativeVisionValue.Magnification(true, 3f))))))
        alteredPlans.forEach { assertFalse(orchestrator.observe(it, listOf(receipt), 120).anyActive) }
    }

    @Test fun futureOldOrUnreferencedReceiptsAreNotTechnicalProof() {
        val engines = listOf(engine("android", magnification))
        val result = plan(engines, listOf(magnification))
        val orchestrator = VisionOrchestrator(engines)
        val receipt = receipt(result)
        for (invalid in listOf(receipt.copy(observedAtMillis = 99), receipt.copy(observedAtMillis = 121),
            receipt.copy(evidenceReference = ""), receipt.copy(applied = null), receipt.copy(engineVersion = "old"))) {
            assertFalse(orchestrator.observe(result, listOf(invalid), 120).anyActive)
        }
    }

    @Test fun anUnrefreshedContextCannotKeepAnOldReceiptActiveIndefinitely() {
        val engines = listOf(engine("android", magnification))
        val result = plan(engines, listOf(magnification))
        val orchestrator = VisionOrchestrator(engines)
        val receipt = receipt(result)
        assertTrue(orchestrator.observe(result, listOf(receipt), 30_110).anyActive)
        assertFalse(orchestrator.observe(result, listOf(receipt), 30_111).anyActive)
        assertTrue(orchestrator.observe(result, listOf(receipt.copy(observedAtMillis = 30_111)), 30_111).anyActive)
    }

    @Test fun confirmationMustMatchTheNativeOrPreviewTransport() {
        val engines = listOf(engine("android", magnification), engine("preview", perceptual))
        val nativePlan = plan(engines, listOf(magnification))
        val previewPlan = plan(engines, listOf(perceptual))
        val orchestrator = VisionOrchestrator(engines)
        assertFalse(orchestrator.observe(nativePlan, listOf(receipt(nativePlan, confirmation = VisionReceiptConfirmation.RENDERER_CONFIRMED)), 120).anyActive)
        assertFalse(orchestrator.observe(previewPlan, listOf(receipt(previewPlan)), 120).anyActive)
        assertTrue(orchestrator.observe(previewPlan, listOf(receipt(previewPlan, confirmation = VisionReceiptConfirmation.RENDERER_CONFIRMED)), 120).anyActive)
    }

    @Test fun manualGuidanceOnlyCountsAfterFreshTechnicalObservationNotUserAttestation() {
        val engines = listOf(engine("samsung", relumino, availability = VisionEngineAvailability.NEEDS_USER_ACTION))
        val result = plan(engines, listOf(relumino))
        val orchestrator = VisionOrchestrator(engines)
        assertFalse(orchestrator.observe(result, listOf(receipt(result)), 120).anyActive)
        assertFalse(orchestrator.observe(result, listOf(receipt(result, status = VisionReceiptStatus.USER_CONFIRMED,
            confirmation = VisionReceiptConfirmation.USER_REPORTED)), 120).anyActive)
        assertTrue(orchestrator.observe(result, listOf(receipt(result, status = VisionReceiptStatus.VERIFIED_EXISTING)), 120).anyActive)
    }

    @Test fun latestFailureSupersedesEarlierSuccessAndContradictorySimultaneousReceiptsFailClosed() {
        val engines = listOf(engine("android", magnification))
        val result = plan(engines, listOf(magnification))
        val orchestrator = VisionOrchestrator(engines)
        val success = receipt(result)
        val failure = success.copy(status = VisionReceiptStatus.FAILED, applied = null, reason = "Command failed")
        assertFalse(orchestrator.observe(result, listOf(success, failure.copy(observedAtMillis = 111)), 120).anyActive)
        assertFalse(orchestrator.observe(result, listOf(success, failure), 120).anyActive)
    }

    @Test fun nativeDisableIsExecutableDespiteOtherConflictsButNeverShownAsAnActiveTransformation() {
        val off = magnification.copy(payload = EnginePayload.Native(NativeVisionCapability.MAGNIFICATION, NativeVisionValue.Magnification(false, 1f)))
        val policy = VisionCompositionPolicy(exclusiveGroups = setOf("everything"))
        val engines = listOf(engine("android", off, composition = policy), engine("samsung", relumino, composition = policy))
        val result = plan(engines, listOf(off, relumino))
        assertEquals(2, result.executable.size)
        val index = result.transformations.indexOfFirst { it.request == off }
        assertFalse(VisionOrchestrator(engines).observe(result, listOf(receipt(result, index)), 120).anyActive)
    }

    @Test fun zeroPerceptualIntensityDoesNotEraseIndependentGeometry() {
        val enlarged = perceptual.copy(payload = EnginePayload.Perceptual(EqualizerPreferences(sizeScale = 1.5f, intensity = 0f), EqualizerScene.TEXT))
        val engines = listOf(engine("preview", enlarged))
        val result = plan(engines, listOf(enlarged))
        assertTrue(VisionOrchestrator(engines).observe(result,
            listOf(receipt(result, confirmation = VisionReceiptConfirmation.RENDERER_CONFIRMED)), 120).anyActive)
    }

    @Test fun neutralOrAdapterIdentityNeverAnnouncesAnActivePerceptualEffect() {
        val neutral = perceptual.copy(payload = EnginePayload.Perceptual(EqualizerPreferences.Neutral, EqualizerScene.TEXT))
        val engines = listOf(engine("preview", neutral, assessmentIdentity = true))
        val result = plan(engines, listOf(neutral))
        assertEquals(VisionPlanDisposition.IDENTITY, result.transformations.single().disposition)
        assertTrue(result.executable.isEmpty())
        assertFalse(VisionOrchestrator(engines).observe(result, emptyList(), 120).anyActive)
    }

    @Test fun staleOpticalParametersAndDuplicateRegistryIdsAreRejected() {
        val engines = listOf(engine("optical", optical, maturity = VisionMaturity.EXPERIMENTAL))
        assertEquals(VisionPlanDisposition.REJECTED,
            plan(engines, listOf(optical), context.copy(allowExperimental = true), revision = 8).transformations.single().disposition)
        assertThrows(IllegalArgumentException::class.java) { VisionOrchestrator(engines + engines) }
    }
}
