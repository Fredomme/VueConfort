package fr.vueconfort.vision

import kotlin.math.*

/** Explicit, experimental near residual in dioptres; never inferred from a diagnosis. */
data class OpticalHypothesis(val sphere: Double, val cylinder: Double, val axisDegrees: Double)

data class OpticalResult(
    val baseline: IntArray,
    val candidate: IntArray,
    val applied: Boolean,
    val explanation: String,
    val metrics: Map<String, Double> = emptyMap(),
    val report: Map<String, String> = emptyMap(),
)

/** real is read-only to callers. Error is a conservative bound on Simpson error estimates. */
data class OpticalTransferGrid(
    val width: Int,
    val height: Int,
    val real: DoubleArray,
    val maxIntegralError: Double,
    val maxPanels: Int,
    val converged: Boolean,
    val computedRadialSamples: Int,
)

/**
 * Static, monochrome optical experiment at one native display pixel per input pixel.
 * Circular uniform 3 mm pupil; 555 nm; S25 nominal pitch; declared working distance.
 * Exact V2.4 pupil-autocorrelation integrals, square pixel aperture and all aliases
 * inside the optical cutoff. The initial supported hypotheses are spherical only.
 *
 * Radial reuse is exact: qx and qy have common denominator 2048, so equal integer
 * squared radii share the same spherical eye OTF. The SCREEN transfer is not radial;
 * every aperture-weighted alias sum is evaluated. There is no interpolation.
 *
 * Output is assessed after cropping, clipping, sRGB quantization and re-embedding
 * into the actually displayed quantized 0.5 field. Both FFT axes double for the
 * finite-support check. These model checks are not evidence of a human benefit.
 */
object OpticalExperiment {
    const val WIDTH = 768
    const val HEIGHT = 256
    const val LOW_WIDTH = 1024
    const val LOW_HEIGHT = 512
    const val HIGH_WIDTH = 2048
    const val HIGH_HEIGHT = 1024
    const val REGULARIZATION = 0.03
    const val MAX_GAIN = 1.1
    const val MIN_OTF = 0.02
    const val MAX_CLIPPING = 0.01
    const val MIN_IMPROVEMENT = 0.01
    const val BOUNDARY_TOLERANCE = 0.0015
    const val INTEGRAL_TOLERANCE = 1e-7
    const val MAX_INTEGRAL_ERROR = 2e-5
    const val MAX_PANELS = 512
    const val PUPIL_MM = 3.0
    const val WAVELENGTH_NM = 555.0
    val PIXEL_PITCH_MM = 25.4 / (hypot(2340.0, 1080.0) / 6.2)
    val BACKGROUND_ARGB = 0xffbcbcbc.toInt()
    private val decode = DoubleArray(256) { decodeSrgb(it / 255.0) }
    private val exterior = decode[188]
    private data class Cached(val distance: Double, val sphere: Double, val grid: OpticalTransferGrid)
    @Volatile private var cached: Cached? = null

    fun process(
        argb: IntArray,
        distanceMm: Double,
        hypothesis: OpticalHypothesis,
        onProgress: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): OpticalResult {
        val began = System.nanoTime()
        require(argb.size == WIDTH * HEIGHT) { "Input must be exactly 768 x 256 physical pixels" }
        RectangularFft.checkCancelled(isCancelled)
        val input = DoubleArray(argb.size)
        val baseline = IntArray(argb.size)
        for (i in argb.indices) {
            if (i and 16383 == 0) RectangularFft.checkCancelled(isCancelled)
            val pixel = argb[i]
            require(pixel ushr 24 == 255) { "Input pixels must be opaque ARGB" }
            val gray = 0.2126 * decode[(pixel ushr 16) and 255] +
                0.7152 * decode[(pixel ushr 8) and 255] + 0.0722 * decode[pixel and 255]
            baseline[i] = grayArgb(gray)
            // The inverse starts from precisely the same quantized gray shown for comparison.
            input[i] = decode[baseline[i] and 255]
        }
        val report = linkedMapOf(
            "method" to "SPHERICAL_PUPIL_AUTOCORRELATION_SIMPSON_EXACT_RADIAL_CACHE",
            "interpretation" to "EXPERIMENTAL_HYPOTHESIS_NOT_DIAGNOSIS_OR_HUMAN_VALIDATION",
            "distanceProvenance" to "DECLARED_NOT_MEASURED",
            "pitchProvenance" to "SAMSUNG_NOMINAL_ESTIMATE",
            "pupilProvenance" to "ASSUMED_3_MM",
            "screenModel" to "UNIT_FILL_SQUARE_PIXELS_ALL_OPTICAL_ALIASES",
            "displayRequirement" to "768x256_NATIVE_PIXELS_NO_ZOOM_GRAY188_SURROUND",
            "lowFft" to "1024x512", "highFft" to "2048x1024",
        )
        val metrics = linkedMapOf(
            "distanceMm" to distanceMm, "sphereD" to hypothesis.sphere,
            "cylinderD" to hypothesis.cylinder, "axisDegrees" to hypothesis.axisDegrees,
            "pupilMm" to PUPIL_MM, "wavelengthNm" to WAVELENGTH_NM,
            "pixelPitchMm" to PIXEL_PITCH_MM, "exteriorLinear" to exterior,
        )
        metrics.entries.removeAll { !it.value.isFinite() }
        fun fallback(reason: String, explanation: String): OpticalResult {
            report["decision"] = "IDENTITY"
            report["reason"] = reason
            metrics["totalMs"] = (System.nanoTime() - began) / 1e6
            return OpticalResult(baseline, baseline.copyOf(), false, explanation, metrics, report)
        }
        val rejection = rejectionReason(distanceMm, hypothesis)
        if (rejection != null) return fallback(rejection, "Hypothèse hors du domaine expérimental : image originale en gris conservée.")
        if (hypothesis.sphere == 0.0) return fallback("ZERO_RESIDUAL_IDENTITY", "Hypothèse nulle : image originale en gris conservée.")

        val kernelBegan = System.nanoTime()
        val previous = cached
        val high = if (previous != null && previous.distance == distanceMm && previous.sphere == abs(hypothesis.sphere)) {
            onProgress("Modèle optique déjà préparé")
            previous.grid
        } else {
            buildTransferForValidation(distanceMm, hypothesis, onProgress, isCancelled).also {
                RectangularFft.checkCancelled(isCancelled)
                cached = Cached(distanceMm, abs(hypothesis.sphere), it)
            }
        }
        metrics["kernelMs"] = (System.nanoTime() - kernelBegan) / 1e6
        metrics["maxIntegralError"] = high.maxIntegralError
        metrics["maxQuadraturePanels"] = high.maxPanels.toDouble()
        metrics["computedRadialSamples"] = high.computedRadialSamples.toDouble()
        if (!high.converged || high.maxIntegralError > MAX_INTEGRAL_ERROR)
            return fallback("INTEGRAL_NOT_CONVERGED", "Calcul optique non convergé : image originale en gris conservée.")

        RectangularFft.checkCancelled(isCancelled)
        // One complex workspace serves every low/high forward and inverse transform.
        val real = DoubleArray(HIGH_WIDTH * HIGH_HEIGHT)
        val imaginary = DoubleArray(real.size)
        onProgress("Calcul de la précompensation 1/5")
        val shown = filter(input, high, LOW_WIDTH, LOW_HEIGHT, true, real, imaginary, isCancelled)
        var clipped = 0
        val proposed = IntArray(input.size)
        for (i in shown.indices) {
            if (shown[i] !in 0.0..1.0) clipped++
            val code = encode8(shown[i].coerceIn(0.0, 1.0))
            proposed[i] = (0xff shl 24) or (code shl 16) or (code shl 8) or code
            shown[i] = decode[code]
            input[i] = decode[baseline[i] and 255]
        }
        val clipping = clipped.toDouble() / shown.size
        metrics["clippingFraction"] = clipping
        onProgress("Vérification de l’original 2/5")
        val normalLow = filter(input, high, LOW_WIDTH, LOW_HEIGHT, false, real, imaginary, isCancelled)
        onProgress("Vérification de l’original 3/5")
        val normalHigh = filter(input, high, HIGH_WIDTH, HIGH_HEIGHT, false, real, imaginary, isCancelled)
        val before = mse(input, normalHigh)
        var boundary = maxDifference(normalLow, normalHigh)
        onProgress("Vérification du candidat 4/5")
        val candidateLow = filter(shown, high, LOW_WIDTH, LOW_HEIGHT, false, real, imaginary, isCancelled)
        onProgress("Vérification du candidat 5/5")
        val candidateHigh = filter(shown, high, HIGH_WIDTH, HIGH_HEIGHT, false, real, imaginary, isCancelled)
        val after = mse(input, candidateHigh)
        boundary = max(boundary, maxDifference(candidateLow, candidateHigh))
        val improvement = (before - after) / before.coerceAtLeast(1e-15)
        metrics["mseNormal"] = before
        metrics["mseCandidate"] = after
        metrics["mseOutput"] = before
        metrics["relativeImprovement"] = improvement
        metrics["boundaryMaxDifference"] = boundary
        RectangularFft.checkCancelled(isCancelled)
        if (!before.isFinite() || !after.isFinite() || !boundary.isFinite())
            return fallback("NON_FINITE_RESULT", "Résultat numérique invalide : image originale en gris conservée.")
        if (boundary > BOUNDARY_TOLERANCE)
            return fallback("FINITE_SUPPORT_NOT_CONVERGED", "Le calcul dépend trop du pourtour : image originale en gris conservée.")
        if (clipping > MAX_CLIPPING)
            return fallback("EXCESS_CLIPPING", "Trop de pixels seraient écrêtés : image originale en gris conservée.")
        if (improvement < MIN_IMPROVEMENT)
            return fallback("NO_STABLE_MODELED_IMPROVEMENT", "Gain calculé insuffisant : image originale en gris conservée.")
        metrics["mseOutput"] = after
        metrics["totalMs"] = (System.nanoTime() - began) / 1e6
        report["decision"] = "APPLY"
        report["reason"] = "BOUNDED_MODELED_CANDIDATE_NOT_HUMAN_VALIDATED"
        return OpticalResult(baseline, proposed, true,
            "Candidat optique calculé pour cette hypothèse ; bénéfice visuel à vérifier.", metrics, report)
    }

    /** Validation hook; builds a fresh, exact high-resolution transfer grid. */
    fun buildTransferForValidation(
        distanceMm: Double,
        hypothesis: OpticalHypothesis,
        onProgress: (String) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): OpticalTransferGrid {
        require(rejectionReason(distanceMm, hypothesis) == null) { "Unsupported optical hypothesis or distance" }
        RectangularFft.checkCancelled(isCancelled)
        val degreesPerPixel = Math.toDegrees(2 * atan(PIXEL_PITCH_MM / (2 * distanceMm)))
        val ppd = 1.0 / degreesPerPixel
        val cutoff = PUPIL_MM * 1e-3 / (WAVELENGTH_NM * 1e-9) * PI / 180
        val cutoffRadius = cutoff / ppd * HIGH_WIDTH
        val cutoffSquared = cutoffRadius * cutoffRadius
        val cache = DoubleArray(ceil(cutoffSquared).toInt() + 1) { Double.NaN }
        val transfer = DoubleArray(HIGH_WIDTH * HIGH_HEIGHT)
        val extent = ceil(cutoff / ppd + 0.5).toInt()
        val aliasCount = extent * 2 + 1
        val halfWidth = HIGH_WIDTH / 2
        val halfHeight = HIGH_HEIGHT / 2
        val xWeights = Array(aliasCount) { alias -> DoubleArray(halfWidth + 1) { x -> sinc(x.toDouble() / HIGH_WIDTH + alias - extent) } }
        val yWeights = Array(aliasCount) { alias -> DoubleArray(halfHeight + 1) { y -> sinc(y.toDouble() / HIGH_HEIGHT + alias - extent) } }
        var maximumEyeError = 0.0
        var maximumWeightSum = 0.0
        var maximumPanels = 0
        var converged = true
        var samples = 0
        for (y in 0..halfHeight) {
            RectangularFft.checkCancelled(isCancelled)
            if (y % 16 == 0) onProgress("Préparation optique ${y * 100 / halfHeight} %")
            for (x in 0..halfWidth) {
                var value = 0.0
                var weightSum = 0.0
                for (ay in 0 until aliasCount) {
                    val weightY = yWeights[ay][y]
                    if (abs(weightY) < 1e-14) continue
                    val iy = 2 * y + (ay - extent) * HIGH_WIDTH
                    for (ax in 0 until aliasCount) {
                        val weight = xWeights[ax][x] * weightY
                        if (abs(weight) < 1e-14) continue
                        val ix = x + (ax - extent) * HIGH_WIDTH
                        val squared = ix * ix + iy * iy
                        if (squared >= cutoffSquared) continue
                        var eye = cache[squared]
                        if (eye.isNaN()) {
                            val integral = radialEyeOtf(sqrt(squared.toDouble()) / HIGH_WIDTH * ppd, hypothesis.sphere)
                            eye = integral.value
                            cache[squared] = eye
                            maximumEyeError = max(maximumEyeError, integral.error)
                            maximumPanels = max(maximumPanels, integral.panels)
                            converged = converged && integral.converged
                            samples++
                        }
                        value += weight * eye
                        weightSum += abs(weight)
                    }
                }
                maximumWeightSum = max(maximumWeightSum, weightSum)
                val mirrorX = (HIGH_WIDTH - x) % HIGH_WIDTH
                val mirrorY = (HIGH_HEIGHT - y) % HIGH_HEIGHT
                transfer[y * HIGH_WIDTH + x] = value
                transfer[y * HIGH_WIDTH + mirrorX] = value
                transfer[mirrorY * HIGH_WIDTH + x] = value
                transfer[mirrorY * HIGH_WIDTH + mirrorX] = value
            }
        }
        RectangularFft.checkCancelled(isCancelled)
        onProgress("Préparation optique 100 %")
        return OpticalTransferGrid(HIGH_WIDTH, HIGH_HEIGHT, transfer,
            maximumEyeError * maximumWeightSum, maximumPanels, converged, samples)
    }

    private fun rejectionReason(distanceMm: Double, hypothesis: OpticalHypothesis): String? = when {
        !distanceMm.isFinite() || distanceMm !in 300.0..600.0 -> "DISTANCE_OUTSIDE_300_600_MM"
        !hypothesis.sphere.isFinite() || abs(hypothesis.sphere) > 0.5 -> "SPHERE_OUTSIDE_PLUS_MINUS_0_5_D"
        !hypothesis.cylinder.isFinite() || hypothesis.cylinder != 0.0 -> "SPHERICAL_HYPOTHESES_ONLY"
        !hypothesis.axisDegrees.isFinite() || hypothesis.axisDegrees !in 0.0..<180.0 -> "INVALID_AXIS"
        else -> null
    }

    private data class Integral(val value: Double, val error: Double, val panels: Int, val converged: Boolean)

    /** Exact specialization of V24 eyeOtf to Q=sphere*I: transverse phase is zero. */
    private fun radialEyeOtf(frequencyCpd: Double, sphere: Double): Integral {
        val lambda = WAVELENGTH_NM * 1e-9
        val radius = PUPIL_MM * 0.0005
        val length = lambda * frequencyCpd * 180 / PI
        val shift = length / (2 * radius)
        if (length == 0.0) return Integral(1.0, 0.0, 0, true)
        if (shift >= 1.0) return Integral(0.0, 0.0, 0, true)
        if (sphere == 0.0) return Integral(2 / PI * (acos(shift) - shift * sqrt(1 - shift * shift)), 0.0, 0, true)
        val parallel = 2 * PI * radius / lambda * sphere * length
        val bound = sqrt(1 - shift)
        fun integrand(t: Double): Double {
            val squared = t * t
            val u = 1 - shift - squared
            return 8 / PI * squared * sqrt((2 - squared).coerceAtLeast(0.0)) * cos(parallel * u)
        }
        fun simpson(panels: Int): Double {
            val h = bound / panels
            var sum = integrand(0.0) + integrand(bound)
            for (j in 1 until panels) sum += (if (j and 1 == 0) 2 else 4) * integrand(j * h)
            return sum * h / 3
        }
        var panels = 16
        var previous = simpson(panels)
        var error = Double.POSITIVE_INFINITY
        while (panels < MAX_PANELS) {
            panels *= 2
            val current = simpson(panels)
            error = abs(current - previous) / 15
            if (error <= INTEGRAL_TOLERANCE) return Integral(current + (current - previous) / 15, error, panels, true)
            previous = current
        }
        return Integral(previous, error, panels, false)
    }

    private fun filter(input: DoubleArray, high: OpticalTransferGrid, width: Int, height: Int, inverse: Boolean,
        real: DoubleArray, imaginary: DoubleArray, isCancelled: () -> Boolean): DoubleArray {
        val count = width * height
        real.fill(exterior, 0, count)
        imaginary.fill(0.0, 0, count)
        val offsetX = (width - WIDTH) / 2
        val offsetY = (height - HEIGHT) / 2
        for (y in 0 until HEIGHT) input.copyInto(real, (y + offsetY) * width + offsetX, y * WIDTH, (y + 1) * WIDTH)
        RectangularFft.transform(real, imaginary, width, height, false, isCancelled)
        val sampleStep = HIGH_WIDTH / width
        for (y in 0 until height) {
            if (y and 31 == 0) RectangularFft.checkCancelled(isCancelled)
            val row = y * width
            val kernelRow = y * sampleStep * HIGH_WIDTH
            for (x in 0 until width) {
                val i = row + x
                val h = high.real[kernelRow + x * sampleStep]
                val gain = if (!inverse) h else if (i == 0 || abs(h) < MIN_OTF) 1.0
                    else (h / (h * h + REGULARIZATION)).coerceIn(-MAX_GAIN, MAX_GAIN)
                real[i] *= gain
                imaginary[i] *= gain
            }
        }
        RectangularFft.transform(real, imaginary, width, height, true, isCancelled)
        return DoubleArray(WIDTH * HEIGHT) { real[(it / WIDTH + offsetY) * width + it % WIDTH + offsetX] }
    }

    private fun decodeSrgb(code: Double) = if (code <= 0.04045) code / 12.92 else ((code + 0.055) / 1.055).pow(2.4)
    private fun encode8(linear: Double): Int {
        val encoded = if (linear <= 0.0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - 0.055
        return (255 * encoded).roundToInt().coerceIn(0, 255)
    }
    private fun grayArgb(linear: Double): Int {
        val code = encode8(linear)
        return (0xff shl 24) or (code shl 16) or (code shl 8) or code
    }
    private fun sinc(value: Double) = if (abs(value) < 1e-12) 1.0 else sin(PI * value) / (PI * value)
    private fun mse(a: DoubleArray, b: DoubleArray): Double {
        var sum = 0.0
        for (i in a.indices) { val difference = a[i] - b[i]; sum += difference * difference }
        return sum / a.size
    }
    private fun maxDifference(a: DoubleArray, b: DoubleArray): Double {
        var maximum = 0.0
        for (i in a.indices) maximum = max(maximum, abs(a[i] - b[i]))
        return maximum
    }
}
