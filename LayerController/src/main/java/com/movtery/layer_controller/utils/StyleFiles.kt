package com.movtery.layer_controller.utils

import com.movtery.layer_controller.data.ButtonStyle
import com.movtery.layer_controller.data.JoystickStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

suspend fun ButtonStyle.saveToStyleFile(dir: File): File {
    val style = this
    return writeStyleFile(dir, uuid) {
        layoutJson.encodeToString(ButtonStyle.serializer(), style)
    }
}

suspend fun JoystickStyle.saveToStyleFile(dir: File): File {
    val style = this
    return writeStyleFile(dir, uuid) {
        layoutJson.encodeToString(JoystickStyle.serializer(), style)
    }
}

fun loadButtonStyle(file: File): ButtonStyle {
    return layoutJson.decodeFromString(ButtonStyle.serializer(), file.readText())
}

fun loadJoystickStyle(file: File): JoystickStyle {
    return layoutJson.decodeFromString(JoystickStyle.serializer(), file.readText())
}

private suspend fun writeStyleFile(
    dir: File,
    uuid: String,
    encode: () -> String
): File = withContext(Dispatchers.IO) {
    val file = File(dir, "$uuid.json")
    val parentDir = file.parentFile ?: File(".")
    parentDir.mkdirs()

    val tempFile = File(parentDir, "${file.name}.tmp")
    try {
        tempFile.writeText(encode())
        if (!tempFile.renameTo(file)) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    } catch (e: Exception) {
        tempFile.delete()
        throw e
    }
    file
}
