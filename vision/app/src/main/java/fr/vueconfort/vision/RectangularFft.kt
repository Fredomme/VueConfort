package fr.vueconfort.vision

import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** In-place radix-two complex FFT on the first width*height entries of two buffers. */
object RectangularFft {
    private data class Plan(val reverse: IntArray, val cos: DoubleArray, val sin: DoubleArray)
    private val plans = ConcurrentHashMap<Int, Plan>()

    fun transform(
        real: DoubleArray,
        imaginary: DoubleArray,
        width: Int,
        height: Int,
        inverse: Boolean = false,
        isCancelled: () -> Boolean = { false },
    ) {
        require(width > 0 && width and (width - 1) == 0)
        require(height > 0 && height and (height - 1) == 0)
        val count = width.toLong() * height
        require(count <= real.size && count <= imaginary.size && real !== imaginary)
        val rows = plan(width)
        val columns = plan(height)
        for (y in 0 until height) {
            if (y and 15 == 0) checkCancelled(isCancelled)
            line(real, imaginary, y * width, width, rows, inverse)
        }
        // Short column scratch improves cache locality without copying the 2D image.
        val columnReal = DoubleArray(height)
        val columnImaginary = DoubleArray(height)
        for (x in 0 until width) {
            if (x and 15 == 0) checkCancelled(isCancelled)
            for (y in 0 until height) {
                val index = y * width + x
                columnReal[y] = real[index]
                columnImaginary[y] = imaginary[index]
            }
            line(columnReal, columnImaginary, 0, height, columns, inverse)
            for (y in 0 until height) {
                val index = y * width + x
                real[index] = columnReal[y]
                imaginary[index] = columnImaginary[y]
            }
        }
        checkCancelled(isCancelled)
    }

    private fun plan(size: Int): Plan = plans.getOrPut(size) {
        val bits = Integer.numberOfTrailingZeros(size)
        Plan(
            IntArray(size) { if (bits == 0) 0 else Integer.reverse(it) ushr (32 - bits) },
            DoubleArray(size / 2) { cos(2.0 * PI * it / size) },
            DoubleArray(size / 2) { sin(2.0 * PI * it / size) },
        )
    }

    private fun line(real: DoubleArray, imaginary: DoubleArray, offset: Int, size: Int, plan: Plan, inverse: Boolean) {
        for (i in 0 until size) {
            val j = plan.reverse[i]
            if (j > i) {
                val a = offset + i
                val b = offset + j
                val re = real[a]; real[a] = real[b]; real[b] = re
                val im = imaginary[a]; imaginary[a] = imaginary[b]; imaginary[b] = im
            }
        }
        var length = 2
        while (length <= size && length > 0) {
            val half = length / 2
            val step = size / length
            var base = 0
            while (base < size) {
                var phase = 0
                for (j in 0 until half) {
                    val a = offset + base + j
                    val b = a + half
                    val wr = plan.cos[phase]
                    val wi = if (inverse) plan.sin[phase] else -plan.sin[phase]
                    val re = wr * real[b] - wi * imaginary[b]
                    val im = wr * imaginary[b] + wi * real[b]
                    real[b] = real[a] - re
                    imaginary[b] = imaginary[a] - im
                    real[a] += re
                    imaginary[a] += im
                    phase += step
                }
                base += length
            }
            length = length shl 1
        }
        if (inverse) for (i in offset until offset + size) {
            real[i] /= size
            imaginary[i] /= size
        }
    }

    internal fun checkCancelled(isCancelled: () -> Boolean) {
        if (Thread.currentThread().isInterrupted || isCancelled()) throw CancellationException("Optical calculation cancelled")
    }
}
