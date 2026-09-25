package fr.vueconfort.app.orchestration

import fr.vueconfort.app.nativevision.NativeVisionValue

/** Deterministic planning and receipt reconciliation. This class never executes an engine. */
class VisionOrchestrator(engines: List<VisionEngine>) {
    private val registry = engines.sortedBy { it.descriptor.engineId }.also { sorted ->
        require(sorted.map { it.descriptor.engineId }.distinct().size == sorted.size) { "Duplicate engine id" }
    }

    fun plan(
        profileId: String,
        profileRevision: Long,
        context: VisionExecutionContext,
        requests: List<VisionRequest>
    ): VisionRenderPlan {
        require(profileId.isNotBlank() && profileRevision >= 0)
        require(requests.map { it.id }.distinct().size == requests.size) { "Duplicate request id" }
        require(requests.map { it.payload.transformationId }.distinct().size == requests.size) { "Duplicate transformation" }
        val candidates = requests.associateWith { request ->
            registry.mapNotNull { engine ->
                val assessment = engine.assess(request, context) ?: return@mapNotNull null
                evaluate(request, profileRevision, context, engine.descriptor, assessment)
            }.sortedWith(candidateOrder(request))
        }
        // The best qualified routes reserve their domains first, independent of registry/request ordering.
        val requestOrder = requests.sortedWith { left, right ->
            val a = candidates.getValue(left).firstOrNull()
            val b = candidates.getValue(right).firstOrNull()
            when {
                a == null && b == null -> left.id.compareTo(right.id)
                a == null -> 1
                b == null -> -1
                else -> globalOrder.compare(a, b).takeIf { it != 0 } ?: left.id.compareTo(right.id)
            }
        }
        val selected = mutableListOf<Candidate>()
        val planned = requestOrder.map { request ->
            val assessed = candidates.getValue(request)
            val considered = assessed.map { candidate ->
                if (candidate.disposition in selectable && selected.any { conflicts(it, candidate) })
                    candidate.copy(disposition = VisionPlanDisposition.CONFLICT,
                        reason = "Combinaison non qualifiée ou domaine déjà réservé par un autre moteur.")
                else candidate
            }
            val chosen = considered.firstOrNull { it.disposition in selectable }
                ?: considered.firstOrNull()
            if (chosen != null && chosen.disposition in selectable && !chosen.noEffect && chosen.disposition != VisionPlanDisposition.IDENTITY) selected += chosen
            val disposition = chosen?.disposition ?: VisionPlanDisposition.UNAVAILABLE
            VisionPlannedTransformation(request, chosen?.engine, chosen?.assessment, disposition,
                chosen?.reason ?: "Aucun moteur ne prend en charge cette transformation exacte.",
                fallback(disposition), considered.filter { it !== chosen }.map {
                    VisionCandidateDecision(it.engine.engineId, it.disposition, it.reason)
                })
        }.sortedWith(compareBy({ it.assessment?.composition?.order ?: Int.MAX_VALUE }, { it.request.id }))
        val operational = planned.count { it.disposition in setOf(VisionPlanDisposition.APPLICABLE, VisionPlanDisposition.GUIDED, VisionPlanDisposition.PERMISSION_REQUIRED) }
        val state = when {
            operational == 0 -> VisionPlanState.IDENTITY
            planned.all { it.disposition in selectable } -> VisionPlanState.PLANNED
            else -> VisionPlanState.PARTIAL
        }
        return VisionRenderPlan(profileId, profileRevision, context, planned, state)
    }

    /** A current route and an exact, fresh technical receipt are both required for an active label. */
    fun observe(plan: VisionRenderPlan, receipts: List<VisionApplicationReceipt>, nowMillis: Long,
                maxReceiptAgeMillis: Long = 30_000): VisionObservedState {
        require(nowMillis >= plan.context.observedAtMillis && maxReceiptAgeMillis >= 0)
        return VisionObservedState(plan, plan.transformations.map { step ->
            val matching = receipts.filter { receipt ->
                receipt.profileId == plan.profileId && receipt.profileRevision == plan.profileRevision &&
                    receipt.context == plan.context && receipt.request == step.request &&
                    receipt.engineId == step.engine?.engineId && receipt.engineVersion == step.engine?.version &&
                    receipt.transport == step.transport && receipt.observedAtMillis in plan.context.observedAtMillis..nowMillis &&
                    nowMillis - receipt.observedAtMillis <= maxReceiptAgeMillis
            }
            // Conflicting observations at the same time cannot prove the requested state.
            val latestTime = matching.maxOfOrNull { it.observedAtMillis }
            val latest = matching.filter { it.observedAtMillis == latestTime }.distinct()
            val receipt = latest.singleOrNull()
            val routeAllowsObservation = step.disposition in setOf(VisionPlanDisposition.APPLICABLE, VisionPlanDisposition.GUIDED)
            val statusAllowsObservation = receipt?.status == VisionReceiptStatus.VERIFIED_EXISTING ||
                (receipt?.status == VisionReceiptStatus.APPLIED && step.canExecuteAutomatically)
            val technical = when (step.transport) {
                VisionTransport.ANDROID_NATIVE, VisionTransport.SAMSUNG_NATIVE -> receipt?.confirmation == VisionReceiptConfirmation.READ_BACK_CONFIRMED
                VisionTransport.INTERNAL_PREVIEW -> receipt?.confirmation == VisionReceiptConfirmation.RENDERER_CONFIRMED
                VisionTransport.FUTURE_DISPLAY_LENS, null -> false // No display transport is implemented or qualified here.
            }
            val active = routeAllowsObservation && statusAllowsObservation && technical &&
                receipt?.applied == step.request.payload && !receipt?.evidenceReference.isNullOrBlank() &&
                !isIdentity(step.request.payload) && step.assessment?.identity != true
            VisionObservedTransformation(step, receipt, active, when {
                active -> receipt!!.reason
                !routeAllowsObservation -> step.reason
                receipt == null -> "Aucune preuve technique fraîche pour cette demande et ce contexte."
                else -> receipt.reason
            })
        })
    }

    private data class Candidate(
        val engine: VisionEngineDescriptor,
        val assessment: VisionEngineAssessment,
        val maturity: VisionMaturity,
        val disposition: VisionPlanDisposition,
        val reason: String,
        val noEffect: Boolean
    )

    private fun evaluate(request: VisionRequest, revision: Long, context: VisionExecutionContext,
                         engine: VisionEngineDescriptor, assessment: VisionEngineAssessment): Candidate {
        val maturity = assessment.maturity ?: engine.maturity
        fun result(disposition: VisionPlanDisposition, reason: String = assessment.reason) =
            Candidate(engine, assessment, maturity, disposition, reason, isIdentity(request.payload))
        val categoryMatches = when (request.payload) {
            is EnginePayload.Native -> engine.category in setOf(VisionEngineCategory.NATIVE_ANDROID, VisionEngineCategory.NATIVE_SAMSUNG)
            is EnginePayload.Perceptual -> engine.category == VisionEngineCategory.PERCEPTUAL
            is EnginePayload.Optical -> engine.category == VisionEngineCategory.OPTICAL ||
                (engine.category == VisionEngineCategory.DISPLAY_TRANSPORT && assessment.availability in
                    setOf(VisionEngineAvailability.INACCESSIBLE, VisionEngineAvailability.UNAVAILABLE))
        }
        if (!categoryMatches)
            return result(VisionPlanDisposition.REJECTED, "Cette famille de moteur ne réalise pas la transformation demandée.")
        if (assessment.transformationId != request.payload.transformationId)
            return result(VisionPlanDisposition.REJECTED, "Le moteur propose une autre transformation ; substitution refusée.")
        if (request.payload is EnginePayload.Perceptual && request.sourceRevision != null && request.sourceRevision != revision)
            return result(VisionPlanDisposition.REJECTED, "Les préférences perceptives proviennent d’une ancienne révision.")
        if (request.payload is EnginePayload.Optical && request.payload.input.sourceRevision != revision)
            return result(VisionPlanDisposition.REJECTED, "Les paramètres optiques proviennent d’une ancienne révision.")
        if (assessment.availability == VisionEngineAvailability.REJECTED) return result(VisionPlanDisposition.REJECTED)
        if (assessment.availability in setOf(VisionEngineAvailability.UNAVAILABLE, VisionEngineAvailability.INACCESSIBLE))
            return result(VisionPlanDisposition.UNAVAILABLE)
        if (assessment.transport !in request.allowedTransports || assessment.transport !in context.availableTransports)
            return result(VisionPlanDisposition.UNAVAILABLE, "Le transport demandé n’est pas disponible dans ce contexte.")
        if (assessment.transport == VisionTransport.INTERNAL_PREVIEW && !assessment.identity &&
            assessment.composition.exclusiveGroups.intersect(context.occupiedDisplayDomains).isNotEmpty())
            return result(VisionPlanDisposition.CONFLICT,
                "Un réglage déjà actif sur le téléphone agit sur le même domaine ; la combinaison de cet aperçu n’est pas qualifiée.")
        val evidence = engine.evidence + assessment.evidence
        if (maturity.rank > engine.maturity.rank || maturity == VisionMaturity.UNAVAILABLE ||
            (maturity == VisionMaturity.COMMERCIAL_VALIDATED && evidence.none { it.kind == VisionEvidenceKind.COMMERCIAL_USE_VALIDATED }) ||
            (maturity == VisionMaturity.DEVICE_VALIDATED && evidence.none { it.kind == VisionEvidenceKind.DEVICE_EXECUTED }))
            return result(VisionPlanDisposition.UNQUALIFIED, "Le niveau annoncé n’est pas étayé par les preuves requises.")
        if (!context.allowExperimental && maturity.rank < VisionMaturity.PROTOTYPE.rank)
            return result(VisionPlanDisposition.UNQUALIFIED, "Ce moteur reste expérimental et n’est pas autorisé dans ce contexte.")
        // An identity requested explicitly can still require a real disable command. Do not skip it here.
        return result(when (assessment.availability) {
            VisionEngineAvailability.READY -> if (assessment.identity) VisionPlanDisposition.IDENTITY else VisionPlanDisposition.APPLICABLE
            VisionEngineAvailability.NEEDS_USER_ACTION -> VisionPlanDisposition.GUIDED
            VisionEngineAvailability.PERMISSION_REQUIRED -> VisionPlanDisposition.PERMISSION_REQUIRED
            else -> VisionPlanDisposition.UNAVAILABLE
        })
    }

    private val selectable = setOf(VisionPlanDisposition.APPLICABLE, VisionPlanDisposition.GUIDED, VisionPlanDisposition.PERMISSION_REQUIRED, VisionPlanDisposition.IDENTITY)
    private fun availabilityRank(candidate: Candidate): Int = when (candidate.disposition) {
        VisionPlanDisposition.APPLICABLE, VisionPlanDisposition.IDENTITY -> 3
        VisionPlanDisposition.GUIDED -> 2
        VisionPlanDisposition.PERMISSION_REQUIRED -> 1
        else -> 0
    }
    private val globalOrder = compareByDescending<Candidate> { availabilityRank(it) }
        .thenByDescending { it.maturity.rank }
        .thenByDescending { it.assessment.quality }
        .thenBy { it.assessment.cost }
        .thenBy { it.engine.engineId }

    private fun candidateOrder(request: VisionRequest) = compareByDescending<Candidate> { availabilityRank(it) }
        .thenByDescending { it.maturity.rank }
        .thenBy { request.preferredEngineIds.indexOf(it.engine.engineId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        .thenByDescending { it.assessment.quality }
        .thenBy { it.assessment.cost }
        .thenBy { it.engine.engineId }

    private fun conflicts(a: Candidate, b: Candidate): Boolean {
        if (a.noEffect || b.noEffect || b.disposition == VisionPlanDisposition.IDENTITY) return false
        val x = a.assessment.composition
        val y = b.assessment.composition
        return x.exclusiveGroups.intersect(y.exclusiveGroups).isNotEmpty() ||
            b.engine.category !in x.compatibleCategories || a.engine.category !in y.compatibleCategories
    }

    private fun fallback(disposition: VisionPlanDisposition): VisionFallback = when (disposition) {
        VisionPlanDisposition.APPLICABLE, VisionPlanDisposition.IDENTITY -> VisionFallback.NONE
        VisionPlanDisposition.GUIDED -> VisionFallback.USER_ACTION
        VisionPlanDisposition.PERMISSION_REQUIRED -> VisionFallback.REQUEST_PERMISSION
        else -> VisionFallback.IDENTITY
    }

    private fun isIdentity(payload: EnginePayload): Boolean = when (payload) {
        // The existing renderer's adapter owns identity: geometry and text need not share pixel intensity.
        is EnginePayload.Perceptual -> false
        is EnginePayload.Optical -> false // Only this model's adapter can assert an optical identity.
        is EnginePayload.Native -> when (val value = payload.value) {
            is NativeVisionValue.Magnification -> !value.enabled || value.scale == 1f
            is NativeVisionValue.Relumino -> !value.enabled
            is NativeVisionValue.ExtraDim -> !value.enabled
            is NativeVisionValue.ColorFilter -> !value.enabled
            is NativeVisionValue.ColorCorrection -> !value.enabled
            is NativeVisionValue.Toggle -> !value.enabled
            is NativeVisionValue.FontScale -> value.scale == 1f
            is NativeVisionValue.Brightness, is NativeVisionValue.ScreenZoom -> false
        }
    }
}
