package fr.vueconfort.app.orchestration

import fr.vueconfort.app.equalizer.EqualizerPreferences
import fr.vueconfort.app.equalizer.EqualizerScene
import fr.vueconfort.app.nativevision.NativeVisionCapabilities
import fr.vueconfort.app.nativevision.NativeVisionCapability
import fr.vueconfort.app.nativevision.NativeVisionValue

enum class VisionEngineCategory { NATIVE_ANDROID, NATIVE_SAMSUNG, PERCEPTUAL, OPTICAL, DISPLAY_TRANSPORT }
enum class VisionTransport { INTERNAL_PREVIEW, ANDROID_NATIVE, SAMSUNG_NATIVE, FUTURE_DISPLAY_LENS }
enum class VisionMaturity(val rank: Int) {
    UNAVAILABLE(0), RESEARCH(1), EXPERIMENTAL(2), PROTOTYPE(3), DEVICE_VALIDATED(4), COMMERCIAL_VALIDATED(5)
}
enum class VisionEvidenceKind {
    CODE_PRESENT, COMPILED, UNIT_TESTED, NUMERICAL_TESTED, INSTRUMENTATION_TESTED, DEVICE_EXECUTED,
    PHYSICAL_EFFECT_OBSERVED, PERCEPTUAL_BENEFIT_DEMONSTRATED, COMMERCIAL_USE_VALIDATED
}
/** A reference to an actual proof; compilation is never a commercial or clinical validation. */
data class VisionEvidence(val kind: VisionEvidenceKind, val reference: String) {
    init { require(reference.isNotBlank()) }
}
enum class VisionEngineAvailability { READY, NEEDS_USER_ACTION, PERMISSION_REQUIRED, UNAVAILABLE, INACCESSIBLE, REJECTED }

/** Engine-owned immutable inputs retain their own scientific model and parameter types. */
interface OpticalEngineInput {
    val modelId: String
    val sourceRevision: Long
}

sealed interface EnginePayload {
    val transformationId: String
    data class Native(val capability: NativeVisionCapability, val value: NativeVisionValue) : EnginePayload {
        init { require(value.isValidFor(capability)) }
        override val transformationId: String get() = "native.${capability.name}"
    }
    data class Perceptual(val preferences: EqualizerPreferences, val scene: EqualizerScene) : EnginePayload {
        override val transformationId: String get() = "perceptual.equalizer"
    }
    data class Optical(val input: OpticalEngineInput) : EnginePayload {
        init { require(input.modelId.isNotBlank() && input.sourceRevision >= 0) }
        override val transformationId: String get() = "optical.${input.modelId}"
    }
}

data class VisionRequest(
    val id: String,
    val payload: EnginePayload,
    val allowedTransports: Set<VisionTransport>,
    val preferredEngineIds: List<String> = emptyList(),
    /** Native requests have their own revision inside the central profile. */
    val sourceRevision: Long? = null
) {
    init { require(id.isNotBlank() && allowedTransports.isNotEmpty() && (sourceRevision == null || sourceRevision >= 0)) }
}

/**
 * An immutable observation for one execution context. Refresh nativeCapabilities before commands.
 * sessionId changes after restart; runtimeRevision changes on permissions, surface or other context changes.
 * Exact context equality also prevents an unchanged revision from hiding a changed capability snapshot.
 */
data class VisionExecutionContext(
    val sessionId: String,
    val runtimeRevision: Long,
    val observedAtMillis: Long,
    val availableTransports: Set<VisionTransport>,
    val nativeCapabilities: NativeVisionCapabilities? = null,
    val allowExperimental: Boolean = false,
    /** Domains occupied by freshly observed physical settings, even when the VueConfort profile is disabled. */
    val occupiedDisplayDomains: Set<String> = emptySet()
) {
    init {
        require(sessionId.isNotBlank() && runtimeRevision >= 0 && observedAtMillis >= 0)
        require(occupiedDisplayDomains.all { it.isNotBlank() })
    }
}

/** Both engines must explicitly permit composition; matching exclusive groups always conflict. */
data class VisionCompositionPolicy(
    val compatibleCategories: Set<VisionEngineCategory> = emptySet(),
    val exclusiveGroups: Set<String> = emptySet(),
    /** Dispatch order only: Android/OEM compositor order is not controlled by this number. */
    val order: Int = 0
)

data class VisionEngineDescriptor(
    val engineId: String,
    val version: String,
    val category: VisionEngineCategory,
    val maturity: VisionMaturity,
    val evidence: List<VisionEvidence> = emptyList(),
    val capabilities: Set<String> = emptySet(),
    val requiredInputs: Set<String> = emptySet(),
    val constraints: List<String> = emptyList()
) {
    init { require(engineId.isNotBlank() && version.isNotBlank()) }
}

/** One assessment is for the exact request identity, never a substitute visual effect. */
data class VisionEngineAssessment(
    val transformationId: String,
    val availability: VisionEngineAvailability,
    val transport: VisionTransport,
    val reason: String,
    val composition: VisionCompositionPolicy = VisionCompositionPolicy(),
    val quality: Int = 0,
    val cost: Int = 0,
    val guardRails: List<String> = emptyList(),
    /** A capability may be less mature than the engine that contains it. */
    val maturity: VisionMaturity? = null,
    val evidence: List<VisionEvidence> = emptyList(),
    val identity: Boolean = false
) {
    init { require(transformationId.isNotBlank() && reason.isNotBlank() && quality in 0..100 && cost in 0..100) }
}

interface VisionEngine {
    val descriptor: VisionEngineDescriptor
    /** Pure assessment, with no command, system read, persistence or fallback execution. */
    fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment?
}
