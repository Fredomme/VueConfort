package fr.vueconfort.app.custommagnifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnifierGeometryTest {
    @Test
    fun displayUsesUpperHalf() {
        assertEquals(MagnifierRect(0, 0, 1080, 1170), MagnifierGeometry.displayRect(1080, 2340))
    }

    @Test
    fun sourceAtTwoXIsCenteredInLowerHalf() {
        val source = MagnifierGeometry.sourceRect(1080, 2340, 2f)
        assertEquals(540, source.width)
        assertEquals(585, source.height)
        assertEquals(540, source.centerX)
        assertTrue(source.top >= 1170)
        assertTrue(source.bottom <= 2340)
    }

    @Test
    fun zoomIsBounded() {
        assertEquals(1.5f, MagnifierGeometry.clampZoom(0f))
        assertEquals(4f, MagnifierGeometry.clampZoom(9f))
        assertEquals(3f, MagnifierGeometry.clampZoom(3f))
    }
}
