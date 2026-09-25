package fr.vueconfort.app.nativevision

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

enum class NativeSettingReadStatus { PRESENT, ABSENT, INACCESSIBLE, ERROR }
data class NativeSettingRead(
    val status: NativeSettingReadStatus,
    val rawValue: String? = null,
) {
    val readable: Boolean get() = status == NativeSettingReadStatus.PRESENT || status == NativeSettingReadStatus.ABSENT
}
data class NativeVisionReadback(
    val value: NativeVisionValue?,
    val readable: Boolean,
    val provenance: String,
    val reason: String,
    val settings: Map<String, NativeSettingRead> = emptyMap(),
)

/** Read and guide only: there is deliberately no Settings setter in this commercial adapter. */
class SamsungNativeVisionAdapter(private val context: Context) {
    val isSamsung: Boolean get() = Build.MANUFACTURER.equals("samsung", ignoreCase = true)
    val isAttestedS25: Boolean get() = isAttestedFirmware(
        Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT, Build.VERSION.INCREMENTAL,
    )

    fun read(capability: NativeVisionCapability): NativeVisionReadback {
        val keys = secureKeys[capability].orEmpty()
        val rows = keys.associateWith(::readSecure)
        val provenance = "Lecture scalaire du fournisseur Android dans le contexte utilisateur courant"
        if (rows.values.any { !it.readable }) return NativeVisionReadback(
            null, false, provenance, "Android ne permet pas de vérifier tous ces paramètres.", rows,
        )
        fun raw(key: String): String? = rows[key]?.rawValue
        fun toggle(key: String): Boolean? = raw(key)?.toIntOrNull()?.let {
            when (it) { 0 -> false; 1 -> true; else -> null }
        }
        val value: NativeVisionValue? = when (capability) {
            NativeVisionCapability.RELUMINO -> if (isSamsung) {
                // These defaults were verified in AccessibilityManagerService on this exact firmware.
                // A missing row is preserved as ABSENT in the evidence, never misreported as a stored zero.
                val enabled = toggle("relumino_switch") ?: false.takeIf {
                    isAttestedS25 && rows["relumino_switch"]?.status == NativeSettingReadStatus.ABSENT
                }
                val thickness = raw("relumino_edge_thickness")?.toFloatOrNull()?.let(ReluminoThickness::fromValue)
                    ?: ReluminoThickness.TWO.takeIf {
                        isAttestedS25 && rows["relumino_edge_thickness"]?.status == NativeSettingReadStatus.ABSENT
                    }
                val color = raw("relumino_type")?.toIntOrNull()?.let(ReluminoColor::fromValue)
                    ?: ReluminoColor.ADAPTIVE.takeIf {
                        isAttestedS25 && rows["relumino_type"]?.status == NativeSettingReadStatus.ABSENT
                    }
                if (enabled != null && thickness != null && color != null)
                    NativeVisionValue.Relumino(enabled, thickness, color) else null
            } else null
            NativeVisionCapability.EXTRA_DIM -> {
                val enabled = toggle("reduce_bright_colors_activated")
                val strength = raw("reduce_bright_colors_level")?.toIntOrNull()
                if (enabled != null && strength != null) NativeVisionValue.ExtraDim(enabled, strength) else null
            }
            NativeVisionCapability.COLOR_FILTER -> {
                val enabled = toggle("color_lens_switch")
                val color = raw("color_lens_type")?.toIntOrNull()
                val opacity = raw("color_lens_opacity")?.toIntOrNull()?.takeIf { it in 0..8 }?.let { 20 + it * 5 }
                if (enabled != null && color != null && opacity != null)
                    NativeVisionValue.ColorFilter(enabled, color, opacity) else null
            }
            NativeVisionCapability.COLOR_CORRECTION -> {
                val enabled = toggle("accessibility_display_daltonizer_enabled")
                val mode = raw("accessibility_display_daltonizer")?.toIntOrNull()
                if (enabled != null && mode != null) NativeVisionValue.ColorCorrection(enabled, mode) else null
            }
            NativeVisionCapability.COLOR_INVERSION -> toggle("accessibility_display_inversion_enabled")?.let { NativeVisionValue.Toggle(it) }
            NativeVisionCapability.HIGH_CONTRAST_TEXT -> toggle("high_text_contrast_enabled")?.let { NativeVisionValue.Toggle(it) }
            NativeVisionCapability.EYE_COMFORT -> return readSystemToggle("blue_light_filter")
            NativeVisionCapability.SYSTEM_BRIGHTNESS -> return readSystemValue(Settings.System.SCREEN_BRIGHTNESS) {
                it.toIntOrNull()?.takeIf { value -> value in 0..255 }?.let { NativeVisionValue.Brightness(it) }
            }
            NativeVisionCapability.FONT_SCALE -> return readSystemValue(Settings.System.FONT_SCALE) {
                it.toFloatOrNull()?.takeIf { value -> value.isFinite() && value in 0.5f..2f }?.let { NativeVisionValue.FontScale(it) }
            }
            else -> null
        }
        val valid = value?.takeIf { it.isValidFor(capability) }
        return NativeVisionReadback(
            value = valid, readable = rows.isNotEmpty() && rows.values.all { it.readable },
            provenance = if (capability == NativeVisionCapability.RELUMINO && isAttestedS25 && rows.values.any {
                it.status == NativeSettingReadStatus.ABSENT
            }) "$provenance ; valeurs absentes interprétées selon les défauts du firmware S931BXXSCCZH1 attestés le 24/09/2026" else provenance,
            reason = if (valid != null) "État des paramètres relu ; cela ne mesure pas l’effet optique." else
                "État complet non déterminé ; aucune valeur par défaut n’est inventée.",
            settings = rows,
        )
    }

    fun apply(request: NativeVisionRequestedState, profileRevision: Long = request.revision): List<NativeVisionApplicationResult> =
        request.values.filterKeys { it != NativeVisionCapability.MAGNIFICATION }.map { (capability, value) ->
            val valid = value.isValidFor(capability)
            val supported = !isSamsungOnly(capability) || isSamsung
            val established = supported && hasPresenceEvidence(capability)
            NativeVisionApplicationResult(
                capability, value,
                status = when {
                    !valid -> NativeVisionApplicationStatus.REJECTED
                    !supported -> NativeVisionApplicationStatus.UNSUPPORTED
                    !established -> NativeVisionApplicationStatus.UNAVAILABLE
                    guidanceIntent(capability) == null -> NativeVisionApplicationStatus.UNAVAILABLE
                    else -> NativeVisionApplicationStatus.NEEDS_USER_ACTION
                },
                engine = engine(capability), provenance = "Guidage vers une activité système exportée et autorisée",
                reason = when {
                    !valid -> "La valeur demandée n’est pas valide."
                    !supported -> "Cette fonction Samsung n’est pas proposée sur cet appareil."
                    !established -> "La présence de cette fonction n’a pas été établie sur cet appareil."
                    else -> guidanceText(capability)
                }, timestampMillis = System.currentTimeMillis(), profileRevision = profileRevision,
            )
        }

    /** Read-back confirms configuration; it never claims VueConfort performed a protected write. */
    fun verify(request: NativeVisionRequestedState, profileRevision: Long = request.revision): List<NativeVisionApplicationResult> =
        apply(request, profileRevision).map { result ->
            if (result.status != NativeVisionApplicationStatus.NEEDS_USER_ACTION) return@map result
            val observation = read(result.capability)
            val matched = observation.value != null && observation.value == result.requested
            result.copy(
                applied = observation.value,
                provenance = observation.provenance,
                reason = if (matched) "Le réglage demandé est confirmé par lecture des paramètres Android." else observation.reason,
                confirmation = if (matched) NativeVisionConfirmation.READ_BACK_CONFIRMED else NativeVisionConfirmation.UNCONFIRMED,
            )
        }

    fun guidanceIntent(capability: NativeVisionCapability): Intent? =
        candidates(capability).firstNotNullOfOrNull(::resolveSafeIntent)

    fun specificSettingsRouteAvailable(capability: NativeVisionCapability): Boolean =
        specificCandidates(capability).any { resolveSafeIntent(it) != null }

    fun hasPresenceEvidence(capability: NativeVisionCapability, observation: NativeVisionReadback = read(capability)): Boolean {
        if (isSamsungOnly(capability) && !isSamsung) return false
        if (capability == NativeVisionCapability.RELUMINO && Build.VERSION.SDK_INT < 34) return false
        if (capability == NativeVisionCapability.EXTRA_DIM && Build.VERSION.SDK_INT < 31) return false
        if (isAttestedS25 || specificSettingsRouteAvailable(capability)) return true
        if (capability in setOf(NativeVisionCapability.SYSTEM_BRIGHTNESS, NativeVisionCapability.FONT_SCALE,
                NativeVisionCapability.SCREEN_ZOOM)) return true
        // A generic accessibility landing page is not evidence of a particular visual function.
        if (capability == NativeVisionCapability.RELUMINO) return observation.value != null &&
            observation.settings.size == 3 && observation.settings.values.all { it.status == NativeSettingReadStatus.PRESENT }
        return observation.settings.values.any { it.status == NativeSettingReadStatus.PRESENT }
    }

    fun openSettings(capability: NativeVisionCapability): Boolean {
        // Resolve again at the point of use: an OS update or permission revocation invalidates cached routes.
        for (candidate in candidates(capability)) {
            val intent = resolveSafeIntent(candidate) ?: continue
            if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
        }
        return false
    }

    fun guidanceText(capability: NativeVisionCapability): String = when (capability) {
        NativeVisionCapability.RELUMINO -> "Dans Accessibilité, ouvrez Améliorations pour la vision, puis Contour Relumino. Appliquez les réglages indiqués et revenez dans VueConfort."
        NativeVisionCapability.MAGNIFICATION -> "Dans Accessibilité, autorisez le service VueConfort à contrôler le grossissement."
        NativeVisionCapability.EXTRA_DIM -> "Ouvrez Atténuation supplémentaire, choisissez le niveau souhaité, puis revenez dans VueConfort."
        NativeVisionCapability.COLOR_FILTER -> "Choisissez l’activation, la couleur et l’opacité du filtre dans les réglages Samsung."
        NativeVisionCapability.COLOR_CORRECTION -> "Choisissez le mode de correction des couleurs dans les réglages d’accessibilité."
        NativeVisionCapability.COLOR_INVERSION -> "Activez ou désactivez l’inversion des couleurs dans les réglages d’accessibilité."
        NativeVisionCapability.HIGH_CONTRAST_TEXT -> "Choisissez le contraste des polices dans les réglages d’accessibilité."
        NativeVisionCapability.EYE_COMFORT -> "Dans les paramètres d’écran, configurez le confort visuel Samsung, puis revenez dans VueConfort."
        NativeVisionCapability.SYSTEM_BRIGHTNESS -> "Réglez la luminosité dans les paramètres d’écran. VueConfort ne modifie pas votre luminosité automatique."
        NativeVisionCapability.FONT_SCALE -> "Dans les paramètres d’écran, choisissez la taille des polices."
        NativeVisionCapability.SCREEN_ZOOM -> "Dans les paramètres d’écran, choisissez le zoom de l’écran."
    }

    private fun candidates(capability: NativeVisionCapability): List<Intent> {
        if (isSamsungOnly(capability) && !isSamsung) return emptyList()
        val display = capability in setOf(NativeVisionCapability.EYE_COMFORT, NativeVisionCapability.SYSTEM_BRIGHTNESS,
            NativeVisionCapability.FONT_SCALE, NativeVisionCapability.SCREEN_ZOOM)
        return specificCandidates(capability) + Intent(if (display) Settings.ACTION_DISPLAY_SETTINGS else Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    private fun specificCandidates(capability: NativeVisionCapability): List<Intent> {
        if (!isSamsung) return emptyList()
        val className = when (capability) {
            NativeVisionCapability.EXTRA_DIM -> "com.android.settings.Settings\$ReduceBrightColorsSettingsActivity"
            NativeVisionCapability.COLOR_FILTER -> "com.android.settings.Settings\$AccessibilityColorLensSettingsActivity"
            NativeVisionCapability.COLOR_CORRECTION -> "com.android.settings.Settings\$AccessibilityDaltonizerSettingsActivity"
            NativeVisionCapability.HIGH_CONTRAST_TEXT -> "com.android.settings.Settings\$AccessibilityHighContrastFontSettingsActivity"
            // The inspected EyeComfort activity requires an OEM permission: the resolver checks it.
            NativeVisionCapability.EYE_COMFORT -> "com.android.settings.Settings\$EyeComfortSettingsActivity"
            else -> null
        } ?: return emptyList()
        return listOf(Intent().setComponent(ComponentName("com.android.settings", className)))
    }

    private fun resolveSafeIntent(candidate: Intent): Intent? = runCatching {
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val info = candidate.component?.let { pm.getActivityInfo(it, 0) }
            ?: pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return@runCatching null
        val permissionGranted = info.permission.isNullOrEmpty() ||
            context.checkSelfPermission(info.permission) == PackageManager.PERMISSION_GRANTED
        if (!NativeSettingsActivityPolicy.isLaunchable(info.exported, info.enabled, info.applicationInfo.enabled,
                permissionGranted, info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)) {
            return@runCatching null
        }
        Intent(candidate).setComponent(ComponentName(info.packageName, info.name))
    }.getOrNull()

    private fun readSecure(key: String): NativeSettingRead = readScalar { Settings.Secure.getString(context.contentResolver, key) }
    private fun readSystemToggle(key: String): NativeVisionReadback = readSystemValue(key) { raw ->
        when (raw.toIntOrNull()) { 0 -> NativeVisionValue.Toggle(false); 1 -> NativeVisionValue.Toggle(true); else -> null }
    }
    private fun readSystemValue(key: String, parse: (String) -> NativeVisionValue?): NativeVisionReadback {
        val row = readScalar { Settings.System.getString(context.contentResolver, key) }
        return NativeVisionReadback(row.rawValue?.let(parse), row.readable,
            "Lecture scalaire Android System/$key, sans écriture", "État relu dans les paramètres Android.", mapOf(key to row))
    }

    companion object {
        fun isAttestedFirmware(manufacturer: String, model: String, sdk: Int, incremental: String): Boolean =
            manufacturer.equals("samsung", ignoreCase = true) && model == "SM-S931B" && sdk == 36 && incremental == "S931BXXSCCZH1"

        fun isSamsungOnly(capability: NativeVisionCapability): Boolean = capability in setOf(
            NativeVisionCapability.RELUMINO, NativeVisionCapability.COLOR_FILTER, NativeVisionCapability.EYE_COMFORT,
        )
        private fun engine(capability: NativeVisionCapability) = if (isSamsungOnly(capability))
            NativeVisionEngine.SAMSUNG_SETTINGS else NativeVisionEngine.ANDROID_PUBLIC

        internal fun readScalar(read: () -> String?): NativeSettingRead = try {
            read()?.let { NativeSettingRead(NativeSettingReadStatus.PRESENT, it) }
                ?: NativeSettingRead(NativeSettingReadStatus.ABSENT)
        } catch (_: SecurityException) { NativeSettingRead(NativeSettingReadStatus.INACCESSIBLE) }
          catch (_: RuntimeException) { NativeSettingRead(NativeSettingReadStatus.ERROR) }

        internal val secureKeys = mapOf(
            NativeVisionCapability.RELUMINO to listOf("relumino_switch", "relumino_edge_thickness", "relumino_type"),
            NativeVisionCapability.EXTRA_DIM to listOf("reduce_bright_colors_activated", "reduce_bright_colors_level"),
            NativeVisionCapability.COLOR_FILTER to listOf("color_lens_switch", "color_lens_type", "color_lens_opacity"),
            NativeVisionCapability.COLOR_CORRECTION to listOf("accessibility_display_daltonizer_enabled", "accessibility_display_daltonizer"),
            NativeVisionCapability.COLOR_INVERSION to listOf("accessibility_display_inversion_enabled"),
            NativeVisionCapability.HIGH_CONTRAST_TEXT to listOf("high_text_contrast_enabled"),
        )
    }
}

internal object NativeSettingsActivityPolicy {
    fun isLaunchable(exported: Boolean, enabled: Boolean, appEnabled: Boolean,
                     permissionGranted: Boolean, systemApp: Boolean): Boolean =
        exported && enabled && appEnabled && permissionGranted && systemApp
}
