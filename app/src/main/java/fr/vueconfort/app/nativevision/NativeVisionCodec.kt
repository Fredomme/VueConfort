package fr.vueconfort.app.nativevision

import java.nio.charset.StandardCharsets
import java.util.Base64

/** Portable codec embedded inside EqualizerProfile's existing storage record. No Android dependency. */
object NativeVisionCodec {
    fun encode(profile: NativeVisionProfile): String {
        val p = profile.validated()
        val f = linkedMapOf<String, String>()
        fun put(key: String, value: Any?) { if (value != null) f[key] = value.toString() }
        fun request(prefix: String, state: NativeVisionRequestedState) {
            put("$prefix.revision", state.revision); put("$prefix.updated", state.updatedAtMillis)
            put("$prefix.provenance", state.provenance)
            state.values.toSortedMap(compareBy { it.name }).forEach { (cap, value) -> put("$prefix.value.${cap.name}", encodeValue(value)) }
        }
        put("schema", p.schemaVersion); put("enabled", p.enabled); put("disable", p.preferences.disableBehavior.name)
        request("requested", p.requested); request("recommended", p.preferences.recommended)
        p.applied.results.toSortedMap(compareBy { it.name }).forEach { (capability, result) ->
            val prefix = "result.${capability.name}"
            put("$prefix.requested", encodeValue(result.requested)); put("$prefix.applied", result.applied?.let(::encodeValue))
            put("$prefix.status", result.status.name); put("$prefix.engine", result.engine.name)
            put("$prefix.provenance", result.provenance); put("$prefix.reason", result.reason)
            put("$prefix.at", result.timestampMillis); put("$prefix.revision", result.profileRevision)
            put("$prefix.restore", result.restorationAvailable); put("$prefix.confirmation", result.confirmation.name)
        }
        p.restorationReferences.toSortedMap(compareBy { it.name }).forEach { (capability, reference) -> put("restore.${capability.name}", reference) }
        p.capabilities?.let { snapshot ->
            put("capabilities.schema", snapshot.schemaVersion); put("capabilities.at", snapshot.observedAtMillis)
            put("capabilities.variant", snapshot.variant.name)
            put("device.manufacturer", snapshot.device.manufacturer); put("device.model", snapshot.device.model)
            put("device.sdk", snapshot.device.sdkInt); put("device.oneUi", snapshot.device.oneUiVersion)
            put("device.fingerprint", snapshot.device.buildFingerprint); put("device.user", snapshot.device.androidUserId)
            snapshot.capabilities.toSortedMap(compareBy { it.name }).forEach { (capability, state) ->
                val prefix = "capability.${capability.name}"
                put("$prefix.presence", state.presence.name); put("$prefix.availability", state.availability.name)
                put("$prefix.auto", state.canApplyAutomatically); put("$prefix.readable", state.readable)
                put("$prefix.engine", state.engine.name); put("$prefix.provenance", state.provenance); put("$prefix.reason", state.reason)
            }
        }
        put("complete", true)
        return f.entries.joinToString("\n") { "${it.key.encoded()}=${it.value.encoded()}" }
    }

    fun decode(raw: String?): NativeVisionProfile? {
        if (raw.isNullOrBlank() || raw.length > 524_288) return null
        return runCatching {
            val f = linkedMapOf<String, String>()
            raw.lineSequence().forEach { line ->
                require(f.size < 1_024)
                val separator = line.indexOf('='); require(separator > 0)
                val key = line.substring(0, separator).decoded()
                require(key !in f)
                f[key] = line.substring(separator + 1).decoded()
            }
            fun value(key: String) = requireNotNull(f[key]) { "Champ natif absent : $key" }
            fun request(prefix: String) = NativeVisionRequestedState(
                values = f.filterKeys { it.startsWith("$prefix.value.") }.map { (key, encoded) ->
                    NativeVisionCapability.valueOf(key.removePrefix("$prefix.value.")) to decodeValue(encoded)
                }.toMap(), revision = value("$prefix.revision").toLong(), updatedAtMillis = value("$prefix.updated").toLong(),
                provenance = value("$prefix.provenance")
            ).validated()
            require(value("complete").toBooleanStrict())
            val resultCaps = f.keys.filter { it.startsWith("result.") }.map { it.split('.')[1] }.distinct().map(NativeVisionCapability::valueOf)
            val results = resultCaps.associateWith { capability ->
                val prefix = "result.${capability.name}"
                NativeVisionApplicationResult(capability, decodeValue(value("$prefix.requested")),
                    NativeVisionApplicationStatus.valueOf(value("$prefix.status")), f["$prefix.applied"]?.let(::decodeValue),
                    NativeVisionEngine.valueOf(value("$prefix.engine")), value("$prefix.provenance"), value("$prefix.reason"),
                    value("$prefix.at").toLong(), value("$prefix.revision").toLong(), value("$prefix.restore").toBooleanStrict(),
                    NativeVisionConfirmation.valueOf(value("$prefix.confirmation")))
            }
            val capabilities = f["capabilities.schema"]?.let { version ->
                val stateCaps = f.keys.filter { it.startsWith("capability.") }.map { it.split('.')[1] }.distinct().map(NativeVisionCapability::valueOf)
                NativeVisionCapabilities(version.toInt(), NativeVisionDevice(value("device.manufacturer"), value("device.model"),
                    value("device.sdk").toInt(), f["device.oneUi"], value("device.fingerprint"), f["device.user"]?.toInt()),
                    NativeVisionVariant.valueOf(value("capabilities.variant")), value("capabilities.at").toLong(),
                    stateCaps.associateWith { capability ->
                        val prefix = "capability.${capability.name}"
                        NativeVisionCapabilityState(capability, NativeVisionPresence.valueOf(value("$prefix.presence")),
                            NativeVisionAvailability.valueOf(value("$prefix.availability")), value("$prefix.auto").toBooleanStrict(),
                            value("$prefix.readable").toBooleanStrict(), NativeVisionEngine.valueOf(value("$prefix.engine")),
                            value("$prefix.provenance"), value("$prefix.reason"))
                    })
            }
            NativeVisionProfile(schemaVersion = value("schema").toInt(), enabled = value("enabled").toBooleanStrict(),
                preferences = NativeVisionPreferences(request("recommended"), NativeVisionDisableBehavior.valueOf(value("disable"))),
                requested = request("requested"), applied = NativeVisionAppliedState(results), capabilities = capabilities,
                restorationReferences = f.filterKeys { it.startsWith("restore.") }.mapKeys { NativeVisionCapability.valueOf(it.key.removePrefix("restore.")) }
            ).validated()
        }.getOrNull()
    }

    private fun encodeValue(value: NativeVisionValue): String = when (value) {
        is NativeVisionValue.Relumino -> listOf("relumino", value.enabled, value.thickness.name, value.color.name)
        is NativeVisionValue.Magnification -> listOf("magnification", value.enabled, value.scale, value.centerX ?: "", value.centerY ?: "", value.mode.name)
        is NativeVisionValue.ExtraDim -> listOf("extra_dim", value.enabled, value.strength)
        is NativeVisionValue.ColorFilter -> listOf("color_filter", value.enabled, value.color, value.opacityPercent)
        is NativeVisionValue.ColorCorrection -> listOf("color_correction", value.enabled, value.mode)
        is NativeVisionValue.Toggle -> listOf("toggle", value.enabled)
        is NativeVisionValue.Brightness -> listOf("brightness", value.value)
        is NativeVisionValue.FontScale -> listOf("font_scale", value.scale)
        is NativeVisionValue.ScreenZoom -> listOf("screen_zoom", value.index)
    }.joinToString("|")

    private fun decodeValue(raw: String): NativeVisionValue {
        val f = raw.split('|')
        fun exact(count: Int) { require(f.size == count) }
        return when (f[0]) {
            "relumino" -> { exact(4); NativeVisionValue.Relumino(f[1].toBooleanStrict(), ReluminoThickness.valueOf(f[2]), ReluminoColor.valueOf(f[3])) }
            "magnification" -> { exact(6); NativeVisionValue.Magnification(f[1].toBooleanStrict(), f[2].toFloat(),
                f[3].takeIf { it.isNotEmpty() }?.toFloat(), f[4].takeIf { it.isNotEmpty() }?.toFloat(), NativeMagnificationMode.valueOf(f[5])) }
            "extra_dim" -> { exact(3); NativeVisionValue.ExtraDim(f[1].toBooleanStrict(), f[2].toInt()) }
            "color_filter" -> { exact(4); NativeVisionValue.ColorFilter(f[1].toBooleanStrict(), f[2].toInt(), f[3].toInt()) }
            "color_correction" -> { exact(3); NativeVisionValue.ColorCorrection(f[1].toBooleanStrict(), f[2].toInt()) }
            "toggle" -> { exact(2); NativeVisionValue.Toggle(f[1].toBooleanStrict()) }
            "brightness" -> { exact(2); NativeVisionValue.Brightness(f[1].toInt()) }
            "font_scale" -> { exact(2); NativeVisionValue.FontScale(f[1].toFloat()) }
            "screen_zoom" -> { exact(2); NativeVisionValue.ScreenZoom(f[1].toInt()) }
            else -> error("Type de commande natif inconnu.")
        }
    }
    private fun String.encoded() = Base64.getUrlEncoder().withoutPadding().encodeToString(toByteArray(StandardCharsets.UTF_8))
    private fun String.decoded() = String(Base64.getUrlDecoder().decode(this), StandardCharsets.UTF_8)
}
