package com.flyer.launcher.flyer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.graphics.createBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.flyer.launcher.core.config.ColorValue

object Brushes {
    private val checkerBrush: ShaderBrush by lazy {
        val bmp = createBitmap(16, 16)
        val light = 0xFFFFFFFF.toInt()
        val dark = 0xFFCCCCCC.toInt()
        for (y in 0 until 16) for (x in 0 until 16) bmp.setPixel(x, y, if ((x / 8 + y / 8) % 2 == 0) light else dark)
        ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }

    fun checker(): Brush = checkerBrush
}

private val palette = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFF101018, 0xFF2A2A3A, 0xFF6A5BFF, 0xFF4FC3F7, 0xFF00F0FF, 0xFF00FF66,
    0xFF8BC34A, 0xFFFFE27A, 0xFFFFB38A, 0xFFFF8A65, 0xFFFF6B6B, 0xFFFF2BD6, 0xFFB388FF, 0xFFF5F1E8,
    0x33FFFFFF, 0x66FFFFFF, 0x33000000, 0x99000000,
).map { ColorValue(it.toInt()) }

/** HSV picker with saturation/value square, hue and alpha sliders, hex entry and swatches. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(title: String, initial: ColorValue, allowAlpha: Boolean, onPreview: (ColorValue) -> Unit, onDismiss: () -> Unit) {
    val hsv = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.argb, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(initial.alpha) }
    var hex by remember { mutableStateOf(initial.toHex()) }

    fun current(): ColorValue {
        val rgb = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
        return ColorValue(rgb).withAlpha(if (allowAlpha) alpha else 1f)
    }

    fun emit() {
        val c = current()
        hex = c.toHex()
        onPreview(c)
    }

    fun setFrom(c: ColorValue) {
        val out = FloatArray(3)
        android.graphics.Color.colorToHSV(c.argb, out)
        hue = out[0]; sat = out[1]; value = out[2]
        alpha = if (allowAlpha) c.alpha else 1f
        emit()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = { setFrom(initial); onDismiss() }) { Text("Revert") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                Canvas(
                    Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            fun pick(o: Offset) {
                                sat = (o.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                            detectTapGestures { pick(it) }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                sat = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                emit()
                            }
                        },
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val p = Offset(sat * size.width, (1f - value) * size.height)
                    drawCircle(Color.White, 10.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = 0.5f), 12.dp.toPx(), p, style = Stroke(1.dp.toPx()))
                }
                Canvas(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
                    drawRect(Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }))
                }
                Slider(hue, { hue = it; emit() }, valueRange = 0f..360f)
                if (allowAlpha) {
                    Text("Opacity ${(alpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                    Slider(alpha, { alpha = it; emit() }, valueRange = 0f..1f)
                }
                Row {
                    Canvas(Modifier.width(48.dp).height(48.dp).clip(RoundedCornerShape(12.dp))) {
                        drawRect(Brushes.checker())
                        drawRoundRect(current().color, size = Size(size.width, size.height), cornerRadius = CornerRadius(0f))
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        hex,
                        onValueChange = { t ->
                            hex = t
                            ColorValue.parse(t)?.let { c ->
                                val out = FloatArray(3)
                                android.graphics.Color.colorToHSV(c.argb, out)
                                hue = out[0]; sat = out[1]; value = out[2]
                                alpha = if (allowAlpha) c.alpha else 1f
                                onPreview(if (allowAlpha) c else c.withAlpha(1f))
                            }
                        },
                        singleLine = true, label = { Text("Hex") },
                        modifier = Modifier.weight(1f),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    palette.filter { allowAlpha || it.alpha >= 0.999f }.forEach { c ->
                        ColorSwatch(c, 28, selected = c.argb == current().argb) { setFrom(c) }
                    }
                }
            }
        },
    )
}
