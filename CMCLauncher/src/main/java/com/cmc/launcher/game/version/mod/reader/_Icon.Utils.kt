package com.cmc.launcher.game.version.mod.reader

import org.apache.commons.compress.archivers.zip.ZipFile
import java.util.zip.ZipFile as JDKZipFile

fun JDKZipFile.tryGetIcon(iconPath: String?): ByteArray? {
    if (iconPath.isNullOrBlank()) return null

    return try {
        getInputStream(getEntry(iconPath))?.readBytes()
    } catch (_: Exception) {
        null
    }
}

fun ZipFile.tryGetIcon(iconPath: String?): ByteArray? {
    if (iconPath.isNullOrBlank()) return null

    return try {
        getEntry(iconPath)?.let { entry ->
            getInputStream(entry).use { it.readBytes() }
        }
    } catch (_: Exception) {
        null
    }
}
