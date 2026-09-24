package fr.vueconfort.vision

import java.util.concurrent.CancellationException
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class OpticalExperimentTest {
    private val size = OpticalExperiment.WIDTH * OpticalExperiment.HEIGHT
    private val sphere = OpticalHypothesis(0.25, 0.0, 0.0)

    @Test
    fun zeroHypothesisProducesCorrectQuantizedLinearGrayWithoutMutation() {
        val pixels = IntArray(size) { 0xff00ff00.toInt() }
        pixels[0] = 0xffff0000.toInt()
        pixels[1] = 0xff0000ff.toInt()
        val before = pixels.copyOf()
        val result = OpticalExperiment.process(pixels, 400.0, OpticalHypothesis(0.0, 0.0, 0.0))
        assertFalse(result.applied)
        // Linear sRGB primary luminances 0.2126 / 0.0722 / 0.7152 quantize to these codes.
        assertEquals(127, result.baseline[0] and 255)
        assertEquals(76, result.baseline[1] and 255)
        assertEquals(220, result.baseline[2] and 255)
        assertArrayEquals(result.baseline, result.candidate)
        assertNotSame(result.baseline, result.candidate)
        assertArrayEquals(before, pixels)
    }

    @Test
    fun invalidHypothesesAndDistancesKeepBaselineAndFiniteMetrics() {
        val pixels = IntArray(size) { OpticalExperiment.BACKGROUND_ARGB }
        for ((distance, hypothesis) in listOf(
            299.0 to sphere, 601.0 to sphere, Double.NaN to sphere,
            400.0 to OpticalHypothesis(0.501, 0.0, 0.0),
            400.0 to OpticalHypothesis(Double.POSITIVE_INFINITY, 0.0, 0.0),
            400.0 to OpticalHypothesis(0.25, -0.1, 90.0),
            400.0 to OpticalHypothesis(0.25, 0.0, 180.0),
        )) {
            val result = OpticalExperiment.process(pixels, distance, hypothesis)
            assertFalse(result.applied)
            assertArrayEquals(pixels, result.baseline)
            assertArrayEquals(result.baseline, result.candidate)
            assertTrue(result.metrics.values.all { it.isFinite() })
        }
    }

    @Test
    fun invalidInputAndCancellationAreRejectedBeforeKernelBuild() {
        assertThrows(IllegalArgumentException::class.java) {
            OpticalExperiment.process(IntArray(1), 400.0, sphere)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OpticalExperiment.process(IntArray(size), 400.0, sphere)
        }
        assertThrows(CancellationException::class.java) {
            OpticalExperiment.process(IntArray(size) { OpticalExperiment.BACKGROUND_ARGB }, 400.0, sphere, isCancelled = { true })
        }
        assertThrows(CancellationException::class.java) {
            OpticalExperiment.buildTransferForValidation(400.0, sphere, isCancelled = { true })
        }
    }

    @Test
    fun moderateContrastProducesARealBoundedCandidate() {
        val original = bars(124, 188)
        val before = original.copyOf()
        val progress = mutableListOf<String>()
        val result = OpticalExperiment.process(original, 400.0, sphere, onProgress = { progress.add(it) })
        assertTrue("${result.report} ${result.metrics}", result.applied)
        assertFalse(result.baseline.contentEquals(result.candidate))
        assertTrue(result.metrics.getValue("clippingFraction") <= 0.01)
        assertTrue(result.metrics.getValue("relativeImprovement") >= 0.01)
        assertTrue(result.metrics.getValue("boundaryMaxDifference") <= 0.0015)
        assertTrue(result.metrics.getValue("maxIntegralError") <= 2e-5)
        assertTrue(result.metrics.getValue("mseOutput") < result.metrics.getValue("mseNormal"))
        assertTrue(progress.any { it.contains("5/5") })
        assertTrue(result.candidate.all { it ushr 24 == 255 && (it and 255) == (it ushr 8 and 255) && (it and 255) == (it ushr 16 and 255) })
        assertArrayEquals(before, original)
    }

    @Test
    fun highContrastIsNotAppliedWhenClippingExceedsTheBound() {
        val result = OpticalExperiment.process(bars(0, 255), 400.0, sphere)
        assertTrue("${result.metrics}", result.metrics.getValue("clippingFraction") > 0.01)
        assertFalse(result.applied)
        assertArrayEquals(result.baseline, result.candidate)
        assertEquals(result.metrics.getValue("mseNormal"), result.metrics.getValue("mseOutput"), 0.0)
    }

    @Test
    fun kernelPreparationCanBeCancelledWhileProgressing() {
        var cancel = false
        assertThrows(CancellationException::class.java) {
            OpticalExperiment.buildTransferForValidation(300.0, sphere,
                onProgress = { cancel = true }, isCancelled = { cancel })
        }
    }

    private fun bars(dark: Int, light: Int): IntArray = IntArray(size) { index ->
        val x = index % OpticalExperiment.WIDTH
        val y = index / OpticalExperiment.WIDTH
        val code = if (x in 32 until OpticalExperiment.WIDTH - 32 && y in 32 until OpticalExperiment.HEIGHT - 32)
            if (x % 16 < 8) dark else light
        else 188
        (0xff shl 24) or (code shl 16) or (code shl 8) or code
    }
}
