package fr.vueconfort.app.recommendation

import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.ui.screens.ComfortCalibrationAnswers

object RecommendationEngine {

    fun generateAssistProfile(
        answers: ComfortCalibrationAnswers,
        previous: AssistProfile? = null
    ): AssistProfile {
        var scale = answers.preferredScale
        if (answers.difficulty == "Très petits textes") scale += 0.5f
        if (answers.difficulty == "Longues lectures") scale -= 0.25f
        if ("Extérieur" in answers.usages) scale = scale.coerceAtLeast(2f)

        val lock = answers.difficulty == "Longues lectures" ||
            "Lecture longue" in answers.usages
        val description = answers.usages.ifEmpty { listOf("Usage général") }
            .joinToString(", ")

        return (previous ?: AssistProfile.defaults()
            .first { it.id == AssistProfile.CUSTOM_ID })
            .copy(
                name = "Personnalisé",
                description = description,
                magnificationScale = scale.coerceIn(1f, 8f),
                magnificationEnabled = scale > 1f,
                overlayAlpha = answers.overlayAlpha.coerceIn(0.55f, 1f),
                locked = lock,
                expanded = !lock,
                predefined = false,
                updatedAtMillis = System.currentTimeMillis()
            )
    }
}
