package fr.vueconfort.app.optical

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.vueconfort.app.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

/** PixelCopy reads the actual rendered window, not a CPU approximation of the AGSL code. */
@RunWith(AndroidJUnit4::class)
class OpticalRendererPixelDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val aggressive = OpticalSettings(enabled = true, sharpness = 0.8f,
        localContrast = 1.5f, gamma = 1.25f, brightness = 0.8f, temperature = 0.2f,
        whiteReduction = 0.3f, horizontalStretch = 1.1f, verticalStretch = 0.9f,
        cylindricalDistortion = 0.08f, distortionAxisDegrees = 47f,
        quality = OpticalQuality.QUALITY)

    @Test fun neutralDisabledAndZeroIntensityExactlyMatchUnmodifiedWindowPixels() = withScene { scene ->
        val source = scene.capture()
        val cases = listOf(
            "neutral" to OpticalSettings(),
            "enabled-neutral" to OpticalSettings(enabled = true),
            "disabled-nonneutral-geometry" to aggressive.copy(enabled = false),
            "zero-intensity-nonneutral-geometry" to aggressive.copy(globalIntensity = 0f)
        )
        for ((label, settings) in cases) {
            scene.update(settings)
            assertArrayEquals(label, source, scene.capture())
        }
    }

    @Test fun holdOriginalIsExactAndReleaseRestoresStaticSceneWithoutChangingSettings() = withScene { scene ->
        val source = scene.capture()
        val treatment = aggressive.copy(horizontalStretch = 1f, verticalStretch = 1f,
            cylindricalDistortion = 0f)
        scene.update(treatment)
        val adjusted = scene.capture()
        assertTrue("treatment must visibly change pixels", changed(source, adjusted) > 100)
        val positionBefore = Rect(scene.bounds.get())
        scene.update(treatment, bypass = true)
        assertArrayEquals("held original", source, scene.capture())
        assertEquals("same frame and position", positionBefore, scene.bounds.get())
        assertEquals(treatment, scene.settings.value)
        scene.update(treatment)
        assertArrayEquals("release restores exact previous output", adjusted, scene.capture())
        scene.update(treatment.copy(localContrast = 0.7f))
        assertTrue("uniform changes must repaint an otherwise static scene",
            changed(adjusted, scene.capture()) > 100)
    }

    @Test fun sharpeningDoesNotExceedBoundedPerChannelDetail() = withScene { scene ->
        val source = scene.capture()
        scene.update(OpticalSettings(enabled = true, sharpness = 0.8f))
        val result = scene.capture()
        assertTrue("source contains edges affected by sharpening", changed(source, result) > 0)
        // 0.12 * 255 plus 2 quantization levels. Other operations are neutral.
        source.indices.forEach { index ->
            for (shift in listOf(0, 8, 16)) {
                assertTrue("detail bound at pixel $index channel $shift",
                    abs(((source[index] ushr shift) and 255) - ((result[index] ushr shift) and 255)) <= 33)
            }
            assertEquals("alpha retained", source[index] ushr 24, result[index] ushr 24)
        }
    }

    private fun changed(first: IntArray, second: IntArray) = first.indices.count { first[it] != second[it] }

    private fun withScene(block: (Scene) -> Unit) {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        check(instrumentation.targetContext.packageName == "fr.vueconfort.app.preview")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val scene = Scene(scenario)
            scenario.onActivity { activity ->
                activity.window.setFormat(PixelFormat.RGBA_8888)
                activity.setContent {
                    val density = LocalDensity.current
                    Box(Modifier.fillMaxSize().background(Color(0xff273849)), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(with(density) { 256.toDp() }, with(density) { 192.toDp() })
                            .onGloballyPositioned {
                                val bounds = it.boundsInWindow()
                                scene.bounds.set(Rect(bounds.left.roundToInt(), bounds.top.roundToInt(),
                                    bounds.right.roundToInt(), bounds.bottom.roundToInt()))
                            }
                            .then(if (scene.attached.value) Modifier.opticalRender(scene.settings.value,
                                scene.bypass.value) else Modifier)
                        ) {
                            drawRect(Color(0xffb7b7b7))
                            // Fixed pixel positions, multiple contrasts, thin lines and a colour patch.
                            for (row in 0 until 12) for (column in 0 until 16) {
                                val gray = 56 + ((column * 17 + row * 23) % 152)
                                drawRect(Color(gray, gray, gray),
                                    Offset(column * 16f, row * 16f), Size(15f, 15f))
                            }
                            drawRect(Color(0xffbd805a), Offset(80f, 55f), Size(63f, 43f))
                            for (x in 0 until 40 step 2) drawRect(Color(0xff454545),
                                Offset(12f + x, 95f), Size(1f, 42f))
                        }
                    }
                }
            }
            try { block(scene) } finally { instrumentation.waitForIdleSync() }
        }
    }

    private inner class Scene(val scenario: ActivityScenario<MainActivity>) {
        val settings = mutableStateOf(OpticalSettings())
        val bypass = mutableStateOf(false)
        val attached = mutableStateOf(false)
        val bounds = AtomicReference(Rect())

        fun update(next: OpticalSettings, bypass: Boolean = false) {
            instrumentation.runOnMainSync {
                settings.value = next
                this.bypass.value = bypass
                attached.value = true
            }
        }

        fun capture(): IntArray {
            instrumentation.waitForIdleSync()
            val frames = CountDownLatch(1)
            instrumentation.runOnMainSync {
                Choreographer.getInstance().postFrameCallback {
                    Choreographer.getInstance().postFrameCallback { frames.countDown() }
                }
            }
            assertTrue("two display frames", frames.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            val captured = CountDownLatch(1)
            val rectangle = Rect(bounds.get())
            assertEquals(256, rectangle.width())
            assertEquals(192, rectangle.height())
            val bitmap = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888)
            var status = -1
            scenario.onActivity { activity ->
                PixelCopy.request(activity.window, rectangle, bitmap, {
                    status = it
                    captured.countDown()
                }, Handler(Looper.getMainLooper()))
            }
            assertTrue("window pixel copy", captured.await(5, TimeUnit.SECONDS))
            assertEquals(PixelCopy.SUCCESS, status)
            return IntArray(256 * 192).also {
                bitmap.getPixels(it, 0, 256, 0, 0, 256, 192)
                bitmap.recycle()
            }
        }
    }
}
