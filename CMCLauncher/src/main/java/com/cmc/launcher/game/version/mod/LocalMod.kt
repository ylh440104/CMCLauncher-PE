package com.cmc.launcher.game.version.mod

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.utils.logging.Logger
import kotlinx.io.IOException
import org.apache.commons.io.FileUtils
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "LocalMod"

const val DISABLED_MOD_SUFFIX = ".disabled"

class LocalMod(

    modFile: File,

    val fileSize: Long,

    val id: String,

    val loader: ModLoader,

    val name: String,

    val description: String? = null,

    val version: String? = null,

    val authors: List<String>,

    val icon: ByteArray? = null,

    val notMod: Boolean = false,

    val checkRemote: Boolean = true,
) {
    var file by mutableStateOf(modFile)
        private set

    fun disable() {
        val currentPath = file.absolutePath
        if (file.isDisabled()) return

        val newFile = File("$currentPath$DISABLED_MOD_SUFFIX")
        if (!file.renameToSafely(newFile)) return

        file = newFile
    }

    fun enable() {
        val newFile = enabledMod(file)
        if (!file.renameToSafely(newFile)) return

        file = newFile
    }

    private fun File.renameToSafely(dest: File): Boolean {
        return try {
            dest.parentFile?.mkdirs()
            Files.move(
                this.toPath(),
                dest.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
            true
        } catch (e: IOException) {
            Logger.warning(TAG, "Failed to rename file {$this} to $dest!", e)
            false
        }
    }
}

fun File.isEnabled(): Boolean = !absolutePath.endsWith(DISABLED_MOD_SUFFIX, ignoreCase = true)

fun File.isDisabled(): Boolean = !this.isEnabled()

fun createNotMod(file: File): LocalMod = LocalMod(
    modFile = file,
    fileSize = FileUtils.sizeOf(file),
    id = "",
    loader = ModLoader.UNKNOWN,
    name = file.name,
    description = null,
    version = null,
    authors = emptyList(),
    icon = null,
    notMod = true
)

fun enabledMod(file: File): File {
    if (file.isEnabled()) return file

    val currentPath = file.absolutePath
    val newPath = currentPath.dropLast(DISABLED_MOD_SUFFIX.length)
    return File(newPath)
}
