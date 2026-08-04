package fr.vueconfort.app.optical

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

private const val OPTICAL_SHADER = """
uniform shader content;
uniform float2 size;
uniform float sharpness;
uniform float contrast;
uniform float gammaValue;
uniform float brightness;
uniform float saturation;
uniform float temperature;
uniform float whiteReduction;
uniform float distortion;
uniform float axis;
uniform float intensity;

half4 main(float2 p) {
    float2 center = size * 0.5;
    float2 q = p - center;
    float ca = cos(axis);
    float sa = sin(axis);
    float2 r = float2(ca*q.x - sa*q.y, sa*q.x + ca*q.y);
    float normalized = r.x / max(size.x, 1.0);
    r.y += distortion * normalized * normalized * size.y;
    q = float2(ca*r.x + sa*r.y, -sa*r.x + ca*r.y);
    float2 samplePoint = q + center;

    half4 original = content.eval(samplePoint);
    half4 c = original;
    if (sharpness > 0.001) {
        half4 n = content.eval(samplePoint + float2(0.0, -1.0));
        half4 s = content.eval(samplePoint + float2(0.0, 1.0));
        half4 e = content.eval(samplePoint + float2(1.0, 0.0));
        half4 w = content.eval(samplePoint + float2(-1.0, 0.0));
        c = clamp(original * (1.0 + 4.0 * sharpness) - (n+s+e+w) * sharpness, 0.0, 1.0);
    }
    float3 rgb = float3(c.r, c.g, c.b);
    rgb = (rgb - 0.5) * contrast + 0.5;
    rgb = pow(max(rgb, float3(0.0)), float3(1.0 / gammaValue));
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    rgb = mix(float3(luma), rgb, saturation);
    rgb *= float3(1.0 + temperature, 1.0, 1.0 - temperature);
    rgb *= brightness;
    rgb *= 1.0 - whiteReduction * smoothstep(0.55, 1.0, luma);
    half4 adjusted = half4(clamp(rgb, 0.0, 1.0), c.a);
    return mix(original, adjusted, intensity);
}
"""

fun Modifier.opticalRender(settings: OpticalSettings): Modifier = composed {
    val clean = settings.sanitized()
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val effect = if (clean.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember(clean, size) { createOpticalEffect(clean, size) }
    } else null
    this
        .onSizeChanged { size = it }
        .graphicsLayer {
            scaleX = clean.horizontalStretch
            scaleY = clean.verticalStretch
            clip = true
            renderEffect = effect
        }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createOpticalEffect(
    settings: OpticalSettings,
    size: IntSize
): androidx.compose.ui.graphics.RenderEffect {
    val shader = RuntimeShader(OPTICAL_SHADER)
    val economy = settings.quality == OpticalQuality.ECONOMY
    shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
    shader.setFloatUniform(
        "sharpness",
        if (economy) 0f else (settings.sharpness + settings.edgeEnhancement * 0.5f)
    )
    shader.setFloatUniform("contrast", settings.localContrast)
    shader.setFloatUniform("gammaValue", settings.gamma)
    shader.setFloatUniform("brightness", settings.brightness)
    shader.setFloatUniform("saturation", settings.saturation)
    shader.setFloatUniform("temperature", settings.temperature)
    shader.setFloatUniform("whiteReduction", settings.whiteReduction)
    shader.setFloatUniform(
        "distortion",
        if (settings.quality == OpticalQuality.QUALITY) settings.cylindricalDistortion else 0f
    )
    shader.setFloatUniform("axis", Math.toRadians(settings.distortionAxisDegrees.toDouble()).toFloat())
    shader.setFloatUniform("intensity", settings.globalIntensity)
    return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
}
