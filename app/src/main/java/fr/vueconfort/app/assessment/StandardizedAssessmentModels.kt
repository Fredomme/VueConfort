package fr.vueconfort.app.assessment

enum class StandardProtocol { STANDARDIZED_V2 }
enum class AcuityMethod { LANDOLT_C, TUMBLING_E }
enum class StandardEye { OD, OG, OU }
enum class TestDistance(val centimeters: Int) {
    NEAR_40(40), INTERMEDIATE_60(60), INTERMEDIATE_70(70),
    INTERMEDIATE_80(80), FAR_200(200), FAR_300(300), FAR_400(400)
}

data class PhysicalDisplayCalibration(
    val horizontalFactor: Float,
    val verticalFactor: Float,
    val xdpi: Float,
    val ydpi: Float,
    val density: Float,
    val orientation: String,
    val calculatedWidthMm: Float,
    val calculatedHeightMm: Float,
    val deviceModel: String,
    val calibratedAtMillis: Long,
    val valid: Boolean
)

data class StandardAcuityResult(
    val method: AcuityMethod,
    val eye: StandardEye,
    val distance: TestDistance,
    val withCorrection: Boolean,
    val logMar: Float?,
    val decimalAcuity: Float?,
    val tenths: Float?,
    val correctAnswers: Int,
    val totalTrials: Int,
    val errorRate: Float,
    val smallestDisplayableLogMar: Float,
    val smallestValidatedLogMar: Float?,
    val reliability: ResultReliability,
    val measuredAtMillis: Long
)

data class StandardContrastResult(
    val eye: StandardEye,
    val minimumContrast: Float?,
    val contrastSensitivity: Float?,
    val logContrastSensitivity: Float?,
    val correctAnswers: Int,
    val totalTrials: Int,
    val errorRate: Float,
    val declaredBrightnessStable: Boolean,
    val reliability: ResultReliability
)

data class AmslerResult(
    val eye: StandardEye,
    val linesStraight: Boolean,
    val wavyLines: Boolean,
    val missingArea: Boolean,
    val darkArea: Boolean,
    val blurredArea: Boolean,
    val centralPointVisible: Boolean,
    val recentOrSudden: Boolean,
    val annotationX: Float?,
    val annotationY: Float?
)

data class StandardizedAssessmentReport(
    val id: String,
    val protocol: StandardProtocol,
    val createdAtMillis: Long,
    val calibration: PhysicalDisplayCalibration,
    val conditionsConfirmed: Boolean,
    val acuityResults: List<StandardAcuityResult>,
    val contrastResults: List<StandardContrastResult>,
    val amslerResults: List<AmslerResult>
)
