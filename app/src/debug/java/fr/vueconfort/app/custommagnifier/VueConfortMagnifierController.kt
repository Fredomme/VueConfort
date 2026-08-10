package fr.vueconfort.app.custommagnifier

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.graphics.Point
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import fr.vueconfort.app.R
import kotlin.math.roundToInt

internal class VueConfortMagnifierController(
    private val service: AccessibilityService,
    private val windowManager: WindowManager,
    private val onClosed: () -> Unit
) {
    private val frameProvider = ScreenshotFrameProvider(service)
    private val settings = MagnifierSettings(service)
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var imageView: ImageView? = null
    private var statusView: TextView? = null
    private var zoomView: TextView? = null
    private var displayedBitmap: Bitmap? = null
    private val sourceRect = MagnifierRect(0, 0, 0, 0)
    private val androidSourceRect = Rect()
    private val destinationRect = Rect()
    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val renderCanvas = Canvas()
    private var running = false
    private var paused = false
    private var captureInFlight = false
    private var startedAt = 0L
    private var frameCount = 0L
    private var errorCount = 0L
    private var latencyTotalMs = 0L
    private var captureIntervalMs = BALANCED_INTERVAL_MS
    private var consecutiveSuccesses = 0

    fun requestStart() {
        if (settings.consented) start() else showDisclosure()
    }

    private fun showDisclosure() {
        closeOverlayOnly()
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = roundedBackground(0xFA102B3F.toInt())
        }
        root.addView(textView(service.getString(R.string.custom_magnifier_name), 20f))
        root.addView(textView(service.getString(R.string.custom_magnifier_disclosure), 15f))
        root.addView(Button(service).apply {
            text = service.getString(R.string.custom_magnifier_activate)
            setOnClickListener { settings.consented = true; start() }
        })
        root.addView(Button(service).apply { text = service.getString(R.string.cancel); setOnClickListener { close() } })
        val (width, _) = windowSize()
        addOverlay(root, width - dp(32), WindowManager.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
    }

    private fun start() {
        closeOverlayOnly()
        running = true
        paused = false
        captureInFlight = false
        startedAt = SystemClock.elapsedRealtime()
        frameCount = 0
        errorCount = 0
        latencyTotalMs = 0
        captureIntervalMs = BALANCED_INTERVAL_MS
        consecutiveSuccesses = 0
        showMagnifierOverlay()
        scheduleCapture(120)
    }

    private fun showMagnifierOverlay() {
        val root = FrameLayout(service).apply { background = roundedBackground(Color.BLACK) }
        imageView = ImageView(service).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            contentDescription = "Zone agrandie expérimentale"
        }.also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }

        statusView = textView("Loupe VueConfort — expérimental", 13f).also {
            it.setBackgroundColor(0xC0000000.toInt())
            root.addView(it, FrameLayout.LayoutParams(-1, dp(42), Gravity.TOP or Gravity.START).apply {
                topMargin = dp(54)
            })
        }
        val controls = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xD0000000.toInt())
        }
        controls.addView(controlButton("−") { changeZoom(-0.5f) }, weightedControl())
        zoomView = textView("${settings.zoom}×", 14f).also {
            it.gravity = Gravity.CENTER
            controls.addView(it, weightedControl())
        }
        controls.addView(controlButton("+") { changeZoom(0.5f) }, weightedControl())
        controls.addView(controlButton("Pause") {
            paused = !paused
            text = if (paused) "Reprendre" else "Pause"
            statusView?.text = if (paused) "Loupe VueConfort — en pause" else "Loupe VueConfort — expérimental"
            if (!paused) scheduleCapture(0)
        }, weightedControl())
        controls.addView(controlButton("Fermer") { close() }, weightedControl())
        root.addView(controls, FrameLayout.LayoutParams(-1, dp(54), Gravity.TOP or Gravity.START))

        val (width, height) = windowSize()
        addOverlay(root, width, height / 2, Gravity.TOP or Gravity.START)
    }

    private fun scheduleCapture(delayMs: Long) {
        handler.removeCallbacks(captureRunnable)
        if (running && !paused) handler.postDelayed(captureRunnable, delayMs)
    }

    private val captureRunnable = Runnable {
        if (!running || paused || captureInFlight) return@Runnable
        captureInFlight = true
        val captureStarted = SystemClock.elapsedRealtime()
        frameProvider.captureOnce(
            onFrame = { screen ->
                    captureInFlight = false
                    val latency = SystemClock.elapsedRealtime() - captureStarted
                    latencyTotalMs += latency
                    frameCount++
                    updateFrame(screen)
                    consecutiveSuccesses++
                    if (consecutiveSuccesses >= SUCCESS_WINDOW) {
                        captureIntervalMs = (captureIntervalMs - ADAPTIVE_STEP_MS)
                            .coerceAtLeast(QUALITY_INTERVAL_MS)
                        consecutiveSuccesses = 0
                    }
                    if (frameCount % 120L == 0L) logMetrics()
                    scheduleCapture((captureIntervalMs - latency).coerceAtLeast(MIN_DELAY_MS))
                },
            onFailure = { errorCode, throwable ->
                    captureInFlight = false
                    errorCount++
                    consecutiveSuccesses = 0
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) {
                        captureIntervalMs = (captureIntervalMs + BACKOFF_STEP_MS)
                            .coerceAtMost(BATTERY_INTERVAL_MS)
                        statusView?.text = "Cadence réduite temporairement"
                        scheduleCapture(captureIntervalMs)
                    } else {
                        statusView?.text = captureErrorMessage(errorCode, throwable)
                        paused = true
                        logMetrics()
                    }
                }
        )
    }

    private fun updateFrame(screen: Bitmap) {
        MagnifierGeometry.sourceRect(screen.width, screen.height, settings.zoom, sourceRect)
        if (isNearlyBlack(screen, sourceRect)) {
            screen.recycle()
            displayedBitmap?.recycle()
            displayedBitmap = null
            imageView?.setImageDrawable(null)
            statusView?.text = "Ce contenu protégé ne peut pas être agrandi. Utilisez la loupe Android."
            return
        }
        val view = imageView ?: run { screen.recycle(); return }
        val output = ensureOutputBitmap(sourceRect.width, sourceRect.height)
        androidSourceRect.set(sourceRect.left, sourceRect.top, sourceRect.right, sourceRect.bottom)
        destinationRect.set(0, 0, output.width, output.height)
        renderCanvas.drawBitmap(screen, androidSourceRect, destinationRect, renderPaint)
        screen.recycle()
        if (view.drawable == null) view.setImageBitmap(output) else view.invalidate()
        zoomView?.text = "${settings.zoom}×"
    }

    private fun changeZoom(delta: Float) {
        settings.zoom = MagnifierGeometry.clampZoom(settings.zoom + delta)
        zoomView?.text = "${settings.zoom}×"
        if (!paused) scheduleCapture(0)
    }

    fun onConfigurationChanged() {
        if (!running) return
        closeOverlayOnly()
        showMagnifierOverlay()
        if (!paused) scheduleCapture(100)
    }

    private fun captureErrorMessage(errorCode: Int?, throwable: Throwable?): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Capture trop rapprochée — appuyez sur Reprendre"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Écran indisponible — utilisez la loupe Android"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "Accès refusé — utilisez la loupe Android"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> "Ce contenu protégé ne peut pas être agrandi. Utilisez la loupe Android."
        else -> "Contenu protégé ou capture indisponible — utilisez la loupe Android"
    }

    private fun isNearlyBlack(bitmap: Bitmap, area: MagnifierRect): Boolean {
        var dark = 0
        var sampled = 0
        val stepX = (area.width / 12).coerceAtLeast(1)
        val stepY = (area.height / 12).coerceAtLeast(1)
        var y = area.top + stepY / 2
        while (y < area.bottom) {
            var x = area.left + stepX / 2
            while (x < area.right) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.red(pixel) < 6 && Color.green(pixel) < 6 && Color.blue(pixel) < 6) dark++
                sampled++
                x += stepX
            }
            y += stepY
        }
        return sampled > 0 && dark * 100 / sampled >= 96
    }

    private fun logMetrics() {
        val elapsed = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(1)
        val fps = frameCount * 1000f / elapsed
        val latency = if (frameCount == 0L) 0 else latencyTotalMs / frameCount
        Log.i(TAG, "elapsedMs=$elapsed frames=$frameCount fps=$fps avgLatencyMs=$latency errors=$errorCount intervalMs=$captureIntervalMs")
    }

    fun close() {
        running = false
        paused = true
        handler.removeCallbacksAndMessages(null)
        logMetrics()
        closeOverlayOnly()
        onClosed()
    }

    private fun closeOverlayOnly() {
        overlay?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlay = null
        imageView = null
        statusView = null
        zoomView = null
        displayedBitmap?.recycle()
        displayedBitmap = null
        renderCanvas.setBitmap(null)
    }

    private fun ensureOutputBitmap(width: Int, height: Int): Bitmap {
        displayedBitmap?.takeIf { !it.isRecycled && it.width == width && it.height == height }?.let {
            return it
        }
        displayedBitmap?.recycle()
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            displayedBitmap = it
            renderCanvas.setBitmap(it)
        }
    }

    private fun addOverlay(view: View, width: Int, height: Int, gravity: Int) {
        val params = WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }
        windowManager.addView(view, params)
        overlay = view
    }

    private fun controlButton(label: String, action: Button.() -> Unit) = Button(service).apply {
        text = label
        textSize = 10f
        minWidth = 0
        setPadding(0, 0, 0, 0)
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun weightedControl() = LinearLayout.LayoutParams(0, dp(54), 1f)

    private fun textView(value: String, size: Float) = TextView(service).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(dp(8), dp(6), dp(8), dp(6))
    }

    private fun roundedBackground(color: Int) = GradientDrawable().apply {
        setColor(color)
        setStroke(dp(3), Color.rgb(0, 174, 189))
        cornerRadius = dp(18).toFloat()
    }

    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).roundToInt()

    @Suppress("DEPRECATION")
    private fun windowSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
    } else {
        Point().also { windowManager.defaultDisplay.getRealSize(it) }.let { it.x to it.y }
    }

    companion object {
        private const val TAG = "VueConfortMagnifier"
        private const val QUALITY_INTERVAL_MS = 400L
        private const val BALANCED_INTERVAL_MS = 450L
        private const val BATTERY_INTERVAL_MS = 1_000L
        private const val ADAPTIVE_STEP_MS = 10L
        private const val BACKOFF_STEP_MS = 50L
        private const val SUCCESS_WINDOW = 600
        private const val MIN_DELAY_MS = 20L
    }
}
