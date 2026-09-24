package fr.vueconfort.app.recommendation

import fr.vueconfort.app.assessment.AssessmentProfileEngine
import fr.vueconfort.app.assessment.StandardizedAssessmentReport
import fr.vueconfort.app.assessment.StandardizedProfileEngine
import fr.vueconfort.app.assessment.VisualComfortAssessment
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.calibration.CalibrationParameter
import kotlin.math.abs
import kotlin.math.max

data class PrescriptionProfileRecommendation(
    val assistProfile: AssistProfile,
    val visualProfile: VisualProfile,
    val reasons: List<String>,
    val calibrationPriorities: List<CalibrationParameter>,
    val usedPrescription: Boolean,
    val usedVueConfortTests: Boolean
)

/**
 * Conservative display-initialisation policy, not an optical or medical correction.
 *
 * ADD and reading distance can initialise near-reading size because VueConfort can
 * actually change text size and Android magnification. SPH/CYL/AXIS are retained as
 * distinct OD/OG evidence and select calibration priorities, but are never converted
 * into a simulated lens correction. When tests exist, the least favourable tested
 * requirement wins; values from both eyes are never averaged.
 */
object PrescriptionProfileEngine {
    fun recommend(
        prescription: OpticalPrescription,
        currentAssistProfile: AssistProfile,
        currentVisualProfile: VisualProfile,
        legacyAssessment: VisualComfortAssessment? = null,
        standardizedAssessment: StandardizedAssessmentReport? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): PrescriptionProfileRecommendation {
        require(prescription.isValid) {
            prescription.validationErrors().joinToString(" ")
        }

        val reasons = mutableListOf<String>()
        val priorities = linkedSetOf<CalibrationParameter>()
        val maximumAddition = listOfNotNull(
            prescription.rightEye.addition,
            prescription.leftEye.addition
        ).maxOrNull()

        val additionScale = when {
            maximumAddition == null || maximumAddition == 0f -> 1f
            maximumAddition < 1f -> 1.25f
            maximumAddition < 2f -> 1.5f
            else -> 2f
        }
        val distanceFactor = prescription.recommendedReadingDistanceCm
            ?.let { (it / 40f).coerceIn(0.75f, 1.75f) } ?: 1f

        var scale = max(currentAssistProfile.magnificationScale, additionScale * distanceFactor)
        var fontSize = max(currentVisualProfile.fontSizeSp, when {
            maximumAddition == null || maximumAddition == 0f -> currentVisualProfile.fontSizeSp
            maximumAddition < 1f -> 20f
            maximumAddition < 2f -> 22f
            else -> 24f
        } * distanceFactor)
        var overlayAlpha = currentAssistProfile.overlayAlpha
        var optical = currentAssistProfile.optical
        var usedTests = false

        val legacyRecommendation = legacyAssessment?.let(AssessmentProfileEngine::recommend)
        val standardProfile = standardizedAssessment?.let(StandardizedProfileEngine::create)
        if (legacyRecommendation != null) {
            usedTests = true
            scale = max(scale, legacyRecommendation.profile.magnificationScale)
            overlayAlpha = max(overlayAlpha, legacyRecommendation.profile.overlayAlpha)
            optical = legacyRecommendation.profile.optical
            fontSize = max(
                fontSize,
                listOfNotNull(
                    legacyAssessment.right?.comfortableTextSp,
                    legacyAssessment.left?.comfortableTextSp,
                    legacyAssessment.both?.comfortableTextSp
                ).maxOrNull()?.toFloat() ?: fontSize
            )
            reasons += "Le besoin le plus exigeant observé lors du bilan VueConfort a été conservé."
        }
        if (standardProfile != null) {
            usedTests = true
            scale = max(scale, standardProfile.magnificationScale)
            overlayAlpha = max(overlayAlpha, standardProfile.overlayAlpha)
            reasons += "Le bilan standardisé a été combiné au point de départ de lecture."
        }

        if (maximumAddition != null && maximumAddition > 0f) {
            reasons += "L’addition fournie initialise prudemment la taille et le grossissement de lecture de près."
            priorities += CalibrationParameter.FONT_SIZE
        }
        prescription.recommendedReadingDistanceCm?.let {
            reasons += "La distance de lecture indiquée ($it cm) ajuste le point de départ, puis doit être validée à l’écran."
            priorities += CalibrationParameter.FONT_SIZE
        }

        val hasSphere = prescription.rightEye.sphere != null || prescription.leftEye.sphere != null
        if (hasSphere) {
            reasons += "Les sphères OD et OG sont conservées séparément ; l’écran ne simule pas une correction sphérique."
            priorities += CalibrationParameter.FONT_SIZE
        }
        val hasCylinder = prescription.rightEye.cylinder != null || prescription.leftEye.cylinder != null
        if (hasCylinder) {
            reasons += "Cylindre et axe orientent la vérification de netteté et d’espacement sans reproduire un verre correcteur."
            priorities += CalibrationParameter.FONT_WEIGHT
            priorities += CalibrationParameter.LETTER_SPACING
        }

        val rightSphericalEquivalent = prescription.rightEye.sphere?.let {
            it + (prescription.rightEye.cylinder ?: 0f) / 2f
        }
        val leftSphericalEquivalent = prescription.leftEye.sphere?.let {
            it + (prescription.leftEye.cylinder ?: 0f) / 2f
        }
        if (rightSphericalEquivalent != null && leftSphericalEquivalent != null &&
            abs(rightSphericalEquivalent - leftSphericalEquivalent) >= 1f
        ) {
            reasons += "La différence OD/OG impose une validation œil par œil ; aucune moyenne arbitraire n’est appliquée."
            priorities += CalibrationParameter.FONT_SIZE
            priorities += CalibrationParameter.CONTRAST
        }

        if (usedTests) priorities += CalibrationParameter.CONTRAST
        priorities += CalibrationParameter.LINE_HEIGHT

        return PrescriptionProfileRecommendation(
            assistProfile = currentAssistProfile.copy(
                id = AssistProfile.MY_VISION_ID,
                name = "Ma vue",
                description = if (usedTests) {
                    "Correction visuelle + tests VueConfort"
                } else {
                    "Point de départ issu de ma correction visuelle"
                },
                magnificationScale = scale.coerceIn(1f, 8f),
                magnificationEnabled = scale > 1f,
                overlayAlpha = overlayAlpha.coerceIn(0.55f, 1f),
                predefined = false,
                updatedAtMillis = nowMillis,
                optical = optical
            ).sanitized(),
            visualProfile = currentVisualProfile.copy(
                id = AssistProfile.MY_VISION_ID,
                name = "Ma vue",
                fontSizeSp = fontSize.coerceIn(14f, 34f),
                localZoomEnabled = scale > 1f,
                calibrated = false,
                calibrationConfidence = 0f,
                updatedAtMillis = nowMillis
            ),
            reasons = reasons.distinct(),
            calibrationPriorities = priorities.toList(),
            usedPrescription = true,
            usedVueConfortTests = usedTests
        )
    }
}
