package fr.vueconfort.app.custommagnifier

import kotlin.math.roundToInt

internal data class MagnifierRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
}

internal object MagnifierGeometry {
    fun displayRect(screenWidth: Int, screenHeight: Int): MagnifierRect =
        MagnifierRect(0, 0, screenWidth.coerceAtLeast(1), (screenHeight / 2).coerceAtLeast(1))

    fun sourceRect(screenWidth: Int, screenHeight: Int, zoom: Float): MagnifierRect {
        val safeZoom = zoom.coerceIn(1.5f, 4f)
        val display = displayRect(screenWidth, screenHeight)
        val width = (display.width / safeZoom).roundToInt().coerceIn(1, screenWidth)
        val height = (display.height / safeZoom).roundToInt().coerceIn(1, screenHeight / 2)
        val centerX = screenWidth / 2
        val centerY = screenHeight * 3 / 4
        val left = (centerX - width / 2).coerceIn(0, screenWidth - width)
        val top = (centerY - height / 2).coerceIn(screenHeight / 2, screenHeight - height)
        return MagnifierRect(left, top, left + width, top + height)
    }

    fun clampZoom(value: Float): Float = value.coerceIn(1.5f, 4f)
}
