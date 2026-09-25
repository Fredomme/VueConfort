package fr.vueconfort.app.nativevision

import android.content.Context
import android.os.Build

/** Collect on resume and before each command. No hidden SystemProperties, shell, or cross-user reads. */
class NativeVisionEnvironment(private val context: Context) {
    fun snapshot(variant: NativeVisionVariant, labPermissionGranted: Boolean = false): NativeVisionRuntimeSnapshot {
        val samsung = SamsungNativeVisionAdapter(context)
        val android = AndroidNativeVisionAdapter()
        val magnification = android.readMagnificationState()
        val connected = android.isControllerConnected()
        val attested = samsung.isAttestedS25
        val labGranted = variant == NativeVisionVariant.LAB && labPermissionGranted
        val observations = NativeVisionCapability.entries.associateWith { capability ->
            if (capability == NativeVisionCapability.MAGNIFICATION) {
                NativeVisionCapabilityObservation(
                    presence = if (Build.VERSION.SDK_INT >= 24) NativeVisionPresence.PRESENT else NativeVisionPresence.ABSENT,
                    provenance = "API publique MagnificationController du service VueConfort existant ; activation exacte à partir d’Android 14",
                    readable = magnification != null,
                    settingsRouteAvailable = samsung.guidanceIntent(capability) != null,
                    publicApiAvailable = Build.VERSION.SDK_INT >= 34 && (!connected || magnification?.restorable == true),
                    publicPermissionGranted = connected,
                    publicPermissionRequestable = !connected,
                )
            } else {
                val readback = samsung.read(capability)
                val specificRoute = samsung.specificSettingsRouteAvailable(capability)
                val standardPublicDisplay = capability in setOf(NativeVisionCapability.SYSTEM_BRIGHTNESS,
                    NativeVisionCapability.FONT_SCALE, NativeVisionCapability.SCREEN_ZOOM)
                val samsungOnly = SamsungNativeVisionAdapter.isSamsungOnly(capability)
                val present = when {
                    samsungOnly && !samsung.isSamsung -> NativeVisionPresence.ABSENT
                    samsung.hasPresenceEvidence(capability, readback) -> NativeVisionPresence.PRESENT
                    else -> NativeVisionPresence.UNKNOWN
                }
                NativeVisionCapabilityObservation(
                    presence = present,
                    provenance = buildString {
                        append(readback.provenance)
                        if (attested) append(" ; SM-S931B / API 36 / S931BXXSCCZH1 attestés le 24/09/2026")
                        if (specificRoute) append(" ; activité dédiée système exportée et autorisée vérifiée")
                        if (standardPublicDisplay) append(" ; réglage d’affichage Android, guidage utilisateur uniquement")
                    },
                    readable = readback.readable,
                    settingsRouteAvailable = samsung.guidanceIntent(capability) != null,
                    // No WRITE_SETTINGS setter is implemented in this increment.
                    publicApiAvailable = false,
                    // Relumino was verified through an ordinary app UID with the lab grant.
                    // Extra Dim's earlier proof used shell; its @Readable restriction prevents
                    // a trustworthy APK snapshot/rollback here even with that write grant.
                    labCommandAttested = attested && capability == NativeVisionCapability.RELUMINO,
                    labPermissionGranted = labGranted,
                )
            }
        }
        return NativeVisionRuntimeSnapshot(
            device = NativeVisionDevice(
                manufacturer = Build.MANUFACTURER, model = Build.MODEL, sdkInt = Build.VERSION.SDK_INT,
                // The version is from the exact-build audit, not a reflection call or a brand guess.
                oneUiVersion = if (attested) "80500 (firmware attesté le 24/09/2026)" else null,
                buildFingerprint = Build.FINGERPRINT, androidUserId = null,
            ), variant = variant, observedAtMillis = System.currentTimeMillis(), observations = observations,
        )
    }
}
