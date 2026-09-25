package fr.vueconfort.app.nativevision

import android.content.Context

internal object VariantNativeVisionFactory {
    fun hasLabAccess(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, capabilities: () -> NativeVisionCapabilities): NativeVisionLabGateway? = null
}
