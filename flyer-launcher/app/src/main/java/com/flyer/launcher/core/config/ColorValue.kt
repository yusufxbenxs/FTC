package com.flyer.launcher.core.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * ARGB color stored in configuration files as a readable "#AARRGGBB" string so exported
 * setups can be edited by hand.
 */
@Immutable
@Serializable(with = ColorValueSerializer::class)
@JvmInline
value class ColorValue(val argb: Int) {
    val color: Color get() = Color(argb)
    val alpha: Float get() = ((argb ushr 24) and 0xFF) / 255f

    fun withAlpha(alpha: Float): ColorValue {
        val a = (alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return ColorValue((argb and 0x00FFFFFF) or (a shl 24))
    }

    fun toHex(): String = "#" + argb.toUInt().toString(16).padStart(8, '0').uppercase()

    companion object {
        val White = ColorValue(0xFFFFFFFF.toInt())
        val Black = ColorValue(0xFF000000.toInt())
        val Transparent = ColorValue(0)

        fun of(color: Color): ColorValue {
            val a = (color.alpha * 255f + 0.5f).toInt()
            val r = (color.red * 255f + 0.5f).toInt()
            val g = (color.green * 255f + 0.5f).toInt()
            val b = (color.blue * 255f + 0.5f).toInt()
            return ColorValue((a shl 24) or (r shl 16) or (g shl 8) or b)
        }

        /** Parses #RGB, #RRGGBB or #AARRGGBB. Returns null for malformed input. */
        fun parse(text: String): ColorValue? {
            val s = text.trim().removePrefix("#")
            if (s.isEmpty() || s.any { !it.isLetterOrDigit() }) return null
            val full = when (s.length) {
                3 -> "FF" + s.map { "$it$it" }.joinToString("")
                6 -> "FF$s"
                8 -> s
                else -> return null
            }
            val v = full.toLongOrNull(16) ?: return null
            return ColorValue(v.toInt())
        }
    }
}

object ColorValueSerializer : KSerializer<ColorValue> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.flyer.launcher.ColorValue", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ColorValue) = encoder.encodeString(value.toHex())

    override fun deserialize(decoder: Decoder): ColorValue =
        ColorValue.parse(decoder.decodeString()) ?: ColorValue.Transparent
}

fun argb(value: Long) = ColorValue(value.toInt())
