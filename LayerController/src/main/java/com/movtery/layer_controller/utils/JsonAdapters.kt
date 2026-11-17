package com.movtery.layer_controller.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ColorAsLong", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeLong(value.toArgb().toLong() and 0xFFFFFFFFL)
    }

    override fun deserialize(decoder: Decoder): Color {
        val longValue = decoder.decodeLong()
        val high32 = (longValue ushr 32) and 0xFFFFFFFFL
        val argb = if (high32 != 0L) {
            (longValue ushr 32).toInt()
        } else {
            longValue.toInt()
        }
        return Color(argb)
    }
}
