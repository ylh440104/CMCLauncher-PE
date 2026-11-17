package com.cmc.launcher.game.download.modpack.install

import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.game.download.addon.platform.Platform
import com.cmc.launcher.game.download.modpack.platform.AbstractPack
import com.cmc.launcher.game.download.modpack.platform.curseforge.CurseForgeManifest
import com.cmc.launcher.game.download.modpack.platform.curseforge.CurseForgePack
import com.cmc.launcher.game.download.modpack.platform.modrinth.ModrinthManifest
import com.cmc.launcher.game.download.modpack.platform.modrinth.ModrinthPack
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.file.extractFromZip
import com.cmc.launcher.utils.file.readText
import com.cmc.launcher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import org.apache.commons.compress.archivers.zip.ZipFile as ApacheZipFile
import java.util.zip.ZipFile as JdkZipFile

private const val TAG = "ParserModPack"

private data class PackParserConfig<M, P : AbstractPack>(
    val manifestPath: String,
    val manifestType: Class<M>,
    val createPack: (manifest: M) -> P,
    val readPack: suspend P.(Task, File, suspend (String, File) -> Unit) -> ModPackInfo
)

suspend fun parserModPack(
    file: File,
    platform: Platform,
    targetFolder: File,
    task: Task
): ModPackInfo = withContext(Dispatchers.IO) {

    val emptyFile = File("")

    when (platform) {
        Platform.CURSEFORGE -> {
            val config = PackParserConfig(
                manifestPath = "manifest.json",
                manifestType = CurseForgeManifest::class.java,
                createPack = { CurseForgePack(root = emptyFile, it) },
                readPack = { task, target, extract ->
                    readCurseForge(task, target, extract)
                }
            )
            parseModPackGeneric(file, targetFolder, task, config)
        }
        Platform.MODRINTH -> {
            val config = PackParserConfig(
                manifestPath = "modrinth.index.json",
                manifestType = ModrinthManifest::class.java,
                createPack = { ModrinthPack(root = emptyFile, it) },
                readPack = { task, target, extract ->
                    readModrinth(task, target, extract)
                }
            )
            parseModPackGeneric(file, targetFolder, task, config)
        }
    }
}

suspend fun <T> withZipFile(
    file: File,
    loaderJdk: suspend (JdkZipFile) -> T,
    loaderApache: suspend (ApacheZipFile) -> T
): T {
    return withContext(Dispatchers.IO) {
        try {
            JdkZipFile(file).use { loaderJdk(it) }
        } catch (e: Exception) {
            Logger.warning(TAG, "JDK ZipFile failed to parse ${file.name}, fallback to Apache ZipFile.", e)
            ApacheZipFile.builder().setFile(file).get().use { loaderApache(it) }
        }
    }
}

private suspend fun <M, P : AbstractPack> parseModPackGeneric(
    file: File,
    targetFolder: File,
    task: Task,
    config: PackParserConfig<M, P>
): ModPackInfo = withZipFile(
    file = file,
    loaderJdk = { zip ->
        task.updateProgress(-1f)

        val json = zip.readText(config.manifestPath)
        val manifest = GSON.fromJson(json, config.manifestType)
        val pack = config.createPack(manifest)

        config.readPack(pack, task, targetFolder) { internal, out ->
            zip.extractFromZip(internal, out)
        }
    },
    loaderApache = { zip ->
        task.updateProgress(-1f)

        val entry = zip.getEntry(config.manifestPath)
            ?: throw IOException("${config.manifestPath} not found in ${file.name}")

        val json = zip.getInputStream(entry).bufferedReader().readText()
        val manifest = GSON.fromJson(json, config.manifestType)
        val pack = config.createPack(manifest)

        config.readPack(pack, task, targetFolder) { internal, out ->
            zip.extractFromZip(internal, out)
        }
    }
)
