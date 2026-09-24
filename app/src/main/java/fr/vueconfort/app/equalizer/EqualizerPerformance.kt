package fr.vueconfort.app.equalizer

import android.annotation.SuppressLint
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Trace
import android.view.Choreographer
import android.view.FrameMetrics
import android.view.Window
import androidx.annotation.RequiresApi
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.ceil

/**
 * Opt-in instrumentation. No listener, allocation or trace is installed until [start].
 * The UI calls [command] from the real control callback, and [recordDraw] AFTER drawing
 * the preview with that command's settings. A draw is display-list recording, NOT presentation.
 * FrameMetrics TOTAL_DURATION ends at app FrameCompleted, NOT SurfaceFlinger presentation.
 * Perfetto FrameTimeline must independently supply presentation and compositor-drop evidence.
 */
object EqualizerPerformance {
    private val sessions = AtomicLong()
    @Volatile private var active: Session? = null

    fun start(window: Window, maxCommands: Int = 32768): Session {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Start on the UI thread" }
        require(maxCommands in 1024..131072)
        active?.close()
        return Session(window, sessions.incrementAndGet(), maxCommands).also { active = it }
    }

    fun command(control: String): Long = active?.command(control) ?: 0L

    /** Must run inside the preview's draw callback, after drawContent(). */
    fun recordDraw(commandId: Long) { active?.recordDraw(commandId) }

    class Session internal constructor(
        private val window: Window,
        private val sessionId: Long,
        private val capacity: Int,
    ) : Closeable {
        private val lock = Any()
        private val startedNanos = System.nanoTime()
        private val base = sessionId shl 32
        private val commandNanos = LongArray(capacity)
        private val drawNanos = LongArray(capacity)
        private val frameCompletedNanos = LongArray(capacity)
        private val drawVsyncNanos = LongArray(capacity)
        private val frameTimelineIds = LongArray(capacity) { -1L }
        private val controls = arrayOfNulls<String>(capacity)
        private val frameDurations = LongArray(capacity * 2)
        private val pendingDraws = IntArray(1024)
        private var pendingCount = 0
        private var commandCount = 0
        private var frameCount = 0
        private var firstDrawFrames = 0
        private var appDeadlineMisses = 0
        private var deadlineFromSystemFrames = 0
        private var estimatedMissedRefreshIntervals = 0L
        private var reportsDropped = 0L
        private var commandOverflow = 0L
        private var frameOverflow = 0L
        private var unmatchedDraws = 0L
        private var invalidDrawCallbacks = 0L
        private var latestDrawIndex = -1
        private var endedNanos = 0L
        private var closed = false
        private val memories = mutableListOf<MemorySample>()
        private val nominalRefreshHz = window.decorView.display?.refreshRate?.toDouble()
            ?.takeIf { it.isFinite() && it > 0.0 } ?: 60.0
        private val nominalIntervalNanos = (1_000_000_000.0 / nominalRefreshHz).toLong()
        private val thread = HandlerThread("VC-Equalizer-Metrics").apply { start() }
        private val handler = Handler(thread.looper)
        private val choreographer = Choreographer.getInstance()
        private var currentFrameTimeNanos = 0L
        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!closed) {
                    // Public callback timestamp; getFrameTimeNanos itself is a hidden API.
                    // Animation callbacks precede traversal/draw in this same UI frame.
                    currentFrameTimeNanos = frameTimeNanos
                    choreographer.postFrameCallback(this)
                }
            }
        }
        private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
            // Copy only the needed primitive fields while FrameMetrics is valid. No per-frame
            // FrameMetrics copy, file I/O, JSON, memory query or quantile calculation here.
            val intended = metrics.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP)
            val vsync = metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP)
            val total = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            val first = metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L
            val deadline = if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L
            val timeline = if (Build.VERSION.SDK_INT >= 36)
                frameTimelineVsyncId(metrics) else -1L
            synchronized(lock) {
                if (!closed) {
                    reportsDropped += dropped.coerceAtLeast(0)
                    if (total >= 0L) {
                        if (frameCount < frameDurations.size) frameDurations[frameCount++] = total else frameOverflow++
                        if (first) firstDrawFrames++ else {
                            val budget = if (deadline > 0) deadline else nominalIntervalNanos
                            if (deadline > 0) deadlineFromSystemFrames++
                            if (total > budget) appDeadlineMisses++
                            // Estimate only: a slow app frame is not a count of physical display drops.
                            estimatedMissedRefreshIntervals += (ceil(total.toDouble() / nominalIntervalNanos).toLong() - 1L).coerceAtLeast(0L)
                        }
                    }
                    val completed = if (intended > 0 && total >= 0) intended + total else 0L
                    var retained = 0
                    for (p in 0 until pendingCount) {
                        val i = pendingDraws[p]
                        when {
                            drawVsyncNanos[i] == vsync && completed >= drawNanos[i] -> {
                                frameCompletedNanos[i] = completed
                                frameTimelineIds[i] = timeline
                                if (timeline >= 0) marker { "VC_EQ_FRAME:${base + i + 1L}:$timeline" }
                            }
                            drawVsyncNanos[i] <= vsync -> unmatchedDraws++
                            else -> pendingDraws[retained++] = i
                        }
                    }
                    pendingCount = retained
                }
            }
        }

        init {
            window.addOnFrameMetricsAvailableListener(listener, handler)
            choreographer.postFrameCallback(frameCallback)
        }

        internal fun command(control: String): Long {
            val now = System.nanoTime()
            val token = synchronized(lock) {
                if (closed) return 0L
                if (commandCount >= capacity) { commandOverflow++; return 0L }
                val i = commandCount++
                commandNanos[i] = now
                controls[i] = control
                base + i + 1L
            }
            marker { "VC_EQ_CMD:$token:$control" }
            return token
        }

        internal fun recordDraw(token: Long) {
            if (token == 0L || token ushr 32 != sessionId) return
            val i = token.toInt() - 1
            val now = System.nanoTime()
            val vsync = currentFrameTimeNanos
            synchronized(lock) {
                if (closed || i !in 0 until commandCount || drawNanos[i] != 0L) return
                drawNanos[i] = now
                drawVsyncNanos[i] = vsync
                latestDrawIndex = maxOf(latestDrawIndex, i)
                if (vsync == 0L) invalidDrawCallbacks++
                else if (pendingCount < pendingDraws.size) pendingDraws[pendingCount++] = i
                else unmatchedDraws++
            }
            marker { "VC_EQ_DRAW:$token:$vsync" }
        }

        /** Samples the complete instrumented process, including test/recorder overhead. No forced GC. */
        fun sampleMemory(label: String): MemorySample {
            val info = Debug.MemoryInfo()
            Debug.getMemoryInfo(info)
            val runtime = Runtime.getRuntime()
            val sample = MemorySample(label, System.nanoTime(), runtime.totalMemory() - runtime.freeMemory(),
                Debug.getNativeHeapAllocatedSize(), info.totalPss.toLong() * 1024,
                info.totalPrivateDirty.toLong() * 1024,
                info.getMemoryStat("summary.graphics")?.toLongOrNull()?.times(1024),
                Debug.getRuntimeStat("art.gc.bytes-allocated")?.toLongOrNull(),
                Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull(),
                Debug.getRuntimeStat("art.gc.gc-time")?.toLongOrNull())
            synchronized(lock) { memories += sample }
            return sample
        }

        /** Drains already delivered metric callbacks; does not assert that a display presented. */
        fun awaitMetrics(timeoutMillis: Long = 2000): Boolean {
            check(Looper.myLooper() != Looper.getMainLooper()) { "Do not block the UI thread" }
            val barrier = CountDownLatch(1)
            return handler.post { barrier.countDown() } && barrier.await(timeoutMillis, TimeUnit.MILLISECONDS)
        }

        /** Call after the timed run; snapshot/JSON allocations are outside the measured hot path. */
        fun snapshot(): Snapshot = synchronized(lock) {
            val commands = List(commandCount) { i -> CommandSample(base + i + 1L, controls[i].orEmpty(),
                commandNanos[i], drawNanos[i].takeIf { it > 0 }, drawVsyncNanos[i].takeIf { it > 0 },
                frameCompletedNanos[i].takeIf { it > 0 }, frameTimelineIds[i].takeIf { it >= 0 }) }
            Snapshot(startedNanos, if (endedNanos == 0L) System.nanoTime() else endedNanos,
                nominalRefreshHz, commands, distribution(commands.mapNotNull { it.drawNanos?.minus(it.commandNanos) }),
                distribution(commands.mapNotNull { it.appFrameCompletedNanos?.minus(it.commandNanos) }),
                distribution(frameDurations.take(frameCount)), frameCount, firstDrawFrames, appDeadlineMisses,
                deadlineFromSystemFrames, estimatedMissedRefreshIntervals, reportsDropped, commandOverflow,
                frameOverflow, unmatchedDraws, invalidDrawCallbacks,
                (0 until commandCount).count { drawNanos[it] == 0L && it < latestDrawIndex }, memories.toList())
        }

        override fun close() {
            check(Looper.myLooper() == Looper.getMainLooper()) { "Close on the UI thread" }
            synchronized(lock) { if (closed) return; closed = true; endedNanos = System.nanoTime() }
            if (active === this) active = null
            choreographer.removeFrameCallback(frameCallback)
            window.removeOnFrameMetricsAvailableListener(listener)
            thread.quitSafely()
        }
    }

    data class Distribution(val count: Int, val p50Ms: Double?, val p95Ms: Double?, val p99Ms: Double?, val maximumMs: Double?) {
        internal fun json() = JSONObject().put("count", count).nullable("p50Ms", p50Ms)
            .nullable("p95Ms", p95Ms).nullable("p99Ms", p99Ms).nullable("maximumMs", maximumMs)
    }

    data class CommandSample(val id: Long, val control: String, val commandNanos: Long,
        val drawNanos: Long?, val drawVsyncNanos: Long?, val appFrameCompletedNanos: Long?, val frameTimelineVsyncId: Long?) {
        internal fun json() = JSONObject().put("id", id).put("control", control).put("commandNanos", commandNanos)
            .nullable("drawNanos", drawNanos).nullable("drawVsyncNanos", drawVsyncNanos)
            .nullable("appFrameCompletedNanos", appFrameCompletedNanos).nullable("frameTimelineVsyncId", frameTimelineVsyncId)
    }

    data class MemorySample(val label: String, val timestampNanos: Long, val javaUsedBytes: Long,
        val nativeAllocatedBytes: Long, val totalPssBytes: Long, val privateDirtyBytes: Long,
        val graphicsPrivateBytes: Long?, val processAllocatedBytes: Long?, val gcCount: Long?, val gcTimeMs: Long?) {
        internal fun json() = JSONObject().put("label", label).put("timestampNanos", timestampNanos)
            .put("javaUsedBytes", javaUsedBytes).put("nativeAllocatedBytes", nativeAllocatedBytes)
            .put("totalPssBytes", totalPssBytes).put("privateDirtyBytes", privateDirtyBytes)
            .nullable("graphicsPrivateBytes", graphicsPrivateBytes).nullable("processAllocatedBytes", processAllocatedBytes)
            .nullable("gcCount", gcCount).nullable("gcTimeMs", gcTimeMs)
    }

    data class Snapshot(val startedNanos: Long, val endedNanos: Long, val nominalRefreshHz: Double,
        val commands: List<CommandSample>, val commandToDraw: Distribution, val commandToAppFrameComplete: Distribution,
        val appFrameDuration: Distribution, val frameCount: Int, val firstDrawFrames: Int,
        val appDeadlineMisses: Int, val deadlineFromSystemFrames: Int, val estimatedMissedRefreshIntervals: Long,
        val frameMetricsReportsDropped: Long, val commandCapacityOverflows: Long, val frameCapacityOverflows: Long,
        val unmatchedDraws: Long, val invalidDrawCallbacks: Long, val coalescedBeforeDraw: Int,
        val memorySamples: List<MemorySample>) {
        fun toJson(): String {
            val first = memorySamples.firstOrNull()
            val last = memorySamples.lastOrNull()
            val allocated = if (first?.processAllocatedBytes != null && last?.processAllocatedBytes != null)
                (last.processAllocatedBytes - first.processAllocatedBytes).takeIf { it >= 0 } else null
            return JSONObject().put("schemaVersion", 1).put("clock", "System.nanoTime / Android monotonic")
                .put("startedNanos", startedNanos).put("endedNanos", endedNanos).put("nominalRefreshHzAtStart", nominalRefreshHz)
                .put("commandDefinition", "Real UI callback; excludes touch hardware and input dispatch before the callback")
                .put("drawDefinition", "Preview display-list recording; not GPU completion or display presentation")
                .put("appFrameCompleteDefinition", "FrameMetrics.INTENDED_VSYNC_TIMESTAMP + TOTAL_DURATION, joined by exact VSYNC_TIMESTAMP")
                .put("physicalPresentationMeasured", false).put("commandToPresentedFrame", JSONObject.NULL)
                .put("physicalDisplayDroppedFrames", JSONObject.NULL)
                .put("presentationEvidenceNeeded", "Join frameTimelineVsyncId to Perfetto SurfaceFlinger FrameTimeline; reject dropped/unmatched frames")
                .put("commandToDraw", commandToDraw.json()).put("commandToAppFrameComplete", commandToAppFrameComplete.json())
                .put("appFrameDuration", appFrameDuration.json()).put("frameCount", frameCount)
                .put("firstDrawFrames", firstDrawFrames).put("appDeadlineMisses", appDeadlineMisses)
                .put("deadlineFromSystemFrames", deadlineFromSystemFrames)
                .put("estimatedMissedRefreshIntervals", estimatedMissedRefreshIntervals)
                .put("frameMetricsReportsDropped", frameMetricsReportsDropped)
                .put("commandCapacityOverflows", commandCapacityOverflows).put("frameCapacityOverflows", frameCapacityOverflows)
                .put("unmatchedDraws", unmatchedDraws).put("invalidDrawCallbacks", invalidDrawCallbacks)
                .put("coalescedBeforeDraw", coalescedBeforeDraw)
                .put("commandsWithoutFrameMetric", commands.count { it.drawNanos != null && it.appFrameCompletedNanos == null })
                .put("commandsPendingDraw", commands.count { it.drawNanos == null } - coalescedBeforeDraw)
                .put("allocationScope", "Entire instrumented process, including instrumentation and recorder; not renderer-only")
                .nullable("processAllocatedBytesDuringSamples", allocated)
                .put("memoryPeakDefinition", "Maximum of periodic samples; not an instantaneous high-water mark")
                .nullable("sampledPeakTotalPssBytes", memorySamples.maxOfOrNull { it.totalPssBytes })
                .put("memorySamples", JSONArray().also { a -> memorySamples.forEach { a.put(it.json()) } })
                .put("commands", JSONArray().also { a -> commands.forEach { a.put(it.json()) } }).toString(2)
        }
    }

    private fun distribution(nanos: List<Long>): Distribution {
        val sorted = nanos.filter { it >= 0 }.sorted()
        fun p(fraction: Double): Double? = if (sorted.isEmpty()) null
            else sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(0, sorted.lastIndex)] / 1e6
        return Distribution(sorted.size, p(.50), p(.95), p(.99), sorted.lastOrNull()?.div(1e6))
    }

    private fun JSONObject.nullable(name: String, value: Any?): JSONObject = put(name, value ?: JSONObject.NULL)

    @RequiresApi(36)
    @SuppressLint("WrongConstant")
    private fun frameTimelineVsyncId(metrics: FrameMetrics): Long {
        // Android SDK 36 publishes this metric and getMetric explicitly handles it.
        // The SDK's @Metric IntDef omits it. If the platform jankApi flag is off,
        // getMetric returns -1; that remains unavailable rather than invented evidence.
        return metrics.getMetric(FrameMetrics.FRAME_TIMELINE_VSYNC_ID)
    }

    private inline fun marker(name: () -> String) {
        if (Build.VERSION.SDK_INT < 29 || Trace.isEnabled()) {
            Trace.beginSection(name().take(127))
            Trace.endSection()
        }
    }
}
