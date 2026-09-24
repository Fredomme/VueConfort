package fr.vueconfort.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VisionGeometryTest {
    private fun crop(
        screenWidth: Int = 1000,
        sourceTop: Int = 100,
        sourceBottom: Int = 900,
        outputWidth: Int = 600,
        outputHeight: Int = 300,
        zoom: Double = 1.0,
        centerX: Double = 500.0,
        centerY: Double = 500.0
    ) = VisionGeometry.crop(screenWidth, sourceTop, sourceBottom, outputWidth, outputHeight, zoom, centerX, centerY)

    @Test fun zoomChangesSourceSizeWithoutChangingTheRequestedCenter() {
        assertEquals(PixelRect(200, 350, 800, 650), crop())
        assertEquals(PixelRect(350, 425, 650, 575), crop(zoom = 2.0))
        assertEquals(PixelRect(450, 475, 550, 525), crop(zoom = 6.0))
    }

    @Test fun sourceHeightFitsBothDimensionsTogether() {
        val result = crop(screenWidth = 600, sourceTop = 100, sourceBottom = 400,
            outputWidth = 1080, outputHeight = 600, centerX = 300.0, centerY = 250.0)
        assertEquals(PixelRect(30, 100, 570, 400), result)
        assertEquals(1080.0 / 600.0, result.width.toDouble() / result.height, 1e-9)
    }

    @Test fun sourceWidthFitsBothDimensionsTogether() {
        assertEquals(PixelRect(0, 525, 300, 675), crop(screenWidth = 300,
            sourceTop = 0, sourceBottom = 1200, outputWidth = 1000, outputHeight = 500,
            centerX = 150.0, centerY = 600.0))
    }

    @Test fun outOfBoundsCentersClampAtEverySourceEdge() {
        assertEquals(PixelRect(0, 100, 300, 250), crop(zoom = 2.0,
            centerX = -Double.MAX_VALUE, centerY = -Double.MAX_VALUE))
        assertEquals(PixelRect(700, 750, 1000, 900), crop(zoom = 2.0,
            centerX = Double.MAX_VALUE, centerY = Double.MAX_VALUE))
    }

    @Test fun tinyOutputsAndNarrowSourceRemainAtLeastOnePixel() {
        assertEquals(PixelRect(0, 0, 1, 1), crop(screenWidth = 1, sourceTop = 0,
            sourceBottom = 1, outputWidth = 1, outputHeight = 1, zoom = 6.0,
            centerX = 0.0, centerY = 0.0))
        val narrow = crop(screenWidth = 1, outputWidth = 1000, outputHeight = 1)
        assertEquals(1, narrow.width)
        assertEquals(1, narrow.height)
    }

    @Test fun roundingRetainsTheNearestIntegerDimensions() {
        val result = crop(outputWidth = 101, outputHeight = 51, zoom = 2.0)
        assertEquals(51, result.width)
        assertEquals(26, result.height)
    }

    @Test fun maximumIntegerBoundsDoNotOverflow() {
        val result = crop(screenWidth = Int.MAX_VALUE, sourceTop = Int.MAX_VALUE - 1000,
            sourceBottom = Int.MAX_VALUE, outputWidth = Int.MAX_VALUE,
            outputHeight = Int.MAX_VALUE, centerX = Double.MAX_VALUE, centerY = Double.MAX_VALUE)
        assertEquals(PixelRect(Int.MAX_VALUE - 1000, Int.MAX_VALUE - 1000,
            Int.MAX_VALUE, Int.MAX_VALUE), result)
        assertEquals(1000, result.width)
        assertEquals(1000, result.height)
    }

    @Test fun allSampledCropsStayInsideTheUsableBandAndOutsideThePanel() {
        val panel = PixelRect(0, 1000, 1080, 2340)
        for (zoom in listOf(1.0, 1.1, 2.567, 4.0, 6.0)) {
            for (x in listOf(-1e100, 0.0, 540.0, 1080.0, 1e100)) {
                for (y in listOf(-1e100, 80.0, 500.0, 990.0, 1e100)) {
                    val result = crop(screenWidth = 1080, sourceTop = 80, sourceBottom = 990,
                        outputWidth = 1080, outputHeight = 650, zoom = zoom, centerX = x, centerY = y)
                    assertTrue(result.left >= 0 && result.right <= 1080)
                    assertTrue(result.top >= 80 && result.bottom <= 990)
                    assertTrue(result.width >= 1 && result.height >= 1)
                    assertFalse(VisionGeometry.overlaps(result, panel, margin = 10))
                }
            }
        }
    }

    @Test fun invalidDimensionsAndNonfiniteInputsAreRejected() {
        expectInvalid { crop(screenWidth = 0) }
        expectInvalid { crop(sourceTop = -1) }
        expectInvalid { crop(sourceTop = 900, sourceBottom = 900) }
        expectInvalid { crop(sourceBottom = 99) }
        expectInvalid { crop(outputWidth = 0) }
        expectInvalid { crop(outputHeight = -1) }
        for (zoom in listOf(0.999, 6.001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            expectInvalid { crop(zoom = zoom) }
        }
        for (center in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            expectInvalid { crop(centerX = center) }
            expectInvalid { crop(centerY = center) }
        }
    }

    @Test fun overlapIsSymmetricAndTouchingEdgesRemainDisjoint() {
        val a = PixelRect(10, 10, 20, 20)
        val intersecting = PixelRect(19, 19, 30, 30)
        assertTrue(VisionGeometry.overlaps(a, intersecting))
        assertTrue(VisionGeometry.overlaps(intersecting, a))
        assertFalse(VisionGeometry.overlaps(a, PixelRect(20, 10, 30, 20)))
        assertFalse(VisionGeometry.overlaps(a, PixelRect(10, 20, 20, 30)))
        assertFalse(VisionGeometry.overlaps(a, PixelRect(15, 15, 15, 20)))
    }

    @Test fun marginReservesTheRequestedGapOnce() {
        val a = PixelRect(10, 10, 20, 20)
        val b = PixelRect(21, 10, 30, 20)
        assertFalse(VisionGeometry.overlaps(a, b))
        assertFalse(VisionGeometry.overlaps(a, b, margin = 1))
        assertTrue(VisionGeometry.overlaps(a, b, margin = 2))
        assertTrue(VisionGeometry.overlaps(b, a, margin = 2))
        expectInvalid { VisionGeometry.overlaps(a, b, margin = -1) }
    }

    @Test fun largeMarginsDoNotWrapIntegerEdges() {
        val high = PixelRect(Int.MAX_VALUE - 20, Int.MAX_VALUE - 20, Int.MAX_VALUE, Int.MAX_VALUE)
        val low = PixelRect(0, 0, 10, 10)
        assertFalse(VisionGeometry.overlaps(high, low))
        assertTrue(VisionGeometry.overlaps(high, low, margin = Int.MAX_VALUE))
        assertTrue(VisionGeometry.overlaps(low, high, margin = Int.MAX_VALUE))
        val negative = PixelRect(Int.MIN_VALUE, Int.MIN_VALUE, Int.MIN_VALUE + 10, Int.MIN_VALUE + 10)
        assertFalse(VisionGeometry.overlaps(negative, high, margin = Int.MAX_VALUE))
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected validation failure.
        }
    }
}
