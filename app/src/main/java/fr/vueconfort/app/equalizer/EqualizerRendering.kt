package fr.vueconfort.app.equalizer

import fr.vueconfort.app.optical.OpticalSettings
import fr.vueconfort.app.optical.renderPlan

/** Fast perceptual mapping only. No preference is interpreted as SPH, CYL, AXE or ADD. */
fun EqualizerPreferences.toOpticalSettings(): OpticalSettings {
    val p = validated()
    return OpticalSettings(
        enabled = true,
        sharpness = p.sharpness,
        localContrast = p.contrast,
        // A monotone, bounded dimming of image levels; never changes system screen brightness.
        brightness = 1f - .18f * p.lightComfort,
        whiteReduction = .30f * p.lightComfort,
        globalIntensity = p.intensity
    )
}

fun EqualizerPreferences.renderRecord(scene: EqualizerScene, revision: Long,
                                     supportsPixels: Boolean = true): EqualizerRenderRecord {
    val p = validated()
    val plan = p.toOpticalSettings().renderPlan()
    val stroke = if (scene == EqualizerScene.TEXT) (p.fontWeight - 400) / 400f * .65f * p.intensity else 0f
    val rejected = !supportsPixels && !plan.isIdentity
    return EqualizerRenderRecord(
        engineVersion = "agsl-perceptual-1",
        decision = when {
            rejected -> EqualizerRenderDecision.REJECTED
            plan.isIdentity && stroke == 0f -> EqualizerRenderDecision.IDENTITY
            else -> EqualizerRenderDecision.APPLY
        },
        parameters = linkedMapOf(
            "viewportScale" to p.sizeScale,
            "textStrokeDp" to stroke,
            "sharpness" to if (supportsPixels) plan.sharpness else 0f,
            "contrast" to if (supportsPixels) plan.contrast else 1f,
            "brightness" to if (supportsPixels) plan.brightness else 1f,
            "whiteReduction" to if (supportsPixels) plan.whiteReduction else 0f,
            "pixelIntensity" to if (supportsPixels) plan.intensity else 0f
        ),
        reasons = listOf("GEOMETRY_SEPARATE_FROM_PIXEL_IDENTITY", "PREVIEW_DRAW_OBSERVED") +
            if (rejected) listOf("PIXEL_EFFECTS_REQUIRE_ANDROID_13") else emptyList(),
        sourceRevision = revision
    )
}
