package fr.vueconfort.app.magnifier

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.MagnificationConfig
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.R
import fr.vueconfort.app.core.CoreActionReceiver
import fr.vueconfort.app.core.ErrorCategory
import fr.vueconfort.app.core.ErrorReporter
import fr.vueconfort.app.core.VueConfortCoreState
import fr.vueconfort.app.custommagnifier.VueConfortMagnifierController
import fr.vueconfort.app.data.OverlayPreferences
import fr.vueconfort.app.data.VisualProfileRepository
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.AmbientLightLevel
import fr.vueconfort.app.model.AutomationRule
import fr.vueconfort.app.model.AutomationStatus
import fr.vueconfort.app.model.AutomationTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import java.util.Calendar

class ScreenMagnifierService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var repository: VisualProfileRepository
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, throwable ->
            ErrorReporter.from(ErrorCategory.UNKNOWN, "service_coroutine", throwable)
            VueConfortCoreState.error("service_coroutine", throwable)
        }
    )

    private var controlView: View? = null
    private var panelView: View? = null
    private var readerView: View? = null
    private var customMagnifierController: VueConfortMagnifierController? = null
    private var controlParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var readerParams: WindowManager.LayoutParams? = null
    private var lastExtractedPackage = ""
    private var lastExtractedText = ""
    private var corePaused = false

    private var preferences = OverlayPreferences()
    private var activeProfile: AssistProfile = AssistProfile.defaults().first()
    private var magnificationEnabled = false
    private var releasing = false
    private var profileLabelView: TextView? = null
    private var stateLabelView: TextView? = null
    private var panelRoot: LinearLayout? = null
    private var sourceLabelView: TextView? = null
    private var automationRules: List<AutomationRule> = emptyList()
    private var automationStatus = AutomationStatus()
    private var availableProfileIds: Set<String> = emptySet()
    private var foregroundPackage = ""
    private var lightLevel: AmbientLightLevel? = null
    private var appChangeJob: Job? = null
    private var lightChangeJob: Job? = null
    private var timeJob: Job? = null
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var sensorRegistered = false
    private val lightListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val candidate = classifyLight(event.values.firstOrNull() ?: return)
            if (candidate == lightLevel) return
            lightChangeJob?.cancel()
            lightChangeJob = serviceScope.launch {
                delay(2_000)
                lightLevel = candidate
                evaluateAutomation()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        releasing = false
        VueConfortCoreState.serviceActive = true
        VueConfortCoreState.extractionAvailable = true
        VueConfortCoreState.log("service", "connecté")
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        repository = VisualProfileRepository(applicationContext)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        serviceScope.launch {
            repository.ensureAssistProfilesMigrated()
            preferences = repository.overlayPreferences.first()
            activeProfile = repository.activeAssistProfile.first()
            preferences = preferences.copy(
                activeProfileId = activeProfile.id,
                magnificationScale = activeProfile.magnificationScale,
                panelAlpha = activeProfile.overlayAlpha,
                locked = activeProfile.locked,
                expanded = activeProfile.expanded
            )
            if (preferences.expanded) showControlPanel() else showFloatingButton()
            applyMagnificationState(activeProfile)
            showPersistentNotification()
            repository.activeAssistProfile.collectLatest { selected ->
                if (selected != activeProfile) {
                    activeProfile = selected
                    applyAssistProfile(selected)
                    showPersistentNotification()
                }
            }
        }
        serviceScope.launch {
            combine(
                repository.automationRules,
                repository.automationStatus,
                repository.assistProfiles
            ) { rules, status, profiles -> Triple(rules, status, profiles) }
                .collectLatest { (rules, status, profiles) ->
                    automationRules = rules.filter { it.profileId in profiles.map { p -> p.id } }
                    automationStatus = status
                    VueConfortCoreState.automaticProfilesActive =
                        automationRules.any { it.enabled } && !status.manualPaused
                    availableProfileIds = profiles.map { it.id }.toSet()
                    updateSensorRegistration()
                    scheduleTimeEvaluation()
                    sourceLabelView?.text = automationSourceText()
                    evaluateAutomation()
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            lastExtractedPackage = ""
        }
        val packageName = event?.packageName?.toString().orEmpty()
        if (
            packageName.isBlank() ||
            packageName == this.packageName ||
            packageName == foregroundPackage
        ) return
        foregroundPackage = packageName
        appChangeJob?.cancel()
        appChangeJob = serviceScope.launch {
            delay(650)
            evaluateAutomation()
        }
    }

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        customMagnifierController?.onConfigurationChanged()
        controlView?.post { clampAndUpdate(controlView, controlParams, save = true) }
        panelView?.post { clampAndUpdate(panelView, panelParams, save = true) }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        releaseServiceResources()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        releaseServiceResources()
        serviceScope.cancel()
        instance = null
        VueConfortCoreState.serviceActive = false
        VueConfortCoreState.extractionAvailable = false
        cancelNotification()
        super.onDestroy()
    }

    private fun showFloatingButton() {
        if (releasing || controlView != null) return

        val button = TextView(this).apply {
            text = getString(R.string.magnifier_short_label)
            textSize = 18f
            gravity = Gravity.CENTER
            contentDescription = getString(R.string.magnifier_panel_title)
            setTextColor(0xFFFFFFFF.toInt())
            background = roundedBackground(0xE6143A5A.toInt(), 40f)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { expandPanel() }
        }
        val params = overlayLayoutParams(dp(60), dp(60)).apply {
            x = preferences.buttonX
            y = preferences.buttonY
        }
        makeDraggable(button, params, locked = { false }) {
            preferences = preferences.copy(buttonX = params.x, buttonY = params.y)
            persistPreferences()
        }

        addOverlay(
            button,
            params,
            onAdded = {
                controlView = button
                controlParams = params
                button.post { clampAndUpdate(button, params, save = true) }
            }
        )
    }

    private fun expandPanel() {
        preferences = preferences.copy(expanded = true)
        persistPreferences()
        removeControl()
        showControlPanel()
    }

    private fun showControlPanel() {
        if (releasing || panelView != null) return

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            alpha = preferences.panelAlpha
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = roundedBackground(0xFA1E1E1E.toInt(), 18f)
        }
        panelRoot = panel

        val title = label(getString(R.string.magnifier_panel_title), 19f)
        title.setOnLongClickListener {
            preferences = preferences.copy(locked = !preferences.locked)
            persistPreferences()
            Toast.makeText(
                this,
                if (preferences.locked) "Barre verrouillée" else "Barre déverrouillée",
                Toast.LENGTH_SHORT
            ).show()
            true
        }
        val stateLabel = label(
            if (magnificationEnabled) "Effet actif" else "Effet inactif",
            16f
        )
        stateLabelView = stateLabel
        val scaleLabel = label("Intensité ${scaleText()}", 16f)
        val profileLabel = label("Profil : ${activeProfile.name}", 15f).apply {
            background = roundedBackground(0xFF143A5A.toInt(), 10f)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
        profileLabelView = profileLabel
        val sourceLabel = label(automationSourceText(), 13f)
        sourceLabelView = sourceLabel
        val profileList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        profileLabel.setOnClickListener {
            if (profileList.visibility == View.VISIBLE) {
                profileList.visibility = View.GONE
            } else {
                serviceScope.launch {
                    val profiles = repository.assistProfiles.first()
                    profileList.removeAllViews()
                    profiles.forEach { candidate ->
                        profileList.addView(
                            createButton(
                                if (candidate.id == activeProfile.id) {
                                    "✓ ${candidate.name}"
                                } else candidate.name
                            ) {
                                profileList.visibility = View.GONE
                                serviceScope.launch {
                                    repository.activateAssistProfile(candidate.id)
                                }
                            },
                            panelButtonLayout()
                        )
                    }
                    profileList.visibility = View.VISIBLE
                }
            }
        }

        val toggleButton = createButton(
            if (magnificationEnabled) {
                getString(R.string.magnifier_disable)
            } else {
                getString(R.string.magnifier_enable)
            }
        ) {
            if (magnificationEnabled) disableMagnification() else enableMagnification()
            text = if (magnificationEnabled) {
                getString(R.string.magnifier_disable)
            } else {
                getString(R.string.magnifier_enable)
            }
            stateLabel.text = if (magnificationEnabled) "Effet actif" else "Effet inactif"
        }

        val scaleBar = SeekBar(this).apply {
            max = 14
            progress = ((preferences.magnificationScale - 1f) * 2f).roundToInt()
            contentDescription = "Intensité globale du grossissement"
            setOnSeekBarChangeListener(simpleSeekListener { progressValue, fromUser ->
                if (!fromUser) return@simpleSeekListener
                preferences = preferences.copy(
                    magnificationScale = (1f + progressValue / 2f).coerceIn(1f, 8f)
                )
                scaleLabel.text = "Intensité ${scaleText()}"
                if (magnificationEnabled) enableMagnification()
                persistPreferences()
            })
        }

        val alphaLabel = label(
            "Transparence ${(preferences.panelAlpha * 100).roundToInt()} %",
            15f
        )
        val alphaBar = SeekBar(this).apply {
            max = 45
            progress = ((preferences.panelAlpha - 0.55f) * 100f).roundToInt()
            contentDescription = "Transparence du panneau"
            setOnSeekBarChangeListener(simpleSeekListener { progressValue, fromUser ->
                if (!fromUser) return@simpleSeekListener
                val alphaValue = (0.55f + progressValue / 100f).coerceIn(0.55f, 1f)
                preferences = preferences.copy(panelAlpha = alphaValue)
                panel.alpha = alphaValue
                alphaLabel.text = "Transparence ${(alphaValue * 100).roundToInt()} %"
                persistPreferences()
            })
        }

        val lockButton = createButton(
            if (preferences.locked) "Déverrouiller le panneau" else "Verrouiller le panneau"
        ) {
            preferences = preferences.copy(locked = !preferences.locked)
            text = if (preferences.locked) {
                "Déverrouiller le panneau"
            } else {
                "Verrouiller le panneau"
            }
            persistPreferences()
        }
        val reduceButton = createButton("Réduire") { reducePanel() }
        val stopButton = createButton("Arrêter VueConfort") {
            releaseServiceResources()
            disableSelf()
        }

        panel.addView(title, matchWidthLayout())
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(createButton("−") { changeMagnification(-0.5f) }, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(createButton("+") { changeMagnification(0.5f) }, LinearLayout.LayoutParams(0, dp(54), 1f))
            panel.addView(this, panelButtonLayout())
        }
        panel.addView(createButton("Lire") { showReader() }, panelButtonLayout())
        if (BuildConfig.DEBUG) {
            panel.addView(label(getString(R.string.custom_magnifier_mode), 14f), matchWidthLayout())
            panel.addView(
                createButton(getString(R.string.custom_magnifier_android)) {
                    customMagnifierController?.close()
                    customMagnifierController = null
                    enableMagnification()
                },
                panelButtonLayout()
            )
            panel.addView(
                createButton(getString(R.string.custom_magnifier_name)) {
                    disableMagnification()
                    removePanel()
                    customMagnifierController = VueConfortMagnifierController(
                        service = this@ScreenMagnifierService,
                        windowManager = windowManager,
                        onClosed = {
                            customMagnifierController = null
                            if (!releasing && controlView == null && panelView == null) showFloatingButton()
                        }
                    ).also { prototype ->
                        prototype.requestStart()
                    }
                },
                panelButtonLayout()
            )
        }
        panel.addView(profileLabel, matchWidthLayout())
        panel.addView(profileList, matchWidthLayout())
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(createButton("Recentrer") { recenterMagnification() }, LinearLayout.LayoutParams(0, dp(54), 1f))
            addView(createButton("Réinitialiser") { resetMagnification() }, LinearLayout.LayoutParams(0, dp(54), 1f))
            panel.addView(this, panelButtonLayout())
        }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(reduceButton, LinearLayout.LayoutParams(0, dp(54), 1f))
            panel.addView(this, panelButtonLayout())
        }

        val params = overlayLayoutParams(dp(310), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            x = preferences.panelX
            y = preferences.panelY
        }
        makeDraggable(panel, params, locked = { preferences.locked }) {
            preferences = preferences.copy(panelX = params.x, panelY = params.y)
            persistPreferences()
        }

        addOverlay(
            panel,
            params,
            onAdded = {
                panelView = panel
                panelParams = params
                panel.post { clampAndUpdate(panel, params, save = true) }
            },
            onFailure = {
                preferences = preferences.copy(expanded = false)
                persistPreferences()
                showFloatingButton()
            }
        )
    }

    private fun reducePanel() {
        preferences = preferences.copy(expanded = false)
        persistPreferences()
        removePanel()
        showFloatingButton()
    }

    private fun enableMagnification() {
        val scale = preferences.magnificationScale
        val bounds = windowBounds()
        val success = runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val builder = MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_WINDOW)
                .setScale(scale)
                .setCenterX(bounds.first / 2f)
                .setCenterY(bounds.second / 2f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                builder.setActivated(true)
            }
            magnificationController.setMagnificationConfig(builder.build(), true)
        } else {
            @Suppress("DEPRECATION")
            magnificationController.setScale(scale, true)
        } }.getOrElse {
            ErrorReporter.from(ErrorCategory.MAGNIFICATION_UNAVAILABLE, "magnification_enable", it)
            VueConfortCoreState.error("magnification_enable", it)
            false
        }
        magnificationEnabled = success
        VueConfortCoreState.magnificationActive = success
        showPersistentNotification()
        if (!success) {
            Toast.makeText(this, getString(R.string.magnifier_failure), Toast.LENGTH_LONG).show()
        }
    }

    private fun changeMagnification(delta: Float) {
        preferences = preferences.copy(
            magnificationScale = (preferences.magnificationScale + delta).coerceIn(1f, 8f)
        )
        if (preferences.magnificationScale <= 1f) disableMagnification() else enableMagnification()
        persistPreferences()
        VueConfortCoreState.log("zoom", scaleText())
    }

    private fun recenterMagnification() {
        if (!magnificationEnabled) return
        enableMagnification()
        VueConfortCoreState.log("zoom", "recentré")
    }

    private fun resetMagnification() {
        preferences = preferences.copy(magnificationScale = 1f)
        persistPreferences()
        disableMagnification()
        VueConfortCoreState.log("zoom", "réinitialisé")
    }

    private fun applyAssistProfile(profile: AssistProfile) {
        preferences = preferences.copy(
            activeProfileId = profile.id,
            magnificationScale = profile.magnificationScale,
            panelAlpha = profile.overlayAlpha,
            locked = profile.locked,
            expanded = profile.expanded
        )
        panelView?.alpha = profile.overlayAlpha
        profileLabelView?.text = "Profil : ${profile.name}"
        applyMagnificationState(profile)

        if (profile.expanded && panelView == null) {
            removeControl()
            showControlPanel()
        } else if (!profile.expanded && controlView == null) {
            removePanel()
            showFloatingButton()
        }
    }

    private fun applyMagnificationState(profile: AssistProfile) {
        if (profile.magnificationEnabled) enableMagnification() else disableMagnification()
        stateLabelView?.text = if (magnificationEnabled) "Effet actif" else "Effet inactif"
    }

    private fun disableMagnification() {
        runCatching { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val builder = MagnificationConfig.Builder()
                .setMode(MagnificationConfig.MAGNIFICATION_MODE_WINDOW)
                .setScale(1f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                builder.setActivated(false)
            }
            magnificationController.setMagnificationConfig(builder.build(), true)
        } else {
            @Suppress("DEPRECATION")
            magnificationController.reset(true)
        } }.onFailure {
            ErrorReporter.from(ErrorCategory.MAGNIFICATION_UNAVAILABLE, "magnification_reset", it)
            VueConfortCoreState.error("magnification_reset", it)
        }
        magnificationEnabled = false
        VueConfortCoreState.magnificationActive = false
        showPersistentNotification()
    }

    private fun releaseServiceResources() {
        if (releasing) return
        releasing = true
        appChangeJob?.cancel()
        lightChangeJob?.cancel()
        timeJob?.cancel()
        if (::sensorManager.isInitialized && sensorRegistered) {
            sensorManager.unregisterListener(lightListener)
            sensorRegistered = false
        }
        removePanel()
        removeControl()
        removeReader()
        customMagnifierController?.close()
        customMagnifierController = null
        VueConfortCoreState.overlayActive = false
        if (::windowManager.isInitialized) runCatching { disableMagnification() }
    }

    private fun addOverlay(
        view: View,
        params: WindowManager.LayoutParams,
        onAdded: () -> Unit,
        onFailure: () -> Unit = {}
    ) {
        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                onAdded()
                VueConfortCoreState.overlayActive = true
                VueConfortCoreState.log("overlay", "affiché")
            }
            .onFailure {
                ErrorReporter.from(ErrorCategory.OVERLAY_UNAVAILABLE, "overlay_add", it)
                VueConfortCoreState.error("overlay", it)
                onFailure()
            }
    }

    private fun removeControl() {
        controlView?.let { view ->
            if (view.isAttachedToWindow) runCatching { windowManager.removeView(view) }
                .onFailure { ErrorReporter.from(ErrorCategory.OVERLAY_UNAVAILABLE, "overlay_remove_control", it) }
        }
        controlView = null
        controlParams = null
    }

    private fun removePanel() {
        panelView?.let { view ->
            if (view.isAttachedToWindow) runCatching { windowManager.removeView(view) }
                .onFailure { ErrorReporter.from(ErrorCategory.OVERLAY_UNAVAILABLE, "overlay_remove_panel", it) }
        }
        panelView = null
        panelParams = null
        panelRoot = null
        profileLabelView = null
        stateLabelView = null
        sourceLabelView = null
    }

    private fun showReader() {
        if (readerView != null || releasing) return
        val extracted = extractReadableText()
        val content = extracted.ifBlank {
            "Cette application ne fournit pas de texte accessible sur la fenêtre actuelle."
        }
        var textSize = 20f
        var extraSpacing = 8f
        var letterSpacing = 0f
        var dark = false
        val textView = TextView(this).apply {
            text = content
            setTextSize(textSize)
            setTextColor(0xFF111111.toInt())
            setPadding(dp(16), dp(16), dp(16), dp(16))
            setLineSpacing(extraSpacing, 1.15f)
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(textView) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            alpha = activeProfile.overlayAlpha.coerceIn(0.75f, 1f)
            background = roundedBackground(0xFFF8F5ED.toInt(), 18f)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        fun applyColors(highContrast: Boolean = false) {
            val background = when {
                highContrast -> 0xFF000000.toInt()
                dark -> 0xFF171717.toInt()
                else -> 0xFFF8F5ED.toInt()
            }
            val foreground = when {
                highContrast -> 0xFFFFFF00.toInt()
                dark -> 0xFFF5F5F5.toInt()
                else -> 0xFF111111.toInt()
            }
            root.background = roundedBackground(background, 18f)
            textView.setTextColor(foreground)
        }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(createButton("A+") { textSize = (textSize + 2f).coerceAtMost(38f); textView.textSize = textSize }, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(createButton("A−") { textSize = (textSize - 2f).coerceAtLeast(14f); textView.textSize = textSize }, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(createButton("Interligne") { extraSpacing = if (extraSpacing < 14f) extraSpacing + 3f else 5f; textView.setLineSpacing(extraSpacing, 1.15f) }, LinearLayout.LayoutParams(0, dp(50), 1.5f))
            addView(createButton("Espacement") { letterSpacing = if (letterSpacing < 0.08f) letterSpacing + 0.02f else 0f; textView.letterSpacing = letterSpacing }, LinearLayout.LayoutParams(0, dp(50), 1.5f))
            root.addView(this, matchWidthLayout())
        }
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(createButton("Fond clair") { dark = false; applyColors() }, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(createButton("Fond sombre") { dark = true; applyColors() }, LinearLayout.LayoutParams(0, dp(50), 1f))
            addView(createButton("Contraste") { applyColors(true) }, LinearLayout.LayoutParams(0, dp(50), 1f))
            root.addView(this, matchWidthLayout())
        }
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(createButton("Fermer") { removeReader() }, panelButtonLayout())
        val params = overlayLayoutParams(dp(350), dp(560)).apply { x = dp(12); y = dp(80) }
        makeDraggable(root, params, locked = { false }) {}
        addOverlay(root, params, onAdded = {
            readerView = root
            readerParams = params
            VueConfortCoreState.readerActive = true
            VueConfortCoreState.log("lecture", "ouverte")
        })
    }

    private fun extractReadableText(): String {
        val root = runCatching { rootInActiveWindow }.getOrElse {
            ErrorReporter.from(ErrorCategory.EXTRACTION_IMPOSSIBLE, "root_security", it)
            return ""
        } ?: run {
            ErrorReporter.from(ErrorCategory.EXTRACTION_IMPOSSIBLE, "root_unavailable")
            return ""
        }
        val currentPackage = root.packageName?.toString().orEmpty()
        if (currentPackage == lastExtractedPackage && lastExtractedText.isNotBlank()) {
            return lastExtractedText
        }
        val values = LinkedHashSet<String>()
        var visited = 0
        fun visit(node: AccessibilityNodeInfo?) {
            if (node == null || visited++ >= 350 || values.sumOf { it.length } >= 20_000) return
            listOf(node.text, node.contentDescription).forEach { raw ->
                raw?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let(values::add)
            }
            for (index in 0 until node.childCount) visit(node.getChild(index))
        }
        visit(root)
        lastExtractedPackage = currentPackage
        lastExtractedText = values.joinToString("\n")
        VueConfortCoreState.log("lecture", if (values.isEmpty()) "texte indisponible" else "texte extrait")
        return lastExtractedText
    }

    private fun removeReader() {
        readerView?.let { view ->
            if (view.isAttachedToWindow) runCatching { windowManager.removeView(view) }
                .onFailure { ErrorReporter.from(ErrorCategory.OVERLAY_UNAVAILABLE, "overlay_remove_reader", it) }
        }
        readerView = null
        readerParams = null
        VueConfortCoreState.readerActive = false
    }

    private fun showPersistentNotification() {
        if (releasing) return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "État VueConfort", NotificationManager.IMPORTANCE_LOW)
            )
        }
        fun actionIntent(action: String, requestCode: Int) = PendingIntent.getBroadcast(
            this, requestCode,
            Intent(this, CoreActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("VueConfort actif")
            .setContentText("${activeProfile.name} · ${scaleText()}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_view, "Lire", actionIntent(ACTION_READ, 1)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, if (corePaused) "Reprendre" else "Pause", actionIntent(ACTION_PAUSE, 2)).build())
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Fermer", actionIntent(ACTION_CLOSE, 3)).build())
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onFailure { VueConfortCoreState.error("notification", it) }
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    private fun handleAction(action: String) {
        when (action) {
            ACTION_READ -> showReader()

            ACTION_MAGNIFIER_ENABLE -> {
                corePaused = false

                if (
                    controlView == null &&
                    panelView == null
                ) {
                    showFloatingButton()
                }

                enableMagnification()
                showPersistentNotification()
            }

            ACTION_PAUSE, ACTION_TOGGLE -> {
                corePaused = !corePaused
                if (corePaused) {
                    removePanel(); removeControl(); removeReader(); disableMagnification()
                } else {
                    preferences = preferences.copy(expanded = false)
                    showFloatingButton()
                    applyMagnificationState(activeProfile)
                }
                showPersistentNotification()
            }
            ACTION_CLOSE -> {
                releaseServiceResources()
                disableSelf()
            }
        }
    }

    private fun makeDraggable(
        view: View,
        params: WindowManager.LayoutParams,
        locked: () -> Boolean,
        onDragFinished: () -> Unit
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        view.setOnTouchListener { touchedView, event ->
            if (locked()) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > dp(5) || abs(dy) > dp(5)) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    clampPosition(touchedView, params)
                    runCatching { windowManager.updateViewLayout(touchedView, params) }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (moved) onDragFinished() else if (event.actionMasked == MotionEvent.ACTION_UP) {
                        touchedView.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun clampAndUpdate(
        view: View?,
        params: WindowManager.LayoutParams?,
        save: Boolean
    ) {
        if (view == null || params == null || !view.isAttachedToWindow) return
        clampPosition(view, params)
        runCatching { windowManager.updateViewLayout(view, params) }
        if (save) {
            preferences = if (view === controlView) {
                preferences.copy(buttonX = params.x, buttonY = params.y)
            } else {
                preferences.copy(panelX = params.x, panelY = params.y)
            }
            persistPreferences()
        }
    }

    private fun clampPosition(view: View, params: WindowManager.LayoutParams) {
        val (screenWidth, screenHeight) = windowBounds()
        val width = view.width.takeIf { it > 0 } ?: params.width.coerceAtLeast(dp(60))
        val height = view.height.takeIf { it > 0 } ?: dp(60)
        val margin = dp(8)
        params.x = params.x.coerceIn(margin, (screenWidth - width - margin).coerceAtLeast(margin))
        params.y = params.y.coerceIn(margin, (screenHeight - height - margin).coerceAtLeast(margin))
    }

    private fun windowBounds(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels
        }
    }

    private fun evaluateAutomation() {
        if (releasing || automationStatus.manualPaused) return
        val now = Calendar.getInstance()
        val winner = automationRules.asSequence()
            .filter { it.enabled && it.profileId in availableProfileIds }
            .filter { ruleMatches(it, now) }
            .maxWithOrNull(
                compareBy<AutomationRule> { triggerWeight(it.trigger) + it.priority }
                    .thenBy { -it.createdAtMillis }
            ) ?: return

        val source = when (winner.trigger) {
            AutomationTrigger.APPLICATION -> "Application"
            AutomationTrigger.AMBIENT_LIGHT -> "Luminosité"
            AutomationTrigger.TIME_RANGE -> "Horaire"
        }
        val reason = when (winner.trigger) {
            AutomationTrigger.APPLICATION -> applicationLabel(winner.packageName)
            AutomationTrigger.AMBIENT_LIGHT -> when (winner.lightLevel) {
                AmbientLightLevel.LOW -> "Faible luminosité"
                AmbientLightLevel.MEDIUM -> "Luminosité moyenne"
                AmbientLightLevel.HIGH -> "Forte luminosité"
            }
            AutomationTrigger.TIME_RANGE -> winner.name
        }
        if (
            automationStatus.activeRuleId == winner.id &&
            automationStatus.profileId == winner.profileId &&
            automationStatus.source == source
        ) return
        serviceScope.launch {
            repository.applyAutomatedProfile(
                winner.profileId,
                source,
                reason,
                winner.id
            )
        }
    }

    private fun ruleMatches(rule: AutomationRule, now: Calendar): Boolean {
        return when (rule.trigger) {
            AutomationTrigger.APPLICATION ->
                rule.packageName.isNotBlank() && rule.packageName == foregroundPackage
            AutomationTrigger.AMBIENT_LIGHT -> lightLevel == rule.lightLevel
            AutomationTrigger.TIME_RANGE -> {
                val minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                var dayIndex = (now.get(Calendar.DAY_OF_WEEK) + 5) % 7
                if (rule.startMinutes > rule.endMinutes && minute < rule.endMinutes) {
                    dayIndex = (dayIndex + 6) % 7
                }
                if (rule.daysMask and (1 shl dayIndex) == 0) return false
                if (rule.startMinutes == rule.endMinutes) {
                    true
                } else if (rule.startMinutes < rule.endMinutes) {
                    minute in rule.startMinutes until rule.endMinutes
                } else {
                    minute >= rule.startMinutes || minute < rule.endMinutes
                }
            }
        }
    }

    private fun triggerWeight(trigger: AutomationTrigger) = when (trigger) {
        AutomationTrigger.APPLICATION -> 3_000
        AutomationTrigger.AMBIENT_LIGHT -> 2_000
        AutomationTrigger.TIME_RANGE -> 1_000
    }

    private fun updateSensorRegistration() {
        if (!::sensorManager.isInitialized) return
        val needed = automationRules.any {
            it.enabled && it.trigger == AutomationTrigger.AMBIENT_LIGHT
        }
        if (needed && !sensorRegistered && lightSensor != null) {
            sensorRegistered = sensorManager.registerListener(
                lightListener,
                lightSensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        } else if (!needed && sensorRegistered) {
            sensorManager.unregisterListener(lightListener)
            sensorRegistered = false
            lightLevel = null
        }
    }

    private fun classifyLight(lux: Float): AmbientLightLevel {
        return when (lightLevel) {
            AmbientLightLevel.LOW ->
                if (lux < 75f) AmbientLightLevel.LOW else if (lux > 1_150f) {
                    AmbientLightLevel.HIGH
                } else AmbientLightLevel.MEDIUM
            AmbientLightLevel.HIGH ->
                if (lux > 850f) AmbientLightLevel.HIGH else if (lux < 45f) {
                    AmbientLightLevel.LOW
                } else AmbientLightLevel.MEDIUM
            else -> when {
                lux < 50f -> AmbientLightLevel.LOW
                lux > 1_000f -> AmbientLightLevel.HIGH
                else -> AmbientLightLevel.MEDIUM
            }
        }
    }

    private fun scheduleTimeEvaluation() {
        timeJob?.cancel()
        if (automationRules.none { it.enabled && it.trigger == AutomationTrigger.TIME_RANGE }) return
        timeJob = serviceScope.launch {
            while (true) {
                val delayMillis = 60_000L - (System.currentTimeMillis() % 60_000L)
                delay(delayMillis)
                evaluateAutomation()
            }
        }
    }

    private fun applicationLabel(packageName: String): String =
        runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault("Application")

    private fun automationSourceText(): String {
        val reason = automationStatus.reason.takeIf { it.isNotBlank() }
        return if (reason == null) automationStatus.source else "${automationStatus.source} · $reason"
    }

    private fun persistPreferences() {
        if (!::repository.isInitialized) return
        val snapshot = preferences
        serviceScope.launch { repository.saveOverlayPreferences(snapshot) }
    }

    private fun overlayLayoutParams(width: Int, height: Int) =
        WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun createButton(label: String, action: Button.() -> Unit) =
        Button(this).apply {
            text = label
            textSize = 15f
            minHeight = dp(48)
            setOnClickListener { action() }
        }

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        gravity = Gravity.CENTER
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(4), dp(4), dp(4), dp(4))
    }

    private fun simpleSeekListener(onChanged: (Int, Boolean) -> Unit) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onChanged(progress, fromUser)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }

    private fun scaleText(): String {
        val rounded = (preferences.magnificationScale * 10f).roundToInt() / 10f
        return "${rounded}×"
    }

    private fun roundedBackground(color: Int, radiusDp: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp.toInt()).toFloat()
        }

    private fun panelButtonLayout() =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)).apply {
            topMargin = dp(6)
        }

    private fun matchWidthLayout() =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val ACTION_READ = "fr.vueconfort.app.action.READ"
        const val ACTION_PAUSE = "fr.vueconfort.app.action.PAUSE"
        const val ACTION_CLOSE = "fr.vueconfort.app.action.CLOSE"
        const val ACTION_TOGGLE = "fr.vueconfort.app.action.TOGGLE"
        const val ACTION_MAGNIFIER_ENABLE = "fr.vueconfort.app.action.MAGNIFIER_ENABLE"
        private const val CHANNEL_ID = "vueconfort_state"
        private const val NOTIFICATION_ID = 4107

        @Volatile private var instance: ScreenMagnifierService? = null

        fun handleExternalAction(action: String) {
            instance?.handleAction(action)
        }
    }
}
