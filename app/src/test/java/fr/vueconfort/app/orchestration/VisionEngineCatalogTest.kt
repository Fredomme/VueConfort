package fr.vueconfort.app.orchestration

import org.junit.Assert.*
import org.junit.Test

class VisionEngineCatalogTest {
    @Test fun researchMetadataCannotExecuteWithoutItsTypedInputsEvenWhenExperimentalUseIsAllowed() {
        val catalog = VisionEngineCatalog.defaultEngines()
        assertEquals(catalog.size, catalog.map { it.descriptor.engineId }.distinct().size)
        assertEquals(setOf("v2", "v2.1", "v2.2", "v2.3", "v2.4", "d4", "d5", "vision-pixels", "vision-optical", "gpu-projection"),
            VisionEngineCatalog.researchEngines.map { it.modelId }.toSet())
        VisionEngineCatalog.researchEngines.forEach { metadata ->
            val request = opticalRequest(metadata.modelId)
            val candidates = catalog.mapNotNull { it.assess(request, context) }
            assertEquals(1, candidates.size)
            assertEquals(if (metadata.modelId == "v2.4") VisionEngineAvailability.REJECTED else
                VisionEngineAvailability.INACCESSIBLE, candidates.single().availability)
            val plan = VisionOrchestrator(catalog).plan("profile", 1, context, listOf(request))
            assertEquals(if (metadata.category == VisionEngineCategory.PERCEPTUAL || metadata.modelId == "v2.4")
                VisionPlanDisposition.REJECTED else VisionPlanDisposition.UNAVAILABLE, plan.transformations.single().disposition)
            assertEquals(metadata.category, OpticalResearchVisionEngine(metadata).descriptor.category)
            assertTrue(plan.executable.isEmpty())
        }
    }

    @Test fun historicalNumericAndDeviceProofsAreNeverPromotedToHumanOrCommercialValidation() {
        VisionEngineCatalog.researchEngines.forEach { metadata ->
            assertTrue(metadata.sourceReferences.isNotEmpty())
            assertTrue(metadata.entryPoints.isNotEmpty())
            assertTrue(metadata.requiredInputs.isNotEmpty())
            assertTrue(metadata.outputContract.isNotBlank())
            assertTrue(metadata.evidence.none { it.kind in setOf(VisionEvidenceKind.PERCEPTUAL_BENEFIT_DEMONSTRATED,
                VisionEvidenceKind.PHYSICAL_EFFECT_OBSERVED, VisionEvidenceKind.COMMERCIAL_USE_VALIDATED) })
            assertEquals(VisionMaturity.EXPERIMENTAL, OpticalResearchVisionEngine(metadata).descriptor.maturity)
        }
        val v21 = VisionEngineCatalog.researchEngines.single { it.modelId == "v2.1" }
        assertTrue(v21.limitations.contains("NUMERICAL_VALIDATION_FAILED_21_OF_37_SELECTED_CASES_WORSE"))
        val d4 = VisionEngineCatalog.researchEngines.single { it.modelId == "d4" }
        assertTrue(d4.limitations.contains("NOT_A_RENDERER"))
        assertTrue(d4.evidence.none { it.kind == VisionEvidenceKind.DEVICE_EXECUTED })
        assertTrue(d4.evidence.any { it.kind == VisionEvidenceKind.UNIT_TESTED })
        assertTrue(d4.evidence.none { it.kind == VisionEvidenceKind.NUMERICAL_TESTED })
    }

    @Test fun futureTransportHasReferencesButNoImplementationOrAvailability() {
        val engine = FutureDisplayLensVisionEngine()
        assertEquals(VisionMaturity.UNAVAILABLE, engine.descriptor.maturity)
        assertTrue(engine.descriptor.evidence.isEmpty())
        assertEquals(3, VisionEngineCatalog.futureTransportReferences.size)
        val request = opticalRequest("display-lens-dl0")
        assertEquals(VisionEngineAvailability.INACCESSIBLE, engine.assess(request, context)!!.availability)
        val plan = VisionOrchestrator(VisionEngineCatalog.defaultEngines()).plan("profile", 1, context, listOf(request))
        assertEquals(VisionPlanDisposition.UNAVAILABLE, plan.transformations.single().disposition)
        assertTrue(plan.executable.isEmpty())
    }

    @Test fun anUnknownOpticalModelGetsNoSubstituteEngine() {
        val plan = VisionOrchestrator(VisionEngineCatalog.defaultEngines()).plan("profile", 1, context, listOf(opticalRequest("unknown")))
        assertEquals(VisionPlanDisposition.UNAVAILABLE, plan.transformations.single().disposition)
        assertNull(plan.transformations.single().engine)
    }

    private val context = VisionExecutionContext("test", 1, 100, VisionTransport.entries.toSet(), allowExperimental = true)
    private fun opticalRequest(model: String) = VisionRequest(model, EnginePayload.Optical(object : OpticalEngineInput {
        override val modelId = model
        override val sourceRevision = 1L
    }), VisionTransport.entries.toSet())
}
