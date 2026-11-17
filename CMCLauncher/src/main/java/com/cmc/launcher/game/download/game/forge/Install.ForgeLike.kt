package com.cmc.launcher.game.download.game.forge

import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import com.cmc.launcher.R
import com.cmc.launcher.components.jre.Jre
import com.cmc.launcher.context.GlobalContext
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.coroutine.withTaskLogOutput
import com.cmc.launcher.game.download.game.GameLibDownloader
import com.cmc.launcher.game.download.game.getLibraryPath
import com.cmc.launcher.game.download.game.models.ForgeLikeInstallProcessor
import com.cmc.launcher.game.download.game.models.toPath
import com.cmc.launcher.game.download.game.parseLibraryComponents
import com.cmc.launcher.game.download.jvm_server.runJvmRetryRuntimes
import com.cmc.launcher.game.download.jvm_server.stopAllNonMainProcesses
import com.cmc.launcher.game.version.download.BaseMinecraftDownloader
import com.cmc.launcher.notification.NoticeProgress
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.file.ensureDirectory
import com.cmc.launcher.utils.file.extractEntryToFile
import com.cmc.launcher.utils.file.extractFromZip
import com.cmc.launcher.utils.file.readText
import com.cmc.launcher.utils.json.parseToJson
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.string.isBiggerOrEqualTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.jackhuang.hmcl.util.DigestUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.zip.ZipFile
import kotlin.io.path.name

private const val TAG = "Install.ForgeLike"

const val FORGE_LIKE_INSTALL_ID = "Install.ForgeLike"

fun getForgeLikeInstallTask(
    isNew: Boolean,
    downloader: BaseMinecraftDownloader,
    loaderName: String,
    tempFolderName: String,
    tempInstaller: File,
    tempGameFolder: File,
    tempMinecraftDir: File,
    inherit: String,
    logOutputHolder: MutableStateFlow<TaskLogOutput?>
): Task {
    return Task.runTask(
        id = FORGE_LIKE_INSTALL_ID,
        task = { task ->
            val tempVanillaJar = File(tempMinecraftDir, "versions/$inherit/$inherit.jar")
            val tempVersionJson = File(tempMinecraftDir, "versions/$tempFolderName/$tempFolderName.json")
            if (isNew) {

                withTaskLogOutput(
                    holder = logOutputHolder,
                    title = androidText(
                        R.string.download_game_install_base_install,
                        loaderName
                    )
                ) { output ->
                    installNewForgeHMCLWay(
                        task = task,
                        loaderName = loaderName,
                        tempInstaller = tempInstaller,
                        tempGameFolder = tempGameFolder,
                        tempMinecraftDir = tempMinecraftDir,
                        tempVersionJson = tempVersionJson,
                        tempVanillaJar = tempVanillaJar,
                        logOutput = output
                    )
                }
            } else {
                installOldForge(
                    task = task,
                    downloader = downloader,
                    tempInstaller = tempInstaller,
                    tempMinecraftDir = tempMinecraftDir,
                    tempFolderName = tempFolderName,
                    tempVersionJson = tempVersionJson,
                    inherit = inherit
                )
            }

            progressIgnoreList(tempVersionJson)
        }
    )
}

private suspend fun installNewForgeHMCLWay(
    task: Task,
    loaderName: String,
    tempInstaller: File,
    tempGameFolder: File,
    tempMinecraftDir: File,
    tempVersionJson: File,
    tempVanillaJar: File,
    logOutput: TaskLogOutput
) = withContext(Dispatchers.IO) {
    task.updateProgress(-1f)

    val tempDir = File(tempMinecraftDir, ".temp/forge_installer_cache").ensureDirectory()
    val vars = mutableMapOf<String, String>()

    val installProfile = ZipFile(tempInstaller).use { zip ->
        val installProfile = zip.readText("install_profile.json").parseToJson()

        zip.extractEntryToFile("version.json", tempVersionJson)

        task.updateProgress(0.2f)
        task.updateMessage(androidText(R.string.download_game_install_forgelike_preparing_mapping_file, loaderName))
        installProfile["data"].asJsonObject?.let { data ->
            for ((key, value) in data.entrySet()) {
                if (value.isJsonObject) {
                    val client = value.asJsonObject["client"]
                    if (client != null && client.isJsonPrimitive) {
                        Logger.info(TAG, "Attempting to recognize mapping: ${client.asString}")
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
                                Logger.info(TAG, "Extracting item $item to directory $dest")
                                dest.toString()
                            }
                        )?.let {
                            vars[key] = it
                            Logger.info(TAG, "Recognized as mapping $key - $it")
                        }
                    }
                }
            }
        }

        installProfile
    }

    task.updateProgress(1f)
    task.updateMessage(androidText(
        R.string.download_game_install_forgelike_preparing_mapping_file, loaderName
    ))

    vars["SIDE"] = "client"
    vars["MINECRAFT_JAR"] = tempVanillaJar.absolutePath
    vars["MINECRAFT_VERSION"] = tempVanillaJar.absolutePath
    vars["ROOT"] = tempMinecraftDir.absolutePath
    vars["INSTALLER"] = tempInstaller.absolutePath
    vars["LIBRARY_DIR"] = File(tempMinecraftDir, "libraries").absolutePath

    val processors: List<ForgeLikeInstallProcessor> = installProfile["processors"]
        ?.asJsonArray
        ?.let { GSON.fromJson(it, object : TypeToken<List<ForgeLikeInstallProcessor>>() {}.type) }
        ?: return@withContext

    runProcessors(
        task = task,
        loaderName = loaderName,
        tempMinecraftDir = tempMinecraftDir,
        tempGameDir = tempGameFolder,
        processors = processors,
        vars = vars,
        logOutput = logOutput
    )
}

private suspend fun installOldForge(
    task: Task,
    downloader: BaseMinecraftDownloader,
    tempInstaller: File,
    tempMinecraftDir: File,
    tempFolderName: String,
    tempVersionJson: File,
    inherit: String
) = withContext(Dispatchers.IO) {
    val librariesFolder = File(tempMinecraftDir, "libraries")

    val versionInfo: JsonObject? = ZipFile(tempInstaller).use { zip ->
        task.updateProgress(0.2f)
        val installProfile = zip.readText("install_profile.json").parseToJson()

        task.updateProgress(0.4f)
        File(tempMinecraftDir, "versions/$tempFolderName").ensureDirectory()
        task.updateProgress(0.5f)

        if (!installProfile.has("install")) {
            Logger.info(TAG, "Starting the Forge installation, Legacy method A")

            val jsonVersion = zip.readText(installProfile["json"].asString.trimStart('/')).parseToJson()
            jsonVersion.addProperty("id", tempFolderName)
            tempVersionJson.writeText(GSON.toJson(jsonVersion))
            task.updateProgress(0.6f)

            zip.extractFromZip("maven", librariesFolder)

            null
        } else {
            Logger.info(TAG, "Starting the Forge installation, Legacy method B")
            val artifact = installProfile["install"].asJsonObject["path"].asString
            val jarPath = getLibraryPath(artifact, baseFolder = tempMinecraftDir.absolutePath)

            val jarFile = File(jarPath)
            if (jarFile.exists()) jarFile.delete()

            zip.extractEntryToFile(installProfile["install"].asJsonObject["filePath"].asString, jarFile)
            task.updateProgress(0.9f)

            val versionInfo = installProfile["versionInfo"].asJsonObject
            if (!versionInfo.has("inheritsFrom")) {
                versionInfo.addProperty("inheritsFrom", inherit)
            }
            tempVersionJson.writeText(GSON.toJson(versionInfo))

            versionInfo.apply {
                get("libraries").asJsonArray.removeAll {
                    val lib = it.asJsonObject
                    if (lib.has("name")) {
                        val name = lib["name"].asString

                        name == artifact
                    } else false
                }
            }
        }
    }
    task.updateProgress(1f)

    versionInfo?.let { info ->
        val libDownloader = GameLibDownloader(
            downloader = downloader,
            gameJson = GSON.toJson(info)
        )
        libDownloader.schedule(
            task = task,
            targetDir = librariesFolder
        )

        libDownloader.download(task)
    }
}

private suspend fun runProcessors(
    task: Task,
    loaderName: String,
    tempMinecraftDir: File,
    tempGameDir: File,
    processors: List<ForgeLikeInstallProcessor>,
    vars: Map<String, String>,
    logOutput: TaskLogOutput
): Unit = withContext(Dispatchers.IO) {

    val commandList = processors.mapNotNull { processor ->
        val options = parseOptions(tempMinecraftDir, processor.getArgs(), vars)
        if (options["task"] == "DOWNLOAD_MOJMAPS" || !processor.isSide("client")) return@mapNotNull null

        val outputs: Map<String, String> = processor.getOutputs().mapKeys { (k, _) ->
            parseLiteral(tempMinecraftDir, k, vars) ?: run {
                throw IllegalArgumentException("Invalid forge installation configuration")
            }
        }.mapValues { (_, v) ->
            parseLiteral(tempMinecraftDir, v, vars) ?: run {
                throw IllegalArgumentException("Invalid forge installation configuration")
            }
        }.also {
            Logger.info(TAG, "Parsed output mappings for ${processor.javaClass.simpleName}: ${it.entries.joinToString("\n") { entry -> "${entry.key} = ${entry.value}" }}")
        }

        val anyMissing = outputs.any { (key, expectedHash) ->
            val artifact = tempMinecraftDir.toPath().resolve(key)
            if (!Files.exists(artifact)) return@any true

            val actualHash = Files.newInputStream(artifact).use { stream ->
                DigestUtils.digestToString("SHA-1", stream)
            }
            if (actualHash != expectedHash) {
                Files.delete(artifact)
                Logger.info(TAG, "Invalid artifact removed: $artifact")
                true
            } else false
        }

        if (outputs.isNotEmpty() && !anyMissing) return@mapNotNull null

        val jarPath = tempMinecraftDir.toPath().resolve("libraries").resolve(processor.getJar().toPath())
        require(Files.isRegularFile(jarPath)) { "Game processor file not found: $jarPath" }

        val mainClass = JarFile(jarPath.toFile()).use {
            it.manifest.mainAttributes.getValue(Attributes.Name.MAIN_CLASS)
        }.takeIf(String::isNotBlank)
            ?: throw Exception("Game processor jar missing Main-Class: $jarPath")

        val classpath = processor.getClasspath().map { lib ->
            tempMinecraftDir.toPath().resolve("libraries").resolve(lib.toPath()).also { path ->
                require(Files.isRegularFile(path)) { "Missing dependency: $path" }
            }.toString()
        } + jarPath.toString()

        val jvmArgs = buildList {
            add("-cp")
            add(classpath.joinToString(File.pathSeparator))
            add(mainClass)
            addAll(
                processor.getArgs().map { arg ->
                    parseLiteral(tempMinecraftDir, arg, vars)
                        ?: throw IOException("Invalid forge installation argument: $arg")
                }
            )
        }.joinToString(" ")

        Triple(processor, jvmArgs, outputs.map { (key, value) -> Paths.get(key) to value })
    }

    stopAllNonMainProcesses(GlobalContext)

    commandList.forEachIndexed { index, (processor, jvmArgs, outputs) ->
        val step = index + 1
        val progress = step.toFloat() / commandList.size
        val taskStr = outputs.joinToString(", ") { (artifact, _) -> artifact.name }

        runJvmRetryRuntimes(
            logId = FORGE_LIKE_INSTALL_ID,
            jvmArgs = jvmArgs,
            prefixArgs = { null },
            jre = Jre.JRE_8,
            userHome = tempGameDir.absolutePath.trimEnd('\\'),
            postSummary = "$loaderName $taskStr ($step/${commandList.size})",
            postProgress = NoticeProgress(commandList.size, step),
            logOutput = logOutput,
            start = {
                val jarPath = processor.getJar().toPath()

                task.updateProgress(progress)
                task.updateMessage(androidText(
                    R.string.download_game_install_base_installing, taskStr
                ))

                Logger.info(TAG, "Start to run $jarPath with args: $jvmArgs")
            }
        )

        for ((artifact, value) in outputs) {
            if (!Files.isRegularFile(artifact)) throw FileNotFoundException("File missing: $artifact")

            val code: String = Files.newInputStream(artifact).use { stream ->
                DigestUtils.digestToString("SHA-1", stream)
            }
            if (code != value) {
                Files.delete(artifact)
                throw IOException("Checksum mismatch: expected $value, got $code for $artifact")
            }
        }
    }
}

private suspend fun progressIgnoreList(
    tempVersionJson: File
) = withContext(Dispatchers.IO) {
    val jsonObject = tempVersionJson.readText().parseToJson()

    val libraries = jsonObject["libraries"]?.takeIf { it.isJsonArray }?.asJsonArray ?: return@withContext

    val hasNewBootstrapLauncher = libraries
        .mapNotNull {
            je -> je.takeIf {
                je1 -> je1.isJsonObject
            }?.asJsonObject
                ?.get("name")
                ?.takeIf { !it.isJsonNull }
                ?.asString
        }
        .map { parseLibraryComponents(it) }
        .any { it.groupId == "cpw.mods" && it.artifactId == "bootstraplauncher" && it.version.isBiggerOrEqualTo("0.1.17") }

    if (!hasNewBootstrapLauncher) return@withContext

    val jvmArgs = jsonObject["arguments"]?.takeIf { it.isJsonObject }
        ?.asJsonObject?.get("jvm")?.takeIf { it.isJsonArray }?.asJsonArray ?: return@withContext

    val ignoreListIndex = jvmArgs.indexOfLast {
        it.isJsonPrimitive && it.asJsonPrimitive.isString && it.asString.startsWith("-DignoreList=")
    }.takeIf { it != -1 } ?: return@withContext

    val originalArg = jvmArgs[ignoreListIndex].asString
    jvmArgs[ignoreListIndex] = GSON.toJsonTree("$originalArg,\${primary_jar_name}")

    tempVersionJson.writeText(
        GSON.toJson(jsonObject)
    )
}
