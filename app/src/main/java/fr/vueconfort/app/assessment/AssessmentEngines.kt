package fr.vueconfort.app.assessment

import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.optical.OpticalSettings
import kotlin.math.abs
import kotlin.math.roundToInt

object AcuityTestEngine {
    val optotypeSizesMm = listOf(8f, 6f, 4.5f, 3.5f, 2.7f, 2.1f)

    fun result(
        eye: TestedEye,
        distanceCm: Int,
        withCorrection: Boolean,
        answers: List<Boolean>
    ): EyeComfortResult {
        val groups = answers.chunked(3)
        val bestLevel = groups.indexOfLast { group -> group.count { it } >= 2 }.coerceAtLeast(0)
        val correct = answers.count { it }
        val errorRate = if (answers.isEmpty()) 100 else
            ((answers.size - correct) * 100f / answers.size).roundToInt()
        val size = optotypeSizesMm.getOrElse(bestLevel) { optotypeSizesMm.first() }
        return EyeComfortResult(
            eye, distanceCm, withCorrection, size,
            ((bestLevel + 1) * 100f / optotypeSizesMm.size).roundToInt(),
            errorRate, 0, 0, 28, 34,
            (1.5f + (optotypeSizesMm.size - bestLevel) * 0.25f).coerceIn(1f, 8f),
            answers.size
        )
    }
}

object ContrastTestEngine {
    fun score(answers: List<Boolean>): Int =
        if (answers.isEmpty()) 0 else (answers.count { it } * 100f / answers.size).roundToInt()
}

object ReliabilityEngine {
    fun calculate(
        physicallyCalibrated: Boolean,
        distanceConfirmed: Boolean,
        results: List<EyeComfortResult>,
        interruptions: Int
    ): ResultReliability {
        var points = 0
        if (physicallyCalibrated) points += 2
        if (distanceConfirmed) points += 2
        if (results.sumOf { it.trialCount } >= 24) points += 2
        if (results.all { it.errorRatePercent <= 45 }) points += 1
        if (interruptions == 0) points += 1
        return when {
            points >= 7 -> ResultReliability.HIGH
            points >= 4 -> ResultReliability.MEDIUM
            else -> ResultReliability.LOW
        }
    }
}

object AssessmentProfileEngine {
    fun recommend(assessment: VisualComfortAssessment): AssessmentRecommendation {
        val eyes = listOfNotNull(assessment.right, assessment.left)
        val leastFavorable = eyes.minByOrNull { it.acuityScore }
        val difference = if (assessment.right != null && assessment.left != null) {
            abs(assessment.right.acuityScore - assessment.left.acuityScore)
        } else 0
        var scale = leastFavorable?.preferredMagnification ?: 2f
        val reasons = mutableListOf<String>()
        if (eyes.any { it.contrastScore < 55 }) {
            scale += 0.5f
            reasons += "Les symboles peu contrastés ont demandé davantage d’effort."
        }
        if (eyes.any { it.overloadScore >= 60 }) {
            scale += 0.5f
            reasons += "Les présentations chargées ont été moins confortables."
        }
        if (assessment.usualDistanceCm > 0 && leastFavorable != null) {
            scale *= (assessment.usualDistanceCm.toFloat() / leastFavorable.distanceCm)
                .coerceIn(0.8f, 1.4f)
        }
        scale = scale.coerceIn(1f, 8f)
        if (reasons.isEmpty()) reasons += "Réglage fondé sur la taille confortable choisie."
        val urgent = assessment.doubleVision ||
            assessment.recentDistortion || assessment.missingOrDarkArea
        val check = urgent || difference >= 25 || eyes.any { it.acuityScore <= 25 }
        return AssessmentRecommendation(
            profile = AssistProfile(
                id = "visual_assessment",
                name = "Bilan visuel",
                description = "Profil proposé par le bilan de confort",
                magnificationScale = scale,
                magnificationEnabled = scale > 1f,
                overlayAlpha = if (eyes.any { it.contrastScore < 55 }) 0.95f else 0.78f,
                locked = eyes.any { it.overloadScore >= 60 },
                expanded = false,
                predefined = false,
                optical = OpticalSettings(
                    enabled = true,
                    sharpness = if (eyes.any { it.overloadScore >= 60 }) 0.18f else 0.1f,
                    localContrast = if (eyes.any { it.contrastScore < 55 }) 1.18f else 1.06f,
                    gamma = 1f,
                    whiteReduction = if (eyes.any { it.contrastScore < 55 }) 0.12f else 0.05f
                )
            ),
            reasons = reasons,
            professionalCheckRecommended = check,
            urgentAdvice = urgent,
            eyeDifference = difference
        )
    }
}
