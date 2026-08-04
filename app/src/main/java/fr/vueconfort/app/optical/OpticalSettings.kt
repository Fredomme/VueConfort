package fr.vueconfort.app.optical

enum class OpticalQuality { ECONOMY, BALANCED, QUALITY }

data class OpticalSettings(
    val enabled: Boolean = false,
    val sharpness: Float = 0f,
    val localContrast: Float = 1f,
    val gamma: Float = 1f,
    val brightness: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val whiteReduction: Float = 0f,
    val edgeEnhancement: Float = 0f,
    val horizontalStretch: Float = 1f,
    val verticalStretch: Float = 1f,
    val cylindricalDistortion: Float = 0f,
    val distortionAxisDegrees: Float = 0f,
    val globalIntensity: Float = 1f,
    val quality: OpticalQuality = OpticalQuality.BALANCED
) {
    fun sanitized() = copy(
        sharpness = sharpness.coerceIn(0f, 0.8f),
        localContrast = localContrast.coerceIn(0.7f, 1.5f),
        gamma = gamma.coerceIn(0.7f, 1.4f),
        brightness = brightness.coerceIn(0.7f, 1.25f),
        saturation = saturation.coerceIn(0f, 1.4f),
        temperature = temperature.coerceIn(-0.25f, 0.25f),
        whiteReduction = whiteReduction.coerceIn(0f, 0.4f),
        edgeEnhancement = edgeEnhancement.coerceIn(0f, 0.6f),
        horizontalStretch = horizontalStretch.coerceIn(0.9f, 1.1f),
        verticalStretch = verticalStretch.coerceIn(0.9f, 1.1f),
        cylindricalDistortion = cylindricalDistortion.coerceIn(-0.08f, 0.08f),
        distortionAxisDegrees = distortionAxisDegrees.coerceIn(0f, 180f),
        globalIntensity = globalIntensity.coerceIn(0f, 1f)
    )

    companion object {
        val Neutral = OpticalSettings()
    }
}

object OpticalGuidanceEngine {
    fun sharpness(level: Int) = OpticalSettings(
        enabled = level > 0,
        sharpness = listOf(0f, 0.18f, 0.32f, 0.48f)[level.coerceIn(0, 3)],
        edgeEnhancement = listOf(0f, 0.08f, 0.14f, 0.2f)[level.coerceIn(0, 3)]
    )

    fun contrastGamma(choice: Int) = when (choice) {
        0 -> OpticalSettings(enabled = true, localContrast = 0.88f, gamma = 1.08f)
        2 -> OpticalSettings(enabled = true, localContrast = 1.28f, gamma = 0.92f)
        3 -> OpticalSettings(enabled = true, localContrast = 1.05f, gamma = 1.22f)
        4 -> OpticalSettings(enabled = true, localContrast = 1.05f, gamma = 0.82f)
        else -> OpticalSettings(enabled = true, localContrast = 1.08f, gamma = 1f)
    }

    fun color(choice: Int) = when (choice) {
        1 -> OpticalSettings(enabled = true, temperature = 0.1f)
        2 -> OpticalSettings(enabled = true, temperature = 0.2f, whiteReduction = 0.12f)
        3 -> OpticalSettings(enabled = true, temperature = -0.1f)
        4 -> OpticalSettings(enabled = true, whiteReduction = 0.28f, brightness = 0.94f)
        else -> OpticalSettings(enabled = true)
    }
}
