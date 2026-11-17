package com.movtery.layer_controller.utils

import androidx.compose.ui.graphics.Color
import com.movtery.layer_controller.layout.ControlLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.File
import java.util.Base64
import java.util.Locale
import java.util.UUID

internal val layoutJson = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        contextual(Color::class, ColorSerializer)
    }
}

internal fun randomUUID(length: Int = 12): String =
    UUID.randomUUID()
        .toString()
        .replace("-", "")
        .take(length)

internal fun getAButtonUUID() = randomUUID(18)

fun newRandomFileName(characters: Int = 8): String {
    val uuid = UUID.randomUUID()
    val bytes = ByteArray(16).apply {
        System.arraycopy(
            longToBytes(uuid.mostSignificantBits), 0, this, 0, 8
        )
        System.arraycopy(
            longToBytes(uuid.leastSignificantBits), 0, this, 8, 8
        )
    }

    val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    return encoded.take(characters)
}

private fun longToBytes(long: Long): ByteArray {
    val buffer = ByteArray(8)
    for (i in 0 until 8) {
        buffer[i] = (long shr (8 * (7 - i))).toByte()
    }
    return buffer
}

suspend fun ControlLayout.saveToFile(file: File) {
    withContext(Dispatchers.IO) {
        val jsonString = layoutJson.encodeToString(this@saveToFile)
        val parentDir = file.parentFile ?: File(".")
        parentDir.mkdirs()

        val tempFile = File(parentDir, "${file.name}.tmp")
        try {
            tempFile.writeText(jsonString)
            if (!tempFile.renameTo(file)) {
                tempFile.copyTo(file, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}

fun <E> checkMin(
    tag: String,
    value: E,
    min: E
) where E : Number, E : Comparable<E> {
    if (value < min) {
        error("$tag is below its minimum value: $min, current: $value")
    }
}

fun <E : Comparable<E>> checkInRange(
    tag: String,
    value: E,
    range: ClosedRange<E>
) {
    if (value !in range) {
        error("{$tag} exceeds the valid range of values: $range, current: $value")
    }
}

fun Locale.toSimpleLangTag(): String {
    return "$language-$country"
}

internal fun Locale.compareLangTag(
    targetTag: String
): Boolean {
    return if (targetTag.contains("-")) {
        toSimpleLangTag() == targetTag
    } else {

        language == targetTag
    }
}
