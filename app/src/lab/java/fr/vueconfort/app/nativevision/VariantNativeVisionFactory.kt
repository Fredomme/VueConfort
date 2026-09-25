package fr.vueconfort.app.nativevision

import android.content.Context

internal object VariantNativeVisionFactory {
    fun hasLabAccess(context: Context): Boolean = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun create(context: Context, capabilities: () -> NativeVisionCapabilities): NativeVisionLabGateway {
        val adapter = SamsungNativeVisionLabAdapter(context, capabilities)
        return object : NativeVisionLabGateway {
            override fun apply(request: NativeVisionRequestedState, profileRevision: Long, isCurrent: () -> Boolean) =
                adapter.apply(request, profileRevision, isCurrent)
            override fun restore(profileRevision: Long, request: NativeVisionRequestedState) =
                adapter.restorePreviousState(profileRevision, request)
            override fun keepCurrentState() = adapter.keepCurrentState()
            override fun hasPendingRestoration() = adapter.hasPendingRestoration()
        }
    }
}
