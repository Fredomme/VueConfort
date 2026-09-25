package fr.vueconfort.app.precompensation.v24

import fr.vueconfort.app.precompensation.FourierReferenceV2
import kotlin.math.*

enum class ScientificConditionV24 { N, G, P, S }
data class RenderResultV24(val condition: ScientificConditionV24, val decision: OpticalDecisionV24,
    val output: DoubleArray?, val reasons: List<String>, val usedResidual: ResidualOpticalDefectV24?,
    val mseNormal: Double = Double.NaN, val mseOutput: Double = Double.NaN,
    val boundaryMaxDifference: Double = Double.NaN, val maxIntegralError: Double = Double.NaN,
    val maxQuadraturePanels: Int = 0, val clippingFraction: Double = 0.0)

/** Static scientific renderer. 128x128 physical pixels, known surrounding linear field 0.5.
 * Optical inversion runs in a 256 field; retinal/support check in both 256 and 512 fields.
 * The actual cropped, quantized output (not an unshown padded halo) is assessed.
 * No own-forward result is a substitute for the external independent validation suite. */
class ScientificRendererV24(val contract: OpticalContractV24) {
    companion object {
        const val BACKGROUND = .5
        const val REGULARIZATION = .03
        const val MAX_GAIN = 1.1
        const val MIN_RELATIVE_IMPROVEMENT = .01
    }
    private val high by lazy { OpticalKernelV24.build(contract) }
    private val low by lazy { high.half() }

    fun transferForValidation(): TransferGridV24 = high

    fun render(input: DoubleArray, condition: ScientificConditionV24): RenderResultV24 {
        val errors = contract.rejectionReasons().toMutableList()
        if (input.size != 128 * 128 || input.any { !it.isFinite() || it !in 0.0..1.0 }) errors += "INVALID_LINEAR_STIMULUS"
        if (errors.isNotEmpty()) return RenderResultV24(condition, OpticalDecisionV24.REJECTED, null, errors, contract.residual)
        // N and explicit IDENTITY are exact array copies. NEVER run a supposed neutral inverse.
        if (condition == ScientificConditionV24.N || contract.requestedDecision == OpticalDecisionV24.IDENTITY)
            return RenderResultV24(condition, OpticalDecisionV24.IDENTITY, input.copyOf(), listOf("EXACT_IDENTITY"), contract.residual)
        if (condition == ScientificConditionV24.G) {
            val out = DoubleArray(input.size) { (.5 + 1.1 * (input[it] - .5)).coerceIn(0.0, 1.0) }
            return RenderResultV24(condition, OpticalDecisionV24.APPLY, out, listOf("GENERIC_10_PERCENT_CONTRAST_NOT_PERSONALIZED"), contract.residual)
        }
        // Prespecified wrong-profile control: never silently use the true personalized model.
        if (condition == ScientificConditionV24.S) {
            val r = requireNotNull(contract.residual)
            val shifted = r.copy(sphereD = 0.0, cylinderD = if (r.cylinderD == 0.0) -.5 else -.25,
                axisDegrees = (r.axisDegrees + 90.0) % 180.0,
                calibrationId = r.calibrationId + "|PREDECLARED_SHIFTED_CONTROL")
            val result = ScientificRendererV24(contract.copy(residual = shifted)).personalized(input, enforceImprovement = false)
            return result.copy(condition = condition)
        }
        val r = requireNotNull(contract.residual)
        if (r.sphereD == 0.0 && r.cylinderD == 0.0)
            return RenderResultV24(condition, OpticalDecisionV24.IDENTITY, input.copyOf(), listOf("IDEAL_EYE_IDENTITY_POLICY"), r)
        return personalized(input, enforceImprovement = true)
    }

    private fun personalized(input: DoubleArray, enforceImprovement: Boolean): RenderResultV24 {
        if (!high.converged || high.maxIntegralError > OpticalKernelV24.INDEPENDENT_OTF_TOLERANCE)
            return RenderResultV24(ScientificConditionV24.P, OpticalDecisionV24.REJECTED, null, listOf("INTEGRAL_NOT_CONVERGED"), contract.residual)
        val quantizedTarget = quantized(input)
        val normalLow = forward(quantizedTarget, low)
        val normalHigh = forward(quantizedTarget, high)
        val raw = inverse(input, low)
        val clipping = raw.count { it !in 0.0..1.0 }.toDouble() / raw.size
        val shown = quantized(DoubleArray(raw.size) { raw[it].coerceIn(0.0, 1.0) })
        val retinalLow = forward(shown, low)
        val retinalHigh = forward(shown, high)
        val boundary = max(maxDifference(normalLow, normalHigh), maxDifference(retinalLow, retinalHigh))
        val before = mse(quantizedTarget, normalHigh)
        val after = mse(quantizedTarget, retinalHigh)
        if (boundary > OpticalKernelV24.BOUNDARY_LUMINANCE_TOLERANCE)
            return RenderResultV24(ScientificConditionV24.P, OpticalDecisionV24.REJECTED, null,
                listOf("FINITE_SUPPORT_NOT_CONVERGED"), contract.residual, before, after, boundary, high.maxIntegralError, high.maxPanels, clipping)
        val apply = clipping <= .01 && (!enforceImprovement || (before - after) / before.coerceAtLeast(1e-15) >= MIN_RELATIVE_IMPROVEMENT)
        return RenderResultV24(ScientificConditionV24.P, if (apply) OpticalDecisionV24.APPLY else OpticalDecisionV24.IDENTITY,
            if (apply) shown else input.copyOf(), listOf(if (apply) "BOUNDED_CANDIDATE" else "NO_STABLE_IMPROVEMENT_IDENTITY"),
            contract.residual, before, if (apply) after else before, boundary, high.maxIntegralError, high.maxPanels, clipping)
    }

    /** Exact known exterior field is the quantized same sRGB gray used by the Android view. */
    private fun embed(input: DoubleArray, n: Int): DoubleArray {
        val exterior = LuminanceV24.decode(LuminanceV24.code8(BACKGROUND) / 255.0)
        val result = DoubleArray(n * n) { exterior }
        val offset = (n - 128) / 2
        for (y in 0 until 128) input.copyInto(result, (y + offset) * n + offset, y * 128, (y + 1) * 128)
        return result
    }
    private fun crop(input: DoubleArray, n: Int): DoubleArray {
        val offset = (n - 128) / 2
        return DoubleArray(128 * 128) { input[(it / 128 + offset) * n + it % 128 + offset] }
    }
    fun forward(input: DoubleArray, grid: TransferGridV24): DoubleArray {
        val n = grid.size; val field = embed(input, n)
        val f = FourierReferenceV2.transform2d(field, DoubleArray(field.size), n, false)
        val re = DoubleArray(field.size) { f.real[it] * grid.real[it] }
        val im = DoubleArray(field.size) { f.imag[it] * grid.real[it] }
        return crop(FourierReferenceV2.transform2d(re, im, n, true).real, n)
    }
    private fun inverse(input: DoubleArray, grid: TransferGridV24): DoubleArray {
        val n = grid.size; val field = embed(input, n)
        val f = FourierReferenceV2.transform2d(field, DoubleArray(field.size), n, false)
        val re = DoubleArray(field.size); val im = DoubleArray(field.size)
        for (i in field.indices) {
            val h = grid.real[i]
            val gain = if (i == 0 || abs(h) < .02) 1.0 else (h / (h * h + REGULARIZATION)).coerceIn(-MAX_GAIN, MAX_GAIN)
            re[i] = f.real[i] * gain; im[i] = f.imag[i] * gain
        }
        return crop(FourierReferenceV2.transform2d(re, im, n, true).real, n)
    }
    private fun quantized(input: DoubleArray) = DoubleArray(input.size) { LuminanceV24.decode(LuminanceV24.code8(input[it]) / 255.0) }
    private fun mse(a: DoubleArray, b: DoubleArray) = a.indices.sumOf { (a[it] - b[it]).pow(2) } / a.size
    private fun maxDifference(a: DoubleArray, b: DoubleArray) = a.indices.maxOf { abs(a[it] - b[it]) }
}

/** Deterministic linear-light raster stimuli: shared by Android and the independent suite.
 * Bitmap glyphs are deliberately fixed, avoiding font/dp scaling. At least 16 pixels of
 * background border. No stimulus is a validated acuity chart or a patient measurement. */
object ScientificStimuliV24 {
    val names = listOf("LANDOLT_C", "LETTERS", "TEXT", "BARS_VERTICAL", "BARS_HORIZONTAL", "SINE_4", "SINE_8", "SINE_16")
    fun create(name: String): DoubleArray {
        require(name in names)
        val v = DoubleArray(128 * 128) { .5 }
        fun set(x: Int, y: Int, value: Double) { if (x in 0..127 && y in 0..127) v[y * 128 + x] = value }
        when (name) {
            "LANDOLT_C" -> for (y in 24..103) for (x in 24..103) {
                val r = hypot(x - 63.5, y - 63.5)
                // Landolt proportions: outer diameter 5g, inner diameter 3g, gap g=12 px.
                if (r in 18.0..30.0 && !(x > 63.5 && abs(y - 63.5) < 6)) set(x, y, .2)
            }
            "LETTERS", "TEXT" -> {
                val glyphs = mapOf('E' to listOf("11111","10000","11110","10000","11111"),
                    'H' to listOf("10001","10001","11111","10001","10001"),
                    'V' to listOf("10001","10001","01010","01010","00100"),
                    'U' to listOf("10001","10001","10001","10001","01110"),
                    'C' to listOf("01111","10000","10000","10000","01111"),
                    'O' to listOf("01110","10001","10001","10001","01110"),
                    'N' to listOf("10001","11001","10101","10011","10001"),
                    'F' to listOf("11111","10000","11110","10000","10000"),
                    'R' to listOf("11110","10001","11110","10100","10010"),
                    'T' to listOf("11111","00100","00100","00100","00100"))
                val lines = if (name == "LETTERS") listOf("EH") else listOf("VUE", "CONFORT")
                val scale = if (name == "LETTERS") 6 else 2
                lines.forEachIndexed { line, text ->
                    val start = (128 - text.length * 6 * scale) / 2
                    text.forEachIndexed { index, char -> glyphs.getValue(char).forEachIndexed { y, row ->
                        row.forEachIndexed { x, bit -> if (bit == '1') for (dy in 0 until scale) for (dx in 0 until scale)
                            set(start + index * 6 * scale + x * scale + dx, 40 + line * 24 + y * scale + dy, .2) }
                    } }
                }
            }
            else -> for (y in 16..111) for (x in 16..111) {
                val edge = min(min(x - 16, 111 - x), min(y - 16, 111 - y))
                val taper = min(1.0, edge / 8.0)
                val oscillation = when (name) {
                    "BARS_VERTICAL" -> if ((x / 4) % 2 == 0) .2 else -.2
                    "BARS_HORIZONTAL" -> if ((y / 4) % 2 == 0) .2 else -.2
                    else -> .1 * cos(2 * PI * name.substringAfter("SINE_").toInt() * x / 128)
                }
                set(x, y, .5 + taper * oscillation)
            }
        }
        return v
    }
}
