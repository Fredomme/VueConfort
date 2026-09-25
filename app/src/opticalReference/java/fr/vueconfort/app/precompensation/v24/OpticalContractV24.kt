package fr.vueconfort.app.precompensation.v24

import kotlin.math.*

enum class OpticalDecisionV24 { APPLY, IDENTITY, REJECTED }
enum class TestEyeV24 { OD, OG }
enum class LuminanceConventionV24 { LINEAR_RELATIVE_SRGB_D65 }
enum class ResidualOriginV24 { SYNTHETIC_DIRECT, MEASURED_NEAR, PERCEPTUAL_ESTIMATE }
enum class PhysicalPitchOriginV24 { SAMSUNG_NOMINAL_ESTIMATE, INSTRUMENT_MEASURED }
enum class WearStateV24 { NONE, KNOWN_CORRECTION, UNKNOWN }

/** Clinical lens powers in dioptres (m^-1); minus cylinder, axis degrees modulo 180.
 * Axis: x right, y down in the stimulus. Clinical axes must be mapped to this frame.
 * Distance prescription is metadata, NEVER automatically used as the near residual. */
data class PrescriptionV24(val sphereD: Double, val cylinderD: Double, val axisDegrees: Double, val addD: Double? = null)
data class WornCorrectionV24(val state: WearStateV24, val lens: PrescriptionV24? = null,
    val vertexDistanceMm: Double? = null, val nearAddActuallyUsedD: Double? = null)
/** Equivalent required correction at the working distance, supplied explicitly.
 * No accommodation is inferred from age; null accommodationD means UNKNOWN. */
data class ResidualOpticalDefectV24(val sphereD: Double, val cylinderD: Double, val axisDegrees: Double,
    val origin: ResidualOriginV24, val calibrationId: String, val accommodationD: Double? = null)

data class PhysicalGeometryV24(
    val finalWidthPx: Int = 128, val finalHeightPx: Int = 128,
    val pixelPitchMm: Double = 25.4 / (hypot(2340.0, 1080.0) / 6.2),
    val pitchOrigin: PhysicalPitchOriginV24 = PhysicalPitchOriginV24.SAMSUNG_NOMINAL_ESTIMATE,
    val viewingDistanceMm: Double = 400.0,
    val distanceProvenance: String = "EXPERIMENTAL_DECLARED_NOT_MEASURED",
) {
    val physicalPpi get() = 25.4 / pixelPitchMm
    val widthMm get() = finalWidthPx * pixelPitchMm
    val heightMm get() = finalHeightPx * pixelPitchMm
    val widthAngleDeg get() = Math.toDegrees(2 * atan(widthMm / (2 * viewingDistanceMm)))
    val heightAngleDeg get() = Math.toDegrees(2 * atan(heightMm / (2 * viewingDistanceMm)))
    /** Local paraxial angular pitch at the centre, degrees/physical pixel. */
    val degreesPerPixel get() = Math.toDegrees(2 * atan(pixelPitchMm / (2 * viewingDistanceMm)))
    val pixelsPerDegree get() = 1.0 / degreesPerPixel
    val nyquistPerAxisCpd get() = pixelsPerDegree / 2
    fun frequencyCpd(index: Int, fftSize: Int): Double =
        (if (index <= fftSize / 2) index else index - fftSize).toDouble() / fftSize * pixelsPerDegree
}

data class OpticalContractV24(
    val geometry: PhysicalGeometryV24 = PhysicalGeometryV24(),
    val eye: TestEyeV24 = TestEyeV24.OD,
    val prescription: PrescriptionV24? = null,
    val wornCorrection: WornCorrectionV24 = WornCorrectionV24(WearStateV24.UNKNOWN),
    val residual: ResidualOpticalDefectV24? = null,
    val pupilDiameterMm: Double = 3.0,
    val pupilProvenance: String = "SYNTHETIC_ASSUMED",
    val wavelengthNm: Double = 555.0,
    val luminance: LuminanceConventionV24 = LuminanceConventionV24.LINEAR_RELATIVE_SRGB_D65,
    val requestedDecision: OpticalDecisionV24 = OpticalDecisionV24.APPLY,
) {
    /** Bounded numerical V1 domain; 128 physical pixels surrounded by the known 0.5 field.
     * Limits are validated against the independent audit integral, not a claim about users. */
    fun rejectionReasons(): List<String> = buildList {
        val g = geometry
        if (g.finalWidthPx != 128 || g.finalHeightPx != 128) add("V1_FINAL_RASTER_MUST_BE_128_SQUARE")
        if (!g.pixelPitchMm.isFinite() || g.physicalPpi !in 400.0..430.0) add("PHYSICAL_PITCH_OUTSIDE_V1")
        if (!g.viewingDistanceMm.isFinite() || g.viewingDistanceMm !in 300.0..600.0) add("DISTANCE_OUTSIDE_300_600_MM")
        if (g.distanceProvenance.isBlank() || pupilProvenance.isBlank()) add("MISSING_PROVENANCE")
        if (!pupilDiameterMm.isFinite() || pupilDiameterMm !in 2.0..5.0) add("PUPIL_OUTSIDE_2_5_MM")
        if (wavelengthNm != 555.0) add("V1_MONOCHROMATIC_555_NM_ONLY")
        if (requestedDecision == OpticalDecisionV24.REJECTED) add("CALLER_REJECTED")
        if (residual == null) add("NEAR_RESIDUAL_UNKNOWN") else {
            val r = residual
            if (!listOf(r.sphereD, r.cylinderD, r.axisDegrees).all { it.isFinite() }) add("NON_FINITE_RESIDUAL")
            if (r.cylinderD !in -0.5..0.0 || abs(r.sphereD) > 0.5 || abs(r.sphereD + r.cylinderD) > 0.5)
                add("V1_PRINCIPAL_POWERS_LIMIT_0_5_D")
            if (r.axisDegrees !in 0.0..<180.0) add("AXIS_MUST_BE_0_TO_180_EXCLUSIVE_Y_DOWN")
            if (r.calibrationId.isBlank()) add("MISSING_RESIDUAL_PROVENANCE")
            if (r.accommodationD != null && (!r.accommodationD.isFinite() || r.accommodationD < 0)) add("INVALID_ACCOMMODATION")
        }
        if (wornCorrection.state == WearStateV24.KNOWN_CORRECTION && wornCorrection.lens == null) add("MISSING_WORN_LENS")
    }
}

/** Technical evidence of app pixels mapping to native display pixels. This cannot certify
 * optical pitch, OLED photometry or an unobservable vendor compositor; those stay UNKNOWN. */
data class DisplayObservationV24(val viewWidthPx: Int, val viewHeightPx: Int,
    val canvasDensityScale: Double, val transformIsTranslationOnly: Boolean,
    val logicalDisplayWidthPx: Int, val logicalDisplayHeightPx: Int,
    val nativeModeWidthPx: Int, val nativeModeHeightPx: Int, val fullyVisible: Boolean) {
    fun rejectionReasons(g: PhysicalGeometryV24): List<String> = buildList {
        if (viewWidthPx != g.finalWidthPx || viewHeightPx != g.finalHeightPx) add("VIEW_SIZE_MISMATCH")
        if (abs(canvasDensityScale - 1) > 1e-6 || !transformIsTranslationOnly) add("CANVAS_SCALE_OR_ROTATION")
        if (logicalDisplayWidthPx != nativeModeWidthPx || logicalDisplayHeightPx != nativeModeHeightPx) add("DISPLAY_MODE_SCALING")
        if (g.pitchOrigin == PhysicalPitchOriginV24.SAMSUNG_NOMINAL_ESTIMATE &&
            listOf(nativeModeWidthPx, nativeModeHeightPx).sorted() != listOf(1080, 2340)) add("S25_NOMINAL_PITCH_REQUIRES_S25_NATIVE_FORMAT")
        if (!fullyVisible) add("STIMULUS_CLIPPED")
    }
}

/** Shared by every V2.4 bitmap and test. No linear value is interpreted as encoded sRGB. */
object LuminanceV24 {
    fun decode(code: Double): Double { require(code.isFinite() && code in 0.0..1.0)
        return if (code <= .04045) code / 12.92 else ((code + .055) / 1.055).pow(2.4) }
    fun encode(linear: Double): Double { require(linear.isFinite() && linear in 0.0..1.0)
        return if (linear <= .0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - .055 }
    fun code8(linear: Double): Int = (255 * encode(linear)).roundToInt().coerceIn(0, 255)
    fun argb(linear: Double): Int { val c = code8(linear); return (0xff shl 24) or (c shl 16) or (c shl 8) or c }
    fun pixels(linear: DoubleArray) = IntArray(linear.size) { argb(linear[it]) }
}
