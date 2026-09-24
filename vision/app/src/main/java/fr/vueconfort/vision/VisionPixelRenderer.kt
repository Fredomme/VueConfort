package fr.vueconfort.vision

import kotlin.math.pow

/**
 * Display adjustments for a rectangular, opaque ARGB image already at its final size.
 *
 * Processing is in linear light: contrast around luminance 0.5, brightness, then
 * a 3x3 luminance unsharp mask. The resulting luminance delta is added equally
 * to linear R, G and B, preserving their differences until final gamut clipping.
 * No optical model, eye prescription or perceptual-benefit claim is involved.
 *
 * Each call returns a new array and never changes the input. A neutral call is
 * an exact copy. Memory use is one output array, plus one luminance array only
 * when contours are active. The two sRGB lookup tables are shared and immutable.
 */
object VisionPixelRenderer {
    private const val ENCODE_STEPS = 65536
    private val decode = DoubleArray(256) { code ->
        val value = code / 255.0
        if (value <= 0.04045) value / 12.92
        else ((value + 0.055) / 1.055).pow(2.4)
    }
    private val encode = IntArray(ENCODE_STEPS + 1) { index ->
        val value = index.toDouble() / ENCODE_STEPS
        val encoded = if (value <= 0.0031308) value * 12.92
        else 1.055 * value.pow(1.0 / 2.4) - 0.055
        (encoded * 255.0 + 0.5).toInt().coerceIn(0, 255)
    }

    fun apply(
        pixels: IntArray,
        width: Int,
        height: Int,
        brightness: Double = 0.0,
        contrast: Double = 1.0,
        contours: Double = 0.0,
    ): IntArray {
        require(width > 0 && height > 0 && width.toLong() * height == pixels.size.toLong()) {
            "width and height must be positive and match the pixel count"
        }
        require(brightness.isFinite() && brightness in -0.25..0.25) {
            "brightness must be finite and within [-0.25, 0.25]"
        }
        require(contrast.isFinite() && contrast in 0.5..2.0) {
            "contrast must be finite and within [0.5, 2.0]"
        }
        require(contours.isFinite() && contours in 0.0..2.0) {
            "contours must be finite and within [0, 2]"
        }

        val noToneAdjustment = brightness == 0.0 && contrast == 1.0
        if (noToneAdjustment && contours == 0.0) {
            for (pixel in pixels) requireOpaque(pixel)
            return pixels.copyOf()
        }

        val output = IntArray(pixels.size)
        if (contours == 0.0) {
            for (index in pixels.indices) {
                val pixel = pixels[index]
                requireOpaque(pixel)
                val red = decode[(pixel ushr 16) and 255]
                val green = decode[(pixel ushr 8) and 255]
                val blue = decode[pixel and 255]
                val luminance = luminance(red, green, blue)
                val delta = (luminance - 0.5) * contrast + 0.5 + brightness - luminance
                output[index] = argb(red + delta, green + delta, blue + delta)
            }
            return output
        }

        val toned = DoubleArray(pixels.size)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            requireOpaque(pixel)
            val luminance = luminance(
                decode[(pixel ushr 16) and 255],
                decode[(pixel ushr 8) and 255],
                decode[pixel and 255],
            )
            toned[index] = if (noToneAdjustment) luminance
            else (luminance - 0.5) * contrast + 0.5 + brightness
        }

        val contourWeight = contours / 9.0
        for (y in 0 until height) {
            val row = y * width
            val upper = if (y == 0) row else row - width
            val lower = if (y == height - 1) row else row + width
            for (x in 0 until width) {
                val index = row + x
                val left = if (x == 0) x else x - 1
                val right = if (x == width - 1) x else x + 1
                val center = toned[index]
                // Edge extension, never row wrapping. Differences preserve uniform fields exactly.
                val neighborDifference =
                    (toned[upper + left] - center) + (toned[upper + x] - center) +
                        (toned[upper + right] - center) + (toned[row + left] - center) +
                        (toned[row + right] - center) + (toned[lower + left] - center) +
                        (toned[lower + x] - center) + (toned[lower + right] - center)
                val adjustedLuminance = center - contourWeight * neighborDifference
                val pixel = pixels[index]
                val red = decode[(pixel ushr 16) and 255]
                val green = decode[(pixel ushr 8) and 255]
                val blue = decode[pixel and 255]
                val delta = adjustedLuminance - luminance(red, green, blue)
                output[index] = argb(red + delta, green + delta, blue + delta)
            }
        }
        return output
    }

    private fun requireOpaque(pixel: Int) {
        require(pixel ushr 24 == 255) { "all pixels must be opaque ARGB" }
    }

    private fun luminance(red: Double, green: Double, blue: Double) =
        0.2126 * red + 0.7152 * green + 0.0722 * blue

    private fun code(linear: Double): Int = when {
        linear <= 0.0 -> 0
        linear >= 1.0 -> 255
        else -> encode[(linear * ENCODE_STEPS + 0.5).toInt()]
    }

    private fun argb(red: Double, green: Double, blue: Double): Int =
        (0xff shl 24) or (code(red) shl 16) or (code(green) shl 8) or code(blue)
}
