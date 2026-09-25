package fr.vueconfort.app.orchestration

/** Technical inventory only. These references never load files or enter the personal profile. */
data class VisionResearchEngineMetadata(
    val modelId: String,
    val version: String,
    val sourceReferences: List<String>,
    val entryPoints: List<String>,
    val requiredInputs: List<String>,
    val outputContract: String,
    val evidence: List<VisionEvidence>,
    val limitations: List<String>,
    val category: VisionEngineCategory = VisionEngineCategory.OPTICAL
)

object VisionEngineCatalog {
    fun defaultEngines(): List<VisionEngine> = listOf(
        AndroidPublicVisionEngine(), SamsungGuidedVisionEngine(), AndroidSettingsGuidedVisionEngine(),
        PerceptualPreviewVisionEngine()
    ) + researchEngines.map(::OpticalResearchVisionEngine) + FutureDisplayLensVisionEngine()

    /** work/ and outputs/ refer to the research workspace; Desktop/ refers to the original desktop archive. */
    val researchEngines: List<VisionResearchEngineMetadata> = listOf(
        research(
            "v2", "2", listOf("$PRECOMPENSATION/OpticalModelV2.kt"),
            listOf("EyeOpticalModelFactory.prescription(rx: Spherocylinder, pupil: PupilModel, wavelengthNm: Double = 555, size: Int = 24): EyeOpticalModel",
                "PrecompensationEngine.render(target: DoubleArray, otf: OtfResult, p: PrecompensationParameters): PrecompensationResult"),
            listOf("Confirmed sphere/cylinder/axis", "Pupil model", "Square linear luminance target", "OTF", "PrecompensationParameters"),
            "CPU wavefront, PSF/OTF, output and simulated luminance arrays with synthetic metrics",
            "$REPORTS/OPTICAL_MODEL_V2_REPORT.md",
            listOf("SYNTHETIC_REFERENCE_CPU_ONLY", "NO_PERSONAL_OR_GLOBAL_VALIDATION")),
        research(
            "v2.1", "2.1", listOf("$PRECOMPENSATION/OpticalEngineV21.kt"),
            listOf("OpticalEngineV21.evaluate(c: SyntheticOpticalCase, size: Int = 16): V21CaseResult"),
            listOf("SyntheticOpticalCase", "Square grid size"), "Candidate selection and reconstruction metrics",
            "$REPORTS/OPTICAL_ENGINE_V2_1_REPORT.md",
            listOf("NUMERICAL_VALIDATION_FAILED_21_OF_37_SELECTED_CASES_WORSE", "6993_COMBINATIONS_ARE_NOT_HUMAN_VALIDATION",
                "INVERSE_ANGULAR_MAPPING_INCOMPLETE", "NO_HUMAN_USE_VALIDATION")),
        research(
            "v2.2", "2.2", listOf("$PRECOMPENSATION/PhysicalOpticsV22.kt"),
            listOf("PhysicalOpticsEngineV22.eyeOtf(w, pupil, wavelengthNm = 555, pupilGridSize = 16, fftSize = 64): PhysicalOtf",
                "sampleEyeOtfOnDisplayGrid(eye, display, imageWidthPx, imageHeightPx, hMin = .02): DisplaySampledOtf",
                "SafePrecompensationV22.select(target, sampled, allowCorrection = true): SafeSelectionV22"),
            listOf("Wavefront", "Pupil", "Physical display geometry", "Viewing distance", "Square linear luminance"),
            "Display-sampled OTF and guarded synthetic precompensation selection", "$REPORTS/OPTICAL_ENGINE_V2_2_REPORT.md",
            listOf("SYNTHETIC_NUMERICAL_BATTERY_ONLY", "NO_REALTIME_OR_HUMAN_VALIDATION")),
        research(
            "v2.3", "2.3", listOf("$PRECOMPENSATION/VisionCalibrationV23.kt", "$PRECOMPENSATION/P0OpticalFrameRendererV23.kt"),
            listOf("HumanVisionCalibrationController.calculate(professional, measurements, refinementRaw, conditions, pupilMm = 3.0, strategy = BINOCULAR_COMPROMISE, bypassCache = false): CalibrationComputationV23",
                "P0OpticalFrameRendererV23(size = 64).render(input: DoubleArray, contract: V23FrequencyRenderContract): P0RenderedOpticalFrameV23"),
            listOf("ProfessionalVisionData?", "VueConfortVisionMeasurements?", "PerceptualRefinementV23",
                "CalibrationConditionsV23", "V23FrequencyRenderContract", "64 × 64 luminance"),
            "Separate OD/OG calculation, decisions and arrays; P0 fixed synthetic model bridge",
            "$REPORTS/VISION_CALIBRATION_V2_3_REPORT.md",
            listOf("P0_DEFAULT_MODEL_IS_SYNTHETIC", "PIXEL_PROOF_IS_NOT_OPTICAL_BENEFIT", "NO_RECORDED_HUMAN_PERCEPTUAL_VOTE"),
            deviceProof = "$REPORTS/V2_3_RENDER_PIPELINE_DIAGNOSTIC.md"),
        research(
            "v2.4", "2.4", listOf("$PRECOMPENSATION/v24/OpticalContractV24.kt",
                "$PRECOMPENSATION/v24/OpticalKernelV24.kt", "$PRECOMPENSATION/v24/ScientificRendererV24.kt",
                "work/v24-controls/project/app/src/debug/java/fr/vueconfort/app/capturev24/CapturePipelineV24.kt"),
            listOf("ScientificRendererV24(contract: OpticalContractV24).render(input: DoubleArray, condition: ScientificConditionV24): RenderResultV24",
                "CapturePipelineV24.linearGray(pixels: IntArray): DoubleArray",
                "CapturePipelineV24.validGeometry(width, height, nativeWidth, nativeHeight, rotation)"),
            listOf("OpticalContractV24", "Physical geometry and residual model", "Exactly 128 × 128 linear relative SRGB_D65", "ScientificConditionV24"),
            "APPLY/IDENTITY/REJECTED, nullable output, rejection reasons, residual, MSE and integration/clipping metrics",
            "outputs/v24-capture/Bilan.md",
            listOf("FROZEN_PATCH_ONLY", "FOUR_CONDITIONS_N_G_P_S", "FFT_256_512_CONTRACT", "NO_GLOBAL_REALTIME_OR_HUMAN_VALIDATION"),
            deviceProof = "outputs/v24-capture/Bilan.md"),
        research(
            "d4", "d4-experimental", listOf("$PHASE_D/IndividualVisualModelD4Experimental.kt"),
            listOf("IndividualVisualModelAssemblerV3Experimental.assemble(policy, v3, d1, d2, d3, calibration, prescription, modelId, nowMillis): IndividualVisualModelV3Experimental"),
            listOf("D4SelectionPolicy", "V3 archive", "D1/D2/D3 recorded sessions", "DisplayCalibrationProfile", "OpticalPrescription?"),
            "Provenance-aware model aggregate and eligibility; not a pixel renderer",
            "$LEGACY_TESTS/TEST-fr.vueconfort.app.assessment.v3.phaseD.IndividualVisualModelD4Test.xml",
            listOf("8_EXISTING_UNIT_TESTS_PASSED_2026_09_24", "NO_MEASURED_PSF_MTF_OR_KERNEL", "NOT_A_RENDERER", "DEVICE_AND_PERCEPTUAL_EVIDENCE_NOT_FOUND"),
            proofKind = VisionEvidenceKind.UNIT_TESTED),
        research(
            "d5", "d5-experimental", listOf("$PHASE_D/PreCompensationD5Experimental.kt",
                "work/vueconfort-commercial/project/app/src/debug/java/fr/vueconfort/app/assessment/v3/phaseD/D5V23RenderAdapter.kt"),
            listOf("DegradationModelBuilderD5.build(d4, distanceCm, uncertaintyCm, deviceModel, calibrationId): VisualDegradationModelD5Experimental",
                "PreCompensationOperatorD5Experimental.render(input: FloatImageD5, model: VisualDegradationModelD5Experimental, requestedStrength: Double): D5RenderResult",
                "D5V23RenderAdapter(size = 64).render(bundle, calibration, input: DoubleArray, enabled): D5V23PipelineResult"),
            listOf("D4 model", "Distance and uncertainty", "Matching device/calibration identities", "FloatImageD5", "Requested strength"),
            "Bounded Gaussian first-order unsharp and model/identity/rejection proof; V2.3 bridge limited to DEV_SYNTHETIC",
            "$LEGACY_TESTS/TEST-fr.vueconfort.app.assessment.v3.phaseD.PreCompensationD5Test.xml",
            listOf("11_EXISTING_UNIT_TESTS_PASSED_2026_09_24", "GAUSSIAN_HEURISTIC_NOT_OCULAR_PSF", "NO_D3_ANISOTROPY_OR_D2_ADD", "NO_PERSONAL_DEVICE_PROOF"),
            proofKind = VisionEvidenceKind.UNIT_TESTED),
        research(
            "vision-pixels", "0.1", listOf("work/vueconfort-vision/project/app/src/main/java/fr/vueconfort/vision/VisionPixelRenderer.kt"),
            listOf("VisionPixelRenderer.apply(pixels: IntArray, width: Int, height: Int, brightness: Double = 0, contrast: Double = 1, contours: Double = 0): IntArray"),
            listOf("Opaque ARGB pixels", "Final dimensions", "Brightness/contrast/contour controls"),
            "New ARGB array, linear-luminance brightness/contrast and 3 × 3 unsharp", "outputs/vueconfort-vision/Mode-emploi-et-bilan.md",
            listOf("24_HISTORICAL_UNIT_TESTS_PASSED", "PERCEPTUAL_HEURISTIC_NOT_OPTICAL", "SEPARATE_APPLICATION_AND_PROFILE", "PARTIAL_PANEL_NOT_GLOBAL_DISPLAY", "NO_PERCEPTUAL_BENEFIT_PROOF"),
            deviceProof = "outputs/vueconfort-vision/Mode-emploi-et-bilan.md", proofKind = VisionEvidenceKind.UNIT_TESTED,
            category = VisionEngineCategory.PERCEPTUAL),
        research(
            "vision-optical", "0.2", listOf("work/vision-optical/project/app/src/main/java/fr/vueconfort/vision/OpticalExperiment.kt"),
            listOf("OpticalExperiment.process(argb: IntArray, distanceMm: Double, hypothesis: OpticalHypothesis, onProgress, isCancelled): OpticalResult"),
            listOf("Exactly 768 × 256 opaque ARGB", "Declared distance", "Synthetic spherical hypothesis .15/.25/.40 D", "Nominal S25 pixel pitch"),
            "Baseline/candidate arrays, applied decision, explanation, metrics and report",
            "outputs/vueconfort-vision-0.2/Verification-independante.json",
            listOf("FIXED_IMAGE_ONLY", "FIXED_3_MM_PUPIL_555_NM", "NO_CYLINDER", "NO_HYPOTHESIS_ASSIGNED_TO_USER", "NO_RECORDED_PERCEPTUAL_VOTE"),
            deviceProof = "outputs/vueconfort-vision-0.2/Verification-sur-S25.json"),
        VisionResearchEngineMetadata(
            modelId = "gpu-projection", version = "historical-debug",
            sourceReferences = listOf("Desktop/VueConfort-S25/app/src/debug/java/fr/vueconfort/app/mediaprojection/GpuProjectionRenderer.kt",
                "Desktop/VueConfort-S25/app/src/debug/java/fr/vueconfort/app/mediaprojection/OpticalGpuParameters.kt"),
            entryPoints = listOf("GpuProjectionRenderer(captureW, captureH, outputW, outputH, outputTexture, onInputReady, onMetrics)",
                "start(); setOptical(OpticalGpuParameters); setZoom(...); setPaused(...); stop()"),
            requiredInputs = listOf("MediaProjection/OES Surface", "Output SurfaceTexture", "ExperimentalOpticalParameters"),
            outputContract = "EGL/GLES partial TextureView panel and loop metrics; no measured display latency",
            evidence = listOf(VisionEvidence(VisionEvidenceKind.CODE_PRESENT,
                "Desktop/VueConfort-S25/app/src/debug/java/fr/vueconfort/app/mediaprojection/GpuProjectionRenderer.kt"),
                VisionEvidence(VisionEvidenceKind.DEVICE_EXECUTED, "outputs/vueconfort-global-research/Audit-code-existant.md")),
            limitations = listOf("PARTIAL_CAPTURE_ROI", "LOOP_FREQUENCY_NOT_DISPLAY_CADENCE", "LIFECYCLE_RESIZE_STATIC_FRAME_GAPS",
                "NO_QUALIFIED_OCULAR_MODEL", "NO_PERCEPTUAL_BENEFIT_PROOF"), category = VisionEngineCategory.DISPLAY_TRANSPORT)
    )

    val futureTransportReferences = listOf(
        "outputs/vueconfort-display-lens/Premier-prototype-DL0.md",
        "outputs/vueconfort-display-lens/Contrat-OEM-minimal.md",
        "outputs/vueconfort-display-lens/Rapport-architecture-finale-candidate.md")
}

private const val PRECOMPENSATION = "work/vueconfort-commercial/project/app/src/debug/java/fr/vueconfort/app/precompensation"
private const val PHASE_D = "work/vueconfort-commercial/project/app/src/main/java/fr/vueconfort/app/assessment/v3/phaseD"
private const val REPORTS = "work/v24-controls/project/docs/precompensation-lab"
private const val LEGACY_TESTS = "work/vueconfort-commercial/project/app/build/test-results/testReleaseUnitTest"

private fun research(modelId: String, version: String, sources: List<String>, entryPoints: List<String>,
                     inputs: List<String>, output: String, verificationReference: String, limitations: List<String>,
                     deviceProof: String? = null, proofKind: VisionEvidenceKind = VisionEvidenceKind.NUMERICAL_TESTED,
                     category: VisionEngineCategory = VisionEngineCategory.OPTICAL) = VisionResearchEngineMetadata(
    modelId, version, sources, entryPoints, inputs, output,
    evidence = buildList {
        sources.forEach { add(VisionEvidence(VisionEvidenceKind.CODE_PRESENT, it)) }
        add(VisionEvidence(proofKind, verificationReference))
        deviceProof?.let {
            add(VisionEvidence(VisionEvidenceKind.COMPILED, it))
            add(VisionEvidence(VisionEvidenceKind.INSTRUMENTATION_TESTED, it))
            add(VisionEvidence(VisionEvidenceKind.DEVICE_EXECUTED, it))
        }
    },
    limitations = limitations + listOf("PERCEPTUAL_BENEFIT_NOT_DEMONSTRATED", "COMMERCIAL_USE_NOT_VALIDATED"), category = category)
