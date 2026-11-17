package com.cmc.launcher.game.version.saves

import com.cmc.launcher.utils.file.CompressZipEntryAdapter
import com.cmc.launcher.utils.file.JavaZipEntryAdapter
import com.cmc.launcher.utils.file.UnpackZipException
import com.cmc.launcher.utils.file.ZipEntryBase
import com.cmc.launcher.utils.file.extractFromZip
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile as JDKZipFile

private const val TAG = "InstallSaves"

suspend fun unpackSaveZip(zipFile: File, targetPath: File) = withContext(Dispatchers.IO) {
    val path = extractLevelPath(zipFile) ?: throw IOException("Unable to locate the level where the level.dat file is stored.")
    Logger.info(TAG, "Found the level of the level.data file: $path")
    val target = File(targetPath, zipFile.nameWithoutExtension)
    try {
        JDKZipFile(zipFile).use { zip ->
            zip.extractFromZip(path, target)
            Logger.info(TAG, "Decompression is complete")
        }
    } catch (e: Exception) {
        if (e !is UnpackZipException) {
            tryApacheZip(zipFile, path, target)
        } else {
            throw e
        }
    }
    FileUtils.deleteQuietly(zipFile)
}

private suspend fun tryApacheZip(zipFile: File, path: String, target: File) {
    FileUtils.deleteQuietly(target)
    val zipFile1 = ZipFile.Builder()
        .setFile(zipFile)
        .get()

    zipFile1.use { zip ->
        zip.extractFromZip(path, target)
        Logger.info(TAG, "Decompression is complete")
    }
}

private fun extractLevelPath(file: File): String? {
    if (!file.exists() || !file.isFile) {
        return null
    }

    if (!file.name.endsWith(".zip", ignoreCase = true)) {
        return null
    }

    try {
        JDKZipFile(file).use { zip ->
            val entries = zip.entries().asSequence()
                .map { entry ->
                    JavaZipEntryAdapter(entry)
                }
            return findLevelEntryName(entries)
        }
    } catch (e: Exception) {
        if (e !is UnpackZipException) {
            return extractLevelPathCompress(file)
        } else {
            throw e
        }
    }
}

private fun extractLevelPathCompress(file: File): String? {
    val zipFile = ZipFile.Builder()
        .setFile(file)
        .get()

    zipFile.use { zip ->
        val entries = zip.entries.asSequence()
            .map { entry ->
                CompressZipEntryAdapter(entry)
            }
        return findLevelEntryName(entries)
    }
}

private fun <T : ZipEntryBase> findLevelEntryName(
    entries: Sequence<T>
): String? {
    val allEntries = entries.toList()
    var currentPrefix = ""

    while (true) {
        val inLayer = allEntries.filter {

            it.name.startsWith(currentPrefix) && it.name != currentPrefix
        }
        if (inLayer.isEmpty()) {

            return null
        }

        val hasLevelDat = inLayer.any {
            val relative = it.name.removePrefix(currentPrefix)
            !it.isDirectory && relative.equals("level.dat", ignoreCase = true)
        }

        if (hasLevelDat) {

            val hasFolder = inLayer.any {
                val relative = it.name.removePrefix(currentPrefix)
                it.isDirectory || relative.contains("/")
            }
            return if (hasFolder) currentPrefix.removeSuffix("/") else null
        }

        val relativeNames = inLayer.map {
            it.name.removePrefix(currentPrefix)
        }

        val topLevelNames = relativeNames.map {
            it.substringBefore("/")
        }.distinct()

        if (topLevelNames.size == 1) {
            val name = topLevelNames[0]
            val isFolder = inLayer.any {
                val rel = it.name.removePrefix(currentPrefix)
                rel.startsWith("$name/") || (rel == name && it.isDirectory)
            }
            val hasFileWithSameName = inLayer.any {
                val rel = it.name.removePrefix(currentPrefix)
                !it.isDirectory && rel == name
            }

            if (isFolder && !hasFileWithSameName) {
                currentPrefix = "$currentPrefix$name/"
                continue
            }
        }
        return null
    }
}
