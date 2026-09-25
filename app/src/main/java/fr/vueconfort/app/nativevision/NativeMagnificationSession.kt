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

    suspend fun restore(profileRevision: Long): NativeVisionRestorationOutcome = gate.withLock {
        withContext(Dispatchers.Main.immediate) {
            if (!hasPendingRestoration()) return@withContext NativeVisionRestorationOutcome(true, false, emptyList())
            val journal = runCatching { readJournal() }.getOrElse {
                return@withContext NativeVisionRestorationOutcome(false, true, listOf(failure(
                    NativeVisionValue.Magnification(false, 1f), profileRevision,
                    "Le journal de restauration n’est pas lisible. Aucun réglage n’a été imposé.",
                )))
            }
            val requested = journal.before.asValue() ?: NativeVisionValue.Magnification(false, 1f)
            if (journal.fingerprint != Build.FINGERPRINT) return@withContext NativeVisionRestorationOutcome(false, true, listOf(
                failure(requested, profileRevision, "Android a été mis à jour depuis la sauvegarde. Le réglage actuel est conservé."),
            ))
            val actual = adapter.readMagnificationState()
            if (actual != null && journal.before.matches(actual)) {
                val removed = keepCurrentState()
                return@withContext NativeVisionRestorationOutcome(removed, !removed, emptyList())
            }
            val expected = if (actual != null && journal.previousExpected.matches(actual)) journal.previousExpected else journal.expected
            val result = adapter.restoreMagnification(journal.before, expected, profileRevision)
            val restored = result.status == NativeVisionApplicationStatus.APPLIED_AUTO
            val removed = restored && keepCurrentState()
            NativeVisionRestorationOutcome(restored && removed, !removed, listOf(result.copy(restorationAvailable = !removed)))
        }
    }

    /** Only discards ownership; this action never writes to the Android controller. */
    fun keepCurrentState(): Boolean = runCatching {
        file.delete()
        !hasPendingRestoration()
    }.getOrDefault(false)

    private fun writeJournal(journal: Journal): Boolean {
        val bytes = JSONObject().put("schema", 1).put("fingerprint", journal.fingerprint)
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
        require(root.getInt("schema") == 1)
        return Journal(fromJson(root.getJSONObject("before")), fromJson(root.getJSONObject("expected")),
            fromJson(root.getJSONObject("previousExpected")), root.getString("fingerprint"))
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
                               val previousExpected: NativeMagnificationSnapshot, val fingerprint: String)
    private fun failure(value: NativeVisionValue, revision: Long, reason: String) = NativeVisionApplicationResult(
        capability = NativeVisionCapability.MAGNIFICATION, requested = value,
        status = NativeVisionApplicationStatus.REJECTED, engine = NativeVisionEngine.ANDROID_PUBLIC,
        provenance = "Journal local du MagnificationController existant ; contrôle de concurrence avant commande",
        reason = reason, timestampMillis = System.currentTimeMillis(), profileRevision = revision,
        restorationAvailable = hasPendingRestoration(),
    )
    companion object { private val gate = Mutex() }
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
