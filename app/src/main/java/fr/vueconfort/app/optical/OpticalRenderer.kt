package fr.vueconfort.app.optical

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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

float3 straightRgb(half4 c, float3 fallback) {
    return c.a > 0.0001 ? float3(c.rgb) / float(c.a) : fallback;
}

half4 main(float2 p) {
    float2 samplePoint = p;
    if (distortion != 0.0) {
        float2 center = size * 0.5;
        float2 q = p - center;
        float ca = cos(axis);
        float sa = sin(axis);
        float2 r = float2(ca*q.x - sa*q.y, sa*q.x + ca*q.y);
        float normalized = r.x / max(size.x, 1.0);
        r.y += distortion * normalized * normalized * size.y;
        q = float2(ca*r.x + sa*r.y, -sa*r.x + ca*r.y);
        samplePoint = q + center;
    }
    half4 original = content.eval(samplePoint);
    if (intensity == 0.0 || original.a <= 0.0001) return original;
    float3 rgb = float3(original.rgb) / float(original.a);
    if (sharpness > 0.0) {
        float3 n = straightRgb(content.eval(samplePoint + float2(0.0, -1.0)), rgb);
        float3 s = straightRgb(content.eval(samplePoint + float2(0.0, 1.0)), rgb);
        float3 e = straightRgb(content.eval(samplePoint + float2(1.0, 0.0)), rgb);
        float3 w = straightRgb(content.eval(samplePoint + float2(-1.0, 0.0)), rgb);
        // Bound per-channel detail and overshoot in the shader's RGB space.
        // This is perceptual sharpening, not an inverse optical model.
        float3 detail = clamp((4.0*rgb - n-s-e-w)*sharpness, -0.12, 0.12);
        float3 localMin = min(rgb, min(min(n,s), min(e,w)));
        float3 localMax = max(rgb, max(max(n,s), max(e,w)));
        rgb = clamp(rgb + detail, max(localMin - 0.02, 0.0), min(localMax + 0.02, 1.0));
    }
    rgb = (rgb - 0.5) * contrast + 0.5;
    rgb = pow(max(rgb, float3(0.0)), float3(1.0 / gammaValue));
    float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
    rgb = mix(float3(luma), rgb, saturation);
    rgb *= float3(1.0 + temperature, 1.0, 1.0 - temperature);
    rgb *= brightness;
    rgb *= 1.0 - whiteReduction * smoothstep(0.55, 1.0, luma);
    half4 adjusted = half4(clamp(rgb, 0.0, 1.0) * float(original.a), original.a);
    return mix(original, adjusted, intensity);
}
"""

/**
 * Source-size pixel treatment. Disabled, intensity zero and effective neutral settings are
 * exact identity: no shader, clipping or legacy scale is installed.
 *
 * [bypass] is a transient comparison, never a change to the settings. It removes pixel/color
 * operations while retaining the active legacy geometric transform. Equalizer scenes keep
 * stretch=1 and distortion=0; their size/zoom/crop is established outside this modifier, so
 * holding Original preserves their content, position, frame and zoom exactly.
 *
 * One shader is compiled per composition; parameter changes update uniforms and create a new
 * RenderEffect snapshot, which also invalidates a stationary scene. No bitmap or asynchronous
 * work queue is created. API < 33 has no AGSL pixel treatments (UI must expose that limitation).
 */
fun Modifier.opticalRender(settings: OpticalSettings, bypass: Boolean = false): Modifier = composed {
    val plan = settings.renderPlan(bypass)
    var size by remember { mutableStateOf(IntSize(1, 1)) }
    val factory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { OpticalEffectFactory() }
    } else null
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && factory != null && plan.needsShader) {
        remember(factory, plan, size) { factory.create(plan, size) }
    } else null
    if (plan.isIdentity) this
    else this
        .onSizeChanged { size = it }
        .graphicsLayer {
            scaleX = plan.horizontalScale
            scaleY = plan.verticalScale
            clip = true
            renderEffect = effect
        }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class OpticalEffectFactory {
    private val shader = RuntimeShader(OPTICAL_SHADER)

    fun create(plan: OpticalRenderPlan, size: IntSize): androidx.compose.ui.graphics.RenderEffect {
        shader.setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        shader.setFloatUniform("sharpness", plan.sharpness)
        shader.setFloatUniform("contrast", plan.contrast)
        shader.setFloatUniform("gammaValue", plan.gamma)
        shader.setFloatUniform("brightness", plan.brightness)
        shader.setFloatUniform("saturation", plan.saturation)
        shader.setFloatUniform("temperature", plan.temperature)
        shader.setFloatUniform("whiteReduction", plan.whiteReduction)
        shader.setFloatUniform("distortion", plan.distortion)
        shader.setFloatUniform("axis", Math.toRadians(plan.axisDegrees.toDouble()).toFloat())
        shader.setFloatUniform("intensity", plan.intensity)
        return RenderEffect.createRuntimeShaderEffect(shader, "content").asComposeRenderEffect()
    }
}
