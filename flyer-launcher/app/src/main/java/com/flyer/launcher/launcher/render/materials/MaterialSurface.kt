package com.flyer.launcher.launcher.render.materials

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import com.flyer.launcher.core.config.EffectsLevel
import com.flyer.launcher.core.config.StyleConfig
import com.flyer.launcher.core.config.SurfaceMaterial
import com.flyer.launcher.core.theme.LocalDeviceTraits
import com.flyer.launcher.core.theme.LocalFlyerConfig
import kotlin.math.cos
import kotlin.math.sin

/** Shared film-grain texture for frosted materials. */
private object Grain {
    val brush: ShaderBrush by lazy {
        val size = 96
        val bmp = createBitmap(size, size)
        val rnd = java.util.Random(11)
        for (y in 0 until size) for (x in 0 until size) {
            val v = rnd.nextInt(256)
            bmp.setPixel(x, y, android.graphics.Color.argb(255, v, v, v))
        }
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
}

/**
 * Renders a launcher surface (dock, folder, drawer, menu, search bar, widget, taskbar, lock
 * panel) in the configured material.
 *
 * * Liquid glass: backdrop blur + edge refraction with colour fringing (Android 13+), light tint,
 *   top sheen and a specular rim lit from [StyleConfig.lightAngle].
 * * Frosted: strong backdrop blur (Android 12+), milky tint and film grain.
 * * Holographic: glass with an animated iridescent sheen.
 * * Neumorphic / Clay: soft extruded surfaces with paired light and dark shadows.
 * * Neon: dark glass with a glowing (optionally pulsing) outline.
 * * Outline: wireframe.
 *
 * The backdrop only contains launcher-drawn content (see [BackdropLayer]); on the system wallpaper,
 * glass materials keep their tint, sheen and rim but cannot blur or bend the wallpaper.
 */
@Composable
fun MaterialSurface(
    tint: Color,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: BackdropLayer? = null,
    topCornersOnly: Boolean = false,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val config = LocalFlyerConfig.current
    val style = config.style
    val material = if (enabled) style.material else SurfaceMaterial.FLAT
    val shape: Shape = if (topCornersOnly) RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius) else RoundedCornerShape(cornerRadius)
    val borderMod = if (borderWidth > 0.dp && borderColor.alpha > 0f) Modifier.border(borderWidth, borderColor, shape) else Modifier

    if (material == SurfaceMaterial.FLAT) {
        Box(modifier.clip(shape).background(tint, shape).then(borderMod), content = content)
        return
    }

    val traits = LocalDeviceTraits.current
    val effects = config.performance.effects
    val minimal = effects == EffectsLevel.MINIMAL || config.performance.lowMemoryMode
    val blurAllowed = traits.blurSupported && config.performance.blurEffects && !minimal
    val cap = if (effects == EffectsLevel.BALANCED) 16 else 64
    val density = LocalDensity.current
    val radiusPx = with(density) { cornerRadius.toPx() }
    val animate = effectsAnimate()
    val needsTime = (material == SurfaceMaterial.HOLOGRAPHIC) || (material == SurfaceMaterial.NEON && style.glowPulse)
    val time = rememberEffectTime(animate && needsTime, config.performance.effectsFps)

    Box(
        modifier
            .drawBehind { drawOuterEffects(material, style, tint, radiusPx, density.density, time.value, topCornersOnly) }
            .clip(shape),
    ) {
        if (material.usesBackdrop && backdrop != null && !minimal) {
            BackdropView(
                backdrop = backdrop,
                blurPx = if (blurAllowed) with(density) { (if (material == SurfaceMaterial.FROSTED) style.frostDp else style.frostDp / 2).coerceAtMost(cap).dp.toPx() } else 0f,
                refract = material != SurfaceMaterial.FROSTED && Shaders.runtimeShadersSupported,
                radiusPx = radiusPx,
                thicknessPx = with(density) { style.thicknessDp.dp.toPx() },
                refraction = style.refraction,
                chroma = style.chromatic,
            )
        }
        Box(
            Modifier.matchParentSize().drawBehind { drawFill(material, style, tint, time.value) },
        )
        content()
        Box(
            Modifier.matchParentSize().drawWithContent {
                drawContent()
                drawRim(material, style, shape, density.density, tint)
            }.then(borderMod),
        )
    }
}

/** Plain backdrop blur (no material), e.g. the dock's own blur setting with the flat material. */
@Composable
fun BoxScope.BackdropBlur(backdrop: BackdropLayer, blurPx: Float) =
    BackdropView(backdrop, blurPx, refract = false, radiusPx = 0f, thicknessPx = 0f, refraction = 0f, chroma = 0f)

@Composable
private fun BoxScope.BackdropView(
    backdrop: BackdropLayer,
    blurPx: Float,
    refract: Boolean,
    radiusPx: Float,
    thicknessPx: Float,
    refraction: Float,
    chroma: Float,
) {
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val shader = remember(refract) {
        if (refract && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(Shaders.GLASS) else null
    }
    Box(
        Modifier.matchParentSize()
            .onGloballyPositioned { coords = it }
            .graphicsLayer {
                renderEffect = when {
                    shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                        Shaders.glassEffect(shader, size.width, size.height, radiusPx, thicknessPx, refraction, chroma, blurPx).asComposeRenderEffect()
                    blurPx > 0.5f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                        android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                    else -> null
                }
                clip = true
            }
            .drawBehind { drawBackdrop(backdrop, coords) },
    )
}

private fun lightVector(style: StyleConfig): Offset {
    val a = Math.toRadians(style.lightAngle.toDouble())
    return Offset(cos(a).toFloat(), sin(a).toFloat())
}

private fun DrawScope.drawFill(material: SurfaceMaterial, style: StyleConfig, tint: Color, time: Float) {
    when (material) {
        SurfaceMaterial.LIQUID_GLASS, SurfaceMaterial.HOLOGRAPHIC -> {
            drawRect(tint.copy(alpha = (tint.alpha * style.tintStrength).coerceIn(0.04f, 0.6f)))
            // Soft sheen from the top, like light caught inside the glass.
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.16f), Color.Transparent), endY = size.height * 0.55f))
            if (material == SurfaceMaterial.HOLOGRAPHIC) {
                val shift = (time * 0.25f * style.holoSpeed) % 1f
                val w = size.width
                val hues = listOf(0xFFFF7AD9, 0xFF7AE7FF, 0xFFFFE27A, 0xFF9DFF7A, 0xFFB388FF, 0xFFFF7AD9).map { Color(it).copy(alpha = 0.22f) }
                drawRect(Brush.linearGradient(hues, start = Offset(-w + 2 * w * shift, 0f), end = Offset(2 * w * shift, size.height), tileMode = TileMode.Mirror))
            }
        }
        SurfaceMaterial.FROSTED -> {
            drawRect(Color.White.copy(alpha = 0.10f))
            drawRect(tint.copy(alpha = (tint.alpha * style.tintStrength * 1.6f).coerceIn(0.08f, 0.8f)))
            if (style.grain > 0f) drawRect(Grain.brush, alpha = style.grain * 0.07f)
        }
        SurfaceMaterial.NEUMORPHIC -> {
            val base = tint.copy(alpha = 1f)
            val l = lightVector(style)
            drawRect(
                Brush.linearGradient(
                    listOf(lerp(base, Color.White, 0.08f), lerp(base, Color.Black, 0.06f)),
                    start = Offset(size.width * (0.5f + 0.5f * l.x), size.height * (0.5f + 0.5f * l.y)),
                    end = Offset(size.width * (0.5f - 0.5f * l.x), size.height * (0.5f - 0.5f * l.y)),
                ),
            )
        }
        SurfaceMaterial.CLAY -> {
            val base = tint.copy(alpha = 1f)
            drawRect(base)
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f * style.depth), Color.Transparent), center = Offset(size.width * 0.25f, size.height * 0.2f), radius = size.maxDimension * 0.7f))
            drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.18f * style.depth), Color.Transparent), center = Offset(size.width * 0.85f, size.height * 1.05f), radius = size.maxDimension * 0.6f))
        }
        SurfaceMaterial.NEON -> {
            drawRect(lerp(tint.copy(alpha = 1f), Color.Black, 0.75f).copy(alpha = 0.72f))
        }
        SurfaceMaterial.OUTLINE, SurfaceMaterial.FLAT -> Unit
    }
}

/** Shadows and glows drawn outside the clipped surface. */
private fun DrawScope.drawOuterEffects(
    material: SurfaceMaterial,
    style: StyleConfig,
    tint: Color,
    radiusPx: Float,
    density: Float,
    time: Float,
    topOnly: Boolean,
) {
    val r = CornerRadius(radiusPx, radiusPx)
    when (material) {
        SurfaceMaterial.NEUMORPHIC, SurfaceMaterial.CLAY -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return // shadow layers need hardware support
            val d = 7f * density * style.depth
            val blur = 16f * density * style.depth.coerceAtLeast(0.2f)
            val l = lightVector(style)
            val base = tint.copy(alpha = 1f)
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint()
                paint.isAntiAlias = true
                paint.color = base.toArgb()
                val lightColor = if (material == SurfaceMaterial.CLAY) lerp(base, Color.White, 0.5f) else Color.White
                paint.setShadowLayer(blur, l.x * d, l.y * d, lightColor.copy(alpha = 0.7f * style.depth).toArgb())
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r.x, r.y, paint)
                val darkColor = if (material == SurfaceMaterial.CLAY) lerp(base, Color.Black, 0.55f) else Color.Black
                paint.setShadowLayer(blur * if (material == SurfaceMaterial.CLAY) 1.6f else 1f, -l.x * d * 1.2f, -l.y * d * 1.4f, darkColor.copy(alpha = 0.38f * style.depth).toArgb())
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r.x, r.y, paint)
            }
        }
        SurfaceMaterial.NEON -> {
            val pulse = if (style.glowPulse) 0.75f + 0.25f * sin(time * 2.4f) else 1f
            val glow = style.glowRadiusDp * density
            for (i in 4 downTo 1) {
                val w = 1.5f * density + glow * i / 4f
                drawRoundRect(style.glowColor.color.copy(alpha = 0.16f / i * 2f * pulse), cornerRadius = r, style = Stroke(w))
            }
        }
        SurfaceMaterial.LIQUID_GLASS, SurfaceMaterial.HOLOGRAPHIC -> {
            // Faint contact shadow so the pane floats.
            if (!topOnly) drawRoundRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(0f, 2f * density), size = size, cornerRadius = r)
        }
        else -> Unit
    }
}

/** Specular rim (glass), crisp outline (neon / outline). */
private fun DrawScope.drawRim(material: SurfaceMaterial, style: StyleConfig, shape: Shape, density: Float, tint: Color) {
    val outline = shape.createOutline(size, layoutDirection, this)
    when (material) {
        SurfaceMaterial.LIQUID_GLASS, SurfaceMaterial.HOLOGRAPHIC, SurfaceMaterial.FROSTED -> {
            val l = lightVector(style)
            val h = style.highlight * if (material == SurfaceMaterial.FROSTED) 0.5f else 1f
            val start = Offset(size.width * (0.5f - 0.5f * l.x), size.height * (0.5f - 0.5f * l.y))
            val end = Offset(size.width * (0.5f + 0.5f * l.x), size.height * (0.5f + 0.5f * l.y))
            val rim = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.95f * h), Color.White.copy(alpha = 0.08f * h), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.45f * h)),
                start = start, end = end,
            )
            drawOutline(outline, rim, style = Stroke(1.3f * density))
            // A wider, softer inner glow along the lit edge.
            drawOutline(outline, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f * h), Color.Transparent), start = start, end = Offset((start.x + end.x) / 2, (start.y + end.y) / 2)), style = Stroke(5f * density))
        }
        SurfaceMaterial.NEON -> drawOutline(outline, style.glowColor.color, style = Stroke(1.6f * density))
        SurfaceMaterial.OUTLINE -> drawOutline(outline, tint.copy(alpha = maxOf(0.7f, tint.alpha)), style = Stroke(1.4f * density))
        else -> Unit
    }
}
