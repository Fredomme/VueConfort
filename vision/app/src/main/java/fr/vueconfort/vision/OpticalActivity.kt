package fr.vueconfort.vision

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/** Static, uncalibrated comparison at a fixed native pixel size. */
class OpticalActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val destroyed = AtomicBoolean(false)
    private var cancellation = AtomicBoolean(false)
    @Volatile private var generation = 0
    private lateinit var field: OpticalField
    private lateinit var status: TextView
    private lateinit var distance: Spinner
    private lateinit var hypothesis: Spinner
    private lateinit var unmagnified: CheckBox
    private lateinit var calculate: Button
    private lateinit var originalButton: Button
    private lateinit var treatedButton: Button
    private val ratings = mutableListOf<Button>()
    private lateinit var source: IntArray
    private var baseline: Bitmap? = null
    private var candidate: Bitmap? = null
    private var running = false
    private var resultReady = false
    private var treated = false
    private var geometryError: String? = null
    private var technicalEvidence = false
    private var currentDistanceMm = 0.0
    private var currentHypothesis = 0
    private var inputSource = "synthetic"
    private var evidenceRecord: JSONObject? = null
    private data class RetainedSource(val pixels: IntArray, val inputSource: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        technicalEvidence = intent.getBooleanExtra("technical_evidence", false)
        @Suppress("DEPRECATION")
        val retained = lastNonConfigurationInstance as? RetainedSource
        val captured = if (retained == null) OpticalSnapshot.take() else null
        inputSource = retained?.inputSource ?: if (captured != null) "capture" else "synthetic"
        source = retained?.pixels ?: captured ?: syntheticText()
        baseline = bitmap(grayscale(source))

        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(GRAY)
        }
        shell.setOnApplyWindowInsetsListener { v, insets ->
            val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        setContentView(shell)
        shell.requestApplyInsets()
        shell.addView(label("Essai optique · VueConfort Vision", 19f, true).apply {
            setPadding(dp(10), dp(8), dp(10), dp(6))
        })
        field = OpticalField(this).apply { image = this@OpticalActivity.baseline }
        shell.addView(field, LinearLayout.LayoutParams(-1, FIELD_HEIGHT))

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(248, 246, 252)) }
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(16))
        }
        scroll.addView(controls, android.widget.FrameLayout.LayoutParams(-1, -2))
        controls.addView(label(if (inputSource == "synthetic")
            "Texte d’essai à contraste modéré, à taille réelle. Gardez la même distance pour comparer."
            else "Image capturée figée, à taille réelle. Gardez la même distance pour comparer.", 16f))
        controls.addView(label("Hypothèses expérimentales · aucun diagnostic ni correction personnelle.", 14f))

        val selections = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(selections, LinearLayout.LayoutParams(-1, -2))
        distance = spinner(listOf("Choisir la distance", "30 cm", "40 cm", "50 cm", "60 cm"))
        hypothesis = spinner(listOf("Effet A", "Effet B", "Effet C"))
        selections.addView(column("Distance des yeux", distance), LinearLayout.LayoutParams(0, -2, 1f))
        selections.addView(column("Hypothèse de calcul", hypothesis), LinearLayout.LayoutParams(0, -2, 1f))
        unmagnified = CheckBox(this).apply {
            text = "Agrandissement du système désactivé"
            textSize = 15f
            minHeight = dp(48)
            setTextColor(TEXT)
            setOnCheckedChangeListener { _, _ -> invalidateResult() }
        }
        controls.addView(unmagnified)
        controls.addView(label("Désactivez l’agrandissement du système pour cet essai. Pupille supposée : 3 mm.", 13f))

        calculate = button("Calculer l’effet") { if (running) cancelCalculation() else calculateEffect() }
        controls.addView(calculate)
        val comparison = LinearLayout(this)
        controls.addView(comparison, LinearLayout.LayoutParams(-1, -2))
        originalButton = button("Original") { show(false) }
        treatedButton = button("Traité") { show(true) }
        comparison.addView(originalButton, LinearLayout.LayoutParams(0, -2, 1f))
        comparison.addView(treatedButton, LinearLayout.LayoutParams(0, -2, 1f))
        val ratingRow = LinearLayout(this)
        controls.addView(ratingRow, LinearLayout.LayoutParams(-1, -2))
        listOf("Mieux" to "better", "Pareil" to "same", "Moins bien" to "worse").forEach { (title, key) ->
            val b = button(title) { recordPreference(key, title) }
            ratings.add(b)
            ratingRow.addView(b, LinearLayout.LayoutParams(0, -2, 1f))
        }
        status = label("Choisissez la distance, puis calculez une hypothèse.", 15f).apply {
            setPadding(0, dp(8), 0, dp(8))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        controls.addView(status)
        controls.addView(button("Revenir à Vision") {
            startActivity(Intent(this, VisionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        })
        val changed = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { invalidateResult() }
            override fun onNothingSelected(parent: AdapterView<*>?) { invalidateResult() }
        }
        distance.onItemSelectedListener = changed
        hypothesis.onItemSelectedListener = changed
        updateButtons()
        field.post { checkGeometry() }
    }

    private fun spinner(items: List<String>) = Spinner(this).apply {
        adapter = ArrayAdapter(this@OpticalActivity, android.R.layout.simple_spinner_item, items).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        minimumHeight = dp(48)
        setSelection(0)
    }

    private fun column(title: String, view: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(label(title, 13f).apply { setPadding(0, dp(8), 0, 0) })
        addView(view, LinearLayout.LayoutParams(-1, -2))
    }

    @Suppress("DEPRECATION")
    private fun checkGeometry(): Boolean {
        val manager = getSystemService(WindowManager::class.java)
        val display = manager.defaultDisplay
        val bounds = manager.maximumWindowMetrics.bounds
        geometryError = when {
            !Build.MODEL.startsWith("SM-S931") -> "Cet essai optique est prévu pour le Galaxy S25. La taille physique des pixels doit être vérifiée sur les autres modèles."
            display.rotation != Surface.ROTATION_0 || bounds.width() != 1080 || bounds.height() != 2340 ||
                display.mode.physicalWidth != 1080 || display.mode.physicalHeight != 2340 ->
                "Utilisez le S25 en portrait, avec son affichage natif 1080 × 2340, puis rouvrez cet essai."
            field.width < FIELD_WIDTH || field.height != FIELD_HEIGHT ->
                "Cet affichage est trop petit pour conserver l’image et sa marge grise à taille réelle. Aucun agrandissement automatique n’est appliqué."
            !field.isUnitScale -> "L’affichage est redimensionné. Revenez à la taille système normale avant cet essai."
            else -> null
        }
        if (geometryError != null) {
            cancelCalculation(announce = false)
            resultReady = false
            invalidateEvidence("DISPLAY_GEOMETRY_CHANGED")
            status.text = geometryError
        }
        updateButtons()
        return geometryError == null
    }

    private fun invalidateResult() {
        if (!::status.isInitialized) return
        cancelCalculation(announce = false)
        resultReady = false
        treated = false
        field.image = baseline
        field.invalidate()
        invalidateEvidence("SELECTION_CHANGED")
        status.text = geometryError ?: "Choisissez la distance, puis calculez une hypothèse."
        updateButtons()
    }

    private fun calculateEffect() {
        if (!checkGeometry()) return
        if (distance.selectedItemPosition == 0) {
            status.text = "Choisissez d’abord votre distance de lecture : 30, 40, 50 ou 60 cm."
            return
        }
        if (!unmagnified.isChecked) {
            status.text = "Désactivez l’agrandissement du système, puis cochez la confirmation pour conserver la taille réelle des pixels."
            return
        }
        currentDistanceMm = doubleArrayOf(300.0, 400.0, 500.0, 600.0)[distance.selectedItemPosition - 1]
        currentHypothesis = hypothesis.selectedItemPosition
        val distanceMm = currentDistanceMm
        val hypothesisIndex = currentHypothesis
        val optical = OpticalHypothesis(doubleArrayOf(.15, .25, .40)[hypothesisIndex], 0.0, 0.0)
        cancellation = AtomicBoolean(false)
        val localCancellation = cancellation
        val request = ++generation
        running = true
        resultReady = false
        invalidateEvidence("CALCULATING_NEW_RESULT")
        show(false)
        status.text = "Calcul sur l’image figée… Cela peut prendre un moment. Vous pouvez annuler."
        updateButtons()
        worker.execute {
            try {
                val result = OpticalExperiment.process(source.copyOf(), distanceMm, optical,
                    onProgress = { progress -> main.post {
                        if (!destroyed.get() && generation == request) status.text = progress
                    } },
                    isCancelled = { destroyed.get() || localCancellation.get() || generation != request })
                if (destroyed.get() || localCancellation.get() || generation != request) return@execute
                require(result.baseline.size == WIDTH * HEIGHT && result.candidate.size == WIDTH * HEIGHT)
                val raw = bitmap(result.baseline)
                val processed = bitmap(result.candidate)
                main.post {
                    if (destroyed.get() || localCancellation.get() || generation != request) {
                        raw.recycle(); processed.recycle()
                        return@post
                    }
                    running = false
                    field.image = null
                    baseline?.recycle(); candidate?.recycle()
                    baseline = raw; candidate = processed
                    resultReady = result.applied
                    show(result.applied)
                    status.text = if (result.applied) "Effet calculé. Comparez Original et Traité à la même distance. ${result.explanation}"
                        else "L’effet n’a pas été appliqué : ${result.explanation} L’original est conservé."
                    if (technicalEvidence) saveEvidence(result, distanceMm, hypothesisIndex, request)
                    updateButtons()
                }
            } catch (e: Exception) {
                main.post {
                    if (!destroyed.get() && generation == request) {
                        running = false
                        resultReady = false
                        status.text = if (localCancellation.get()) "Calcul annulé."
                            else "Le calcul n’a pas abouti. L’image originale est conservée."
                        show(false)
                    }
                }
            }
        }
    }

    private fun cancelCalculation(announce: Boolean = true) {
        cancellation.set(true)
        generation++
        if (running) {
            running = false
            invalidateEvidence("CALCULATION_CANCELLED")
            if (announce && ::status.isInitialized) status.text = "Calcul annulé."
        }
        if (::calculate.isInitialized) updateButtons()
    }

    private fun show(showTreated: Boolean) {
        treated = showTreated && resultReady
        field.image = if (treated) candidate else baseline
        field.contentDescription = if (treated) "Image traitée à taille réelle" else "Image originale grise à taille réelle"
        field.invalidate()
        updateButtons()
        updateEvidenceDisplay()
    }

    private fun updateButtons() {
        if (!::calculate.isInitialized) return
        calculate.text = if (running) "Annuler le calcul" else "Calculer l’effet"
        calculate.isEnabled = geometryError == null
        distance.isEnabled = !running
        hypothesis.isEnabled = !running
        unmagnified.isEnabled = !running
        originalButton.isEnabled = !running
        treatedButton.isEnabled = !running && resultReady
        originalButton.text = if (!treated) "Original ✓" else "Original"
        treatedButton.text = if (treated) "Traité ✓" else "Traité"
        ratings.forEach { it.isEnabled = !running && resultReady }
    }

    private fun recordPreference(key: String, title: String) {
        if (!resultReady || running) return
        getSharedPreferences("optical-comparison", MODE_PRIVATE).edit()
            .putString("last_preference", key)
            .putFloat("distance_mm", currentDistanceMm.toFloat())
            .putInt("hypothesis", currentHypothesis)
            .putLong("recorded_at", System.currentTimeMillis()).apply()
        status.text = "Votre avis « $title » est enregistré sur ce téléphone. Il ne définit pas une correction de vue."
    }

    private fun saveEvidence(result: OpticalResult, distanceMm: Double, hypothesisIndex: Int, request: Int) {
        val xy = IntArray(2)
        field.getLocationOnScreen(xy)
        val left = xy[0] + (field.width - WIDTH) / 2
        val top = xy[1] + (field.height - HEIGHT) / 2
        val metadata = JSONObject().put("kind", "STATIC_OPTICAL_EXPERIMENT_NOT_PERSONAL_CORRECTION")
            .put("valid", true).put("status", "COMPLETED")
            .put("applied", result.applied).put("explanation", result.explanation)
            .put("distanceMm", distanceMm).put("hypothesis", hypothesisIndex)
            .put("selectedDistanceMm", distanceMm).put("selectedHypothesis", "ABC"[hypothesisIndex].toString())
            .put("inputsource", inputSource).put("displayed", if (treated) "candidate" else "original")
            .put("metrics", JSONObject(result.metrics)).put("report", JSONObject(result.report))
            .put("sphere", doubleArrayOf(.15, .25, .40)[hypothesisIndex]).put("cylinder", 0.0)
            .put("pupilMmAssumed", 3.0).put("screenX", left).put("screenY", top)
            .put("width", WIDTH).put("height", HEIGHT).put("postFilterScale", 1)
            .put("fieldLeft", xy[0]).put("fieldTop", xy[1]).put("fieldWidth", field.width).put("fieldHeight", field.height)
            .put("systemMagnificationOffConfirmed", unmagnified.isChecked)
        evidenceRecord = metadata
        val metadataJson = metadata.toString(2)
        val originalPixels = result.baseline.copyOf()
        val candidatePixels = result.candidate.copyOf()
        worker.execute {
            if (destroyed.get() || generation != request) return@execute
            runCatching {
                val directory = File(filesDir, "optical-evidence").apply { mkdirs() }
                val before = bitmap(originalPixels)
                val after = bitmap(candidatePixels)
                try {
                    File(directory, "original.png").outputStream().use { before.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(directory, "candidate.png").outputStream().use { after.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(directory, "report.json").writeText(metadataJson)
                } finally { before.recycle(); after.recycle() }
            }
        }
    }

    private fun updateEvidenceDisplay() {
        if (!technicalEvidence || destroyed.get()) return
        val record = evidenceRecord ?: return
        record.put("displayed", if (geometryError != null) "unavailable" else if (treated) "candidate" else "original")
        val json = record.toString(2)
        val request = generation
        worker.execute {
            if (!destroyed.get() && generation == request) runCatching {
                File(filesDir, "optical-evidence/report.json").writeText(json)
            }
        }
    }

    /** A saved candidate remains an historical image once its comparison is invalidated. */
    private fun invalidateEvidence(reason: String) {
        if (!technicalEvidence || destroyed.get()) return
        val previous = evidenceRecord ?: return
        val history = if (previous.optBoolean("valid", true)) {
            JSONObject(previous.toString()).put("historical", true)
        } else previous.optJSONObject("previousResult")
        evidenceRecord = JSONObject().put("kind", "STATIC_OPTICAL_EXPERIMENT_NOT_PERSONAL_CORRECTION")
            .put("valid", false).put("status", "INVALIDATED").put("invalidationReason", reason)
            .put("applied", false).put("candidateAvailable", false).put("imagesAreHistorical", true)
            .put("inputsource", inputSource).put("previousResult", history)
            .put("selectedDistanceMm", if (distance.selectedItemPosition > 0)
                doubleArrayOf(300.0, 400.0, 500.0, 600.0)[distance.selectedItemPosition - 1] else JSONObject.NULL)
            .put("selectedHypothesis", "ABC"[hypothesis.selectedItemPosition].toString())
        treated = false
        updateEvidenceDisplay()
    }

    private inner class OpticalField(context: Context) : View(context) {
        var image: Bitmap? = null
        var isUnitScale = true
            private set
        private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false; isDither = false }
        private val transform = FloatArray(9)
        @Suppress("DEPRECATION")
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(GRAY)
            canvas.matrix.getValues(transform)
            isUnitScale = abs(transform[Matrix.MSCALE_X] - 1f) < .00001f && abs(transform[Matrix.MSCALE_Y] - 1f) < .00001f &&
                abs(transform[Matrix.MSKEW_X]) < .00001f && abs(transform[Matrix.MSKEW_Y]) < .00001f
            if (width < FIELD_WIDTH || height != FIELD_HEIGHT || !isUnitScale) {
                post { if (!destroyed.get()) checkGeometry() }
                return
            }
            image?.let { canvas.drawBitmap(it, ((width - WIDTH) / 2).toFloat(), ((height - HEIGHT) / 2).toFloat(), paint) }
        }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { if (!destroyed.get()) checkGeometry() }
        }
    }

    private fun label(text: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(TEXT)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 16f
        isAllCaps = false
        minHeight = dp(48)
        setPadding(dp(4), dp(4), dp(4), dp(4))
        backgroundTintList = ColorStateList.valueOf(Color.rgb(232, 221, 248))
        setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(Color.rgb(120, 116, 125), Color.rgb(101, 52, 177))))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setOnClickListener { action() }
    }

    private fun bitmap(pixels: IntArray) = Bitmap.createBitmap(pixels, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply {
        density = Bitmap.DENSITY_NONE
    }

    private fun grayscale(pixels: IntArray): IntArray {
        fun decode(value: Int): Double {
            val x = value / 255.0
            return if (x <= .04045) x / 12.92 else ((x + .055) / 1.055).pow(2.4)
        }
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val linear = .2126 * decode((p ushr 16) and 255) + .7152 * decode((p ushr 8) and 255) + .0722 * decode(p and 255)
            val encoded = if (linear <= .0031308) 12.92 * linear else 1.055 * linear.pow(1.0 / 2.4) - .055
            val c = (encoded * 255.0).roundToInt().coerceIn(0, 255)
            Color.rgb(c, c, c)
        }
    }

    private fun syntheticText(): IntArray {
        val b = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888).apply { density = Bitmap.DENSITY_NONE }
        try {
            val canvas = Canvas(b)
            canvas.drawColor(Color.rgb(211, 211, 211))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(160, 160, 160)
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                isSubpixelText = false
            }
            p.textSize = 38f
            canvas.drawText("Lire cette phrase à votre rythme.", 24f, 65f, p)
            p.textSize = 32f
            canvas.drawText("Des contours, des détails, du texte.", 24f, 135f, p)
            p.textSize = 28f
            canvas.drawText("Comparer sans changer la distance.", 24f, 202f, p)
            return IntArray(WIDTH * HEIGHT).also { b.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT) }
        } finally { b.recycle() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    @Deprecated("Retains only an in-memory image across configuration changes")
    override fun onRetainNonConfigurationInstance(): Any? =
        if (::source.isInitialized) RetainedSource(source.copyOf(), inputSource) else null

    override fun onDestroy() {
        destroyed.set(true)
        cancellation.set(true)
        generation++
        worker.shutdownNow()
        if (::field.isInitialized) field.image = null
        baseline?.recycle(); candidate?.recycle()
        baseline = null; candidate = null
        super.onDestroy()
    }

    companion object {
        private const val WIDTH = 768
        private const val HEIGHT = 256
        private const val FIELD_WIDTH = WIDTH + 256
        private const val FIELD_HEIGHT = HEIGHT + 256
        private val GRAY = Color.rgb(188, 188, 188)
        private val TEXT = Color.rgb(37, 30, 48)
    }
}
