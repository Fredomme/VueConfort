package fr.vueconfort.app.orchestration

enum class VisionPlanDisposition { APPLICABLE, GUIDED, PERMISSION_REQUIRED, IDENTITY, UNAVAILABLE, REJECTED, UNQUALIFIED, CONFLICT }
enum class VisionPlanState { PLANNED, PARTIAL, IDENTITY }
enum class VisionFallback { NONE, USER_ACTION, REQUEST_PERMISSION, IDENTITY }

data class VisionCandidateDecision(
    val engineId: String,
    val disposition: VisionPlanDisposition,
    val reason: String
)

data class VisionPlannedTransformation(
    val request: VisionRequest,
    val engine: VisionEngineDescriptor?,
    val assessment: VisionEngineAssessment?,
    val disposition: VisionPlanDisposition,
    val reason: String,
    val fallback: VisionFallback,
    /** Rejections remain visible even when another engine handles the same exact effect. */
    val alternatives: List<VisionCandidateDecision> = emptyList()
) {
    val canExecuteAutomatically: Boolean get() = disposition == VisionPlanDisposition.APPLICABLE
    val transport: VisionTransport? get() = assessment?.transport
}

/** A plan authorizes a route. It contains no assertion that pixels or system settings have changed. */
data class VisionRenderPlan(
    val profileId: String,
    val profileRevision: Long,
    val context: VisionExecutionContext,
    val transformations: List<VisionPlannedTransformation>,
    val state: VisionPlanState
) {
    val executable: List<VisionPlannedTransformation> get() = transformations.filter { it.canExecuteAutomatically }
    val selectedEngines: List<VisionEngineDescriptor> get() = transformations.filter {
        it.disposition in setOf(VisionPlanDisposition.APPLICABLE, VisionPlanDisposition.GUIDED,
            VisionPlanDisposition.PERMISSION_REQUIRED, VisionPlanDisposition.IDENTITY)
    }.mapNotNull { it.engine }.distinctBy { it.engineId }
}

enum class VisionReceiptStatus { APPLIED, VERIFIED_EXISTING, USER_CONFIRMED, IDENTITY, NOT_APPLIED, FAILED }
enum class VisionReceiptConfirmation { NONE, USER_REPORTED, RENDERER_CONFIRMED, READ_BACK_CONFIRMED }

/** A new runtime observation, not the historic receipt persisted alongside a profile. */
data class VisionApplicationReceipt(
    val profileId: String,
    val profileRevision: Long,
    val context: VisionExecutionContext,
    val request: VisionRequest,
    val engineId: String,
    val engineVersion: String,
    val transport: VisionTransport,
    val status: VisionReceiptStatus,
    val confirmation: VisionReceiptConfirmation,
    val observedAtMillis: Long,
    val applied: EnginePayload? = null,
    val evidenceReference: String = "",
    val reason: String
) {
    init { require(profileId.isNotBlank() && profileRevision >= 0 && observedAtMillis >= 0 && reason.isNotBlank()) }
}

data class VisionObservedTransformation(
    val planned: VisionPlannedTransformation,
    val receipt: VisionApplicationReceipt?,
    val active: Boolean,
    val reason: String
)

data class VisionObservedState(val plan: VisionRenderPlan, val transformations: List<VisionObservedTransformation>) {
    val anyActive: Boolean get() = transformations.any { it.active }
}
