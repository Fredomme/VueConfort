package fr.vueconfort.app.precompensation.v24

import kotlin.math.*

data class OtfIntegralV24(val value: Double, val errorEstimate: Double, val panels: Int, val converged: Boolean)

/** Continuous scalar incoherent optics, order-two wavefront, circular uniform pupil.
 * No raster pupil, PSF truncation or OTF interpolation. Units: fx/fy cycles/degree.
 * Reference: Wyant/Goodman pupil autocorrelation; see docs for the derivation.
 * Production integration uses composite Simpson doubling, independently checked against
 * the prior audit's Python Gauss-Legendre integral. The error estimate is NOT that check. */
object OpticalKernelV24 {
    const val INTEGRAL_TOLERANCE = 1e-7
    const val MAX_PANELS = 512
    const val INDEPENDENT_OTF_TOLERANCE = 2e-5
    const val BOUNDARY_LUMINANCE_TOLERANCE = 0.0015
    const val HIGH_FFT_SIZE = 512
    const val LOW_FFT_SIZE = 256

    fun cutoffCpd(pupilMm: Double, wavelengthNm: Double = 555.0) = pupilMm * 1e-3 / (wavelengthNm * 1e-9) * PI / 180

    fun eyeOtf(fxCpd: Double, fyCpd: Double, residual: ResidualOpticalDefectV24,
        pupilMm: Double, wavelengthNm: Double = 555.0): OtfIntegralV24 {
        require(listOf(fxCpd, fyCpd, residual.sphereD, residual.cylinderD, residual.axisDegrees, pupilMm, wavelengthNm).all { it.isFinite() })
        require(pupilMm > 0 && wavelengthNm > 0)
        val lambda = wavelengthNm * 1e-9
        val radius = pupilMm * .0005
        val dx = lambda * fxCpd * 180 / PI
        val dy = lambda * fyCpd * 180 / PI
        val length = hypot(dx, dy)
        val shift = length / (2 * radius)
        if (length == 0.0) return OtfIntegralV24(1.0, 0.0, 0, true)
        if (shift >= 1) return OtfIntegralV24(0.0, 0.0, 0, true)
        if (residual.sphereD == 0.0 && residual.cylinderD == 0.0)
            return OtfIntegralV24(2 / PI * (acos(shift) - shift * sqrt(1 - shift * shift)), 0.0, 0, true)
        // Principal meridians u=(cos alpha,sin alpha), v=(-sin alpha,cos alpha).
        // Q = S uu^T + (S+C) vv^T [m^-1], W=-r^T Q r/2 [metres].
        val alpha = Math.toRadians(residual.axisDegrees)
        val ca = cos(alpha); val sa = sin(alpha)
        val qxx = residual.sphereD + residual.cylinderD * sa * sa
        val qyy = residual.sphereD + residual.cylinderD * ca * ca
        val qxy = -residual.cylinderD * ca * sa
        val kx = 2 * PI * radius / lambda * (qxx * dx + qxy * dy)
        val ky = 2 * PI * radius / lambda * (qxy * dx + qyy * dy)
        val parallel = (kx * dx + ky * dy) / length
        val transverse = (-kx * dy + ky * dx) / length
        // Smooth integration variable t: longitudinal coordinate u=1-shift-t^2.
        val bound = sqrt(1 - shift)
        fun integrand(t: Double): Double {
            val u = 1 - shift - t * t
            val halfHeight = t * sqrt((2 - t * t).coerceAtLeast(0.0))
            val vIntegral = halfHeight * sinc(transverse * halfHeight / PI)
            return 8 / PI * t * cos(parallel * u) * vIntegral
        }
        fun simpson(panels: Int): Double {
            val h = bound / panels
            var sum = integrand(0.0) + integrand(bound)
            for (j in 1 until panels) sum += (if (j % 2 == 0) 2 else 4) * integrand(j * h)
            return sum * h / 3
        }
        var n = 16; var previous = simpson(n); var error = Double.POSITIVE_INFINITY
        while (n < MAX_PANELS) {
            n *= 2
            val current = simpson(n)
            error = abs(current - previous) / 15
            if (error <= INTEGRAL_TOLERANCE) return OtfIntegralV24(current + (current - previous) / 15, error, n, true)
            previous = current
        }
        return OtfIntegralV24(previous, error, n, false)
    }

    fun sinc(x: Double): Double = if (abs(x) < 1e-12) 1.0 else sin(PI * x) / (PI * x)

    /** Screen model: unit-fill, centred square emitting pixels, incoherent retina sampled
     * at the same angular centres. Sum ALL replicas within the finite optical cutoff.
     * Hscreen(q)=sum_mn Heye((q+[m,n])*ppd)*sinc(qx+m)*sinc(qy+n).
     * This is not a circular Nyquist mask. Alias pairing enforces the Nyquist seam.
     * Actual OLED subpixel aperture and spectrum remain unmeasured approximations. */
    fun screenTransfer(qx: Double, qy: Double, contract: OpticalContractV24): OtfIntegralV24 {
        val r = requireNotNull(contract.residual)
        val ppd = contract.geometry.pixelsPerDegree
        val fc = cutoffCpd(contract.pupilDiameterMm, contract.wavelengthNm)
        val extent = ceil(fc / ppd + .5).toInt()
        var value = 0.0; var error = 0.0; var panels = 0; var converged = true
        for (my in -extent..extent) for (mx in -extent..extent) {
            val ax = qx + mx; val ay = qy + my
            if (hypot(ax, ay) * ppd >= fc) continue
            val weight = sinc(ax) * sinc(ay)
            if (abs(weight) < 1e-14) continue
            val h = eyeOtf(ax * ppd, ay * ppd, r, contract.pupilDiameterMm, contract.wavelengthNm)
            value += weight * h.value; error += abs(weight) * h.errorEstimate
            panels = max(panels, h.panels); converged = converged && h.converged
        }
        return OtfIntegralV24(value, error, panels, converged)
    }

    fun build(contract: OpticalContractV24): TransferGridV24 {
        require(contract.rejectionReasons().isEmpty())
        val n = HIGH_FFT_SIZE
        val h = DoubleArray(n * n)
        var maxError = 0.0; var maxPanels = 0; var converged = true
        // Compute one member of each Hermitian pair. The continuous screen integral is
        // separately checked on both sides of the seam by tests and independent Python.
        for (y in 0 until n) for (x in 0 until n) {
            val i = y * n + x; val partner = ((n - y) % n) * n + (n - x) % n
            if (i > partner) continue
            val qx = (if (x <= n / 2) x else x - n).toDouble() / n
            val qy = (if (y <= n / 2) y else y - n).toDouble() / n
            val z = screenTransfer(qx, qy, contract)
            h[i] = z.value; h[partner] = z.value
            maxError = max(maxError, z.errorEstimate); maxPanels = max(maxPanels, z.panels)
            converged = converged && z.converged
        }
        return TransferGridV24(n, h, maxError, maxPanels, converged)
    }
}

data class TransferGridV24(val size: Int, val real: DoubleArray, val maxIntegralError: Double,
    val maxPanels: Int, val converged: Boolean) {
    fun half(): TransferGridV24 { val n = size / 2
        return copy(size = n, real = DoubleArray(n * n) { real[(it / n * 2) * size + it % n * 2] }) }
}
