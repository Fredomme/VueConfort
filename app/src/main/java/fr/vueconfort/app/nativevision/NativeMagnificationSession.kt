package fr.vueconfort.app.nativevision

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Private write-ahead recovery record. It is not a second visual profile. */
class NativeMagnificationSession(
    context: Context,
    private val adapter: AndroidNativeVisionAdapter = AndroidNativeVisionAdapter(),
) {
    private val file = AtomicFile(File(context.filesDir, "native-magnification-rollback-v1.json"))

    fun hasPendingRestoration(): Boolean = file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    suspend fun apply(request: NativeVisionRequestedState, profileRevision: Long = request.revision): List<NativeVisionApplicationResult> =
        gate.withLock { withContext(Dispatchers.Main.immediate) {
            val value = request.values[NativeVisionCapability.MAGNIFICATION] ?: return@withContext emptyList()
            if (value !is NativeVisionValue.Magnification || !value.isValidFor(NativeVisionCapability.MAGNIFICATION)) {
                return@withContext listOf(failure(value, profileRevision, "Le grossissement demandé est invalide."))
            }
            val current = adapter.readMagnificationState()
            // Do not delegate to a second read-and-apply path here: a newly valid reading
            // could otherwise trigger a command without the write-ahead journal below.
            if (current == null) return@withContext listOf(failure(value, profileRevision,
                "Activez le service VueConfort dans les réglages d’accessibilité.").copy(
                status = NativeVisionApplicationStatus.NEEDS_USER_PERMISSION))
            if (!current.restorable) return@withContext listOf(failure(value, profileRevision,
                "Android ne fournit pas un état complet à restaurer. Utilisez les commandes de la loupe existante.").copy(
                status = NativeVisionApplicationStatus.NEEDS_USER_ACTION))
            val existing = if (hasPendingRestoration()) runCatching { readJournal() }.getOrElse {
                return@withContext listOf(failure(value, profileRevision, "Le journal précédent ne peut pas être vérifié. Aucune modification n’a été faite."))
            } else null
            if (existing?.restoring == true) return@withContext listOf(failure(value, profileRevision,
                "Une restauration est encore en cours. Terminez-la avant une nouvelle demande de grossissement."))
            if (existing != null && (existing.fingerprint != Build.FINGERPRINT ||
                    (!existing.expected.matches(current) && !existing.previousExpected.matches(current)))) {
                return@withContext listOf(failure(value, profileRevision,
                    "Le contexte ou le grossissement a changé ailleurs. Conservez explicitement ce réglage avant une nouvelle session."))
            }
            val target = adapter.prepareMagnificationTarget(value, current) ?: return@withContext listOf(
                failure(value, profileRevision, "Le centre de la fenêtre Android n’est pas disponible. Aucune modification n’a été faite.").copy(
                    status = NativeVisionApplicationStatus.NEEDS_USER_ACTION))
            val journal = Journal(existing?.before ?: current, target, current, Build.FINGERPRINT)
            if (!writeJournal(journal)) return@withContext listOf(failure(value, profileRevision,
                "Impossible de sauvegarder le grossissement précédent. Aucune modification n’a été faite."))
            val result = adapter.applyMagnification(value, profileRevision, expectedBefore = current, preparedTarget = target)
            // Preserve the WAL even on error: accepted is not proof of application, or of no application.
            listOf(result.copy(restorationAvailable = true))
        } }

    suspend fun restore(profileRevision: Long): NativeVisionRestorationOutcome = restoreGuarded(profileRevision)

    /**
     * Explicit recovery for an observed, interrupted OFF from an older journal. The normal path never
     * adopts an arbitrary OFF as its own. The caller must provide the exact current raw observation.
     */
    suspend fun resumeRestoration(profileRevision: Long,
                                  observedCurrent: NativeMagnificationSnapshot): NativeVisionRestorationOutcome =
        restoreGuarded(profileRevision, observedCurrent)

    private suspend fun restoreGuarded(profileRevision: Long,
                                       explicitObservation: NativeMagnificationSnapshot? = null): NativeVisionRestorationOutcome =
        gate.withLock { withContext(Dispatchers.Main.immediate) {
            if (!hasPendingRestoration()) return@withContext NativeVisionRestorationOutcome(true, false, emptyList())
            var journal = runCatching { readJournal() }.getOrElse {
                return@withContext NativeVisionRestorationOutcome(false, true, listOf(failure(
                    NativeVisionValue.Magnification(false, 1f), profileRevision,
                    "Le journal de restauration n’est pas lisible. Aucun réglage n’a été imposé.",
                )))
            }
            val requested = journal.before.asValue() ?: NativeVisionValue.Magnification(false, 1f)
            if (journal.fingerprint != Build.FINGERPRINT) return@withContext NativeVisionRestorationOutcome(false, true, listOf(
                failure(requested, profileRevision, "Android a été mis à jour depuis la sauvegarde. Le réglage actuel est conservé."),
            ))
            val initial = adapter.readMagnificationState()
            if (explicitObservation != null && initial != explicitObservation) return@withContext NativeVisionRestorationOutcome(false, true,
                listOf(failure(requested, profileRevision, "L’état a changé depuis l’observation de reprise. Aucun réglage n’a été imposé.")))
            if (initial != null && journal.before.matches(initial)) {
                val removed = discardJournalLocked()
                return@withContext NativeVisionRestorationOutcome(removed, !removed, emptyList())
            }
            if (initial == null || !initial.restorable) return@withContext NativeVisionRestorationOutcome(false, true,
                listOf(failure(requested, profileRevision, "Le contrôleur Android ne fournit pas un état restaurable. Le journal est conservé.")))
            val owned = NativeMagnificationRestorePolicy.owns(initial, journal.expected, journal.previousExpected)
            val explicitResume = explicitObservation != null && NativeMagnificationRestorePolicy.canResumeInactive(
                journal.before, journal.expected, journal.previousExpected, explicitObservation, initial)
            if (!owned && !explicitResume) return@withContext NativeVisionRestorationOutcome(false, true,
                listOf(failure(requested, profileRevision, "Le grossissement a été modifié ailleurs. Une observation explicite est nécessaire avant de reprendre cet essai.")))

            val results = mutableListOf<NativeVisionApplicationResult>()
            var actual: NativeMagnificationSnapshot = initial
            // At most one active mode transition, followed by the exact final snapshot.
            repeat(2) {
                val current = actual
                val intermediate = NativeMagnificationRestorePolicy.requiresModeTransition(journal.before, current)
                val target = if (intermediate) NativeMagnificationRestorePolicy.modeTransitionTarget(
                    journal.before, current, listOf(journal.expected, journal.previousExpected)) else journal.before
                if (target == null) return@withContext NativeVisionRestorationOutcome(false, true, results + failure(requested,
                    profileRevision, "Aucun état actif observé ne permet de préparer le mode initial. Le journal est conservé."))
                // Retain the original baseline and record BOTH permitted sides of this step before its command.
                journal = journal.copy(expected = target, previousExpected = current, restoring = true)
                if (!writeJournal(journal)) return@withContext NativeVisionRestorationOutcome(false, true, results + failure(requested,
                    profileRevision, "Impossible de journaliser l’étape de restauration. Aucune nouvelle commande n’a été envoyée."))
                val result = adapter.restoreMagnification(target, current, profileRevision, finalStep = !intermediate)
                results += result.copy(restorationAvailable = true)
                if (result.status != NativeVisionApplicationStatus.APPLIED_AUTO)
                    return@withContext NativeVisionRestorationOutcome(false, true, results)
                val after = adapter.readMagnificationState()
                if (after == null || !target.matches(after)) return@withContext NativeVisionRestorationOutcome(false, true,
                    results + failure(requested, profileRevision, "L’état a changé après l’étape de restauration. Le journal est conservé."))
                actual = after
                if (journal.before.matches(actual)) {
                    val removed = discardJournalLocked()
                    return@withContext NativeVisionRestorationOutcome(removed, !removed,
                        results.map { it.copy(restorationAvailable = !removed) })
                }
            }
            NativeVisionRestorationOutcome(false, true, results)
        } }

    /** Only discards ownership; this action never writes to the Android controller. */
    fun keepCurrentState(): Boolean {
        if (!gate.tryLock()) return false
        return try { discardJournalLocked() } finally { gate.unlock() }
    }

    /** The caller holds gate, including the successful restore path. */
    private fun discardJournalLocked(): Boolean = runCatching {
        file.delete()
        !hasPendingRestoration()
    }.getOrDefault(false)

    private fun writeJournal(journal: Journal): Boolean {
        val bytes = JSONObject().put("schema", 2).put("fingerprint", journal.fingerprint).put("restoring", journal.restoring)
            .put("before", journal.before.toJson()).put("expected", journal.expected.toJson())
            .put("previousExpected", journal.previousExpected.toJson())
            .toString().toByteArray(Charsets.UTF_8)
        val output = runCatching { file.startWrite() }.getOrElse { return false }
        return try {
            output.write(bytes); file.finishWrite(output); true
        } catch (_: Exception) { runCatching { file.failWrite(output) }; false }
    }

    private fun readJournal(): Journal {
        val root = JSONObject(file.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
        val schema = root.getInt("schema")
        require(schema in 1..2)
        return Journal(fromJson(root.getJSONObject("before")), fromJson(root.getJSONObject("expected")),
            fromJson(root.getJSONObject("previousExpected")), root.getString("fingerprint"),
            restoring = if (schema == 2) root.getBoolean("restoring") else false)
    }

    private fun NativeMagnificationSnapshot.toJson(): JSONObject {
        return JSONObject().also { json -> NativeMagnificationSnapshotCodec.encode(this).forEach { (key, value) ->
            // JSONObject.put(key, null) deletes a key; explicit NULL preserves an observed absent viewport.
            json.put(key, value ?: JSONObject.NULL)
        } }
    }

    private fun fromJson(json: JSONObject): NativeMagnificationSnapshot = NativeMagnificationSnapshotCodec.decode(
        NativeMagnificationSnapshotCodec.fields.associateWith { key ->
            require(json.has(key))
            if (json.isNull(key)) null else json.get(key)
        })

    private data class Journal(val before: NativeMagnificationSnapshot, val expected: NativeMagnificationSnapshot,
                               val previousExpected: NativeMagnificationSnapshot, val fingerprint: String,
                               val restoring: Boolean = false)
    private fun failure(value: NativeVisionValue, revision: Long, reason: String) = NativeVisionApplicationResult(
        capability = NativeVisionCapability.MAGNIFICATION, requested = value,
        status = NativeVisionApplicationStatus.REJECTED, engine = NativeVisionEngine.ANDROID_PUBLIC,
        provenance = "Journal local du MagnificationController existant ; contrôle de concurrence avant commande",
        reason = reason, timestampMillis = System.currentTimeMillis(), profileRevision = revision,
        restorationAvailable = hasPendingRestoration(),
    )
    companion object { private val gate = Mutex() }
}

internal object NativeMagnificationRestorePolicy {
    fun owns(current: NativeMagnificationSnapshot, expected: NativeMagnificationSnapshot,
             previousExpected: NativeMagnificationSnapshot): Boolean = expected.matches(current) || previousExpected.matches(current)

    fun canResumeInactive(before: NativeMagnificationSnapshot, expected: NativeMagnificationSnapshot,
                          previousExpected: NativeMagnificationSnapshot, explicitObservation: NativeMagnificationSnapshot,
                          current: NativeMagnificationSnapshot): Boolean =
        explicitObservation == current && before.restorable && current.restorable && !before.enabled && !current.enabled &&
            current.scale == before.scale && current.mode != before.mode &&
            listOf(expected, previousExpected).any { it.restorable && it.enabled && it.mode == current.mode }

    fun requiresModeTransition(before: NativeMagnificationSnapshot, current: NativeMagnificationSnapshot): Boolean =
        !before.enabled && before.mode != current.mode

    fun modeTransitionTarget(before: NativeMagnificationSnapshot, current: NativeMagnificationSnapshot,
                             knownStates: List<NativeMagnificationSnapshot>): NativeMagnificationSnapshot? {
        if (!before.restorable || !current.restorable || !requiresModeTransition(before, current)) return null
        // Reuse a controller/session factor and recorded geometry, never an arbitrary fallback zoom level.
        val active = (listOf(current) + knownStates).firstOrNull { it.enabled && it.restorable } ?: return null
        return current.copy(enabled = true, mode = before.mode, scale = active.scale,
            centerX = current.centerX ?: active.centerX, centerY = current.centerY ?: active.centerY).takeIf { it.restorable }
    }
}

/** The journal's explicit nulls are distinct from a missing/corrupt field. Pure and independently testable. */
internal object NativeMagnificationSnapshotCodec {
    val fields = setOf("enabled", "scale", "centerX", "centerY", "mode", "activationExact")

    fun encode(snapshot: NativeMagnificationSnapshot): Map<String, Any?> {
        require(snapshot.restorable)
        return mapOf("enabled" to snapshot.enabled, "scale" to snapshot.scale, "centerX" to snapshot.centerX,
            "centerY" to snapshot.centerY, "mode" to snapshot.mode!!.name, "activationExact" to snapshot.activationExact)
    }

    fun decode(values: Map<String, Any?>): NativeMagnificationSnapshot {
        require(values.keys.containsAll(fields))
        return NativeMagnificationSnapshot(
            enabled = values.getValue("enabled") as Boolean,
            scale = (values.getValue("scale") as Number).toFloat(),
            centerX = values.getValue("centerX")?.let { (it as Number).toFloat() },
            centerY = values.getValue("centerY")?.let { (it as Number).toFloat() },
            mode = NativeMagnificationMode.valueOf(values.getValue("mode") as String),
            activationExact = values.getValue("activationExact") as Boolean,
        ).also { require(it.restorable) }
    }
}
