package fr.vueconfort.app.magnifier

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager

/** Variant boundary for the screenshot-based R&D prototype. */
internal interface ExperimentalMagnifierBridge {
    val isAvailable: Boolean
    val modeLabel: String
    val nativeModeLabel: String
    val prototypeModeLabel: String

    fun selectNativeMagnifier()
    fun selectPrototype()
    fun onConfigurationChanged()
    fun release()
}

internal object ExperimentalMagnifierBridgeFactory {
    fun create(
        service: AccessibilityService,
        windowManager: WindowManager,
        enableNativeMagnifier: () -> Unit,
        disableNativeMagnifier: () -> Unit,
        preparePrototypeOverlay: () -> Unit,
        restoreOfficialOverlay: () -> Unit
    ): ExperimentalMagnifierBridge = VariantExperimentalMagnifierBridgeFactory.create(
        service = service,
        windowManager = windowManager,
        enableNativeMagnifier = enableNativeMagnifier,
        disableNativeMagnifier = disableNativeMagnifier,
        preparePrototypeOverlay = preparePrototypeOverlay,
        restoreOfficialOverlay = restoreOfficialOverlay
    )
}
