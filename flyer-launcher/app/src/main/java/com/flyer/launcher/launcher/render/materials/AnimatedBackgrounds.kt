package com.flyer.launcher.launcher.render.materials

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import com.flyer.launcher.core.config.BackgroundConfig
import com.flyer.launcher.core.config.BackgroundType
import com.flyer.launcher.core.config.EffectsLevel
import com.flyer.launcher.core.theme.LocalDeviceTraits
import com.flyer.launcher.core.theme.LocalFlyerConfig
import com.flyer.launcher.core.theme.ThemeEngine
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Time (seconds) for launcher effects, updated at most [fps] times per second and only while
 * animations are allowed. Reading it inside draw lambdas only redraws, never recomposes. The
 * frame clock pauses on its own whenever the launcher is not visible.
 */
@Composable
fun rememberEffectTime(animate: Boolean, fps: Int): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate, fps) {
        if (!animate) return@LaunchedEffect
        val frameMs = (1000L / fps.coerceIn(10, 120)).coerceAtLeast(8)
        var start = -1L
        while (true) {
            withFrameNanos { now ->
                if (start < 0) start = now - (time.floatValue * 1e9f).toLong()
                time.floatValue = (now - start) / 1e9f
            }
            delay(frameMs)
        }
    }
    return time
}

/** Whether animated effects should run with the current config and device. */
@Composable
fun effectsAnimate(): Boolean {
    val config = LocalFlyerConfig.current
    val traits = LocalDeviceTraits.current
    return ThemeEngine.animationsActive(config.animations, traits, config.performance.effects) &&
        config.performance.effects != EffectsLevel.MINIMAL && !config.performance.lowMemoryMode
}

private fun palette(bg: BackgroundConfig): List<Color> =
    bg.palette.map { it.color }.ifEmpty { BackgroundConfig().palette.map { it.color } }

/** Launcher-drawn animated (and mesh) backgrounds. */
@Composable
fun AnimatedBackground(bg: BackgroundConfig, modifier: Modifier = Modifier) {
    val fps = LocalFlyerConfig.current.performance.effectsFps
    val time = rememberEffectTime(effectsAnimate() && bg.type.animated, fps)
    val colors = palette(bg)
    val speed = bg.animSpeed
    when (bg.type) {
        BackgroundType.AURORA -> Aurora(colors, time, speed, modifier)
        BackgroundType.LIQUID -> Liquid(colors, time, speed, bg.animDensity, modifier)
        BackgroundType.PARTICLES -> Particles(colors, time, speed, bg.animDensity, modifier)
        BackgroundType.WAVES -> Waves(colors, time, speed, bg.animDensity, modifier)
        BackgroundType.MESH -> Mesh(colors, modifier)
        else -> Unit
    }
}

@Composable
private fun Aurora(colors: List<Color>, time: State<Float>, speed: Float, modifier: Modifier) {
    val shader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(Shaders.AURORA) else null }
    val brush = remember(shader) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null) ShaderBrush(shader) else null }
    Canvas(modifier.fillMaxSize()) {
        // Runtime shaders only work on hardware-accelerated canvases (not in software snapshots).
        if (shader != null && brush != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Shaders.setAurora(shader, size.width, size.height, time.value * speed, colors)
            drawRect(brush)
        } else {
            // Pre-Android 13 / software: drifting soft colour fields.
            val t = time.value * speed * 0.15f
            drawRect(colors.last())
            colors.dropLast(1).forEachIndexed { i, c ->
                val a = t + i * 2.1f
                val center = Offset(size.width * (0.5f + 0.4f * cos(a)), size.height * (0.35f + 0.3f * sin(a * 0.8f)))
                val r = max(size.width, size.height) * 0.7f
                drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.75f), Color.Transparent), center, r), r, center)
            }
        }
    }
}

/** Lava-lamp style blobs that drift and merge. */
@Composable
private fun Liquid(colors: List<Color>, time: State<Float>, speed: Float, density: Float, modifier: Modifier) {
    val count = 3 + (density * 6).toInt()
    Canvas(modifier.fillMaxSize()) {
        val t = time.value * speed * 0.18f
        val base = colors.last()
        drawRect(Brush.verticalGradient(listOf(lerp(base, Color.Black, 0.2f), base)))
        val unit = max(size.width, size.height)
        for (i in 0 until count) {
            val phase = i * 1.7f
            val x = size.width * (0.5f + 0.42f * sin(t * (0.6f + i * 0.13f) + phase))
            val y = size.height * (0.5f + 0.42f * cos(t * (0.5f + i * 0.11f) + phase * 1.3f))
            val r = unit * (0.18f + 0.08f * sin(t + phase))
            val c = colors[i % (colors.size - 1).coerceAtLeast(1)]
            drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0.55f), Color.Transparent), Offset(x, y), r), r, Offset(x, y))
        }
    }
}

/** Slowly drifting, twinkling particles (deterministic, no allocation per frame). */
@Composable
private fun Particles(colors: List<Color>, time: State<Float>, speed: Float, density: Float, modifier: Modifier) {
    val count = 30 + (density * 140).toInt()
    val seeds = remember(count) {
        val rnd = java.util.Random(7)
        FloatArray(count * 5) { rnd.nextFloat() }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = time.value * speed
        val base = colors.last()
        drawRect(Brush.verticalGradient(listOf(lerp(base, Color.Black, 0.35f), base, lerp(base, colors.first(), 0.25f))))
        val unit = size.minDimension
        for (i in 0 until count) {
            val sx = seeds[i * 5]; val sy = seeds[i * 5 + 1]; val sv = seeds[i * 5 + 2]; val ss = seeds[i * 5 + 3]; val sp = seeds[i * 5 + 4]
            val x = ((sx + t * (0.004f + sv * 0.01f)) % 1f) * size.width
            val y = ((sy - t * (0.006f + sv * 0.012f)) % 1f + 1f) % 1f * size.height
            val twinkle = 0.45f + 0.55f * (0.5f + 0.5f * sin(t * (1f + sp * 2f) + sp * 6.28f))
            val r = unit * (0.002f + ss * 0.005f)
            val c = colors[i % (colors.size - 1).coerceAtLeast(1)]
            drawCircle(c.copy(alpha = 0.18f * twinkle), r * 3.2f, Offset(x, y))
            drawCircle(Color.White.copy(alpha = 0.85f * twinkle), r, Offset(x, y))
        }
    }
}

/** Layered sine waves rolling across the bottom of the screen. */
@Composable
private fun Waves(colors: List<Color>, time: State<Float>, speed: Float, density: Float, modifier: Modifier) {
    val layers = 3 + (density * 3).toInt()
    val path = remember { Path() }
    Canvas(modifier.fillMaxSize()) {
        val t = time.value * speed * 0.6f
        val base = colors.last()
        drawRect(Brush.verticalGradient(listOf(lerp(base, Color.Black, 0.25f), base)))
        for (l in 0 until layers) {
            val c = colors[l % (colors.size - 1).coerceAtLeast(1)]
            val baseY = size.height * (0.55f + l * 0.08f)
            val amp = size.height * (0.035f + 0.01f * l)
            val k = (2 * PI / size.width * (1.2f + l * 0.35f)).toFloat()
            path.reset()
            path.moveTo(0f, size.height)
            var x = 0f
            val step = size.width / 48f
            while (x <= size.width + step) {
                val y = baseY + amp * sin(k * x + t * (1f + l * 0.3f) + l)
                path.lineTo(x, y)
                x += step
            }
            path.lineTo(size.width, size.height)
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0.85f)), startY = baseY - amp, endY = size.height))
        }
    }
}

/** Static mesh gradient: four colour pools blended over the base colour. */
@Composable
private fun Mesh(colors: List<Color>, modifier: Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(colors.last())
        val corners = listOf(Offset(0f, 0f), Offset(size.width, 0f), Offset(size.width, size.height), Offset(0f, size.height))
        val r = max(size.width, size.height) * 0.85f
        corners.forEachIndexed { i, o ->
            val c = colors[i % (colors.size - 1).coerceAtLeast(1)]
            drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.9f), Color.Transparent), o, r), r, o)
        }
    }
}
