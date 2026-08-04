package fr.vueconfort.app.assessment

import kotlin.math.log10
import kotlin.math.pow
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.optical.OpticalSettings

object LogMarTestEngine {
    val levels = (10 downTo 0).map { it / 10f }

    fun displayableLevels(
        distance: TestDistance,
        calibration: PhysicalDisplayCalibration
    ) = levels.filter {
        VisualAngleCalculator.isDisplayable(
            VisualAngleCalculator.landoltGeometry(it, distance.centimeters),
            calibration
        )
    }

    fun passes(correctInFive: Int) = correctInFive >= 3

    fun buildResult(
        method: AcuityMethod,
        eye: StandardEye,
        distance: TestDistance,
        withCorrection: Boolean,
        validatedLogMar: Float?,
        smallestDisplayable: Float,
        correct: Int,
        total: Int,
        calibrationValid: Boolean,
        interruptions: Int
    ): StandardAcuityResult {
        val reliable = when {
            !calibrationValid || total < 10 -> ResultReliability.LOW
            total >= 20 && interruptions == 0 -> ResultReliability.HIGH
            else -> ResultReliability.MEDIUM
        }
        val measured = validatedLogMar.takeIf {
            reliable != ResultReliability.LOW && calibrationValid
        }
        return StandardAcuityResult(
            method, eye, distance, withCorrection, measured,
            measured?.let(VisualAngleCalculator::decimalFromLogMar),
            measured?.let(VisualAngleCalculator::tenthsFromLogMar),
            correct, total,
            if (total == 0) 1f else (total - correct).toFloat() / total,
            smallestDisplayable, measured, reliable, System.currentTimeMillis()
        )
    }
}

object ScreenContrastTestEngine {
    val contrastLevels = listOf(1f, 0.5f, 0.25f, 0.125f, 0.063f, 0.032f, 0.016f)
    fun passes(correctInFive: Int) = correctInFive >= 3
    fun sensitivity(minimumContrast: Float) = 1f / minimumContrast
    fun logSensitivity(minimumContrast: Float) = log10(sensitivity(minimumContrast))
}

object StandardizedProfileEngine {
    fun create(report: StandardizedAssessmentReport): AssistProfile {
        val worstLogMar = report.acuityResults
            .filter { it.reliability != ResultReliability.LOW }
            .mapNotNull { it.logMar }
            .maxOrNull() ?: 0.3f
        val scale = (10f.pow(worstLogMar) * 1.25f).coerceIn(1f, 8f)
        val weakContrast = report.contrastResults.any {
            (it.logContrastSensitivity ?: 2f) < 1.2f
        }
        return AssistProfile(
            id = "visual_assessment",
            name = "Bilan visuel",
            description = "Profil proposé depuis STANDARDIZED_V2",
            magnificationScale = scale,
            magnificationEnabled = scale > 1f,
            overlayAlpha = if (weakContrast) 0.95f else 0.78f,
            locked = false,
            expanded = false,
            predefined = false,
            optical = OpticalSettings.Neutral
        )
    }
}
