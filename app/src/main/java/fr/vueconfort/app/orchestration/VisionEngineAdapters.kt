package fr.vueconfort.app.orchestration

import fr.vueconfort.app.equalizer.EqualizerRenderDecision
import fr.vueconfort.app.equalizer.EqualizerRenderRecord
import fr.vueconfort.app.equalizer.renderRecord
import fr.vueconfort.app.nativevision.NativeVisionAvailability
import fr.vueconfort.app.nativevision.NativeVisionCapability
import fr.vueconfort.app.nativevision.NativeVisionEngine
import fr.vueconfort.app.nativevision.NativeVisionPresence
import fr.vueconfort.app.nativevision.NativeVisionValue

/** Pure route adapters. The existing controllers still own commands, readback and receipts. */
class AndroidPublicVisionEngine : VisionEngine {
    override val descriptor = nativeDescriptor("android-public", VisionEngineCategory.NATIVE_ANDROID,
        setOf(NativeVisionCapability.MAGNIFICATION),
        "app/src/main/java/fr/vueconfort/app/nativevision/AndroidNativeVisionAdapter.kt")

    override fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment? =
        assessNative(request, context, descriptor, setOf(NativeVisionCapability.MAGNIFICATION),
            VisionTransport.ANDROID_NATIVE, publicMagnification = true)
}

class SamsungGuidedVisionEngine : VisionEngine {
    override val descriptor = nativeDescriptor("samsung-guided", VisionEngineCategory.NATIVE_SAMSUNG,
        SAMSUNG_CAPABILITIES, "app/src/main/java/fr/vueconfort/app/nativevision/SamsungNativeVisionAdapter.kt")

    override fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment? =
        assessNative(request, context, descriptor, SAMSUNG_CAPABILITIES, VisionTransport.SAMSUNG_NATIVE)
}

/** Android settings are not presented as Samsung-specific features merely because they run on a Galaxy. */
class AndroidSettingsGuidedVisionEngine : VisionEngine {
    private val supported = NativeVisionCapability.entries.toSet() - SAMSUNG_CAPABILITIES - NativeVisionCapability.MAGNIFICATION
    override val descriptor = nativeDescriptor("android-settings-guided", VisionEngineCategory.NATIVE_ANDROID,
        supported, "app/src/main/java/fr/vueconfort/app/nativevision/SamsungNativeVisionAdapter.kt")

    override fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment? =
        assessNative(request, context, descriptor, supported, VisionTransport.ANDROID_NATIVE)
}

/** Existing preview calculations only: this adapter neither renders nor observes a drawn frame. */
class PerceptualPreviewVisionEngine : VisionEngine {
    override val descriptor = VisionEngineDescriptor(
        engineId = "perceptual-preview", version = "agsl-perceptual-1", category = VisionEngineCategory.PERCEPTUAL,
        maturity = VisionMaturity.DEVICE_VALIDATED,
        evidence = listOf(
            VisionEvidence(VisionEvidenceKind.CODE_PRESENT, "app/src/main/java/fr/vueconfort/app/equalizer/EqualizerRendering.kt"),
            VisionEvidence(VisionEvidenceKind.COMPILED, "docs/EQUALIZER_VALIDATION_2026-09-24.md"),
            VisionEvidence(VisionEvidenceKind.INSTRUMENTATION_TESTED, "docs/EQUALIZER_VALIDATION_2026-09-24.md#fonctionnement-et-neutralité"),
            VisionEvidence(VisionEvidenceKind.DEVICE_EXECUTED, "docs/EQUALIZER_VALIDATION_2026-09-24.md")),
        capabilities = setOf("perceptual.equalizer"),
        requiredInputs = setOf("EqualizerPreferences", "EqualizerScene", "Android API level"),
        constraints = listOf("INTERNAL_PREVIEW_ONLY", "PIXEL_EFFECTS_REQUIRE_ANDROID_13", "NO_OPTICAL_OR_PRESCRIPTION_MAPPING",
            "HISTORICAL_DEVICE_PROOF_IS_NOT_A_CURRENT_RENDER_RECEIPT"))

    /** renderRecord itself calls toOpticalSettings: keep that single mapping as the authority. */
    fun renderMapping(payload: EnginePayload.Perceptual, sourceRevision: Long, supportsPixels: Boolean): EqualizerRenderRecord =
        payload.preferences.renderRecord(payload.scene, sourceRevision, supportsPixels).let { record ->
            record.copy(reasons = record.reasons.filterNot { it == "PREVIEW_DRAW_OBSERVED" } + "PLAN_ONLY_NO_RENDER_RECEIPT")
        }

    override fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment? {
        val payload = request.payload as? EnginePayload.Perceptual ?: return null
        val p = payload.preferences
        if (p != p.validated()) return VisionEngineAssessment(payload.transformationId,
            VisionEngineAvailability.REJECTED, VisionTransport.INTERNAL_PREVIEW,
            "Les préférences sortent des bornes de l’aperçu ; aucune correction silencieuse de la demande.")
        val record = renderMapping(payload, sourceRevision = 0,
            supportsPixels = (context.nativeCapabilities?.device?.sdkInt ?: 0) >= 33)
        val parameters = record.parameters
        val groups = buildSet {
            if (parameters.getValue("viewportScale") != 1f) add("layout-scaling")
            if (parameters.getValue("textStrokeDp") != 0f) add("text-weight")
            if (parameters.getValue("pixelIntensity") != 0f &&
                (parameters.getValue("sharpness") != 0f || parameters.getValue("contrast") != 1f)) add("image-color-processing")
            if (parameters.getValue("brightness") != 1f || parameters.getValue("whiteReduction") != 0f) add("luminance-processing")
        }
        return VisionEngineAssessment(payload.transformationId,
            if (record.decision == EqualizerRenderDecision.REJECTED) VisionEngineAvailability.UNAVAILABLE else VisionEngineAvailability.READY,
            VisionTransport.INTERNAL_PREVIEW,
            if (record.decision == EqualizerRenderDecision.REJECTED)
                "Les effets de pixels exigent Android 13 ou supérieur et un contexte connu."
            else "Aperçu interne calculé par le moteur existant ; l’affichage effectif exige un reçu du rendu.",
            composition = VisionCompositionPolicy(COEXISTING_CATEGORIES, groups, order = 10),
            guardRails = listOf("PREVIEW_BEFORE_OS_DISPLAY", "COMPOSITOR_ORDER_NOT_CONTROLLED",
                "NO_GLOBAL_PIXEL_TRANSPORT", "NO_PERCEPTUAL_BENEFIT_CLAIM") + record.reasons,
            identity = record.decision == EqualizerRenderDecision.IDENTITY && parameters.getValue("viewportScale") == 1f)
    }
}

/** Metadata is never an executable bridge to old experiments or another application. */
class OpticalResearchVisionEngine(val research: VisionResearchEngineMetadata) : VisionEngine {
    override val descriptor = VisionEngineDescriptor(
        engineId = "research.${research.modelId}", version = research.version,
        category = research.category, maturity = VisionMaturity.EXPERIMENTAL,
        evidence = research.evidence, capabilities = setOf("optical.${research.modelId}"),
        requiredInputs = research.requiredInputs.toSet(), constraints = research.limitations + "NOT_LINKED_IN_PUBLICATION")

    override fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment? {
        val payload = request.payload as? EnginePayload.Optical ?: return null
        if (payload.input.modelId != research.modelId) return null
        return VisionEngineAssessment(payload.transformationId, VisionEngineAvailability.INACCESSIBLE,
            VisionTransport.INTERNAL_PREVIEW,
            "Référence de recherche conservée séparément : aucun raccord exécutable dans cette application.",
            guardRails = listOf("METADATA_ONLY", "NO_ENGINE_IMPORT", "NO_MODEL_SUBSTITUTION", "NO_COMMERCIAL_VALIDATION"))
    }
}

/** Reserved transport contract: there is no implementation or opt-in that makes DL0 available. */
class FutureDisplayLensVisionEngine : VisionEngine {
    override val descriptor = VisionEngineDescriptor("future-display-lens", "contract-only-0",
        VisionEngineCategory.DISPLAY_TRANSPORT, VisionMaturity.UNAVAILABLE,
        capabilities = setOf("optical.display-lens-dl0"),
        requiredInputs = setOf("Qualified display transport", "Engine-specific input contract"),
        constraints = listOf("NO_IMPLEMENTATION", "NO_OEM_API_AVAILABLE", "NO_GLOBAL_CAPTURE_ADDED"))

    override fun assess(request: VisionRequest, context: VisionExecutionContext): VisionEngineAssessment? {
        val payload = request.payload as? EnginePayload.Optical ?: return null
        if (payload.input.modelId != "display-lens-dl0") return null
        return VisionEngineAssessment(payload.transformationId, VisionEngineAvailability.INACCESSIBLE,
            VisionTransport.FUTURE_DISPLAY_LENS, "Le transport Display Lens est un contrat futur, sans implémentation disponible.")
    }
}

private val SAMSUNG_CAPABILITIES = setOf(NativeVisionCapability.RELUMINO, NativeVisionCapability.COLOR_FILTER,
    NativeVisionCapability.EYE_COMFORT)
private val COEXISTING_CATEGORIES = setOf(VisionEngineCategory.NATIVE_ANDROID, VisionEngineCategory.NATIVE_SAMSUNG,
    VisionEngineCategory.PERCEPTUAL)

private fun nativeDescriptor(id: String, category: VisionEngineCategory, supported: Set<NativeVisionCapability>, source: String) =
    VisionEngineDescriptor(id, "native-route-1", category, VisionMaturity.PROTOTYPE,
        evidence = listOf(VisionEvidence(VisionEvidenceKind.CODE_PRESENT, source),
            VisionEvidence(VisionEvidenceKind.COMPILED, "docs/NATIVE_VISION_VALIDATION_2026-09-25.md")),
        capabilities = supported.map { "native.${it.name}" }.toSet(),
        requiredInputs = setOf("NativeVisionCapabilityResolver fresh result", "Exact NativeVisionValue"),
        constraints = listOf("READBACK_REQUIRED_FOR_ACTIVE_STATE", "NO_PROTECTED_WRITES", "NO_OPTICAL_EQUIVALENCE",
            "NO_LAB_DEPENDENCY", "NO_COMMERCIAL_VALIDATION"))

private fun assessNative(request: VisionRequest, context: VisionExecutionContext, descriptor: VisionEngineDescriptor,
                         supported: Set<NativeVisionCapability>, transport: VisionTransport,
                         publicMagnification: Boolean = false): VisionEngineAssessment? {
    val payload = request.payload as? EnginePayload.Native ?: return null
    if (payload.capability !in supported) return null
    val state = context.nativeCapabilities?.get(payload.capability)
    val availability = when {
        state == null -> VisionEngineAvailability.UNAVAILABLE
        state.capability != payload.capability -> VisionEngineAvailability.REJECTED
        state.presence != NativeVisionPresence.PRESENT -> VisionEngineAvailability.UNAVAILABLE
        state.engine == NativeVisionEngine.SAMSUNG_LAB -> VisionEngineAvailability.INACCESSIBLE
        else -> when (state.availability) {
            NativeVisionAvailability.AVAILABLE_PUBLIC -> if (publicMagnification && state.canApplyAutomatically &&
                state.engine == NativeVisionEngine.ANDROID_PUBLIC) VisionEngineAvailability.READY else VisionEngineAvailability.REJECTED
            NativeVisionAvailability.AVAILABLE_WITH_USER_PERMISSION -> if (publicMagnification)
                VisionEngineAvailability.PERMISSION_REQUIRED else VisionEngineAvailability.REJECTED
            NativeVisionAvailability.AVAILABLE_USER_ACTION -> VisionEngineAvailability.NEEDS_USER_ACTION
            NativeVisionAvailability.AVAILABLE_LAB_ONLY -> VisionEngineAvailability.INACCESSIBLE
            NativeVisionAvailability.REJECTED -> VisionEngineAvailability.REJECTED
            NativeVisionAvailability.UNAVAILABLE, NativeVisionAvailability.UNSUPPORTED_DEVICE -> VisionEngineAvailability.UNAVAILABLE
        }
    }
    return VisionEngineAssessment(payload.transformationId, availability, transport,
        when (availability) {
            VisionEngineAvailability.READY -> "Commande publique exacte disponible ; confirmation par relecture requise."
            VisionEngineAvailability.NEEDS_USER_ACTION -> "Réglage guidé : action dans les paramètres puis relecture quand elle est possible."
            VisionEngineAvailability.INACCESSIBLE -> "La voie privilégiée de laboratoire ne fait pas partie de cette orchestration."
            else -> state?.reason ?: "Aucune observation actuelle de cette capacité."
        },
        composition = nativeComposition(payload),
        guardRails = descriptor.constraints + listOf("NATIVE_VALUES_UNCHANGED", "COMPOSITOR_ORDER_NOT_CONTROLLED"),
        // Even a request to disable needs a real command or user action, never an identity shortcut.
        identity = false)
}

private fun nativeComposition(payload: EnginePayload.Native): VisionCompositionPolicy {
    val disabled = when (val value = payload.value) {
        is NativeVisionValue.Magnification -> !value.enabled || value.scale == 1f
        is NativeVisionValue.Relumino -> !value.enabled
        is NativeVisionValue.ExtraDim -> !value.enabled
        is NativeVisionValue.ColorFilter -> !value.enabled
        is NativeVisionValue.ColorCorrection -> !value.enabled
        is NativeVisionValue.Toggle -> !value.enabled
        is NativeVisionValue.FontScale -> value.scale == 1f
        is NativeVisionValue.Brightness, is NativeVisionValue.ScreenZoom -> false
    }
    if (disabled) return VisionCompositionPolicy(VisionEngineCategory.entries.toSet(), order = 20)
    val group = when (payload.capability) {
        NativeVisionCapability.MAGNIFICATION -> "system-magnification"
        NativeVisionCapability.RELUMINO, NativeVisionCapability.COLOR_FILTER, NativeVisionCapability.COLOR_CORRECTION,
        NativeVisionCapability.COLOR_INVERSION, NativeVisionCapability.EYE_COMFORT -> "image-color-processing"
        NativeVisionCapability.EXTRA_DIM, NativeVisionCapability.SYSTEM_BRIGHTNESS -> "luminance-processing"
        NativeVisionCapability.HIGH_CONTRAST_TEXT -> "text-weight"
        NativeVisionCapability.FONT_SCALE, NativeVisionCapability.SCREEN_ZOOM -> "layout-scaling"
    }
    return VisionCompositionPolicy(COEXISTING_CATEGORIES, setOf(group), order = 20)
}
