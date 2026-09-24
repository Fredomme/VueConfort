package fr.vueconfort.vision

import kotlin.math.roundToInt

/** Pixel edges: left/top included, right/bottom excluded. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object VisionGeometry {
    /**
     * [centerX] and [centerY] are absolute screen coordinates in pixels.
     * The source band is [sourceTop, sourceBottom); its bounds exclude the panel.
     * A common fit factor preserves the output aspect ratio, subject to rounding
     * and the one-pixel minimum. Extreme finite centers are clamped before conversion.
     */
    fun crop(
        screenWidth: Int,
        sourceTop: Int,
        sourceBottom: Int,
        outputWidth: Int,
        outputHeight: Int,
        zoom: Double,
        centerX: Double,
        centerY: Double
    ): PixelRect {
        require(screenWidth > 0) { "Screen width must be positive" }
        require(sourceTop >= 0 && sourceBottom > sourceTop) { "Source band must be nonempty and on screen" }
        require(outputWidth > 0 && outputHeight > 0) { "Output dimensions must be positive" }
        require(zoom.isFinite() && zoom in 1.0..6.0) { "Zoom must be between 1 and 6" }
        require(centerX.isFinite() && centerY.isFinite()) { "Source center must be finite" }

        val sourceHeight = sourceBottom - sourceTop
        val desiredWidth = outputWidth / zoom
        val desiredHeight = outputHeight / zoom
        val fit = minOf(1.0, screenWidth / desiredWidth, sourceHeight / desiredHeight)
        val width = (desiredWidth * fit).roundToInt().coerceIn(1, screenWidth)
        val height = (desiredHeight * fit).roundToInt().coerceIn(1, sourceHeight)
        val left = (centerX - width / 2.0)
            .coerceIn(0.0, (screenWidth - width).toDouble()).roundToInt()
        val top = (centerY - height / 2.0)
            .coerceIn(sourceTop.toDouble(), (sourceBottom - height).toDouble()).roundToInt()
        return PixelRect(left, top, left + width, top + height)
    }

    /**
     * Whether nonempty rectangles overlap after reserving [margin] pixels around
     * either one. A gap exactly equal to margin is accepted; touching at margin 0
     * is not an overlap. Long arithmetic prevents overflow near screen limits.
     */
    fun overlaps(a: PixelRect, b: PixelRect, margin: Int = 0): Boolean {
        require(margin >= 0) { "Margin must not be negative" }
        if (a.left >= a.right || a.top >= a.bottom || b.left >= b.right || b.top >= b.bottom) return false
        return a.left.toLong() - margin < b.right.toLong() &&
            a.right.toLong() + margin > b.left.toLong() &&
            a.top.toLong() - margin < b.bottom.toLong() &&
            a.bottom.toLong() + margin > b.top.toLong()
    }
}
