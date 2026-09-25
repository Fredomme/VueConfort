package fr.vueconfort.app.orchestration

import fr.vueconfort.app.precompensation.v24.*

/** A measured render-surface observation, not a request to create a new capture transport. */
data class OpticalV24PreviewSurface(
    val observation: DisplayObservationV24,
    val deviceModel: String,
    val surroundingWidthPx: Int,
    val surroundingHeightPx: Int,
    val stimulusOffsetXPx: Int,
    val stimulusOffsetYPx: Int,
    val surroundingLinearGray: Double,
    val surroundingUniformAndVisible: Boolean,
    val sessionId: String,
    val runtimeRevision: Long,
    val observedAtMillis: Long
)

enum class OpticalV24Use { SYNTHETIC_REFERENCE, PERSONAL_PREVIEW }

/**
 * No synthetic defaults fill missing personal information. The original engine owns the model.
 * The frame is copied on entry/exit so a selected plan cannot silently change its input pixels.
 */
class OpticalV24Input(
    override val sourceRevision: Long,
    val contract: OpticalContractV24? = null,
    linearFrame: DoubleArray? = null,
    val surface: OpticalV24PreviewSurface? = null,
    val use: OpticalV24Use = OpticalV24Use.PERSONAL_PREVIEW,
    val frameId: String? = null
) : OpticalEngineInput {
    override val modelId = "v2.4"
    private val frame = linearFrame?.copyOf()
    fun linearFrame(): DoubleArray? = frame?.copyOf()

    internal fun missingInputs(): List<String> = buildList {
        if (contract == null) add("OPTICAL_CONTRACT_NOT_RECORDED")
        if (contract?.residual == null) add("NEAR_RESIDUAL_UNKNOWN")
        if (surface == null) add("QUALIFIED_1_TO_1_SURFACE_UNAVAILABLE")
        if (frame == null) add("LINEAR_FRAME_UNAVAILABLE")
        if (frameId.isNullOrBlank()) add("FRAME_PROVENANCE_UNAVAILABLE")
    }

    internal fun rejectionReasons(context: VisionExecutionContext): List<String> = buildList {
        val c = contract ?: return@buildList
        addAll(c.rejectionReasons())
        if (frame != null && (frame.size != 128 * 128 || frame.any { !it.isFinite() || it !in 0.0..1.0 }))
            add("INVALID_LINEAR_STIMULUS")
        if (use == OpticalV24Use.PERSONAL_PREVIEW &&
            (c.residual?.origin == ResidualOriginV24.SYNTHETIC_DIRECT || c.pupilProvenance == "SYNTHETIC_ASSUMED"))
            add("SYNTHETIC_MODEL_IS_NOT_PERSONAL")
        surface?.let { s ->
            addAll(s.observation.rejectionReasons(c.geometry))
            if (!s.observation.canvasDensityScale.isFinite()) add("INVALID_CANVAS_SCALE")
            val gray = LuminanceV24.decode(LuminanceV24.code8(ScientificRendererV24.BACKGROUND) / 255.0)
            if (s.surroundingWidthPx < 512 || s.surroundingHeightPx < 512 || !s.surroundingUniformAndVisible ||
                s.surroundingLinearGray != gray || s.stimulusOffsetXPx < 192 || s.stimulusOffsetYPx < 192 ||
                s.surroundingWidthPx.toLong() - s.stimulusOffsetXPx - 128 < 192 ||
                s.surroundingHeightPx.toLong() - s.stimulusOffsetYPx - 128 < 192) add("KNOWN_512_FIELD_REQUIRED")
            if (s.sessionId != context.sessionId || s.runtimeRevision != context.runtimeRevision ||
                s.observedAtMillis != context.observedAtMillis) add("STALE_SURFACE_OBSERVATION")
            val device = context.nativeCapabilities?.device
            if (s.deviceModel.isBlank() || device?.model != s.deviceModel) add("DEVICE_IDENTITY_NOT_CONFIRMED")
            if (c.geometry.pitchOrigin == PhysicalPitchOriginV24.SAMSUNG_NOMINAL_ESTIMATE &&
                (device?.manufacturer?.equals("samsung", ignoreCase = true) != true || device?.model != "SM-S931B"))
                add("NOMINAL_PITCH_REQUIRES_RECORDED_S25_MODEL")
        }
    }
}

/**
 * Executable adapter around the unchanged V2.4 renderer. It never turns a distance prescription
 * into a near residual, never runs a generic-contrast/control condition and never draws a frame.
 */
class OpticalV24VisionEngine : VisionEngine {
    override val descriptor = VisionEngineDescriptor(
        engineId = "optical-v24", version = "2.4-adapter-1", category = VisionEngineCategory.OPTICAL,
        maturity = VisionMaturity.EXPERIMENTAL,
        evidence = listOf(
            VisionEvidence(VisionEvidenceKind.CODE_PRESENT, "app/src/opticalReference/SHA256SUMS"),
            VisionEvidence(VisionEvidenceKind.UNIT_TESTED, "outputs/v24-capture/tests-capture.xml"),
            VisionEvidence(VisionEvidenceKind.DEVICE_EXECUTED, "outputs/v24-capture/Bilan.md")),
        capabilities = setOf("optical.v2.4"),
        requiredInputs = setOf("OpticalContractV24 with explicit near residual", "Pupil and physical geometry provenance",
            "128 x 128 linear SRGB_D65 frame", "Fresh 1:1 surface inside known 512 x 512 gray field"),
        constraints = listOf("EXPERIMENTAL_OPT_IN_REQUIRED", "INTERNAL_STATIC_PREVIEW_ONLY", "NO_PRESCRIPTION_TO_RESIDUAL_MAPPING",
            "NO_GLOBAL_PIXEL_TRANSPORT", "NO_PERCEPTUAL_BENEFIT_CLAIM", "CPU_RESULT_IS_NOT_A_DRAW_RECEIPT"))

    override fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment? {
        val payload = request.payload as? EnginePayload.Optical ?: return null
        if (payload.input.modelId != "v2.4") return null
        val input = payload.input as? OpticalV24Input
            ?: return assessment(VisionEngineAvailability.REJECTED, "Le contrat typé du moteur optique est requis.",
                listOf("WRONG_INPUT_CONTRACT"))
        val missing = input.missingInputs()
        if (missing.isNotEmpty()) return assessment(VisionEngineAvailability.UNAVAILABLE,
            "La correction avancée nécessite des mesures et un aperçu qualifié qui ne sont pas encore disponibles.", missing)
        val rejected = input.rejectionReasons(context)
        if (rejected.isNotEmpty()) return assessment(VisionEngineAvailability.REJECTED,
            "Les entrées ou les conditions d’affichage ne respectent pas le contrat de ce moteur.", rejected)
        return assessment(VisionEngineAvailability.READY,
            "Calcul optique expérimental disponible pour cet aperçu fixe ; aucun effet global ou bénéfice visuel n’est affirmé.",
            emptyList())
    }

    private fun assessment(availability: VisionEngineAvailability, reason: String, reasons: List<String>) =
        VisionEngineAssessment("optical.v2.4", availability, VisionTransport.INTERNAL_PREVIEW, reason,
            composition = VisionCompositionPolicy(emptySet(), setOf("system-magnification", "layout-scaling", "text-weight",
                "image-color-processing", "luminance-processing"), order = 10),
            cost = 90, guardRails = descriptor.constraints + reasons)

    /**
     * Revalidates the exact selected request before calling the existing CPU renderer. The caller
     * must dispatch off the UI thread, then revalidate context before presenting the result 1:1.
     * A rejected result has no output; IDENTITY remains exact. No drawing receipt is fabricated.
     */
    fun render(plan: VisionRenderPlan, requestId: String, currentContext: VisionExecutionContext): OpticalV24Frame {
        val step = plan.transformations.singleOrNull { it.request.id == requestId }
        val input = (step?.request?.payload as? EnginePayload.Optical)?.input as? OpticalV24Input
        fun refused(reason: String) = OpticalV24Frame(plan, requestId, OpticalDecisionV24.REJECTED, null,
            listOf(reason), null)
        if (currentContext != plan.context) return refused("STALE_EXECUTION_CONTEXT")
        if (step?.engine != descriptor || step.disposition != VisionPlanDisposition.APPLICABLE || input == null)
            return refused("NO_EXECUTABLE_V24_PLAN")
        val rechecked = VisionOrchestrator(listOf(this)).plan(plan.profileId, plan.profileRevision, currentContext,
            listOf(step.request)).transformations.single()
        if (!rechecked.canExecuteAutomatically) return refused("PLAN_NO_LONGER_APPLICABLE")
        val result = ScientificRendererV24(requireNotNull(input.contract))
            .render(requireNotNull(input.linearFrame()), ScientificConditionV24.P)
        return OpticalV24Frame(plan, requestId, result.decision, result.output, result.reasons, result)
    }
}

/** An unpresented CPU result. Only the actual surface may issue RENDERER_CONFIRMED after drawing. */
class OpticalV24Frame internal constructor(
    val plan: VisionRenderPlan,
    val requestId: String,
    val decision: OpticalDecisionV24,
    output: DoubleArray?,
    val reasons: List<String>,
    /** Original scientific diagnostics; callers must not interpret metrics as a perceptual benefit. */
    val diagnostics: RenderResultV24?
) {
    private val pixels = output?.copyOf()
    fun linearPixels(): DoubleArray? = pixels?.copyOf()
    fun mayPresent(currentContext: VisionExecutionContext, currentProfileId: String, currentProfileRevision: Long): Boolean =
        pixels != null && decision != OpticalDecisionV24.REJECTED && plan.context == currentContext &&
            plan.profileId == currentProfileId && plan.profileRevision == currentProfileRevision
}

/** Current profile formats do not contain a measured near residual or a qualified physical surface. */
fun VisionProfileSnapshot.opticalReadinessRequest(): VisionRequest = VisionRequest(
    "optical.v2.4", EnginePayload.Optical(OpticalV24Input(sourceRevision)),
    setOf(VisionTransport.INTERNAL_PREVIEW), sourceRevision = sourceRevision)
