package fr.vueconfort.app.orchestration

import fr.vueconfort.app.assessment.StandardizedAssessmentReport
import fr.vueconfort.app.assessment.VisualComfortAssessment
import fr.vueconfort.app.calibration.CalibrationSession
import fr.vueconfort.app.equalizer.ConfirmedBilanReference
import fr.vueconfort.app.equalizer.EqualizerProfile
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.model.UserVisualContext
import fr.vueconfort.app.model.VisualProfile

/**
 * An immutable, non-persisted view over the existing profile and its separately stored evidence.
 * It does not own another set of preferences, perform a calibration or create an optical model.
 */
class VisionProfileSnapshot(
    val sourceProfile: EqualizerProfile = EqualizerProfile(),
    prescription: OpticalPrescription? = null,
    val calibration: VisionCalibrationSnapshot = VisionCalibrationSnapshot(),
    val visualProfile: VisualProfile? = null,
    /** Questionnaire values can contain old UI defaults; they are not physical measurements. */
    val declaredUserContext: UserVisualContext? = null
) {
    init { sourceProfile.validated() }
    private val sourcePrescription = prescription

    val profileId get() = sourceProfile.id
    val sourceRevision get() = sourceProfile.revision
    val sourceSchemaVersion get() = sourceProfile.schemaVersion
    val equalizerPreferences get() = sourceProfile.preferences
    val equalizerScene get() = sourceProfile.scene
    val calculatedParameters get() = sourceProfile.calculated
    val equalizerApplied get() = sourceProfile.applied
    val nativePreferences get() = sourceProfile.nativeVision.preferences
    val nativeRequested get() = sourceProfile.nativeVision.requested
    val nativeApplied get() = sourceProfile.nativeVision.applied
    val nativeRevision get() = nativeRequested.revision
    /** Historical evidence only. Execution must resolve fresh capabilities in its own context. */
    val lastRecordedNativeCapabilities get() = sourceProfile.nativeVision.capabilities
    val declaredDistanceCm get() = sourceProfile.context.declaredDistanceCm
    val correctionWorn get() = sourceProfile.context.correctionWorn

    val prescriptionStatus: VisionPrescriptionStatus = when {
        prescription == null -> VisionPrescriptionStatus.ABSENT
        !prescription.confirmedByUser -> VisionPrescriptionStatus.UNCONFIRMED
        !prescription.isValid -> VisionPrescriptionStatus.INVALID
        sourceProfile.confirmedBilan != null && sourceProfile.confirmedBilan != ConfirmedBilanReference.from(prescription) ->
            VisionPrescriptionStatus.PROFILE_REFERENCE_MISMATCH
        else -> VisionPrescriptionStatus.CONFIRMED
    }

    /** A current confirmed record, never values inferred from sliders or a self-assessment score. */
    val confirmedPrescription: OpticalPrescription? = prescription.takeIf { prescriptionStatus == VisionPrescriptionStatus.CONFIRMED }
    val prescriptionReferenceMatchesProfile: Boolean = confirmedPrescription != null &&
        sourceProfile.confirmedBilan == ConfirmedBilanReference.from(confirmedPrescription)

    /** Enrich evidence without turning an invalid/unconfirmed/stale document into an absent one. */
    fun withCalibration(calibration: VisionCalibrationSnapshot): VisionProfileSnapshot = VisionProfileSnapshot(
        sourceProfile, sourcePrescription, calibration, visualProfile, declaredUserContext)

    fun currentNativeResult(capability: fr.vueconfort.app.nativevision.NativeVisionCapability) =
        sourceProfile.nativeVision.currentResult(capability)

    /** Describes current profile intent only; no native read, command, persistence or optical inference. */
    fun requests(includePreview: Boolean = true): List<VisionRequest> = buildList {
        if (includePreview) add(VisionRequest(
            id = "perceptual.equalizer", payload = EnginePayload.Perceptual(equalizerPreferences, equalizerScene),
            allowedTransports = setOf(VisionTransport.INTERNAL_PREVIEW), sourceRevision = sourceRevision
        ))
        if (sourceProfile.nativeVision.enabled) addAll(nativeRequests())
    }

    /**
     * Explicit native commands, including OFF, for a caller validating a pending transition.
     * This method intentionally does not assume that the profile has already been activated.
     */
    fun nativeRequests(): List<VisionRequest> = nativeRequested.values.entries.sortedBy { it.key.name }.map { (capability, value) ->
        VisionRequest(id = "native.${capability.name}", payload = EnginePayload.Native(capability, value),
            allowedTransports = setOf(VisionTransport.ANDROID_NATIVE, VisionTransport.SAMSUNG_NATIVE),
            sourceRevision = nativeRevision)
    }
}

enum class VisionPrescriptionStatus { ABSENT, UNCONFIRMED, INVALID, PROFILE_REFERENCE_MISMATCH, CONFIRMED }
enum class VisionCalibrationKind { PERCEPTUAL_PREFERENCES, COMFORT_SELF_ASSESSMENT, STANDARDIZED_SELF_ASSESSMENT }
enum class VisionQuantitativeUncertainty { NOT_RECORDED }

/** References are derived from real source records, never generated as proof that a test occurred. */
data class VisionCalibrationReference(
    val kind: VisionCalibrationKind,
    val recordId: String?,
    val recordedAtMillis: Long,
    val provenance: String
)

/** Null explicitly means the source did not record this condition. A protocol label is not confirmation. */
data class VisionCalibrationConditions(
    val kind: VisionCalibrationKind,
    val conditionsConfirmed: Boolean? = null,
    val physicalDisplayCalibrationConfirmed: Boolean? = null,
    val distanceConfirmed: Boolean? = null,
    val interruptedCount: Int? = null,
    val trials: List<VisionRecordedTestConditions> = emptyList()
)

data class VisionRecordedTestConditions(
    val sourceResult: String,
    val eye: String?,
    val recordedDistanceCm: Int? = null,
    val withCorrection: Boolean? = null,
    val brightnessDeclaredStable: Boolean? = null
)

/**
 * Keeps actual recorded results intact, including their reliability and physical calibration.
 * Choice confidence, acuity/contrast scores and reliability labels remain their original quantities;
 * none becomes a prescription, PSF/OTF, statistical uncertainty or medical validation.
 */
data class VisionCalibrationSnapshot(
    val perceptualSession: CalibrationSession? = null,
    val comfortAssessment: VisualComfortAssessment? = null,
    val standardizedAssessment: StandardizedAssessmentReport? = null
) {
    val hasRecordedEvidence: Boolean get() = perceptualSession != null || comfortAssessment != null || standardizedAssessment != null

    /** Existing source models do not carry a quantitative measurement-error estimate. */
    val quantitativeUncertainty: VisionQuantitativeUncertainty get() = VisionQuantitativeUncertainty.NOT_RECORDED

    val references: List<VisionCalibrationReference> get() = buildList {
        perceptualSession?.let { add(VisionCalibrationReference(VisionCalibrationKind.PERCEPTUAL_PREFERENCES,
            recordId = null, recordedAtMillis = it.startedAtMillis, provenance = "CalibrationSession: user preference comparisons")) }
        comfortAssessment?.let { add(VisionCalibrationReference(VisionCalibrationKind.COMFORT_SELF_ASSESSMENT,
            it.id, it.createdAtMillis, "VisualComfortAssessment: recorded local self-assessment")) }
        standardizedAssessment?.let { add(VisionCalibrationReference(VisionCalibrationKind.STANDARDIZED_SELF_ASSESSMENT,
            it.id, it.createdAtMillis, "StandardizedAssessmentReport:${it.protocol.name}")) }
    }

    val conditions: List<VisionCalibrationConditions> get() = buildList {
        // The perceptual session has no recorded physical distance/correction/brightness conditions.
        perceptualSession?.let { add(VisionCalibrationConditions(VisionCalibrationKind.PERCEPTUAL_PREFERENCES)) }
        comfortAssessment?.let { assessment ->
            add(VisionCalibrationConditions(VisionCalibrationKind.COMFORT_SELF_ASSESSMENT,
                physicalDisplayCalibrationConfirmed = assessment.physicalCalibrationConfirmed,
                distanceConfirmed = assessment.distanceConfirmed, interruptedCount = assessment.interruptedCount,
                trials = listOfNotNull(assessment.right, assessment.left, assessment.both).map { result ->
                    VisionRecordedTestConditions("EyeComfortResult", result.eye.name, result.distanceCm, result.withCorrection)
                }))
        }
        standardizedAssessment?.let { report ->
            add(VisionCalibrationConditions(VisionCalibrationKind.STANDARDIZED_SELF_ASSESSMENT,
                conditionsConfirmed = report.conditionsConfirmed,
                // "valid" is preserved in report.calibration; it is not a user-confirmation flag.
                trials = report.acuityResults.map { result ->
                    VisionRecordedTestConditions("StandardAcuityResult:${result.method.name}", result.eye.name,
                        result.distance.centimeters, result.withCorrection)
                } + report.contrastResults.map { result ->
                    VisionRecordedTestConditions("StandardContrastResult", result.eye.name,
                        brightnessDeclaredStable = result.declaredBrightnessStable)
                } + report.amslerResults.map { result ->
                    VisionRecordedTestConditions("AmslerResult", result.eye.name)
                }))
        }
    }
}
