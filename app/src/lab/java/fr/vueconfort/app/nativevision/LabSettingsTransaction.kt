package fr.vueconfort.app.nativevision

/** Closed vocabulary: neither an Activity nor a profile can supply a settings key. */
internal enum class LabSecureKey(val settingName: String, val capability: NativeVisionCapability) {
    RELUMINO_ENABLED("relumino_switch", NativeVisionCapability.RELUMINO),
    RELUMINO_THICKNESS("relumino_edge_thickness", NativeVisionCapability.RELUMINO),
    RELUMINO_TYPE("relumino_type", NativeVisionCapability.RELUMINO),
    EXTRA_DIM_ENABLED("reduce_bright_colors_activated", NativeVisionCapability.EXTRA_DIM),
    EXTRA_DIM_STRENGTH("reduce_bright_colors_level", NativeVisionCapability.EXTRA_DIM),
    COLOR_FILTER_ENABLED("color_lens_switch", NativeVisionCapability.COLOR_FILTER),
    COLOR_FILTER_TYPE("color_lens_type", NativeVisionCapability.COLOR_FILTER),
    COLOR_FILTER_OPACITY("color_lens_opacity", NativeVisionCapability.COLOR_FILTER),
    COLOR_CORRECTION_ENABLED("accessibility_display_daltonizer_enabled", NativeVisionCapability.COLOR_CORRECTION),
    COLOR_CORRECTION_MODE("accessibility_display_daltonizer", NativeVisionCapability.COLOR_CORRECTION),
    INVERSION_ENABLED("accessibility_display_inversion_enabled", NativeVisionCapability.COLOR_INVERSION),
    HIGH_CONTRAST_TEXT_ENABLED("high_text_contrast_enabled", NativeVisionCapability.HIGH_CONTRAST_TEXT);

    fun accepts(value: String): Boolean = when (this) {
        RELUMINO_THICKNESS -> value in setOf("1.0", "2.0", "3.0", "4.0", "4.99")
        RELUMINO_TYPE -> value.toIntOrNull() in 0..3
        EXTRA_DIM_STRENGTH -> value.toIntOrNull() in 0..100
        COLOR_FILTER_TYPE -> value.toIntOrNull() in 0..11
        COLOR_FILTER_OPACITY -> value.toIntOrNull() in 0..8
        COLOR_CORRECTION_MODE -> value.toIntOrNull() in setOf(0, 11, 12, 13)
        else -> value == "0" || value == "1"
    }
}

internal interface LabSettingsPort {
    fun canWrite(): Boolean
    /** null means absent; an empty string remains an actual stored value. */
    fun read(key: LabSecureKey): String?
    fun put(key: LabSecureKey, value: String): Boolean
    fun delete(key: LabSecureKey): Boolean
}

/** A write-ahead record is committed before touching the provider. */
internal data class LabRestoreRecord(
    val key: LabSecureKey,
    val previous: String?,
    val lastWritten: String?,
    val hasPendingWrite: Boolean = false,
    val pendingValue: String? = null,
    val profileRevision: Long = 0,
) {
    fun owns(current: String?): Boolean = current == lastWritten ||
        (hasPendingWrite && current == pendingValue)
}

internal interface LabJournal {
    /** Implementations must reject corruption, never silently replace it with an empty journal. */
    fun load(): Map<LabSecureKey, LabRestoreRecord>
    /** Must be durable before returning true. */
    fun save(records: Map<LabSecureKey, LabRestoreRecord>): Boolean
}

internal enum class LabTransactionStatus { VERIFIED, PERMISSION_MISSING, CONFLICT, INVALID, ERROR }
internal data class LabTransactionResult(
    val status: LabTransactionStatus,
    val reason: String,
    val restorationAvailable: Boolean,
    val writes: Int = 0,
)

/** Serialized by the adapter. No implicit rollback can overwrite a later system/user choice. */
internal class LabSettingsTransaction(private val port: LabSettingsPort, private val journal: LabJournal) {
    fun hasRestoration(): Boolean = journal.load().isNotEmpty()
    fun records(): Map<LabSecureKey, LabRestoreRecord> = journal.load()

    fun apply(
        values: LinkedHashMap<LabSecureKey, String>,
        revision: Long,
        isCurrent: () -> Boolean = { true },
    ): LabTransactionResult {
        var records = journal.load()
        if (values.any { !it.key.accepts(it.value) }) {
            return LabTransactionResult(LabTransactionStatus.INVALID, "Valeur hors plage autorisée.", records.isNotEmpty())
        }
        if (!port.canWrite()) return permissionMissing(records)
        // Preflight the whole requested group before its first write.
        for (key in values.keys) {
            val record = records[key] ?: continue
            if (!record.owns(port.read(key))) return conflict(records)
        }
        var writes = 0
        for ((key, desired) in values) {
            if (!isCurrent()) return LabTransactionResult(LabTransactionStatus.INVALID,
                "Commande remplacée par un réglage plus récent.", records.isNotEmpty(), writes)
            if (!port.canWrite()) return permissionMissing(records, writes)
            val current = port.read(key)
            val old = records[key]
            if (old != null && !old.owns(current)) return conflict(records, writes)
            if (current == desired) continue
            val pending = (old ?: LabRestoreRecord(key, current, current)).copy(
                hasPendingWrite = true, pendingValue = desired, profileRevision = revision,
            )
            records = records + (key to pending)
            if (!journal.save(records)) return error(records, "Sauvegarde préalable impossible ; commande arrêtée.", writes)
            // Revocation/cancellation may happen during persistence; recheck immediately before mutation.
            if (!isCurrent()) return LabTransactionResult(LabTransactionStatus.INVALID,
                "Commande remplacée avant son écriture.", true, writes)
            if (!port.canWrite()) return permissionMissing(records, writes)
            if (port.read(key) != current) return conflict(records, writes)
            val accepted = port.put(key, desired)
            writes++
            if (!accepted || port.read(key) != desired) return error(records,
                "La valeur n’a pas été confirmée par Android ; restauration conservée.", writes)
            records = records + (key to pending.copy(lastWritten = desired, hasPendingWrite = false, pendingValue = null))
            if (!journal.save(records)) return error(records,
                "Valeur écrite, mais confirmation locale impossible ; journal préalable conservé.", writes)
        }
        return LabTransactionResult(LabTransactionStatus.VERIFIED, "Valeurs relues après commande.", records.isNotEmpty(), writes)
    }

    /** Restores exact strings/absence. Each capability is preflighted as a group. */
    fun restore(capability: NativeVisionCapability): LabTransactionResult {
        var records = journal.load()
        val targets = records.values.filter { it.key.capability == capability }
        if (targets.isEmpty()) return LabTransactionResult(LabTransactionStatus.VERIFIED,
            "Aucune modification à restaurer.", records.isNotEmpty())
        if (!port.canWrite()) return permissionMissing(records)
        for (record in targets) if (!record.owns(port.read(record.key))) return conflict(records)
        // Reverse application order: the activation bit, written last, is restored first.
        var writes = 0
        for (record in targets.asReversed()) {
            if (!port.canWrite()) return permissionMissing(records, writes)
            val current = port.read(record.key)
            if (!record.owns(current)) return conflict(records, writes)
            if (current != record.previous) {
                // Restoring an old out-of-range value is legitimate; the user owned it before VueConfort.
                val pending = record.copy(hasPendingWrite = true, pendingValue = record.previous)
                records = records + (record.key to pending)
                if (!journal.save(records)) return error(records, "Journal de restauration indisponible.", writes)
                if (!port.canWrite()) return permissionMissing(records, writes)
                if (port.read(record.key) != current) return conflict(records, writes)
                val accepted = if (record.previous == null) port.delete(record.key)
                    else port.put(record.key, record.previous)
                writes++
                if (!accepted || port.read(record.key) != record.previous) return error(records,
                    "Restauration non confirmée ; état précédent conservé localement.", writes)
            }
            records = records - record.key
            if (!journal.save(records)) return error(records, "Restauration relue ; journal à confirmer au prochain lancement.", writes)
        }
        return LabTransactionResult(LabTransactionStatus.VERIFIED,
            "État précédent restauré, y compris les clés initialement absentes.", records.isNotEmpty(), writes)
    }

    /** Explicit user choice: stop claiming ownership, while leaving the current display unchanged. */
    fun keepCurrent(): Boolean = journal.save(emptyMap())

    private fun permissionMissing(records: Map<LabSecureKey, LabRestoreRecord>, writes: Int = 0) =
        LabTransactionResult(LabTransactionStatus.PERMISSION_MISSING,
            "Contrôle automatique Samsung indisponible dans cette version de laboratoire. L’état précédent reste conservé.", records.isNotEmpty(), writes)
    private fun conflict(records: Map<LabSecureKey, LabRestoreRecord>, writes: Int = 0) =
        LabTransactionResult(LabTransactionStatus.CONFLICT,
            "Un réglage a changé en dehors de VueConfort. Il est préservé ; la restauration reste en attente.", records.isNotEmpty(), writes)
    private fun error(records: Map<LabSecureKey, LabRestoreRecord>, reason: String, writes: Int) =
        LabTransactionResult(LabTransactionStatus.ERROR, reason, records.isNotEmpty(), writes)
}
