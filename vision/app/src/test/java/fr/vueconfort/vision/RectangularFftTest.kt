package fr.vueconfort.vision

import java.util.concurrent.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

class RectangularFftTest {
    @Test
    fun rectangularTransformMatchesIndependentDirectDft() {
        val width = 8
        val height = 4
        val real = DoubleArray(width * height) { sin(it * 0.7) + it * 0.03 }
        val imaginary = DoubleArray(real.size) { cos(it * 0.2) * 0.1 }
        val originalReal = real.copyOf()
        val originalImaginary = imaginary.copyOf()
        RectangularFft.transform(real, imaginary, width, height)
        for (ky in 0 until height) for (kx in 0 until width) {
            var expectedReal = 0.0
            var expectedImaginary = 0.0
            for (y in 0 until height) for (x in 0 until width) {
                val phase = -2 * PI * (kx.toDouble() * x / width + ky.toDouble() * y / height)
                val index = y * width + x
                expectedReal += originalReal[index] * cos(phase) - originalImaginary[index] * sin(phase)
                expectedImaginary += originalReal[index] * sin(phase) + originalImaginary[index] * cos(phase)
            }
            assertEquals(expectedReal, real[ky * width + kx], 1e-11)
            assertEquals(expectedImaginary, imaginary[ky * width + kx], 1e-11)
        }
        RectangularFft.transform(real, imaginary, width, height, inverse = true)
        assertArrayEquals(originalReal, real, 1e-12)
        assertArrayEquals(originalImaginary, imaginary, 1e-12)
    }

    @Test
    fun oneDimensionalEdgesAndWorkspaceTailArePreserved() {
        for ((width, height) in listOf(1 to 1, 1 to 8, 8 to 1, 16 to 8)) {
            val count = width * height
            val real = DoubleArray(count + 4) { it * 0.125 }
            val imaginary = DoubleArray(count + 4) { -it * 0.125 }
            val originalReal = real.copyOf()
            val originalImaginary = imaginary.copyOf()
            RectangularFft.transform(real, imaginary, width, height)
            for (i in count until real.size) {
                assertEquals(originalReal[i], real[i], 0.0)
                assertEquals(originalImaginary[i], imaginary[i], 0.0)
            }
            RectangularFft.transform(real, imaginary, width, height, inverse = true)
            assertArrayEquals(originalReal, real, 1e-12)
            assertArrayEquals(originalImaginary, imaginary, 1e-12)
        }
    }

    @Test
    fun invalidDimensionsBuffersAndAliasingAreRejected() {
        for ((width, height) in listOf(0 to 4, -1 to 4, 3 to 4, 4 to 3, 8 to 8)) {
            assertThrows(IllegalArgumentException::class.java) {
                RectangularFft.transform(DoubleArray(16), DoubleArray(16), width, height)
            }
        }
        val same = DoubleArray(16)
        assertThrows(IllegalArgumentException::class.java) { RectangularFft.transform(same, same, 4, 4) }
    }

    @Test
    fun cancellationIsObservedBeforeModifyingBuffers() {
        val real = DoubleArray(32) { it.toDouble() }
        val before = real.copyOf()
        assertThrows(CancellationException::class.java) {
            RectangularFft.transform(real, DoubleArray(32), 8, 4, isCancelled = { true })
        }
        assertArrayEquals(before, real, 0.0)
    }
}
