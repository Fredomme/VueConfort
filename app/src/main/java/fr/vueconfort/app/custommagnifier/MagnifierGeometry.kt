package fr.vueconfort.app.custommagnifier

import kotlin.math.roundToInt

internal data class MagnifierRect(var left: Int, var top: Int, var right: Int, var bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
}

internal object MagnifierGeometry {
    fun displayRect(screenWidth: Int, screenHeight: Int): MagnifierRect =
        MagnifierRect(0, 0, screenWidth.coerceAtLeast(1), (screenHeight / 2).coerceAtLeast(1))

    fun sourceRect(screenWidth: Int, screenHeight: Int, zoom: Float): MagnifierRect {
        val rect = MagnifierRect(0, 0, 0, 0)
        sourceRect(screenWidth, screenHeight, zoom, rect)
        return MagnifierRect(rect.left, rect.top, rect.right, rect.bottom)
    }

    fun sourceRect(screenWidth: Int, screenHeight: Int, zoom: Float, out: MagnifierRect) {
        val safeZoom = zoom.coerceIn(1.5f, 4f)
        val displayWidth = screenWidth.coerceAtLeast(1)
        val displayHeight = (screenHeight / 2).coerceAtLeast(1)
        val width = (displayWidth / safeZoom).roundToInt().coerceIn(1, screenWidth)
        val height = (displayHeight / safeZoom).roundToInt().coerceIn(1, screenHeight / 2)
        val centerX = screenWidth / 2
        val centerY = screenHeight * 3 / 4
        val left = (centerX - width / 2).coerceIn(0, screenWidth - width)
        val top = (centerY - height / 2).coerceIn(screenHeight / 2, screenHeight - height)
        out.left = left
        out.top = top
        out.right = left + width
        out.bottom = top + height
    }

    fun clampZoom(value: Float): Float = value.coerceIn(1.5f, 4f)
}
