package fr.vueconfort.app.mediaprojection

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal class GpuMagnifierOverlay(
    private val service: AccessibilityService,
    private val onSurfaceReady: (SurfaceTexture, Int, Int) -> Unit,
    private val onClose: () -> Unit,
    private val onPauseChanged: (Boolean) -> Unit,
    private val onZoomChanged: (Float) -> Unit,
    private val onMoveSource: (Float, Float) -> Unit,
    private val onRecenter: () -> Unit
) {
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val preferences = service.getSharedPreferences("gpu_magnifier_experiment", 0)
    private var root: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var paused = false
    private var locked = true
    private var zoom = preferences.getFloat("zoom", 2f).coerceIn(1.25f, 5f)
    private var windowFraction = preferences.getFloat("window_fraction", 0.5f).coerceIn(0.2f, 0.75f)
    private var initialHeight = 0
    private lateinit var scaleDetector: ScaleGestureDetector

    fun show() {
        val (screenWidth, screenHeight) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            @Suppress("DEPRECATION")
            service.resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        val container = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                setStroke(dp(3), Color.rgb(0, 174, 189))
                cornerRadius = dp(18).toFloat()
            }
        }
        val texture = TextureView(service).apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) =
                    onSurfaceReady(surface, width, height)
                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
            }
        }
        container.addView(texture, FrameLayout.LayoutParams(-1, -1))

        val controls = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xD0102028.toInt())
        }
        val zoomLabel = text("${formatZoom(zoom)}×")
        val lockButton = button("Déverrouiller") {
            locked = !locked
            text = if (locked) "Déverrouiller" else "Verrouiller"
        }
        controls.addView(button("−") { zoom = (zoom - 0.25f).coerceAtLeast(1.25f); zoomLabel.text = "${formatZoom(zoom)}×"; save(); onZoomChanged(zoom) }, weight())
        controls.addView(zoomLabel, weight())
        controls.addView(button("+") { zoom = (zoom + 0.25f).coerceAtMost(5f); zoomLabel.text = "${formatZoom(zoom)}×"; save(); onZoomChanged(zoom) }, weight())
        controls.addView(button("Pause") { paused = !paused; text = if (paused) "Reprendre" else "Pause"; onPauseChanged(paused) }, weight())
        controls.addView(lockButton, weight(1.3f))
        controls.addView(button("Fermer") { onClose() }, weight())
        container.addView(controls, FrameLayout.LayoutParams(-1, dp(54), Gravity.TOP))

        val sourceControls = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xA0102028.toInt())
            addView(text("Loupe VueConfort"), weight(1.5f))
            addView(button("←") { onMoveSource(-0.05f, 0f) }, weight())
            addView(button("↑") { onMoveSource(0f, -0.04f) }, weight())
            addView(button("◎") { onRecenter() }, weight())
            addView(button("↓") { onMoveSource(0f, 0.04f) }, weight())
            addView(button("→") { onMoveSource(0.05f, 0f) }, weight())
        }
        container.addView(sourceControls, FrameLayout.LayoutParams(-1, dp(46), Gravity.BOTTOM))

        scaleDetector = ScaleGestureDetector(service, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                initialHeight = params?.height ?: return false
                return !locked
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (locked) return false
                val proposed = (initialHeight * detector.scaleFactor).roundToInt()
                val min = (screenHeight * 0.2f).roundToInt()
                val max = (screenHeight * 0.75f).roundToInt()
                params?.height = proposed.coerceIn(min, max)
                windowFraction = (params!!.height.toFloat() / screenHeight).coerceIn(0.2f, 0.75f)
                windowManager.updateViewLayout(container, params)
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) = save()
        })
        container.setOnTouchListener { _, event -> !locked && scaleDetector.onTouchEvent(event) }

        params = WindowManager.LayoutParams(
            screenWidth,
            (screenHeight * windowFraction).roundToInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        root = container
        windowManager.addView(container, params)
        onZoomChanged(zoom)
    }

    fun close() {
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        params = null
    }

    private fun save() = preferences.edit().putFloat("zoom", zoom).putFloat("window_fraction", windowFraction).apply()
    private fun button(label: String, action: Button.() -> Unit) = Button(service).apply {
        text = label; textSize = 9f; minWidth = 0; setPadding(0, 0, 0, 0); isAllCaps = false
        setOnClickListener { action() }
    }
    private fun text(label: String) = TextView(service).apply {
        text = label; textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
    }
    private fun weight(value: Float = 1f) = LinearLayout.LayoutParams(0, dp(54), value)
    private fun dp(value: Int) = (value * service.resources.displayMetrics.density).roundToInt()
    private fun formatZoom(value: Float) = ((value * 100).roundToInt() / 100f).toString()
}
