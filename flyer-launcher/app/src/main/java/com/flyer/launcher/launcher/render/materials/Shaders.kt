package com.flyer.launcher.launcher.render.materials

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color

/** AGSL programs (Android 13+) for liquid glass refraction and the aurora background. */
object Shaders {

    val runtimeShadersSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /**
     * Liquid glass: inside a rounded rectangle, content near the edge is pulled inward along the
     * surface normal (like light through a curved glass edge), with optional colour fringing.
     */
    const val GLASS = """
        uniform shader content;
        uniform float2 size;
        uniform float radius;
        uniform float thickness;
        uniform float refraction;
        uniform float chroma;

        float sdRoundBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r, r);
            return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
        }

        half4 main(float2 coord) {
            float2 hs = size * 0.5;
            float2 p = coord - hs;
            float r = min(radius, min(hs.x, hs.y));
            float d = sdRoundBox(p, hs, r);
            float edge = clamp(1.0 + d / thickness, 0.0, 1.0);
            float bend = edge * edge;
            float2 e = float2(1.0, 0.0);
            float2 n = float2(
                sdRoundBox(p + e.xy, hs, r) - sdRoundBox(p - e.xy, hs, r),
                sdRoundBox(p + e.yx, hs, r) - sdRoundBox(p - e.yx, hs, r));
            float len = length(n);
            n = len > 0.0001 ? n / len : float2(0.0, 0.0);
            float2 offset = -n * bend * refraction * thickness;
            float2 base = coord + offset;
            half4 c = content.eval(base);
            if (chroma > 0.0) {
                float2 shift = offset * chroma;
                c.r = content.eval(base + shift).r;
                c.b = content.eval(base - shift).b;
            }
            return c;
        }
    """

    /** Flowing aurora bands mixed from a four-colour palette. */
    const val AURORA = """
        uniform float2 size;
        uniform float time;
        uniform float4 c0;
        uniform float4 c1;
        uniform float4 c2;
        uniform float4 c3;

        half4 main(float2 fc) {
            float2 uv = fc / size;
            float t = time * 0.12;
            float w1 = sin(uv.x * 3.1 + t * 2.0 + sin(uv.y * 2.3 + t)) * 0.5 + 0.5;
            float w2 = sin(uv.y * 4.2 - t * 1.4 + cos(uv.x * 2.7 - t * 0.6)) * 0.5 + 0.5;
            float w3 = sin((uv.x + uv.y) * 2.4 + t * 1.1) * 0.5 + 0.5;
            float band = smoothstep(0.15, 0.95, w1 * (1.0 - uv.y * 0.55));
            float4 col = mix(c3, c0, band);
            col = mix(col, c1, w2 * 0.65 * (1.0 - uv.y * 0.7));
            col = mix(col, c2, smoothstep(0.55, 1.0, w1 * w3) * 0.75);
            return half4(half3(col.rgb), 1.0);
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun glassEffect(
        shader: RuntimeShader,
        width: Float,
        height: Float,
        radiusPx: Float,
        thicknessPx: Float,
        refraction: Float,
        chroma: Float,
        blurPx: Float,
    ): RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("radius", radiusPx)
        shader.setFloatUniform("thickness", thicknessPx.coerceAtLeast(1f))
        shader.setFloatUniform("refraction", refraction)
        shader.setFloatUniform("chroma", chroma)
        val glass = RenderEffect.createRuntimeShaderEffect(shader, "content")
        return if (blurPx > 0.5f) {
            RenderEffect.createChainEffect(glass, RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP))
        } else glass
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun setAurora(shader: RuntimeShader, width: Float, height: Float, time: Float, palette: List<Color>) {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("time", time)
        listOf("c0", "c1", "c2", "c3").forEachIndexed { i, name ->
            val c = palette[i % palette.size]
            shader.setFloatUniform(name, c.red, c.green, c.blue, 1f)
        }
    }
}
