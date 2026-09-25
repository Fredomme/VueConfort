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
        sharpness = sharpness.finiteBounded(0f, 0.8f, 0f),
        localContrast = localContrast.finiteBounded(0.7f, 1.5f, 1f),
        gamma = gamma.finiteBounded(0.7f, 1.4f, 1f),
        brightness = brightness.finiteBounded(0.7f, 1.25f, 1f),
        saturation = saturation.finiteBounded(0f, 1.4f, 1f),
        temperature = temperature.finiteBounded(-0.25f, 0.25f, 0f),
        whiteReduction = whiteReduction.finiteBounded(0f, 0.4f, 0f),
        edgeEnhancement = edgeEnhancement.finiteBounded(0f, 0.6f, 0f),
        horizontalStretch = horizontalStretch.finiteBounded(0.9f, 1.1f, 1f),
        verticalStretch = verticalStretch.finiteBounded(0.9f, 1.1f, 1f),
        cylindricalDistortion = cylindricalDistortion.finiteBounded(-0.08f, 0.08f, 0f),
        distortionAxisDegrees = distortionAxisDegrees.finiteBounded(0f, 180f, 0f),
        globalIntensity = globalIntensity.finiteBounded(0f, 1f, 0f)
    )

    companion object {
        val Neutral = OpticalSettings()
    }
}

// Non-finite imported/corrupt settings must never reach graphics uniforms.
private fun Float.finiteBounded(minimum: Float, maximum: Float, neutral: Float): Float =
    if (isFinite()) coerceIn(minimum, maximum) else neutral

/** Effective geometry and pixel operations, shared by the renderer and its contract tests. */
internal data class OpticalRenderPlan(
    val horizontalScale: Float = 1f,
    val verticalScale: Float = 1f,
    val distortion: Float = 0f,
    val sharpness: Float = 0f,
    val contrast: Float = 1f,
    val gamma: Float = 1f,
    val brightness: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0f,
    val whiteReduction: Float = 0f,
    val axisDegrees: Float = 0f,
    val intensity: Float = 0f
) {
    val hasPixelTreatments: Boolean get() = intensity > 0f && (
        sharpness != 0f || contrast != 1f || gamma != 1f || brightness != 1f ||
            saturation != 1f || temperature != 0f || whiteReduction != 0f)
    val needsShader: Boolean get() = distortion != 0f || hasPixelTreatments
    val isIdentity: Boolean get() = horizontalScale == 1f && verticalScale == 1f && !needsShader
}

internal fun OpticalSettings.renderPlan(bypass: Boolean = false): OpticalRenderPlan {
    val clean = sanitized()
    if (!clean.enabled || clean.globalIntensity == 0f) return OpticalRenderPlan()
    return OpticalRenderPlan(
        horizontalScale = 1f + (clean.horizontalStretch - 1f) * clean.globalIntensity,
        verticalScale = 1f + (clean.verticalStretch - 1f) * clean.globalIntensity,
        distortion = if (clean.quality == OpticalQuality.QUALITY)
            clean.cylindricalDistortion * clean.globalIntensity else 0f,
        sharpness = if (clean.quality == OpticalQuality.ECONOMY) 0f
            else clean.sharpness + clean.edgeEnhancement * 0.5f,
        contrast = clean.localContrast,
        gamma = clean.gamma,
        brightness = clean.brightness,
        saturation = clean.saturation,
        temperature = clean.temperature,
        whiteReduction = clean.whiteReduction,
        axisDegrees = clean.distortionAxisDegrees,
        intensity = if (bypass) 0f else clean.globalIntensity
    )
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
