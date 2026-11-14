package com.flyer.launcher.core.render

import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.flyer.launcher.core.config.IconShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * Geometry of every icon shape. The same path is used to mask icon bitmaps (Android canvas) and to
 * clip/border/shadow in Compose, so the visual result is identical everywhere.
 */
object IconShapes {

    fun path(shape: IconShape, w: Float, h: Float, cornerPercent: Int): Path {
        val p = Path()
        val r = cornerPercent.coerceIn(0, 100) / 100f
        when (shape) {
            IconShape.SYSTEM, IconShape.CIRCLE -> p.addOval(RectF(0f, 0f, w, h), Path.Direction.CW)
            IconShape.SQUARE -> p.addRect(RectF(0f, 0f, w, h), Path.Direction.CW)
            IconShape.ROUNDED_SQUARE -> {
                val rad = minOf(w, h) / 2f * r
                p.addRoundRect(RectF(0f, 0f, w, h), rad, rad, Path.Direction.CW)
            }
            IconShape.SQUIRCLE -> superellipse(p, w, h, 4.0)
            IconShape.TEARDROP -> {
                val rad = minOf(w, h) / 2f
                val small = rad * (0.15f + 0.6f * (1f - r))
                p.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(rad, rad, rad, rad, small, small, rad, rad), Path.Direction.CW)
            }
            IconShape.PEBBLE -> {
                val a = minOf(w, h) / 2f
                val b = a * (0.55f + 0.4f * r)
                p.addRoundRect(RectF(0f, 0f, w, h), floatArrayOf(a, a, b, b, a, a, b, b), Path.Direction.CW)
            }
            IconShape.HEXAGON -> polygon(p, w, h, 6, rotation = 0.0, roundness = r)
            IconShape.CLOVER -> clover(p, w, h)
        }
        return p
    }

    private fun superellipse(p: Path, w: Float, h: Float, n: Double) {
        val cx = w / 2f
        val cy = h / 2f
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = cx + cx * (abs(c).pow(2.0 / n) * sign(c)).toFloat()
            val y = cy + cy * (abs(s).pow(2.0 / n) * sign(s)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    private fun polygon(p: Path, w: Float, h: Float, sides: Int, rotation: Double, roundness: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val pts = (0 until sides).map { i ->
            val a = rotation + 2 * PI * i / sides
            floatArrayOf(cx + cx * cos(a).toFloat(), cy + cy * sin(a).toFloat())
        }
        val k = (roundness * 0.5f).coerceIn(0f, 0.45f)
        for (i in pts.indices) {
            val prev = pts[(i - 1 + sides) % sides]
            val cur = pts[i]
            val next = pts[(i + 1) % sides]
            val sx = cur[0] + (prev[0] - cur[0]) * k
            val sy = cur[1] + (prev[1] - cur[1]) * k
            val ex = cur[0] + (next[0] - cur[0]) * k
            val ey = cur[1] + (next[1] - cur[1]) * k
            if (i == 0) p.moveTo(sx, sy) else p.lineTo(sx, sy)
            p.quadTo(cur[0], cur[1], ex, ey)
        }
        p.close()
    }

    private fun clover(p: Path, w: Float, h: Float) {
        val rw = w / 4f
        val rh = h / 4f
        // Four overlapping circles + a center square give a four-leaf shape.
        p.addOval(RectF(w / 2f - 2 * rw, 0f, w / 2f, h / 2f + rh), Path.Direction.CW)
        p.addOval(RectF(w / 2f, 0f, w / 2f + 2 * rw, h / 2f + rh), Path.Direction.CW)
        p.addOval(RectF(0f, h / 2f - rh, w / 2f, h), Path.Direction.CW)
        p.addOval(RectF(w / 2f, h / 2f - rh, w, h), Path.Direction.CW)
        p.addRect(RectF(rw, rh, w - rw, h - rh), Path.Direction.CW)
        p.fillType = Path.FillType.WINDING
    }

    /** Compose [Shape] backed by the same geometry. */
    fun composeShape(shape: IconShape, cornerPercent: Int): Shape = PathShape(shape, cornerPercent)

    private data class PathShape(val shape: IconShape, val cornerPercent: Int) : Shape {
        override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
            Outline.Generic(path(shape, size.width, size.height, cornerPercent).asComposePath())
    }
}
