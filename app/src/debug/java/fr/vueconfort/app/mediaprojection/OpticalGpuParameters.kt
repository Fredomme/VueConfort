package fr.vueconfort.app.mediaprojection

internal data class OpticalGpuParameters(
    val enabled: Boolean = true,
    val brightness: Float = 1f,
    val contrast: Float = 1.08f,
    val gamma: Float = 1f,
    val saturation: Float = 1f,
    val temperature: Float = 0.04f,
    val whiteReduction: Float = 0.04f,
    val sharpness: Float = 0.18f,
    val directionalStrength: Float = 0.08f,
    val axisDegrees: Float = 0f,
    val horizontalStretch: Float = 1f,
    val verticalStretch: Float = 1f,
    val cylindricalDistortion: Float = 0f,
    val globalIntensity: Float = 0.75f,
    val haloLimit: Float = 0.12f,
    val threshold: Float = 0.015f,
    val regularization: Float = 0.25f
) {
    fun sanitized() = copy(
        brightness = brightness.coerceIn(0.7f, 1.25f), contrast = contrast.coerceIn(0.7f, 1.5f),
        gamma = gamma.coerceIn(0.7f, 1.4f), saturation = saturation.coerceIn(0f, 1.4f),
        temperature = temperature.coerceIn(-0.25f, 0.25f), whiteReduction = whiteReduction.coerceIn(0f, 0.4f),
        sharpness = sharpness.coerceIn(0f, 0.8f), directionalStrength = directionalStrength.coerceIn(-0.5f, 0.5f),
        axisDegrees = ((axisDegrees % 180f) + 180f) % 180f,
        horizontalStretch = horizontalStretch.coerceIn(0.9f, 1.1f), verticalStretch = verticalStretch.coerceIn(0.9f, 1.1f),
        cylindricalDistortion = cylindricalDistortion.coerceIn(-0.08f, 0.08f), globalIntensity = globalIntensity.coerceIn(0f, 1f),
        haloLimit = haloLimit.coerceIn(0.02f, 0.25f), threshold = threshold.coerceIn(0f, 0.08f), regularization = regularization.coerceIn(0f, 1f)
    )
}
