package fr.vueconfort.app.assessment

import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.tan

object VisualAngleCalculator {
    fun millimetersToPixels(mm: Float, dpi: Float, correctionFactor: Float): Float =
        mm * dpi / 25.4f * correctionFactor

    fun pixelsToMillimeters(px: Float, dpi: Float, correctionFactor: Float): Float =
        px * 25.4f / (dpi * correctionFactor).coerceAtLeast(0.001f)

    fun visualAngleArcMinutes(sizeMm: Float, distanceMm: Float): Float =
        Math.toDegrees(2.0 * atan((sizeMm / 2.0) / distanceMm)).toFloat() * 60f

    fun sizeMmForArcMinutes(arcMinutes: Float, distanceMm: Float): Float {
        val radians = Math.toRadians((arcMinutes / 60f).toDouble())
        return (2.0 * distanceMm * tan(radians / 2.0)).toFloat()
    }

    fun decimalFromLogMar(logMar: Float): Float = 10f.pow(-logMar)
    fun tenthsFromLogMar(logMar: Float): Float = decimalFromLogMar(logMar) * 10f

    fun landoltGeometry(logMar: Float, distanceCm: Int): OptotypeGeometry {
        val marArcMinutes = 10f.pow(logMar)
        val outerMm = sizeMmForArcMinutes(5f * marArcMinutes, distanceCm * 10f)
        return OptotypeGeometry(outerMm, outerMm / 5f, outerMm / 5f)
    }

    fun isDisplayable(
        geometry: OptotypeGeometry,
        calibration: PhysicalDisplayCalibration
    ): Boolean {
        val strokePx = millimetersToPixels(
            geometry.strokeMm, calibration.xdpi, calibration.horizontalFactor
        )
        val gapPx = millimetersToPixels(
            geometry.gapMm, calibration.xdpi, calibration.horizontalFactor
        )
        return calibration.valid && strokePx >= 2f && gapPx >= 2f
    }

    init {
        check(kotlin.math.abs(pixelsToMillimeters(millimetersToPixels(10f, 400f, 1f), 400f, 1f) - 10f) < 0.001f)
        check(kotlin.math.abs(decimalFromLogMar(0f) - 1f) < 0.001f)
        check(kotlin.math.abs(tenthsFromLogMar(0.2f) - 6.309f) < 0.02f)
    }
}

data class OptotypeGeometry(
    val outerDiameterMm: Float,
    val strokeMm: Float,
    val gapMm: Float
)
