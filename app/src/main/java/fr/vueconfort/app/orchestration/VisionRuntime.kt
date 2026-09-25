package fr.vueconfort.app.orchestration

import android.content.Context
import fr.vueconfort.app.equalizer.*
import fr.vueconfort.app.nativevision.*
import kotlin.math.abs

/** Thin execution boundary around the existing engines; no second renderer or system writer. */
object VisionRuntime {
    fun orchestrator() = VisionOrchestrator(VisionEngineCatalog.defaultEngines())

    fun plan(snapshot: VisionProfileSnapshot, capabilities: NativeVisionCapabilities,
             sessionId: String, includePreview: Boolean = false, pendingNativeCommand: Boolean = false,
             occupiedDisplayDomains: Set<String> = emptySet()): VisionRenderPlan {
        val context = VisionExecutionContext(sessionId, capabilities.observedAtMillis, capabilities.observedAtMillis,
            setOf(VisionTransport.ANDROID_NATIVE, VisionTransport.SAMSUNG_NATIVE) +
                if (includePreview) setOf(VisionTransport.INTERNAL_PREVIEW) else emptySet(), capabilities, occupiedDisplayDomains = occupiedDisplayDomains)
        return orchestrator().plan(snapshot.profileId, snapshot.sourceRevision, context,
            if (pendingNativeCommand) snapshot.nativeRequests() else snapshot.requests(includePreview))
    }

    /** Fresh read-only observations: a saved receipt is never sufficient to announce activity. */
    fun observe(context: Context, plan: VisionRenderPlan,
                previewDraw: EqualizerRenderRecord? = null, original: Boolean = false): VisionObservedState {
        val android = AndroidNativeVisionAdapter()
        val samsung = SamsungNativeVisionAdapter(context)
        val receipts = plan.transformations.mapNotNull { step ->
            val engine = step.engine ?: return@mapNotNull null
            val transport = step.transport ?: return@mapNotNull null
            val now = System.currentTimeMillis()
            val payload = step.request.payload
            var confirmation = VisionReceiptConfirmation.NONE
            var evidence = ""
            val confirmed = when (payload) {
                is EnginePayload.Native -> {
                    confirmation = VisionReceiptConfirmation.READ_BACK_CONFIRMED
                    if (payload.capability == NativeVisionCapability.MAGNIFICATION) {
                        val actual = android.readMagnificationState()
                        evidence = "MagnificationController current app-process readback"
                        android.isControllerConnected() && actual != null && actual.restorable &&
                            nativeValuesMatch(payload.value, actual.asValue())
                    } else {
                        val actual = samsung.read(payload.capability)
                        evidence = actual.provenance
                        actual.readable && nativeValuesMatch(payload.value, actual.value)
                    }
                }
                is EnginePayload.Perceptual -> {
                    confirmation = VisionReceiptConfirmation.RENDERER_CONFIRMED
                    evidence = "EqualizerPreview current surface post-draw callback"
                    !original && previewDraw != null && previewDraw.sourceRevision == plan.profileRevision &&
                        previewDraw == payload.preferences.renderRecord(payload.scene, plan.profileRevision,
                            plan.context.nativeCapabilities?.device?.sdkInt?.let { it >= 33 } == true)
                }
                is EnginePayload.Optical -> false
            }
            VisionApplicationReceipt(plan.profileId, plan.profileRevision, plan.context, step.request,
                engine.engineId, engine.version, transport,
                if (confirmed) VisionReceiptStatus.VERIFIED_EXISTING else VisionReceiptStatus.NOT_APPLIED,
                if (confirmed) confirmation else VisionReceiptConfirmation.NONE, now,
                if (confirmed) payload else null, if (confirmed) evidence else "",
                if (confirmed) "État relu ou aperçu dessiné dans le contexte actuel." else
                    if (original && payload is EnginePayload.Perceptual) "Comparaison à l’original en cours." else
                        "Ce traitement n’est pas confirmé dans le contexte actuel.")
        }
        return orchestrator().observe(plan, receipts, maxOf(System.currentTimeMillis(), plan.context.observedAtMillis))
    }

    /** Reserve domains of effects observed on the display, even if another application enabled them. */
    fun occupiedDisplayDomains(context: Context, capabilities: NativeVisionCapabilities): Set<String> {
        val settings = SamsungNativeVisionAdapter(context)
        val execution = VisionExecutionContext("native-observation", capabilities.observedAtMillis,
            capabilities.observedAtMillis, setOf(VisionTransport.ANDROID_NATIVE, VisionTransport.SAMSUNG_NATIVE), capabilities)
        val engines = VisionEngineCatalog.defaultEngines()
        // Baseline display brightness/zoom are conditions, not evidence of an added active effect.
        return NativeVisionCapability.entries.filterNot { it in setOf(NativeVisionCapability.SYSTEM_BRIGHTNESS,
            NativeVisionCapability.SCREEN_ZOOM) }.flatMap { capability ->
            val actual = if (capability == NativeVisionCapability.MAGNIFICATION)
                AndroidNativeVisionAdapter().readMagnificationState()?.asValue() else settings.read(capability).value
            if (actual == null) emptySet() else {
                val request = VisionRequest("observed.${capability.name}", EnginePayload.Native(capability, actual),
                    execution.availableTransports)
                engines.mapNotNull { it.assess(request, execution) }.flatMap { it.composition.exclusiveGroups }.toSet()
            }
        }.toSet()
    }

    fun nativeValuesMatch(requested: NativeVisionValue, actual: NativeVisionValue?): Boolean = when {
        requested is NativeVisionValue.Magnification && actual is NativeVisionValue.Magnification ->
            requested.enabled == actual.enabled && requested.mode == actual.mode && abs(requested.scale - actual.scale) <= .001f &&
                (requested.centerX == null || (actual.centerX != null && abs(requested.centerX - actual.centerX) <= 1f)) &&
                (requested.centerY == null || (actual.centerY != null && abs(requested.centerY - actual.centerY) <= 1f))
        else -> requested == actual
    }

    fun refusal(step: VisionPlannedTransformation, revision: Long): NativeVisionApplicationResult {
        val payload = step.request.payload as EnginePayload.Native
        return NativeVisionApplicationResult(payload.capability, payload.value,
            when (step.disposition) {
                VisionPlanDisposition.PERMISSION_REQUIRED -> NativeVisionApplicationStatus.NEEDS_USER_PERMISSION
                VisionPlanDisposition.UNAVAILABLE -> NativeVisionApplicationStatus.UNAVAILABLE
                else -> NativeVisionApplicationStatus.REJECTED
            }, engine = NativeVisionEngine.NONE, provenance = "VUECONFORT_ORCHESTRATOR_PLAN",
            reason = step.reason, timestampMillis = System.currentTimeMillis(), profileRevision = revision)
    }
}
