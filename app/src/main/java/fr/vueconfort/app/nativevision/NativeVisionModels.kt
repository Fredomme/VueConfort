package fr.vueconfort.app.nativevision

/** Native commands are distinct from equalizer pixel parameters and from medical corrections. */
enum class NativeVisionCapability {
    MAGNIFICATION, RELUMINO, EXTRA_DIM, COLOR_FILTER, COLOR_CORRECTION, COLOR_INVERSION,
    HIGH_CONTRAST_TEXT, EYE_COMFORT, SYSTEM_BRIGHTNESS, FONT_SCALE, SCREEN_ZOOM
}
enum class NativeVisionVariant { COMMERCIAL, LAB }
enum class NativeVisionPresence { PRESENT, ABSENT, UNKNOWN }
enum class NativeVisionAvailability {
    AVAILABLE_PUBLIC, AVAILABLE_WITH_USER_PERMISSION, AVAILABLE_USER_ACTION,
    AVAILABLE_LAB_ONLY, UNAVAILABLE, UNSUPPORTED_DEVICE, REJECTED
}
enum class NativeVisionApplicationStatus {
    APPLIED_AUTO, APPLIED_LAB, NEEDS_USER_PERMISSION, NEEDS_USER_ACTION,
    UNAVAILABLE, UNSUPPORTED, REJECTED, ERROR
}
enum class NativeVisionEngine { ANDROID_PUBLIC, SAMSUNG_SETTINGS, SAMSUNG_LAB, NONE }
enum class NativeVisionConfirmation { UNCONFIRMED, USER_CONFIRMED, READ_BACK_CONFIRMED }
enum class NativeVisionDisableBehavior { KEEP_CURRENT, RESTORE_PREVIOUS }
enum class NativeMagnificationMode { FULLSCREEN, WINDOW }

/** These are the five firmware UI stops, not distances in pixels or optical powers. */
enum class ReluminoThickness(val value: Float) {
    ONE(1f), TWO(2f), THREE(3f), FOUR(4f), MAX(4.99f);
    companion object { fun fromValue(value: Float): ReluminoThickness? = entries.firstOrNull { it.value == value } }
}
enum class ReluminoColor(val value: Int) {
    ADAPTIVE(0), BLACK(1), WHITE(2), GREEN(3);
    companion object { fun fromValue(value: Int): ReluminoColor? = entries.firstOrNull { it.value == value } }
}

sealed interface NativeVisionValue {
    data class Relumino(
        val enabled: Boolean = false,
        val thickness: ReluminoThickness = ReluminoThickness.TWO,
        val color: ReluminoColor = ReluminoColor.ADAPTIVE
    ) : NativeVisionValue
    data class Magnification(
        val enabled: Boolean, val scale: Float,
        val centerX: Float? = null, val centerY: Float? = null,
        val mode: NativeMagnificationMode = NativeMagnificationMode.FULLSCREEN
    ) : NativeVisionValue
    data class ExtraDim(val enabled: Boolean, val strength: Int) : NativeVisionValue
    data class ColorFilter(val enabled: Boolean, val color: Int, val opacityPercent: Int) : NativeVisionValue
    data class ColorCorrection(val enabled: Boolean, val mode: Int) : NativeVisionValue
    data class Toggle(val enabled: Boolean) : NativeVisionValue
    data class Brightness(val value: Int) : NativeVisionValue
    data class FontScale(val scale: Float) : NativeVisionValue
    data class ScreenZoom(val index: Int) : NativeVisionValue

    /** Validation rejects rather than silently rounding a command to another transformation. */
    fun isValidFor(capability: NativeVisionCapability): Boolean = when (this) {
        is Relumino -> capability == NativeVisionCapability.RELUMINO
        is Magnification -> capability == NativeVisionCapability.MAGNIFICATION && scale.isFinite() && scale in 1f..8f &&
            ((centerX == null && centerY == null) || (centerX != null && centerY != null &&
                centerX.isFinite() && centerY.isFinite() && centerX >= 0 && centerY >= 0))
        is ExtraDim -> capability == NativeVisionCapability.EXTRA_DIM && strength in 0..100
        is ColorFilter -> capability == NativeVisionCapability.COLOR_FILTER && color in 0..11 &&
            opacityPercent in 20..60 && opacityPercent % 5 == 0
        is ColorCorrection -> capability == NativeVisionCapability.COLOR_CORRECTION && mode in setOf(0, 11, 12, 13)
        is Toggle -> capability in setOf(NativeVisionCapability.COLOR_INVERSION,
            NativeVisionCapability.HIGH_CONTRAST_TEXT, NativeVisionCapability.EYE_COMFORT)
        is Brightness -> capability == NativeVisionCapability.SYSTEM_BRIGHTNESS && value in 0..255
        is FontScale -> capability == NativeVisionCapability.FONT_SCALE && scale.isFinite() && scale in 0.5f..2f
        // An index is an OEM choice, not a public DPI setter. It remains guided in this increment.
        is ScreenZoom -> capability == NativeVisionCapability.SCREEN_ZOOM && index in 0..10
    }
}

data class NativeVisionDevice(
    val manufacturer: String, val model: String, val sdkInt: Int,
    val oneUiVersion: String? = null, val buildFingerprint: String = "", val androidUserId: Int? = null
)
data class NativeVisionCapabilityObservation(
    val presence: NativeVisionPresence = NativeVisionPresence.UNKNOWN,
    val provenance: String,
    val readable: Boolean = false,
    val settingsRouteAvailable: Boolean = false,
    val publicApiAvailable: Boolean = false,
    val publicPermissionGranted: Boolean = false,
    val publicPermissionRequestable: Boolean = false,
    val contextAllowsControl: Boolean = true,
    val labCommandAttested: Boolean = false,
    val labPermissionGranted: Boolean = false,
    val rejectedReason: String? = null
)
data class NativeVisionRuntimeSnapshot(
    val device: NativeVisionDevice, val variant: NativeVisionVariant,
    val observedAtMillis: Long,
    val observations: Map<NativeVisionCapability, NativeVisionCapabilityObservation>
)
data class NativeVisionCapabilityState(
    val capability: NativeVisionCapability,
    val presence: NativeVisionPresence,
    val availability: NativeVisionAvailability,
    val canApplyAutomatically: Boolean,
    val readable: Boolean,
    val engine: NativeVisionEngine,
    val provenance: String,
    val reason: String
)
/** A snapshot, not a permission cache: refresh at startup, resume and immediately before a command. */
data class NativeVisionCapabilities(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val device: NativeVisionDevice,
    val variant: NativeVisionVariant,
    val observedAtMillis: Long,
    val capabilities: Map<NativeVisionCapability, NativeVisionCapabilityState>
) {
    operator fun get(capability: NativeVisionCapability): NativeVisionCapabilityState? = capabilities[capability]
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

data class NativeVisionRequestedState(
    val values: Map<NativeVisionCapability, NativeVisionValue> = emptyMap(),
    val revision: Long = 0,
    val updatedAtMillis: Long = 0,
    val provenance: String = "USER"
) {
    fun validated(): NativeVisionRequestedState {
        require(revision >= 0 && updatedAtMillis >= 0 && provenance.isNotBlank() && provenance.length <= 500)
        require(values.all { (capability, value) -> value.isValidFor(capability) })
        return this
    }
}
data class NativeVisionPreferences(
    val recommended: NativeVisionRequestedState = NativeVisionRequestedState(provenance = "NONE"),
    val disableBehavior: NativeVisionDisableBehavior = NativeVisionDisableBehavior.KEEP_CURRENT
)
data class NativeVisionApplicationResult(
    val capability: NativeVisionCapability,
    val requested: NativeVisionValue,
    val status: NativeVisionApplicationStatus,
    val applied: NativeVisionValue? = null,
    val engine: NativeVisionEngine,
    val provenance: String,
    val reason: String,
    val timestampMillis: Long,
    /** This is the native requested revision, independently of the equalizer renderer's revision. */
    val profileRevision: Long,
    val restorationAvailable: Boolean = false,
    val confirmation: NativeVisionConfirmation = NativeVisionConfirmation.UNCONFIRMED
) {
    fun validated(): NativeVisionApplicationResult {
        require(requested.isValidFor(capability) && (applied == null || applied.isValidFor(capability)))
        require(timestampMillis >= 0 && profileRevision >= 0 && provenance.isNotBlank() && provenance.length <= 500)
        require(reason.isNotBlank() && reason.length <= 2_000)
        if (status == NativeVisionApplicationStatus.APPLIED_AUTO || status == NativeVisionApplicationStatus.APPLIED_LAB) {
            require(applied != null && confirmation == NativeVisionConfirmation.READ_BACK_CONFIRMED)
            require(engine != NativeVisionEngine.NONE)
        }
        // User attestation never masquerades as a technically verified automatic application.
        if (confirmation == NativeVisionConfirmation.USER_CONFIRMED) {
            require(status != NativeVisionApplicationStatus.APPLIED_AUTO && status != NativeVisionApplicationStatus.APPLIED_LAB)
        }
        return this
    }
}
data class NativeVisionAppliedState(
    val results: Map<NativeVisionCapability, NativeVisionApplicationResult> = emptyMap()
)
/** Embedded in the existing personal profile. The lab's rollback journal is a transaction log, not another profile. */
data class NativeVisionProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val enabled: Boolean = false,
    val preferences: NativeVisionPreferences = NativeVisionPreferences(),
    val requested: NativeVisionRequestedState = NativeVisionRequestedState(),
    val applied: NativeVisionAppliedState = NativeVisionAppliedState(),
    val capabilities: NativeVisionCapabilities? = null,
    val restorationReferences: Map<NativeVisionCapability, String> = emptyMap()
) {
    /** Historical receipts remain inspectable, but never count as confirmation of new requests. */
    fun currentResult(capability: NativeVisionCapability): NativeVisionApplicationResult? =
        applied.results[capability]?.takeIf { result ->
            result.profileRevision == requested.revision && requested.values[capability] == result.requested
        }

    fun validated(): NativeVisionProfile {
        require(schemaVersion == CURRENT_SCHEMA_VERSION)
        requested.validated(); preferences.recommended.validated()
        applied.results.forEach { (capability, result) ->
            require(capability == result.capability && result.profileRevision <= requested.revision)
            result.validated()
        }
        capabilities?.let { snapshot ->
            require(snapshot.schemaVersion == NativeVisionCapabilities.CURRENT_SCHEMA_VERSION && snapshot.observedAtMillis >= 0)
            require(snapshot.device.sdkInt > 0 && snapshot.device.manufacturer.length <= 200 && snapshot.device.model.length <= 200)
            require(snapshot.capabilities.all { (capability, state) -> capability == state.capability })
        }
        require(restorationReferences.all { (_, reference) -> reference.isNotBlank() && reference.length <= 500 })
        return this
    }
    companion object { const val CURRENT_SCHEMA_VERSION = 1 }
}

/** Preserve null (key absent) separately from a present raw value such as "0". */
data class NativeVisionStoredValue(val present: Boolean, val rawValue: String? = null) {
    init { require(present == (rawValue != null)) }
    companion object {
        val Absent = NativeVisionStoredValue(false)
        fun fromRaw(raw: String?) = NativeVisionStoredValue(raw != null, raw)
    }
}
