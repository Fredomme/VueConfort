package fr.vueconfort.app.equalizer

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.vueconfort.app.MainActivity
import fr.vueconfort.app.data.VisualProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.ArrayDeque
import kotlin.math.ceil

/**
 * Real touch events, paced using device uptime, are delivered to actual UI sliders.
 * No synthetic future event timestamps, direct ViewModel updates or forced GC.
 * Deliberately NO Compose test rule: its test frame clock can freeze recomposition
 * while raw UiAutomation events run. ActivityScenario leaves Android's clock live.
 * Run only in Aperçu, never the commercial package. No Save/Reset/Delete action is used.
 *
 * Instrumentation arguments:
 *   equalizerDurationSeconds=180 (10..600), equalizerTouchHz=60 (10..120)
 * Output: target app files/performance/equalizer-performance.json.
 * App latency is measured here; presentation latency requires the matching Perfetto trace.
 */
@RunWith(AndroidJUnit4::class)
class EqualizerPerformanceDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalOnboarding: Boolean? = null
    private var recorder: EqualizerPerformance.Session? = null

    @Before fun enterPreviewWithoutChangingSavedProfiles() = runBlocking {
        check(instrumentation.targetContext.packageName == "fr.vueconfort.app.preview")
        val repository = VisualProfileRepository(instrumentation.targetContext)
        originalOnboarding = repository.onboardingCompleted.first()
        if (originalOnboarding == false) {
            repository.setOnboardingCompleted(true)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        boundsOnScreen("eq_open", "Ouvrir l’égaliseur")
        Unit
    }

    @After fun restoreOnboardingAndStopRecorder() {
        recorder?.let { value -> instrumentation.runOnMainSync { value.close() } }
        recorder = null
        scenario?.close()
        scenario = null
        if (originalOnboarding == false) runBlocking {
            VisualProfileRepository(instrumentation.targetContext).setOnboardingCompleted(false)
        }
    }

    @Test fun prolongedRealSliderManipulationRecordsAppFramesAndMemory() {
        val args = InstrumentationRegistry.getArguments()
        val durationSeconds = args.getString("equalizerDurationSeconds")?.toLongOrNull()?.coerceIn(10L, 600L) ?: 180L
        val touchHz = args.getString("equalizerTouchHz")?.toIntOrNull()?.coerceIn(10, 120) ?: 60
        tap("eq_open", "Ouvrir l’égaliseur")
        boundsOnScreen("eq_preview")
        tap("eq_scene_reading")

        // Warm up shaders/layout before installing the measurement listener. Draft only.
        dragSlider("eq_slider_intensity", 350L, touchHz, reverse = false)
        dragSlider("eq_slider_sharpness", 350L, touchHz, reverse = false)
        dragSlider("eq_slider_contrast", 350L, touchHz, reverse = true)
        SystemClock.sleep(250L)
        requireNotNull(scenario).onActivity { activity ->
            recorder = EqualizerPerformance.start(activity.window,
                (durationSeconds * touchHz * 2).toInt().coerceIn(32768, 131072))
        }
        val session = requireNotNull(recorder)
        session.sampleMemory("after_warmup")
        val beganUptime = SystemClock.uptimeMillis()
        val stopUptime = beganUptime + durationSeconds * 1000L
        var nextMemory = beganUptime + 10_000L
        var cycle = 0
        var gestures = 0
        var movesInjected = 0
        val scenes = listOf("eq_scene_reading", "eq_scene_detail", "eq_scene_image")
        val commonControls = listOf("eq_slider_size", "eq_slider_sharpness", "eq_slider_contrast",
            "eq_slider_comfort", "eq_slider_intensity")
        while (SystemClock.uptimeMillis() < stopUptime) {
            val scene = scenes[cycle % scenes.size]
            tap(scene)
            val controls = if (scene == "eq_scene_reading") commonControls + "eq_slider_text_weight" else commonControls
            for (tag in controls) {
                if (SystemClock.uptimeMillis() >= stopUptime) break
                movesInjected += dragSlider(tag, 700L, touchHz, reverse = cycle % 2 == 1)
                gestures++
                if (SystemClock.uptimeMillis() >= nextMemory) {
                    session.sampleMemory("elapsed_${(SystemClock.uptimeMillis() - beganUptime) / 1000}s")
                    nextMemory = SystemClock.uptimeMillis() + 10_000L
                }
            }
            holdOriginal(300L)
            cycle++
        }
        val manipulationEndedUptime = SystemClock.uptimeMillis()
        SystemClock.sleep(300L) // Allow the last app frame's asynchronous metrics to arrive.
        assertTrue("Metrics handler must drain", session.awaitMetrics())
        session.sampleMemory("end_of_manipulation")
        val snapshot = session.snapshot()
        instrumentation.runOnMainSync { session.close() }
        recorder = null

        // Serialization happens after the allocation measurement and after stopping callbacks.
        val report = JSONObject(snapshot.toJson())
            .put("harness", "ActivityScenario_UiAutomation_REAL_CLOCK_V2")
            .put("composeTestRuleUsed", false)
            .put("deviceModel", Build.MODEL).put("sdkInt", Build.VERSION.SDK_INT)
            .put("packageName", instrumentation.targetContext.packageName)
            .put("requestedManipulationSeconds", durationSeconds).put("requestedTouchHz", touchHz)
            .put("actualManipulationMillis", manipulationEndedUptime - beganUptime)
            .put("completedSliderGestures", gestures).put("moveEventsInjected", movesInjected)
            .put("profileSavedByTest", false).put("survivedProlongedManipulation", true)
            .put("developmentPresentationP95TargetMs", 50)
            .put("presentationTargetEvaluated", false)
        val output = File(instrumentation.targetContext.filesDir, "performance/equalizer-performance.json")
        check(output.parentFile!!.mkdirs() || output.parentFile!!.isDirectory)
        output.writeText(report.toString(2))
        instrumentation.sendStatus(0, Bundle().apply {
            putString("equalizer_report_path", output.absolutePath)
            putString("equalizer_app_frame_p95_ms", snapshot.commandToAppFrameComplete.p95Ms?.toString() ?: "unavailable")
        })
        Log.i("VCEqualizerPerf", "commands=${snapshot.commands.size} drawn=${snapshot.commandToDraw.count} " +
            "frameMatched=${snapshot.commandToAppFrameComplete.count} appP95Ms=${snapshot.commandToAppFrameComplete.p95Ms} " +
            "presentation=UNMEASURED report=${output.absolutePath}")

        assertTrue("Actual UI callbacks must have been measured", snapshot.commands.size >= 30)
        assertTrue("Preview draw callbacks must have been measured", snapshot.commandToDraw.count >= 10)
        assertTrue("Too few settings reached a draw: investigate a stalled clock or throughput regression",
            snapshot.commandToDraw.count >= snapshot.commands.size / 4)
        assertTrue("Drawn commands must join real frame metrics", snapshot.commandToAppFrameComplete.count >= 10)
        assertEquals("recordDraw must run within Choreographer drawing", 0L, snapshot.invalidDrawCallbacks)
        assertEquals("Command sample buffer must not truncate", 0L, snapshot.commandCapacityOverflows)
        assertEquals("Frame sample buffer must not truncate", 0L, snapshot.frameCapacityOverflows)
        assertTrue("Memory must be sampled", snapshot.memorySamples.size >= 2)
        assertFalse(report.getBoolean("physicalPresentationMeasured"))
        assertTrue(report.isNull("commandToPresentedFrame"))
        assertTrue("Observer must become inactive", EqualizerPerformance.command("after_stop") == 0L)
    }

    private fun boundsOnScreen(tag: String, fallbackText: String? = null): Rect {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        while (SystemClock.uptimeMillis() < deadline) {
            val node = findNode { value ->
                val id = value.viewIdResourceName.orEmpty()
                id == tag || id.endsWith(":id/$tag") ||
                    (fallbackText != null && value.text?.toString() == fallbackText)
            }
            if (node != null) {
                try {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    if (node.isVisibleToUser && node.isEnabled && bounds.width() > 8 && bounds.height() > 8)
                        return bounds
                    node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id)
                } finally { node.recycle() }
            }
            // No Compose synchronization or virtual-clock advancement here.
            SystemClock.sleep(50L)
        }
        error("Visible enabled UI control not found: $tag")
    }

    /** Returns one owned node; recycles all the other nodes acquired for the traversal. */
    private fun findNode(matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (matches(node)) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let(queue::addLast)
                node.recycle()
            }
            return null
        } finally { while (queue.isNotEmpty()) queue.removeFirst().recycle() }
    }

    private fun tap(tag: String, fallbackText: String? = null) {
        val rect = boundsOnScreen(tag, fallbackText)
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, rect.exactCenterX(), rect.exactCenterY())
        SystemClock.sleep(35L)
        inject(MotionEvent.ACTION_UP, downTime, rect.exactCenterX(), rect.exactCenterY())
        SystemClock.sleep(120L)
    }

    private fun dragSlider(tag: String, durationMs: Long, touchHz: Int, reverse: Boolean): Int {
        val rect = boundsOnScreen(tag)
        val low = rect.left + rect.width() * .12f
        val high = rect.right - rect.width() * .12f
        val from = if (reverse) high else low
        val to = if (reverse) low else high
        val y = rect.exactCenterY()
        val downTime = SystemClock.uptimeMillis()
        var x = from
        var released = false
        inject(MotionEvent.ACTION_DOWN, downTime, x, y)
        val steps = ceil(durationMs * touchHz / 1000.0).toInt().coerceAtLeast(2)
        try {
            for (step in 1..steps) {
                val due = downTime + durationMs * step / steps
                val wait = due - SystemClock.uptimeMillis()
                if (wait > 0) SystemClock.sleep(wait)
                x = from + (to - from) * step / steps
                inject(MotionEvent.ACTION_MOVE, downTime, x, y)
            }
            inject(MotionEvent.ACTION_UP, downTime, x, y)
            released = true
        } finally {
            if (!released) inject(MotionEvent.ACTION_CANCEL, downTime, x, y)
        }
        return steps
    }

    private fun holdOriginal(durationMs: Long) {
        val rect = boundsOnScreen("eq_original")
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.ACTION_DOWN, downTime, rect.exactCenterX(), rect.exactCenterY())
        try { SystemClock.sleep(durationMs) }
        finally { inject(MotionEvent.ACTION_UP, downTime, rect.exactCenterX(), rect.exactCenterY()) }
    }

    private fun inject(action: Int, downTime: Long, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { check(instrumentation.uiAutomation.injectInputEvent(event, true)) { "Touch injection failed" } }
        finally { event.recycle() }
    }
}
