package fr.vueconfort.app.magnifier

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager

internal object VariantExperimentalMagnifierBridgeFactory {
    fun create(
        service: AccessibilityService,
        windowManager: WindowManager,
        enableNativeMagnifier: () -> Unit,
        disableNativeMagnifier: () -> Unit,
        preparePrototypeOverlay: () -> Unit,
        restoreOfficialOverlay: () -> Unit
    ): ExperimentalMagnifierBridge = ReleaseExperimentalMagnifierBridge
}

private object ReleaseExperimentalMagnifierBridge : ExperimentalMagnifierBridge {
    override val isAvailable = false
    override val modeLabel = ""
    override val nativeModeLabel = ""
    override val prototypeModeLabel = ""

    override fun selectNativeMagnifier() = Unit
    override fun selectPrototype() = Unit
    override fun onConfigurationChanged() = Unit
    override fun release() = Unit
}
