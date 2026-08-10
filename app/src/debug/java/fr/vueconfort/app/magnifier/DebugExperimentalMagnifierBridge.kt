package fr.vueconfort.app.magnifier

import android.accessibilityservice.AccessibilityService
import android.view.WindowManager
import fr.vueconfort.app.R
import fr.vueconfort.app.custommagnifier.VueConfortMagnifierController

internal object VariantExperimentalMagnifierBridgeFactory {
    fun create(
        service: AccessibilityService,
        windowManager: WindowManager,
        enableNativeMagnifier: () -> Unit,
        disableNativeMagnifier: () -> Unit,
        preparePrototypeOverlay: () -> Unit,
        restoreOfficialOverlay: () -> Unit
    ): ExperimentalMagnifierBridge = DebugExperimentalMagnifierBridge(
        service = service,
        windowManager = windowManager,
        enableNativeMagnifier = enableNativeMagnifier,
        disableNativeMagnifier = disableNativeMagnifier,
        preparePrototypeOverlay = preparePrototypeOverlay,
        restoreOfficialOverlay = restoreOfficialOverlay
    )
}

private class DebugExperimentalMagnifierBridge(
    private val service: AccessibilityService,
    private val windowManager: WindowManager,
    private val enableNativeMagnifier: () -> Unit,
    private val disableNativeMagnifier: () -> Unit,
    private val preparePrototypeOverlay: () -> Unit,
    private val restoreOfficialOverlay: () -> Unit
) : ExperimentalMagnifierBridge {
    private var controller: VueConfortMagnifierController? = null

    override val isAvailable = true
    override val modeLabel: String
        get() = service.getString(R.string.custom_magnifier_mode)
    override val nativeModeLabel: String
        get() = service.getString(R.string.custom_magnifier_android)
    override val prototypeModeLabel: String
        get() = service.getString(R.string.custom_magnifier_name)

    override fun selectNativeMagnifier() {
        controller?.close()
        controller = null
        enableNativeMagnifier()
    }

    override fun selectPrototype() {
        disableNativeMagnifier()
        preparePrototypeOverlay()
        controller = VueConfortMagnifierController(
            service = service,
            windowManager = windowManager,
            onClosed = {
                controller = null
                restoreOfficialOverlay()
            }
        ).also { it.requestStart() }
    }

    override fun onConfigurationChanged() {
        controller?.onConfigurationChanged()
    }

    override fun release() {
        controller?.close()
        controller = null
    }
}
