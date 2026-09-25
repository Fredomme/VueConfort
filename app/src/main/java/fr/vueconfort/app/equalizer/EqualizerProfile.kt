package fr.vueconfort.app.equalizer

import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.nativevision.NativeVisionCodec
import fr.vueconfort.app.nativevision.NativeVisionProfile
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class EqualizerScene { TEXT, DETAILS, PHOTO }
enum class EqualizerRenderDecision { APPLY, IDENTITY, REJECTED }

/** User preferences, never a prescription or an estimate of the user's refractive error. */
data class EqualizerPreferences(
    val sizeScale: Float = 1f,
    val sharpness: Float = 0f,
    val contrast: Float = 1f,
    val lightComfort: Float = 0f,
    val fontWeight: Int = 400,
    val intensity: Float = 1f
) {
    // Bounds limit this renderer's controls; they are not medical validation.
    fun validated() = copy(
        sizeScale = sizeScale.finiteOr(1f).coerceIn(1f, 2f),
        sharpness = sharpness.finiteOr(0f).coerceIn(0f, 0.8f),
        contrast = contrast.finiteOr(1f).coerceIn(0.7f, 1.5f),
        lightComfort = lightComfort.finiteOr(0f).coerceIn(0f, 1f),
        fontWeight = fontWeight.coerceIn(400, 800),
        intensity = intensity.finiteOr(1f).coerceIn(0f, 1f)
    )

    companion object { val Neutral = EqualizerPreferences() }
}

/** A reference to the separately stored, user-confirmed bilan, not a duplicate health record. */
data class ConfirmedBilanReference(val updatedAtMillis: Long, val source: String) {
    companion object {
        fun from(value: OpticalPrescription?): ConfirmedBilanReference? =
            value?.takeIf { it.isValid && it.confirmedByUser }?.let {
                ConfirmedBilanReference(it.updatedAtMillis, it.source.name)
            }
    }
}

/** Optional declared context. Missing measurements stay unknown rather than being inferred. */
data class EqualizerContext(
    val declaredDistanceCm: Float? = null,
    val correctionWorn: Boolean? = null
)

/** A future engine's output is separate from preferences and from what was actually rendered. */
data class EqualizerCalculatedParameters(
    val engineVersion: String,
    val sourceRevision: Long,
    val parameters: Map<String, Float>,
    val provenance: String
)

/** A receipt for a rendered revision. Holding Original must never overwrite this record. */
data class EqualizerRenderRecord(
    val engineVersion: String,
    val decision: EqualizerRenderDecision,
    val parameters: Map<String, Float>,
    val reasons: List<String> = emptyList(),
    val sourceRevision: Long
)

data class EqualizerProvenance(
    val origin: String = "USER_PERCEPTUAL",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis
)

/**
 * One owner for the equalizer's complete state. No global typography/refinement sidecar is written.
 * Named, versioned fields and namespaced extensions allow later frequency/directional/optical
 * models without pretending those controls are already implemented in the current renderer.
 */
data class EqualizerProfile(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val id: String = PERSONAL_PROFILE_ID,
    val preferences: EqualizerPreferences = EqualizerPreferences.Neutral,
    val scene: EqualizerScene = EqualizerScene.TEXT,
    val revision: Long = 0L,
    val confirmedBilan: ConfirmedBilanReference? = null,
    val context: EqualizerContext = EqualizerContext(),
    val calculated: EqualizerCalculatedParameters? = null,
    val applied: EqualizerRenderRecord? = null,
    val provenance: EqualizerProvenance = EqualizerProvenance(),
    val extensions: Map<String, String> = emptyMap(),
    val nativeVision: NativeVisionProfile = NativeVisionProfile()
) {
    fun validated(): EqualizerProfile {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) { "Version du profil non prise en charge." }
        require(id.isNotBlank() && id.length <= 100) { "Identifiant de profil invalide." }
        require(revision >= 0) { "Révision de profil invalide." }
        require(context.declaredDistanceCm == null ||
            (context.declaredDistanceCm.isFinite() && context.declaredDistanceCm in 10f..200f))
        require(confirmedBilan == null || (confirmedBilan.updatedAtMillis >= 0 && confirmedBilan.source.isNotBlank()))
        require(provenance.origin.isNotBlank() && provenance.createdAtMillis >= 0 && provenance.updatedAtMillis >= 0)
        fun checkParameters(values: Map<String, Float>) {
            require(values.size <= 128 && values.all { (key, value) -> key.isNotBlank() && key.length <= 100 && value.isFinite() })
        }
        calculated?.let {
            require(it.engineVersion.isNotBlank() && it.provenance.isNotBlank() && it.sourceRevision == revision)
            checkParameters(it.parameters)
        }
        applied?.let {
            require(it.engineVersion.isNotBlank() && it.sourceRevision == revision)
            require(it.reasons.size <= 32 && it.reasons.all { reason -> reason.length <= 500 })
            checkParameters(it.parameters)
        }
        require(extensions.size <= 128 && extensions.all { (key, value) -> key.isNotBlank() && key.length <= 100 && value.length <= 4_096 })
        nativeVision.validated()
        return copy(preferences = preferences.validated())
    }

    fun sameUserChoices(other: EqualizerProfile): Boolean =
        preferences == other.preferences && scene == other.scene && context == other.context && extensions == other.extensions

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val PERSONAL_PROFILE_ID = "personal-equalizer"
    }
}

/**
 * Deterministic named-field codec, using the project's existing portable Base64 convention.
 * Android's JSONObject is deliberately not required, so the identical codec is tested on the JVM.
 * Unknown schema versions fail closed and are never silently rewritten by simply opening the UI.
 */
object EqualizerProfileCodec {
    fun encode(profile: EqualizerProfile): String {
        val p = profile.validated()
        val fields = linkedMapOf<String, String>()
        fun put(key: String, value: Any?) { if (value != null) fields[key] = value.toString() }
        put("schema", p.schemaVersion); put("id", p.id); put("scene", p.scene.name); put("revision", p.revision)
        put("size", p.preferences.sizeScale); put("sharpness", p.preferences.sharpness)
        put("contrast", p.preferences.contrast); put("light", p.preferences.lightComfort)
        put("weight", p.preferences.fontWeight); put("intensity", p.preferences.intensity)
        put("distance", p.context.declaredDistanceCm); put("correctionWorn", p.context.correctionWorn)
        p.confirmedBilan?.let { put("bilan.updated", it.updatedAtMillis); put("bilan.source", it.source) }
        put("nativeVision", NativeVisionCodec.encode(p.nativeVision))
        put("origin", p.provenance.origin); put("created", p.provenance.createdAtMillis); put("updated", p.provenance.updatedAtMillis)
        p.calculated?.let {
            put("computed.engine", it.engineVersion); put("computed.revision", it.sourceRevision); put("computed.origin", it.provenance)
            it.parameters.toSortedMap().forEach { (key, value) -> put("computed.parameter.$key", value) }
        }
        p.applied?.let {
            put("render.engine", it.engineVersion); put("render.revision", it.sourceRevision); put("render.decision", it.decision.name)
            it.parameters.toSortedMap().forEach { (key, value) -> put("render.parameter.$key", value) }
            it.reasons.forEachIndexed { index, reason -> put("render.reason.$index", reason) }
        }
        p.extensions.toSortedMap().forEach { (key, value) -> put("extension.$key", value) }
        return fields.entries.joinToString("\n") { "${it.key.encoded()}=${it.value.encoded()}" }
    }

    fun decode(raw: String?): EqualizerProfile? {
        if (raw.isNullOrBlank() || raw.length > 1_048_576) return null
        return runCatching {
            val fields = linkedMapOf<String, String>()
            raw.lineSequence().forEach { line ->
                require(fields.size < 1_024)
                val separator = line.indexOf('=')
                require(separator > 0)
                val key = line.substring(0, separator).decoded()
                require(!fields.containsKey(key)) { "Champ de profil dupliqué." }
                fields[key] = line.substring(separator + 1).decoded()
            }
            fun value(key: String): String = requireNotNull(fields[key]) { "Champ de profil absent : $key" }
            fun parameters(prefix: String) = fields.filterKeys { it.startsWith(prefix) }
                .mapKeys { it.key.removePrefix(prefix) }.mapValues { it.value.toFloat() }
            val version = value("schema").toInt()
            require(version in 1..EqualizerProfile.CURRENT_SCHEMA_VERSION)
            EqualizerProfile(
                // A schema-1 record is migrated in memory only; opening the UI never overwrites it.
                schemaVersion = EqualizerProfile.CURRENT_SCHEMA_VERSION, id = value("id"), scene = EqualizerScene.valueOf(value("scene")), revision = value("revision").toLong(),
                preferences = EqualizerPreferences(
                    value("size").toFloat(), value("sharpness").toFloat(), value("contrast").toFloat(),
                    value("light").toFloat(), value("weight").toInt(), value("intensity").toFloat()
                ),
                confirmedBilan = fields["bilan.updated"]?.let { ConfirmedBilanReference(it.toLong(), value("bilan.source")) },
                context = EqualizerContext(fields["distance"]?.toFloat(), fields["correctionWorn"]?.toBooleanStrict()),
                calculated = fields["computed.engine"]?.let {
                    EqualizerCalculatedParameters(it, value("computed.revision").toLong(), parameters("computed.parameter."), value("computed.origin"))
                },
                applied = fields["render.engine"]?.let {
                    EqualizerRenderRecord(it, EqualizerRenderDecision.valueOf(value("render.decision")), parameters("render.parameter."),
                        fields.filterKeys { key -> key.startsWith("render.reason.") }
                            .entries.sortedBy { entry -> entry.key.removePrefix("render.reason.").toInt() }.map { entry -> entry.value },
                        value("render.revision").toLong())
                },
                provenance = EqualizerProvenance(value("origin"), value("created").toLong(), value("updated").toLong()),
                extensions = fields.filterKeys { it.startsWith("extension.") }.mapKeys { it.key.removePrefix("extension.") },
                nativeVision = if (version == 1) NativeVisionProfile()
                    else requireNotNull(NativeVisionCodec.decode(value("nativeVision")))
            ).validated()
        }.getOrNull()
    }

    private fun String.encoded() = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(StandardCharsets.UTF_8))
    private fun String.decoded() = String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
}

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback
