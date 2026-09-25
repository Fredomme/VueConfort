package fr.vueconfort.app.nativevision

import android.content.Context
import android.provider.Settings

/** Compiled only into Lab. Calls use the application's UID; this class has no shell pathway. */
class SamsungNativeVisionLabAdapter(
    context: Context,
    private val capabilityProvider: () -> NativeVisionCapabilities,
) {
    private val app = context.applicationContext
    private val port = AndroidLabSettingsPort(app)
    private val transaction = LabSettingsTransaction(port, AndroidLabJournal(app))

    /** Run off the main thread. isCurrent invalidates an in-flight obsolete slider/profile request. */
    fun apply(
        request: NativeVisionRequestedState,
        profileRevision: Long = request.revision,
        isCurrent: () -> Boolean = { true },
    ): List<NativeVisionApplicationResult> = synchronized(processLock) {
        request.values.map { (capability, value) ->
            try {
                val state = capabilityProvider()
                val support = state[capability]
                when {
                    !value.isValidFor(capability) || profileRevision < 0 ->
                        result(capability, value, NativeVisionApplicationStatus.REJECTED,
                            "La commande ne correspond pas aux valeurs autorisées.", profileRevision.coerceAtLeast(0))
                    state.variant != NativeVisionVariant.LAB || support?.canApplyAutomatically != true ||
                        support.availability != NativeVisionAvailability.AVAILABLE_LAB_ONLY ->
                        result(capability, value,
                            if (!port.canWrite()) NativeVisionApplicationStatus.NEEDS_USER_PERMISSION
                            else NativeVisionApplicationStatus.UNSUPPORTED,
                            support?.reason ?: "Commande de laboratoire non démontrée sur cet appareil.", profileRevision)
                    !isCurrent() -> result(capability, value, NativeVisionApplicationStatus.REJECTED,
                        "Commande remplacée par un réglage plus récent.", profileRevision)
                    value is NativeVisionValue.ColorFilter && value.enabled && hasColorFilterConflict() ->
                        result(capability, value, NativeVisionApplicationStatus.REJECTED,
                            "Désactive d’abord le confort oculaire Samsung ou la correction ancienne dans les réglages Samsung, puis réessaie. Ces fonctions peuvent se désactiver mutuellement.", profileRevision)
                    else -> {
                        val values = valuesFor(capability, value)
                        if (values == null) result(capability, value, NativeVisionApplicationStatus.UNSUPPORTED,
                            "Cette fonction n’a pas de commande Lab validée.", profileRevision)
                        else {
                            val outcome = transaction.apply(values, profileRevision, isCurrent)
                            val verified = outcome.status == LabTransactionStatus.VERIFIED
                            result(capability, value, outcome.status.applicationStatus(),
                                outcome.reason + if (verified) " Cette relecture ne mesure pas la latence ni le résultat optique du traitement Samsung." else "",
                                profileRevision, if (verified) value else null, hasPendingRestoration(capability))
                        }
                    }
                }
            } catch (_: SecurityException) {
                if (port.canWrite()) result(capability, value, NativeVisionApplicationStatus.REJECTED,
                    "Android refuse l’accès à ce réglage malgré l’autorisation de laboratoire. La commande est arrêtée ; tout journal précédent est conservé.", profileRevision)
                else result(capability, value, NativeVisionApplicationStatus.NEEDS_USER_PERMISSION,
                    "L’autorisation de laboratoire a été retirée. Les réglages déjà mémorisés restent restaurables après son rétablissement.", profileRevision)
            } catch (_: Exception) {
                result(capability, value, NativeVisionApplicationStatus.ERROR,
                    "Commande interrompue. Le journal local est conservé pour vérifier ou restaurer les réglages.", profileRevision)
            }
        }
    }

    /** Caller first invalidates/empties its conflated request channel. This operation is serialized. */
    fun restorePreviousState(
        profileRevision: Long,
        requested: NativeVisionRequestedState = NativeVisionRequestedState(),
    ): NativeVisionRestorationOutcome = synchronized(processLock) {
        val results = mutableListOf<NativeVisionApplicationResult>()
        var success = true
        try {
            val capabilities = transaction.records().keys.map { it.capability }.distinct()
            for (capability in capabilities) {
                val value = requested.values[capability] ?: fallbackValue(capability)
                val state = capabilityProvider()
                val support = state[capability]
                if (state.variant != NativeVisionVariant.LAB || support?.canApplyAutomatically != true ||
                    support.availability != NativeVisionAvailability.AVAILABLE_LAB_ONLY) {
                    success = false
                    results += result(capability, value, NativeVisionApplicationStatus.NEEDS_USER_PERMISSION,
                        "Restauration en attente : le contrôle de laboratoire n’est plus disponible. L’état précédent reste conservé.", profileRevision)
                    continue
                }
                val outcome = transaction.restore(capability)
                val restored = outcome.status == LabTransactionStatus.VERIFIED
                success = success && restored
                // The raw journal, not a guessed Android default, is the authority for restoration.
                val known = if (restored) readKnownValue(capability) else null
                results += result(capability, value,
                    if (restored && known == null) NativeVisionApplicationStatus.NEEDS_USER_ACTION
                    else outcome.status.applicationStatus(),
                    outcome.reason + if (restored && known == null)
                        " Les valeurs absentes restent absentes ; le réglage effectif par défaut est laissé à Android." else "",
                    profileRevision, known, hasPendingRestoration(capability))
            }
            NativeVisionRestorationOutcome(success, transaction.hasRestoration(), results)
        } catch (_: Exception) {
            NativeVisionRestorationOutcome(false, true, results)
        }
    }

    fun keepCurrentState(): Boolean = synchronized(processLock) {
        try { transaction.keepCurrent() } catch (_: Exception) { false }
    }

    fun hasPendingRestoration(): Boolean = synchronized(processLock) {
        // An unreadable journal must not cause the UI to claim that restoration is unnecessary.
        try { transaction.hasRestoration() } catch (_: Exception) { true }
    }

    private fun hasPendingRestoration(capability: NativeVisionCapability): Boolean =
        try { transaction.records().keys.any { it.capability == capability } } catch (_: Exception) { true }

    private fun hasColorFilterConflict(): Boolean =
        Settings.System.getString(app.contentResolver, "blue_light_filter") == "1" ||
            Settings.System.getString(app.contentResolver, "color_blind") == "1"

    private fun result(
        capability: NativeVisionCapability,
        requested: NativeVisionValue,
        status: NativeVisionApplicationStatus,
        reason: String,
        revision: Long,
        applied: NativeVisionValue? = null,
        restorationAvailable: Boolean = hasPendingRestoration(capability),
    ) = NativeVisionApplicationResult(
        capability = capability, requested = requested, status = status, applied = applied,
        engine = NativeVisionEngine.SAMSUNG_LAB,
        provenance = "LAB_APP_UID_SECURE_READ_BACK;ROLLBACK_JOURNAL_V1",
        reason = reason, timestampMillis = System.currentTimeMillis(), profileRevision = revision,
        restorationAvailable = restorationAvailable,
        confirmation = if (applied != null) NativeVisionConfirmation.READ_BACK_CONFIRMED else NativeVisionConfirmation.UNCONFIRMED,
    )

    private fun readKnownValue(capability: NativeVisionCapability): NativeVisionValue? {
        val raw = LabSecureKey.entries.filter { it.capability == capability }.associateWith { port.read(it) }
        if (raw.any { (key, value) -> value != null &&
            if (key == LabSecureKey.RELUMINO_THICKNESS) value.toFloatOrNull() == null else value.toIntOrNull() == null
        }) return null
        fun integer(key: LabSecureKey) = raw[key]?.toIntOrNull()
        val value = when (capability) {
            NativeVisionCapability.RELUMINO -> NativeVisionValue.Relumino(
                enabled = (integer(LabSecureKey.RELUMINO_ENABLED) ?: 0) != 0,
                thickness = ReluminoThickness.fromValue(raw[LabSecureKey.RELUMINO_THICKNESS]?.toFloatOrNull() ?: 2f) ?: return null,
                color = ReluminoColor.fromValue(integer(LabSecureKey.RELUMINO_TYPE) ?: 0) ?: return null,
            )
            NativeVisionCapability.EXTRA_DIM -> NativeVisionValue.ExtraDim(
                (integer(LabSecureKey.EXTRA_DIM_ENABLED) ?: 0) != 0,
                integer(LabSecureKey.EXTRA_DIM_STRENGTH) ?: return null,
            )
            NativeVisionCapability.COLOR_FILTER -> NativeVisionValue.ColorFilter(
                (integer(LabSecureKey.COLOR_FILTER_ENABLED) ?: 0) != 0,
                integer(LabSecureKey.COLOR_FILTER_TYPE) ?: return null,
                (integer(LabSecureKey.COLOR_FILTER_OPACITY) ?: return null) * 5 + 20,
            )
            NativeVisionCapability.COLOR_CORRECTION -> NativeVisionValue.ColorCorrection(
                (integer(LabSecureKey.COLOR_CORRECTION_ENABLED) ?: 0) != 0,
                integer(LabSecureKey.COLOR_CORRECTION_MODE) ?: return null,
            )
            NativeVisionCapability.COLOR_INVERSION -> NativeVisionValue.Toggle((integer(LabSecureKey.INVERSION_ENABLED) ?: 0) != 0)
            NativeVisionCapability.HIGH_CONTRAST_TEXT -> NativeVisionValue.Toggle((integer(LabSecureKey.HIGH_CONTRAST_TEXT_ENABLED) ?: 0) != 0)
            else -> return null
        }
        return value.takeIf { it.isValidFor(capability) }
    }

    private fun fallbackValue(capability: NativeVisionCapability): NativeVisionValue = when (capability) {
        NativeVisionCapability.RELUMINO -> NativeVisionValue.Relumino()
        NativeVisionCapability.EXTRA_DIM -> NativeVisionValue.ExtraDim(false, 0)
        NativeVisionCapability.COLOR_FILTER -> NativeVisionValue.ColorFilter(false, 0, 20)
        NativeVisionCapability.COLOR_CORRECTION -> NativeVisionValue.ColorCorrection(false, 0)
        else -> NativeVisionValue.Toggle(false)
    }

    private fun valuesFor(capability: NativeVisionCapability, value: NativeVisionValue): LinkedHashMap<LabSecureKey, String>? {
        fun bit(enabled: Boolean) = if (enabled) "1" else "0"
        fun ordered(enabled: Boolean, key: LabSecureKey, parameters: List<Pair<LabSecureKey, String>>) =
            LinkedHashMap<LabSecureKey, String>().apply {
                if (!enabled) put(key, "0")
                parameters.forEach { (key, value) -> put(key, value) }
                if (enabled) put(key, "1")
            }
        return when (value) {
            is NativeVisionValue.Relumino -> ordered(value.enabled, LabSecureKey.RELUMINO_ENABLED, listOf(
                LabSecureKey.RELUMINO_THICKNESS to value.thickness.value.toString(),
                LabSecureKey.RELUMINO_TYPE to value.color.value.toString(),
            ))
            is NativeVisionValue.ExtraDim -> ordered(value.enabled, LabSecureKey.EXTRA_DIM_ENABLED,
                listOf(LabSecureKey.EXTRA_DIM_STRENGTH to value.strength.toString()))
            is NativeVisionValue.ColorFilter -> ordered(value.enabled, LabSecureKey.COLOR_FILTER_ENABLED, listOf(
                LabSecureKey.COLOR_FILTER_TYPE to value.color.toString(),
                LabSecureKey.COLOR_FILTER_OPACITY to ((value.opacityPercent - 20) / 5).toString(),
            ))
            is NativeVisionValue.ColorCorrection -> ordered(value.enabled, LabSecureKey.COLOR_CORRECTION_ENABLED,
                listOf(LabSecureKey.COLOR_CORRECTION_MODE to value.mode.toString()))
            is NativeVisionValue.Toggle -> when (capability) {
                NativeVisionCapability.COLOR_INVERSION -> linkedMapOf(LabSecureKey.INVERSION_ENABLED to bit(value.enabled))
                NativeVisionCapability.HIGH_CONTRAST_TEXT -> linkedMapOf(LabSecureKey.HIGH_CONTRAST_TEXT_ENABLED to bit(value.enabled))
                else -> null
            }
            else -> null
        }
    }

    private fun LabTransactionStatus.applicationStatus() = when (this) {
        LabTransactionStatus.VERIFIED -> NativeVisionApplicationStatus.APPLIED_LAB
        LabTransactionStatus.PERMISSION_MISSING -> NativeVisionApplicationStatus.NEEDS_USER_PERMISSION
        LabTransactionStatus.CONFLICT, LabTransactionStatus.INVALID -> NativeVisionApplicationStatus.REJECTED
        LabTransactionStatus.ERROR -> NativeVisionApplicationStatus.ERROR
    }

    private companion object { val processLock = Any() }
}
