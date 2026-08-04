package fr.vueconfort.app.assessment

import fr.vueconfort.app.model.AssistProfile

enum class TestedEye { RIGHT, LEFT, BOTH }
enum class AssessmentDistance(val centimeters: Int) { NEAR(40), INTERMEDIATE(70) }
enum class ResultReliability { LOW, MEDIUM, HIGH }

data class EyeComfortResult(
    val eye: TestedEye,
    val distanceCm: Int,
    val withCorrection: Boolean,
    val smallestOptotypeMm: Float,
    val acuityScore: Int,
    val errorRatePercent: Int,
    val contrastScore: Int,
    val overloadScore: Int,
    val minimumReadableSp: Int,
    val comfortableTextSp: Int,
    val preferredMagnification: Float,
    val trialCount: Int
)

data class VisualComfortAssessment(
    val id: String,
    val createdAtMillis: Long,
    val ageRange: String,
    val wearsCorrection: Boolean,
    val testedWithCorrection: Boolean,
    val usualDistanceCm: Int,
    val physicalCalibrationFactor: Float,
    val physicalCalibrationConfirmed: Boolean,
    val distanceConfirmed: Boolean,
    val interruptedCount: Int,
    val right: EyeComfortResult?,
    val left: EyeComfortResult?,
    val both: EyeComfortResult?,
    val reliability: ResultReliability,
    val doubleVision: Boolean,
    val recentDistortion: Boolean,
    val missingOrDarkArea: Boolean
)

data class AssessmentRecommendation(
    val profile: AssistProfile,
    val reasons: List<String>,
    val professionalCheckRecommended: Boolean,
    val urgentAdvice: Boolean,
    val eyeDifference: Int
)
