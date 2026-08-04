package fr.vueconfort.app.model

import fr.vueconfort.app.optical.OpticalSettings

data class VisualProfile(
    val id: String = DEFAULT_PROFILE_ID,
    val name: String = "Profil principal",

    val fontSizeSp: Float = 19f,
    val fontWeight: Int = 450,
    val letterSpacingSp: Float = 0.15f,
    val lineHeightMultiplier: Float = 1.40f,

    val foregroundArgb: Long = 0xFF202020,
    val backgroundArgb: Long = 0xFFF7F5EF,

    val columnWidthPercent: Float = 100f,
    val horizontalMarginDp: Float = 18f,

    val brightnessPercent: Int = 50,
    val warmthPercent: Int = 10,
    val desaturationPercent: Int = 0,

    val readingGuideEnabled: Boolean = false,
    val lineFocusEnabled: Boolean = false,
    val localZoomEnabled: Boolean = false,

    val calibrated: Boolean = false,
    val calibrationConfidence: Float = 0f,

    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    companion object {
        const val DEFAULT_PROFILE_ID = "main"
    }
}

data class AssistProfile(
    val id: String,
    val name: String,
    val description: String,
    val magnificationScale: Float,
    val magnificationEnabled: Boolean,
    val overlayAlpha: Float,
    val locked: Boolean,
    val expanded: Boolean,
    val predefined: Boolean,
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val optical: OpticalSettings = OpticalSettings.Neutral
) {
    fun sanitized() = copy(
        name = name.trim().ifEmpty { "Profil sans nom" }.take(40),
        description = description.trim().take(100),
        magnificationScale = magnificationScale.coerceIn(1f, 8f),
        overlayAlpha = overlayAlpha.coerceIn(0.55f, 1f),
        optical = optical.sanitized()
    )

    companion object {
        const val STANDARD_ID = "standard"
        const val SMALL_TEXT_ID = "small_text"
        const val READING_ID = "reading"
        const val LONG_READING_ID = "long_reading"
        const val OUTDOOR_ID = "outdoor"
        const val CUSTOM_ID = "custom"

        fun defaults(now: Long = System.currentTimeMillis()) = listOf(
            AssistProfile(STANDARD_ID, "Standard", "Usage général", 1.5f, false, 0.72f, false, false, true, now),
            AssistProfile(SMALL_TEXT_ID, "Petit texte", "Interfaces aux petits caractères", 3f, true, 0.88f, false, true, true, now),
            AssistProfile(READING_ID, "Lecture", "Articles, messages et documents", 2.2f, true, 0.68f, false, false, true, now),
            AssistProfile(LONG_READING_ID, "Lecture longue", "Lecture stable avec peu de manipulations", 1.8f, true, 0.62f, true, false, true, now),
            AssistProfile(OUTDOOR_ID, "Extérieur", "Commandes visibles pour consultation rapide", 2f, true, 1f, false, true, true, now),
            AssistProfile(CUSTOM_ID, "Personnalisé", "Réglages issus de la calibration", 2f, true, 0.82f, false, true, false, now)
        )
    }
}

enum class AutomationTrigger { APPLICATION, TIME_RANGE, AMBIENT_LIGHT }
enum class AmbientLightLevel { LOW, MEDIUM, HIGH }

data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    val profileId: String,
    val trigger: AutomationTrigger,
    val packageName: String = "",
    val startMinutes: Int = 0,
    val endMinutes: Int = 0,
    val daysMask: Int = 0b1111111,
    val lightLevel: AmbientLightLevel = AmbientLightLevel.MEDIUM,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun sanitized() = copy(
        name = name.trim().ifEmpty { "Règle automatique" }.take(50),
        priority = priority.coerceIn(0, 100),
        startMinutes = startMinutes.coerceIn(0, 1439),
        endMinutes = endMinutes.coerceIn(0, 1439)
    )
}

data class AutomationStatus(
    val manualUntilMillis: Long = 0L,
    val source: String = "Manuel",
    val reason: String = "",
    val activeRuleId: String = "",
    val profileId: String = AssistProfile.STANDARD_ID,
    val lastAppliedMillis: Long = 0L
) {
    val manualPaused: Boolean
        get() = manualUntilMillis == Long.MAX_VALUE ||
            manualUntilMillis > System.currentTimeMillis()
}
