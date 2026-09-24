package fr.vueconfort.vision

import android.app.*
import android.content.*
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.*
import android.media.Image
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors

/** Independent pixel-owning magnifier. It never binds to or configures the other VueConfort apps. */
class VisionService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var stopped = false
    @Volatile private var revision = 0L
    private var busy = false
    private var pendingAdjustment = false
    private var cacheReady = false
    private var paused = false
    private var comparing = false
    private var evidence = false
    private var freshAfter = 0L
    private var sequence = 0L
    private var opticalScreenWidth = 0
    private var opticalScreenHeight = 0
    private lateinit var wm: WindowManager
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var root: LinearLayout? = null
    private var marker: View? = null
    private lateinit var heading: TextView
    private lateinit var status: TextView
    private lateinit var raster: PixelView
    private lateinit var compareButton: Button
    private lateinit var pauseButton: Button
    private lateinit var opticalButton: Button
    private val knobs = mutableListOf<VisionRotaryControl>()
    private var original: Bitmap? = null
    private var adjusted: Bitmap? = null
    private var sourceTop = 0
    private var sourceBottom = 0
    private var centerX = 0.0
    private var centerY = 0.0
    private var outputWidth = 0
    private var outputHeight = 0
    private var geometryReady = false
    private var zoom = 2.5
    private var brightness = 0.0
    private var contrast = 1.0
    private var contours = 0.0
    // These two buffers are exclusively owned by the serial worker.
    private var cachedPixels: IntArray? = null
    private var cachedCrop: PixelRect? = null
    private var lastEvidenceRevision = -1L
    private val notificationId = 2421
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { end() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width != opticalScreenWidth || height != opticalScreenHeight) end("Écran modifié : relancez la loupe en portrait.")
        }
    }
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) { if (id == 0) end() }
        override fun onDisplayChanged(id: Int) { if (id == 0 && !geometryValid()) end("Orientation modifiée : relancez la loupe.") }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { end(); return START_NOT_STICKY }
        if (projection != null || stopped) return START_NOT_STICKY
        val consent = intent?.getParcelableExtra("consent", Intent::class.java)
        if (consent == null || !Settings.canDrawOverlays(this)) { end(); return START_NOT_STICKY }
        wm = getSystemService(WindowManager::class.java)
        val bounds = wm.maximumWindowMetrics.bounds
        opticalScreenWidth = bounds.width(); opticalScreenHeight = bounds.height()
        if (opticalScreenHeight <= opticalScreenWidth || !geometryValid()) {
            end("Démarrez VueConfort Vision en portrait."); return START_NOT_STICKY
        }
        evidence = intent.getBooleanExtra("technical_evidence", false)
        val prefs = getSharedPreferences("vision-controls", MODE_PRIVATE)
        zoom = prefs.getFloat("zoom", 2.5f).toDouble().coerceIn(1.0, 6.0)
        brightness = prefs.getFloat("brightness", 0f).toDouble().coerceIn(-.25, .25)
        contrast = prefs.getFloat("contrast", 1f).toDouble().coerceIn(.5, 2.0)
        contours = prefs.getFloat("contours", 0f).toDouble().coerceIn(0.0, 2.0)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("vision-live", "Loupe VueConfort Vision", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, notificationId, Intent(this, VisionService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
        startForeground(notificationId, Notification.Builder(this, "vision-live").setSmallIcon(R.drawable.ic_vision)
            .setContentTitle("VueConfort Vision").setContentText("Loupe active · toucher pour fermer")
            .setContentIntent(stop).setOngoing(true).build())
        try {
            showOverlay()
            projection = getSystemService(MediaProjectionManager::class.java).getMediaProjection(Activity.RESULT_OK, consent)
            projection!!.registerCallback(callback, main)
            reader = ImageReader.newInstance(opticalScreenWidth, opticalScreenHeight, PixelFormat.RGBA_8888, 3)
            display = projection!!.createVirtualDisplay("VueConfortVision", opticalScreenWidth, opticalScreenHeight,
                resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, main)
            getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, main)
            main.post(pump)
        } catch (e: Exception) { end("Loupe indisponible : ${e.javaClass.simpleName}") }
        return START_NOT_STICKY
    }
    @Suppress("DEPRECATION")
    private fun geometryValid(): Boolean {
        if (!::wm.isInitialized) return false
        val bounds = wm.maximumWindowMetrics.bounds
        return wm.defaultDisplay.rotation == Surface.ROTATION_0 &&
            bounds.width() == opticalScreenWidth && bounds.height() == opticalScreenHeight
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun showOverlay() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        root = box
        outputWidth = opticalScreenWidth - dp(24)
        outputHeight = dp(144)
        heading = TextView(this).apply {
            textSize = 14f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(111, 53, 180))
            contentDescription = "Déplacer la zone source : glisser horizontalement ou verticalement"
        }
        var lastX = 0f; var lastY = 0f
        heading.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastX = e.rawX; lastY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    centerX += e.rawX - lastX; centerY += e.rawY - lastY
                    lastX = e.rawX; lastY = e.rawY
                    changedGeometry(); true
                }
                MotionEvent.ACTION_UP -> { heading.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
        box.addView(heading, LinearLayout.LayoutParams(-1, dp(24)))
        raster = PixelView(this)
        val field = FrameLayout(this).apply { setBackgroundColor(Color.rgb(225, 225, 229)) }
        field.addView(raster, FrameLayout.LayoutParams(outputWidth, outputHeight, Gravity.CENTER))
        box.addView(field, LinearLayout.LayoutParams(-1, outputHeight))
        status = TextView(this).apply { textSize = 12f; setTextColor(Color.BLACK); setPadding(dp(8), 0, dp(8), 0)
            text = "Glissez le bandeau violet pour viser. Faites défiler votre application dans la partie haute." }
        box.addView(status, LinearLayout.LayoutParams(-1, dp(40)))
        val controls = LinearLayout(this)
        box.addView(controls, LinearLayout.LayoutParams(-1, dp(106)))
        fun knob(label: String, low: Double, high: Double, value: Double, format: (Double) -> String, change: (Double) -> Unit) {
            val view = VisionRotaryControl(this, label, low, high, value, format) { change(it) }
            controls.addView(view, LinearLayout.LayoutParams(0, -1, 1f)); knobs.add(view)
        }
        knob("Zoom", 1.0, 6.0, zoom, { "×%.2f".format(Locale.FRANCE, it) }) { zoom = it; changedGeometry(); saveSettings() }
        knob("Lumière", -.25, .25, brightness, { "%+.0f %%".format(Locale.FRANCE, it * 100) }) { brightness = it; changedTone() }
        knob("Contraste", .5, 2.0, contrast, { "×%.2f".format(Locale.FRANCE, it) }) { contrast = it; changedTone() }
        knob("Contours", 0.0, 2.0, contours, { "%.0f %%".format(Locale.FRANCE, it * 100) }) { contours = it; changedTone() }
        fun row() = LinearLayout(this).also { box.addView(it, LinearLayout.LayoutParams(-1, dp(48))) }
        fun button(row: LinearLayout, text: String, action: () -> Unit) = Button(this).apply {
            this.text = text; textSize = 12f; isAllCaps = false; setPadding(dp(2), 0, dp(2), 0)
            setOnClickListener { action() }; row.addView(this, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val a = row()
        compareButton = button(a, "Voir original") { comparing = !comparing; present() }.apply { isEnabled = false }
        pauseButton = button(a, "Pause") { paused = !paused; updateTitle() }.apply { isEnabled = false }
        button(a, "Effet fort") { brightness = 0.0; contrast = 1.7; contours = 1.3; syncKnobs(); changedTone() }
        val b = row()
        button(b, "Neutre") { brightness = 0.0; contrast = 1.0; contours = 0.0; syncKnobs(); changedTone() }
        button(b, "Centrer") { centerX = opticalScreenWidth / 2.0; centerY = (sourceTop + sourceBottom) / 2.0; changedGeometry() }
        button(b, "Fermer") { end() }
        opticalButton = button(row(), "Essai optique sur cette image") { openOpticalExperiment() }.apply { isEnabled = false }
        val insets = wm.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        val panelHeight = dp(24 + 144 + 40 + 106 + 48 + 48 + 48)
        val y = opticalScreenHeight - insets.bottom - dp(8) - panelHeight
        val params = WindowManager.LayoutParams(-1, panelHeight, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; this.y = y; title = "VueConfort Vision pixels" }
        wm.addView(box, params)
        marker = object : View(this) {
            private val border = Paint().apply { color = Color.rgb(153, 61, 240); strokeWidth = 2f; style = Paint.Style.STROKE }
            override fun onDraw(canvas: Canvas) { canvas.drawRect(1f, 1f, width - 1f, height - 1f, border) }
        }
        wm.addView(marker, WindowManager.LayoutParams(1, 1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT; alpha = .6f; title = "VueConfort Vision source" })
        box.post {
            if (stopped) return@post
            val xy = IntArray(2); box.getLocationOnScreen(xy)
            sourceTop = insets.top + dp(8); sourceBottom = xy[1] - dp(8)
            if (sourceBottom - sourceTop < dp(80)) { end("Pas assez de place : utilisez le téléphone en portrait."); return@post }
            centerX = opticalScreenWidth / 2.0; centerY = (sourceTop + sourceBottom) / 2.0
            geometryReady = true; changedGeometry()
        }
        updateTitle()
    }
    private fun syncKnobs() {
        listOf(zoom, brightness, contrast, contours).forEachIndexed { i, value -> knobs[i].value = value }
    }
    private fun saveSettings() {
        getSharedPreferences("vision-controls", MODE_PRIVATE).edit().putFloat("zoom", zoom.toFloat())
            .putFloat("brightness", brightness.toFloat()).putFloat("contrast", contrast.toFloat())
            .putFloat("contours", contours.toFloat()).apply()
    }
    private fun changedTone() {
        comparing = false; revision++; saveSettings(); pendingAdjustment = true; requestCachedRender()
    }
    private fun sourceRect() = VisionGeometry.crop(opticalScreenWidth, sourceTop, sourceBottom, outputWidth, outputHeight, zoom, centerX, centerY)
    private fun changedGeometry() {
        if (!geometryReady || stopped) return
        revision++; cacheReady = false; pendingAdjustment = false; paused = false; comparing = false
        pauseButton.isEnabled = false; compareButton.isEnabled = false; opticalButton.isEnabled = false
        status.text = "Mise à jour du cadrage… Le zoom conserve vos autres réglages."
        freshAfter = SystemClock.elapsedRealtime() + 150
        val crop = sourceRect()
        centerX = (crop.left + crop.right) / 2.0; centerY = (crop.top + crop.bottom) / 2.0
        marker?.let {
            val params = it.layoutParams as WindowManager.LayoutParams
            params.x = crop.left - 4; params.y = crop.top - 4; params.width = crop.width + 8; params.height = crop.height + 8
            wm.updateViewLayout(it, params)
        }
        updateTitle()
    }
    private fun updateTitle() {
        heading.text = "↔ VueConfort Vision · ${if (paused) "Pause" else "Direct"}"
        if (::pauseButton.isInitialized) pauseButton.text = if (paused) "Reprendre" else "Pause"
    }
    private data class Request(val revision: Long, val crop: PixelRect, val brightness: Double, val contrast: Double,
        val contours: Double, val zoom: Double, val sequence: Long, val x: Int, val y: Int)
    private fun request(): Request? {
        if (!geometryReady || stopped || !geometryValid()) return null
        val crop = sourceRect(); val xy = IntArray(2); root!!.getLocationOnScreen(xy)
        val panel = PixelRect(xy[0], xy[1], xy[0] + root!!.width, xy[1] + root!!.height)
        if (VisionGeometry.overlaps(crop, panel, 8)) { end("La source recouvre l’aperçu : capture arrêtée."); return null }
        val imageXY = IntArray(2); raster.getLocationOnScreen(imageXY)
        return Request(revision, crop, brightness, contrast, contours, zoom, ++sequence, imageXY[0], imageXY[1])
    }
    private val pump = object : Runnable {
        override fun run() {
            if (stopped) return
            reader?.let { onImage(it) }
            if (!stopped) main.postDelayed(this, 120)
        }
    }
    private fun onImage(source: ImageReader) {
        val now = SystemClock.elapsedRealtime()
        // Keep the newest queued frame through processing and marker settling.
        if (stopped || busy || now < freshAfter) return
        val image = runCatching { source.acquireLatestImage() }.getOrNull() ?: return
        if (paused) { image.close(); return }
        val r = request()
        if (r == null) { image.close(); return }
        if (image.width != opticalScreenWidth || image.height != opticalScreenHeight) { image.close(); end("Capture redimensionnée."); return }
        busy = true; pendingAdjustment = false
        worker.execute { render(r, image) }
    }
    private fun requestCachedRender() {
        if (stopped || busy || !cacheReady || !pendingAdjustment) return
        val r = request() ?: return
        pendingAdjustment = false; busy = true
        worker.execute { render(r, null) }
    }
    private fun capturePixels(image: Image, crop: PixelRect): IntArray {
        val plane = image.planes[0]; require(plane.pixelStride >= 4)
        val buffer = plane.buffer.duplicate()
        val row = ByteArray(crop.width * plane.pixelStride)
        val raw = IntArray(crop.width * crop.height)
        for (y in 0 until crop.height) {
            buffer.position((crop.top + y) * plane.rowStride + crop.left * plane.pixelStride)
            buffer.get(row)
            for (x in 0 until crop.width) {
                val i = x * plane.pixelStride
                raw[y * crop.width + x] = (255 shl 24) or ((row[i].toInt() and 255) shl 16) or
                    ((row[i + 1].toInt() and 255) shl 8) or (row[i + 2].toInt() and 255)
            }
        }
        val small = Bitmap.createBitmap(raw, crop.width, crop.height, Bitmap.Config.ARGB_8888)
        val finalSize = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        try {
            Canvas(finalSize).drawBitmap(small, Rect(0, 0, crop.width, crop.height), Rect(0, 0, outputWidth, outputHeight),
                Paint().apply { isFilterBitmap = true })
            return IntArray(outputWidth * outputHeight).also { finalSize.getPixels(it, 0, outputWidth, 0, 0, outputWidth, outputHeight) }
        } finally { small.recycle(); finalSize.recycle() }
    }
    private fun render(r: Request, image: Image?) {
        var originalResult: Bitmap? = null; var treatedResult: Bitmap? = null
        try {
            val began = SystemClock.elapsedRealtimeNanos()
            if (image != null) { cachedPixels = capturePixels(image, r.crop); cachedCrop = r.crop }
            val pixels = cachedPixels ?: error("Source unavailable")
            require(cachedCrop == r.crop) { "Source changed" }
            val processed = VisionPixelRenderer.apply(pixels, outputWidth, outputHeight, r.brightness, r.contrast, r.contours)
            originalResult = bitmap(pixels); treatedResult = bitmap(processed)
            val elapsed = (SystemClock.elapsedRealtimeNanos() - began) / 1e6
            val raw = originalResult; val treated = treatedResult
            if (evidence && !stopped && revision == r.revision && (lastEvidenceRevision != r.revision || r.sequence % 10L == 0L)) {
                lastEvidenceRevision = r.revision
                val folder = File(filesDir, "technical-evidence").apply { mkdirs() }
                File(folder, "original.png").outputStream().use { raw.compress(Bitmap.CompressFormat.PNG, 100, it) }
                File(folder, "adjusted.png").outputStream().use { treated.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val record = JSONObject().put("kind", "VISION_PIXEL_TEST_NOT_VISION_CORRECTION").put("sequence", r.sequence)
                    .put("revision", r.revision).put("zoom", r.zoom).put("brightness", r.brightness).put("contrast", r.contrast).put("contours", r.contours)
                    .put("source", "${r.crop.left},${r.crop.top},${r.crop.right},${r.crop.bottom}")
                    .put("width", outputWidth).put("height", outputHeight).put("screenX", r.x).put("screenY", r.y)
                    .put("renderMs", elapsed).put("inputHash", hash(pixels)).put("outputHash", hash(processed))
                    .put("resizeBeforeFilters", true).put("postFilterScale", 1).put("personalOpticalCorrection", false)
                File(folder, "report.json").writeText(record.toString(2))
            }
            main.post {
                busy = false
                if (stopped || revision != r.revision) {
                    raw.recycle(); treated.recycle()
                    if (!stopped) requestCachedRender()
                } else {
                    cacheReady = true; pauseButton.isEnabled = true; compareButton.isEnabled = true
                    opticalButton.isEnabled = outputWidth >= OpticalExperiment.WIDTH && outputHeight >= OpticalExperiment.HEIGHT
                    val beforeRaw = original; val beforeTreated = adjusted
                    original = raw; adjusted = treated; present()
                    beforeRaw?.recycle(); beforeTreated?.recycle()
                    requestCachedRender()
                }
            }
            originalResult = null; treatedResult = null
        } catch (e: Exception) {
            originalResult?.recycle(); treatedResult?.recycle()
            main.post { busy = false; if (!stopped) { status.text = "Image indisponible. Revenez à un contenu capturable."; requestCachedRender() } }
        } finally { image?.close() }
    }
    private fun bitmap(pixels: IntArray) = Bitmap.createBitmap(pixels, outputWidth, outputHeight, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
    private fun openOpticalExperiment() {
        if (stopped || !cacheReady) return
        val source = original ?: return
        val width = OpticalExperiment.WIDTH; val height = OpticalExperiment.HEIGHT
        if (source.width < width || source.height < height) return
        // Copy the neutral final-size raster, before tone/contour filters. Never stretch optical output.
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, (source.width - width) / 2, (source.height - height) / 2, width, height)
        OpticalSnapshot.put(pixels)
        try {
            startActivity(Intent(this, OpticalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("technical_evidence", evidence))
            end()
        } catch (_: RuntimeException) {
            OpticalSnapshot.clear()
            status.text = "Essai indisponible. Ouvrez-le depuis l’accueil de VueConfort Vision."
        }
    }
    private fun hash(pixels: IntArray): String {
        val bytes = java.nio.ByteBuffer.allocate(pixels.size * 4); pixels.forEach { bytes.putInt(it) }
        return MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    }
    private fun present() {
        raster.image = if (comparing) original else adjusted
        raster.invalidate()
        compareButton.text = if (comparing) "Voir réglages" else "Voir original"
        status.text = if (comparing) "Original au même zoom · Vos réglages sont conservés."
            else "Glissez le bandeau violet pour viser. Lumière, contraste et contours se combinent."
    }
    private inner class PixelView(context: Context) : View(context) {
        var image: Bitmap? = null
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(Color.rgb(225, 225, 229))
            val b = image ?: return
            if (width != outputWidth || height != outputHeight) { post { end("Dimensions d’affichage modifiées.") }; return }
            canvas.drawBitmap(b, 0f, 0f, paint)
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!geometryValid()) end("Orientation modifiée : relancez la loupe.")
    }
    private fun end(message: String? = null) {
        if (stopped) return
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        cleanup(); stopSelf()
    }
    private fun cleanup() {
        if (stopped) return
        stopped = true; revision++; main.removeCallbacks(pump)
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener)
        if (::wm.isInitialized) {
            root?.let { runCatching { wm.removeViewImmediate(it) } }
            marker?.let { runCatching { wm.removeViewImmediate(it) } }
        }
        root = null; marker = null
        reader?.setOnImageAvailableListener(null, null)
        display?.release(); display = null
        projection?.let { it.unregisterCallback(callback); runCatching { it.stop() } }; projection = null
        val toClose = reader; reader = null
        // Close the reader after the worker releases any acquired image.
        worker.execute { cachedPixels = null; cachedCrop = null; toClose?.close() }
        worker.shutdown()
        original?.recycle(); adjusted?.recycle(); original = null; adjusted = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onDestroy() { cleanup(); super.onDestroy() }
}
