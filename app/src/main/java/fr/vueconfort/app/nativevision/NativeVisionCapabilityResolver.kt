package fr.vueconfort.app.nativevision

/** Pure policy over freshly collected facts. Manufacturer alone never proves a capability. */
class NativeVisionCapabilityResolver {
    fun resolve(snapshot: NativeVisionRuntimeSnapshot): NativeVisionCapabilities {
        require(snapshot.device.sdkInt > 0 && snapshot.observedAtMillis >= 0)
        return NativeVisionCapabilities(
            device = snapshot.device, variant = snapshot.variant, observedAtMillis = snapshot.observedAtMillis,
            capabilities = NativeVisionCapability.entries.associateWith { capability -> resolveOne(capability, snapshot) }
        )
    }

    private fun resolveOne(capability: NativeVisionCapability, snapshot: NativeVisionRuntimeSnapshot): NativeVisionCapabilityState {
        val observation = snapshot.observations[capability]
        val presence = observation?.presence ?: NativeVisionPresence.UNKNOWN
        val provenance = observation?.provenance?.takeIf { it.isNotBlank() } ?: "NO_RUNTIME_EVIDENCE"
        fun state(availability: NativeVisionAvailability, reason: String,
                  automatic: Boolean = false, engine: NativeVisionEngine = NativeVisionEngine.NONE) =
            NativeVisionCapabilityState(capability, presence, availability, automatic,
                observation?.readable == true, engine, provenance, reason)
        val samsungOnly = capability in setOf(NativeVisionCapability.RELUMINO,
            NativeVisionCapability.COLOR_FILTER, NativeVisionCapability.EYE_COMFORT)
        if (samsungOnly && !snapshot.device.manufacturer.equals("samsung", ignoreCase = true)) {
            return state(NativeVisionAvailability.UNSUPPORTED_DEVICE, "Cette fonction Samsung ne concerne pas cet appareil.")
        }
        val minimumApi = when (capability) {
            NativeVisionCapability.MAGNIFICATION -> 24
            NativeVisionCapability.RELUMINO -> 34
            NativeVisionCapability.EXTRA_DIM -> 31
            else -> 1
        }
        if (snapshot.device.sdkInt < minimumApi) {
            return state(NativeVisionAvailability.UNSUPPORTED_DEVICE, "Cette version Android ne propose pas la voie prise en charge.")
        }
        if (observation?.rejectedReason != null) {
            return state(NativeVisionAvailability.REJECTED, observation.rejectedReason)
        }
        if (presence == NativeVisionPresence.ABSENT) {
            return state(NativeVisionAvailability.UNAVAILABLE, "Fonction absente sur cet appareil.")
        }
        if (presence != NativeVisionPresence.PRESENT || observation == null) {
            return state(NativeVisionAvailability.UNAVAILABLE, "La présence de cette fonction n’a pas été établie.")
        }
        if (!observation.contextAllowsControl) {
            return state(NativeVisionAvailability.REJECTED, "La commande n’est pas disponible dans le contexte actuel.")
        }
        // A secure-key read, a settings page or a Samsung manufacturer are not public setter APIs.
        if (observation.publicApiAvailable) {
            if (observation.publicPermissionGranted) {
                return state(NativeVisionAvailability.AVAILABLE_PUBLIC, "Commande Android autorisée et disponible.",
                    automatic = true, engine = NativeVisionEngine.ANDROID_PUBLIC)
            }
            if (observation.publicPermissionRequestable) {
                return state(NativeVisionAvailability.AVAILABLE_WITH_USER_PERMISSION,
                    "Une autorisation utilisateur est nécessaire.", engine = NativeVisionEngine.ANDROID_PUBLIC)
            }
        }
        if (snapshot.variant == NativeVisionVariant.LAB && observation.labCommandAttested && observation.labPermissionGranted) {
            return state(NativeVisionAvailability.AVAILABLE_LAB_ONLY, "Commande autorisée dans cette version de laboratoire.",
                automatic = true, engine = NativeVisionEngine.SAMSUNG_LAB)
        }
        if (observation.settingsRouteAvailable) {
            return state(NativeVisionAvailability.AVAILABLE_USER_ACTION, "À configurer dans les réglages du téléphone.",
                engine = if (samsungOnly) NativeVisionEngine.SAMSUNG_SETTINGS else NativeVisionEngine.ANDROID_PUBLIC)
        }
        if (observation.labCommandAttested) {
            return state(NativeVisionAvailability.AVAILABLE_LAB_ONLY, "Le contrôle automatique nécessite la version de laboratoire autorisée.")
        }
        return state(NativeVisionAvailability.UNAVAILABLE, "Aucune voie de commande prise en charge n’est disponible.")
    }
}
