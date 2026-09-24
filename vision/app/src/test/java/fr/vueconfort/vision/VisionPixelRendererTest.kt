package fr.vueconfort.vision

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VisionPixelRendererTest {
    @Test
    fun neutralRectangularImageIsAnExactIndependentCopy() {
        val original = intArrayOf(
            color(0, 0, 0), color(255, 255, 255), color(255, 0, 128),
            color(16, 120, 240), color(127, 128, 129), color(3, 8, 12),
        )
        val output = VisionPixelRenderer.apply(original, 3, 2)
        assertArrayEquals(original, output)
        assertNotSame(original, output)
    }

    @Test
    fun renderingAndMutatingOutputNeverChangeTheInput() {
        val original = intArrayOf(color(40, 80, 120), color(70, 110, 150), color(60, 100, 140))
        val before = original.copyOf()
        val output = VisionPixelRenderer.apply(original, 3, 1, 0.1, 1.2, 0.5)
        output[0] = color(255, 0, 0)
        assertArrayEquals(before, original)
    }

    @Test
    fun invalidDimensionsIncludingIntegerOverflowAreRejected() {
        val pixel = intArrayOf(color(128, 128, 128))
        for ((width, height) in listOf(0 to 1, 1 to 0, -1 to 1, 1 to -1, 2 to 1,
            65536 to 65536, Int.MAX_VALUE to Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                VisionPixelRenderer.apply(pixel, width, height)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            VisionPixelRenderer.apply(IntArray(0), 1, 1)
        }
    }

    @Test
    fun nonFiniteAndOutOfRangeSettingsAreRejected() {
        val pixel = intArrayOf(color(128, 128, 128))
        for (value in listOf(-0.251, 0.251, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { VisionPixelRenderer.apply(pixel, 1, 1, brightness = value) }
        }
        for (value in listOf(0.499, 2.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { VisionPixelRenderer.apply(pixel, 1, 1, contrast = value) }
        }
        for (value in listOf(-0.001, 2.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { VisionPixelRenderer.apply(pixel, 1, 1, contours = value) }
        }
    }

    @Test
    fun nonOpaquePixelsAreRejectedEvenInNeutralMode() {
        for (pixel in intArrayOf(0x00808080, 0x80808080.toInt(), 0xfe808080.toInt())) {
            assertThrows(IllegalArgumentException::class.java) { VisionPixelRenderer.apply(intArrayOf(pixel), 1, 1) }
            assertThrows(IllegalArgumentException::class.java) { VisionPixelRenderer.apply(intArrayOf(pixel), 1, 1, brightness = 0.1) }
            assertThrows(IllegalArgumentException::class.java) { VisionPixelRenderer.apply(intArrayOf(pixel), 1, 1, contours = 1.0) }
        }
    }

    @Test
    fun rightEdgeDoesNotLeakIntoLeftEdgeOfFollowingRow() {
        val base = color(128, 128, 128)
        val original = IntArray(12) { base }.also { it[3] = color(224, 224, 224) }
        val output = VisionPixelRenderer.apply(original, 4, 3, contours = 1.0)
        assertEquals(base, output[4])
        assertEquals(base, output[5])
        assertTrue((output[7] and 255) < 128)
    }

    @Test
    fun bottomEdgeDoesNotWrapIntoTopEdge() {
        val base = color(128, 128, 128)
        val original = IntArray(12) { base }.also { it[10] = color(224, 224, 224) }
        val output = VisionPixelRenderer.apply(original, 3, 4, contours = 1.0)
        assertEquals(base, output[0])
        assertEquals(base, output[1])
        assertEquals(base, output[2])
        assertTrue((output[7] and 255) < 128)
    }

    @Test
    fun contoursPreserveUniformColorFieldsAndEverySrgbCodeExactly() {
        // Each channel visits every 8-bit code, including both branches of the sRGB curve.
        for (code in 0..255) {
            val pixel = color(code, (code * 17) and 255, 255 - code)
            for ((width, height) in listOf(1 to 1, 1 to 4, 5 to 1, 3 to 2)) {
                val original = IntArray(width * height) { pixel }
                assertArrayEquals(original, VisionPixelRenderer.apply(original, width, height, contours = 2.0))
            }
        }
    }

    @Test
    fun brightnessPreservesLinearChannelDifferencesBeforeClipping() {
        val pixel = color(160, 100, 60)
        val output = VisionPixelRenderer.apply(intArrayOf(pixel), 1, 1, brightness = 0.05)[0]
        val outRed = output ushr 16 and 255
        val outGreen = output ushr 8 and 255
        val outBlue = output and 255
        assertTrue(outRed > outGreen && outGreen > outBlue)
        assertTrue(abs(outRed - referenceEncode(referenceDecode(160) + 0.05)) <= 1)
        assertTrue(abs(outGreen - referenceEncode(referenceDecode(100) + 0.05)) <= 1)
        assertTrue(abs(outBlue - referenceEncode(referenceDecode(60) + 0.05)) <= 1)
        val deltaRed = referenceDecode(outRed) - referenceDecode(160)
        val deltaGreen = referenceDecode(outGreen) - referenceDecode(100)
        val deltaBlue = referenceDecode(outBlue) - referenceDecode(60)
        assertEquals(deltaRed, deltaGreen, 0.006)
        assertEquals(deltaGreen, deltaBlue, 0.006)
    }

    @Test
    fun brightnessContrastAndContoursEachChangeTheImageAndCanCombine() {
        val original = IntArray(9) { color(128, 128, 128) }.also { it[4] = color(160, 160, 160) }
        val brightness = VisionPixelRenderer.apply(original, 3, 3, brightness = 0.1)
        val contrast = VisionPixelRenderer.apply(original, 3, 3, contrast = 1.3)
        val contours = VisionPixelRenderer.apply(original, 3, 3, contours = 0.5)
        val combined = VisionPixelRenderer.apply(original, 3, 3, brightness = 0.1, contrast = 1.3, contours = 0.5)
        for (output in listOf(brightness, contrast, contours, combined)) assertTrue(!original.contentEquals(output))
        for (output in listOf(brightness, contrast, contours)) assertTrue(!combined.contentEquals(output))
        val centerTone = (referenceDecode(160) - 0.5) * 1.3 + 0.5 + 0.1
        val backgroundTone = (referenceDecode(128) - 0.5) * 1.3 + 0.5 + 0.1
        val expectedCenter = referenceEncode(centerTone + 0.5 * (centerTone - backgroundTone) * 8.0 / 9.0)
        assertTrue(abs(expectedCenter - (combined[4] and 255)) <= 1)
        assertEquals(combined[4] ushr 16 and 255, combined[4] ushr 8 and 255)
        assertEquals(combined[4] ushr 8 and 255, combined[4] and 255)
    }

    @Test
    fun extremeAdjustmentsClipChannelsAndAlwaysKeepOpaqueOutput() {
        val black = color(0, 0, 0)
        val white = color(255, 255, 255)
        val result = VisionPixelRenderer.apply(intArrayOf(black, white), 2, 1, 0.25, 2.0, 2.0)
        assertArrayEquals(intArrayOf(black, white), result)
        val colored = VisionPixelRenderer.apply(intArrayOf(color(255, 0, 0)), 1, 1, brightness = 0.25)[0]
        assertEquals(255, colored ushr 24)
        assertEquals(255, colored ushr 16 and 255)
        assertTrue((colored ushr 8 and 255) > 0)
        assertTrue((colored and 255) > 0)
    }

    @Test
    fun contoursUseUnclippedToneUntilFinalChannelOutput() {
        val original = IntArray(9) { color(188, 188, 188) }.also { it[0] = color(255, 255, 255) }
        val output = VisionPixelRenderer.apply(original, 3, 3, brightness = 0.25, contrast = 2.0, contours = 1.0)
        val centerTone = (referenceDecode(188) - 0.5) * 2.0 + 0.5 + 0.25
        val cornerTone = 1.75
        val expectedCenter = referenceEncode(centerTone - (cornerTone - centerTone) / 9.0)
        assertTrue(abs(expectedCenter - (output[4] and 255)) <= 1)
    }

    private fun color(red: Int, green: Int, blue: Int) =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue

    private fun referenceDecode(code: Int): Double {
        val value = code / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }

    private fun referenceEncode(linear: Double): Int {
        val value = linear.coerceIn(0.0, 1.0)
        val encoded = if (value <= 0.0031308) value * 12.92 else 1.055 * value.pow(1.0 / 2.4) - 0.055
        return (encoded * 255.0).roundToInt().coerceIn(0, 255)
    }
}
