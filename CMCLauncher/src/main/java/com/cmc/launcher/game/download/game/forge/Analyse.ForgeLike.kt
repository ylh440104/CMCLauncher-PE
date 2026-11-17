package com.cmc.launcher.game.download.game.forge

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.game.addons.mirror.mapBMCLMirrorUrls
import com.cmc.launcher.game.download.game.GameLibDownloader
import com.cmc.launcher.game.download.game.copyVanillaFiles
import com.cmc.launcher.game.download.game.getLibraryPath
import com.cmc.launcher.game.download.game.models.ForgeLikeInstallProcessor
import com.cmc.launcher.game.version.download.BaseMinecraftDownloader
import com.cmc.launcher.game.version.download.artifactToPath
import com.cmc.launcher.game.version.download.parseTo
import com.cmc.launcher.game.versioninfo.MinecraftVersions
import com.cmc.launcher.game.versioninfo.models.GameManifest
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.file.ensureDirectory
import com.cmc.launcher.utils.file.extractEntryToFile
import com.cmc.launcher.utils.file.readText
import com.cmc.launcher.utils.json.merge
import com.cmc.launcher.utils.json.parseToJson
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.network.fetchStringFromUrls
import com.cmc.launcher.utils.network.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

private const val TAG = "Analyse.ForgeLike"

const val FORGE_LIKE_ANALYSE_ID = "Analyse.ForgeLike"

fun getForgeLikeAnalyseTask(
    downloader: BaseMinecraftDownloader,
    targetTempInstaller: File,
    removeFromDownload: String,
    tempMinecraftFolder: File,
    sourceInherit: String,
    processedInherit: String,
): Task {
    return Task.runTask(
        id = FORGE_LIKE_ANALYSE_ID,
        dispatcher = Dispatchers.IO,
        task = { task ->
            if (sourceInherit != processedInherit) {

                copyVanillaFiles(
                    sourceGameFolder = tempMinecraftFolder,
                    sourceVersion = sourceInherit,
                    destinationGameFolder = tempMinecraftFolder,
                    targetVersion = processedInherit
                )
            }

            analyseNewForge(
                task = task,
                downloader = downloader,
                removeFromDownload = removeFromDownload,
                installer = targetTempInstaller,
                tempMinecraftFolder = tempMinecraftFolder,
                inherit = processedInherit,
            )
        }
    )
}

private suspend fun analyseNewForge(
    task: Task,
    downloader: BaseMinecraftDownloader,
    removeFromDownload: String,
    installer: File,
    tempMinecraftFolder: File,
    inherit: String,
) {
    task.updateProgress(-1f)

    val (installProfile, versionString) = withContext(Dispatchers.IO) {
        ZipFile(installer).use { zip ->
            task.updateProgress(0.2f)

            val installProfileString = zip.readText("install_profile.json")
            val versionString = zip.readText("version.json")

            val installProfile = installProfileString.parseToJson()
            installProfile["libraries"]?.takeIf { it.isJsonArray }?.let { libraries ->
                val libraryList: List<GameManifest.Library> =
                    GSON.fromJson(libraries, object : TypeToken<List<GameManifest.Library>>() {}.type) ?: return@let

                for (library in libraryList) {
                    val path = artifactToPath(library) ?: continue
                    zip.getEntry("maven/$path")?.let { entry ->
                        val dest = File(tempMinecraftFolder, "libraries/$path")
                        zip.extractEntryToFile(entry, dest)
                    }
                }
            }

            installProfile["path"]?.takeIf { it.isJsonPrimitive }?.let { path ->
                val libraryPath = getLibraryPath(path.asString)
                zip.getEntry("maven/$libraryPath")?.let { entry ->
                    val dest = File(tempMinecraftFolder, "libraries/$libraryPath")
                    zip.extractEntryToFile(entry, dest)
                }
            }

            installProfile to versionString
        }
    }

    installProfile.merge(versionString.parseToJson())

    val libDownloader = GameLibDownloader(
        downloader = downloader,
        gameJson = installProfile.toString()
    )
    libDownloader.schedule(task, File(tempMinecraftFolder, "libraries").ensureDirectory(), false)

    task.updateProgress(0.4f)
    scheduleMojangMappings(
        mergedJson = installProfile,
        tempMinecraftDir = tempMinecraftFolder,
        tempVanillaJar = File(tempMinecraftFolder, "versions/$inherit/$inherit.jar"),
        tempInstaller = installer
    ) { urls, sha1, targetFile, size ->
        libDownloader.scheduleDownload(
            urls = urls,
            sha1 = sha1,
            targetFile = targetFile,
            size = size
        )
    }

    task.updateProgress(0.8f)

    libDownloader.apply {
        removeDownload { lib ->
            (lib.targetFile.name.endsWith("$removeFromDownload.jar") ||
             lib.targetFile.name.endsWith("$removeFromDownload-client.jar")).also {
                if (it) {
                    Logger.info(TAG,
                        "The download task has been removed from the scheduled downloads: \n" +
                                "url: \n${lib.urls.joinToString("\n")}\n" +
                                "target path: ${lib.targetFile.absolutePath}"
                    )
                }
            }
        }
    }

    libDownloader.download(task)

    task.updateProgress(1f)
}

private suspend fun scheduleMojangMappings(
    mergedJson: JsonObject,
    tempMinecraftDir: File,
    tempVanillaJar: File,
    tempInstaller: File,
    schedule: (urls: List<String>, sha1: String?, targetFile: File, size: Long) -> Unit
) = withContext(Dispatchers.IO) {
    val tempDir = File(tempMinecraftDir, ".temp/forge_installer_cache").ensureDirectory()
    val vars = mutableMapOf<String, String>()

    ZipFile(tempInstaller).use { zip ->
        zip.readText("install_profile.json").parseToJson()["data"].asJsonObject?.let { data ->
            for ((key, value) in data.entrySet()) {
                if (value.isJsonObject) {
                    val client = value.asJsonObject["client"]
                    if (client != null && client.isJsonPrimitive) {
                        parseLiteral(
                            baseDir = tempMinecraftDir,
                            literal = client.asString,
                            plainConverter = { str ->
                                val dest: Path = Files.createTempFile(tempDir.toPath(), null, null)
                                val item = str
                                    .removePrefix("\\")
                                    .removePrefix("/")
                                    .replace("\\", "/")
                                zip.extractEntryToFile(item, dest.toFile())
                                dest.toString()
                            }
                        )?.let {
                            vars[key] = it
                        }
                    }
                }
            }
        }
    }

    vars += mapOf(
        "SIDE" to "client",
        "MINECRAFT_JAR" to tempVanillaJar.absolutePath,
        "MINECRAFT_VERSION" to tempVanillaJar.absolutePath,
        "ROOT" to tempMinecraftDir.absolutePath,
        "INSTALLER" to tempInstaller.absolutePath,
        "LIBRARY_DIR" to File(tempMinecraftDir, "libraries").absolutePath
    )

    parseProcessors(
        baseDir = tempMinecraftDir,
        jsonObject = mergedJson,
        vars = vars,
        schedule = schedule
    )
}

private suspend fun parseProcessors(
    baseDir: File,
    jsonObject: JsonObject,
    vars: Map<String, String>,
    schedule: (urls: List<String>, sha1: String?, targetFile: File, size: Long) -> Unit
) = withContext(Dispatchers.IO) {
    val processors: List<ForgeLikeInstallProcessor> = jsonObject["processors"]?.asJsonArray?.let { processors ->
        val type = object : TypeToken<List<ForgeLikeInstallProcessor>>() {}.type
        GSON.fromJson(processors, type)
    } ?: return@withContext

    processors.map { processor ->
        parseOptions(baseDir, processor.getArgs(), vars)
    }.forEach { options ->
        if (options["task"] != "DOWNLOAD_MOJMAPS" || options["side"] != "client") return@forEach
        val version = options["version"] ?: return@forEach
        val output = options["output"] ?: return@forEach
        Logger.info(TAG, "Patching DOWNLOAD_MOJMAPS task")

        val versionManifest = MinecraftVersions.getVersionManifest()
        versionManifest.versions.find { it.id == version }?.let { vanilla ->
            val manifest = withRetry(FORGE_LIKE_ANALYSE_ID, maxRetries = 2) {
                fetchStringFromUrls(
                    vanilla.url.mapBMCLMirrorUrls()
                ).parseTo(GameManifest::class.java)
            }
            manifest.downloads?.clientMappings?.let { mappings ->
                schedule(mappings.url.mapBMCLMirrorUrls(), mappings.sha1, File(output), mappings.size)
                Logger.info(TAG, "Mappings: ${mappings.url} (SHA1: ${mappings.sha1})")
            } ?: throw Exception("client_mappings download info not found")
        }
    }
}
