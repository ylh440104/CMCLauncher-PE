package com.cmc.launcher.game.download.game.optifine

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.cmc.launcher.R
import com.cmc.launcher.components.jre.Jre
import com.cmc.launcher.context.GlobalContext
import com.cmc.launcher.coroutine.Task
import com.cmc.launcher.coroutine.TaskLogOutput
import com.cmc.launcher.coroutine.withTaskLogOutput
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.optifine.OptiFineVersion
import com.cmc.launcher.game.download.game.GameLibDownloader
import com.cmc.launcher.game.download.game.isOldVersion
import com.cmc.launcher.game.download.jvm_server.runJvmRetryRuntimes
import com.cmc.launcher.game.download.jvm_server.stopAllNonMainProcesses
import com.cmc.launcher.game.version.download.BaseMinecraftDownloader
import com.cmc.launcher.game.version.download.parseTo
import com.cmc.launcher.game.versioninfo.models.GameManifest
import com.cmc.launcher.path.LibPath
import com.cmc.launcher.ui.androidText
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.file.ensureDirectory
import com.cmc.launcher.utils.file.ensureParentDirectory
import com.cmc.launcher.utils.file.extractEntryToFile
import com.cmc.launcher.utils.file.readText
import com.cmc.launcher.utils.string.isLowerTo
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val TAG = "Install.OptiFine"

const val OPTIFINE_INSTALL_ID = "Install.OptiFine"

const val LAUNCH_WRAPPER_MAIN = "net.minecraft.launchwrapper.Launch"

const val OPTIFINE_TWEAKER = "optifine.OptiFineTweaker"
const val OPTIFINE_FORGE_TWEAKER = "optifine.OptiFineForgeTweaker"

const val TRANSFORMER_DISCOVERY_SERVICE_LIB = "org.jackhuang.hmcl:transformer-discovery-service:1.0"

private const val FORGE_17_MIN_BUILD = "20210924-190833"

fun isMinecraft17Plus(version: String): Boolean {
    val minor = version.split(".").getOrNull(1)?.toIntOrNull() ?: return false
    return minor >= 17
}

fun getOptiFineInstallTask(
    downloader: BaseMinecraftDownloader,
    tempGameDir: File,
    tempMinecraftDir: File,
    tempInstallerJar: File,
    optifineVersion: OptiFineVersion,
    hasBootstrapForge: Boolean,
    logOutputHolder: MutableStateFlow<TaskLogOutput?>
): Task {
    val tempVersionFolder = File(tempMinecraftDir, "versions")
    val tempLibrariesFolder = File(tempMinecraftDir, "libraries")

    return Task.runTask(
        id = OPTIFINE_INSTALL_ID,
        task = { task ->
            task.updateProgress(-1f)
            task.updateMessage(androidText(
                R.string.download_game_install_base_installing, ModLoader.OPTIFINE.displayName
            ))

            checkBootstrapForgeCompatibility(tempInstallerJar, hasBootstrapForge)

            val mavenVersion = optifineVersion.mavenVersion()
            val optifineLibFolder = File(tempLibrariesFolder, "optifine/OptiFine/$mavenVersion")

            val installerLibFile = File(optifineLibFolder, "OptiFine-$mavenVersion-installer.jar")
            copyZipSkippingEntries(tempInstallerJar, installerLibFile, "META-INF/mods.toml")

            val patchedLibFile = File(optifineLibFolder, "OptiFine-$mavenVersion.jar")
            val launchWrapperLibs: List<String> = ZipFile(tempInstallerJar).use { zip ->
                if (zip.getEntry("optifine/Patcher.class") != null) {
                    stopAllNonMainProcesses(GlobalContext)
                    withTaskLogOutput(
                        holder = logOutputHolder,
                        title = androidText(
                            R.string.download_game_install_base_installing,
                            ModLoader.OPTIFINE.displayName
                        )
                    ) { output ->
                        runJvmRetryRuntimes(
                            OPTIFINE_INSTALL_ID,
                            jvmArgs = "-cp" + " " + tempInstallerJar.absolutePath + " " +
                                    "optifine.Patcher" + " " +
                                    File(tempVersionFolder, "${optifineVersion.inherit}/${optifineVersion.inherit}.jar").absolutePath + " " +
                                    tempInstallerJar.absolutePath + " " +
                                    patchedLibFile.absolutePath,
                            prefixArgs = { null },
                            jre = Jre.JRE_8,
                            userHome = tempGameDir.absolutePath.trimEnd('\\'),
                            logOutput = output
                        )
                    }

                    stripZipEntries(patchedLibFile, "META-INF/mods.toml")
                } else {
                    copyZipSkippingEntries(tempInstallerJar, patchedLibFile, "META-INF/mods.toml")
                }

                installLaunchWrapper(zip, tempLibrariesFolder)
            }

            val discoveryServiceFile = File(
                tempLibrariesFolder,
                "org/jackhuang/hmcl/transformer-discovery-service/1.0/transformer-discovery-service-1.0.jar"
            ).apply { ensureParentDirectory() }
            LibPath.TRANSFORMER_DISCOVERY_SERVICE.copyTo(discoveryServiceFile, overwrite = true)

            val tempOfFolder = File(tempVersionFolder, optifineVersion.version)
            val tempOfJson = File(tempOfFolder, "${optifineVersion.version}.json")
            val tempMcJson = File(tempVersionFolder, "${optifineVersion.inherit}/${optifineVersion.inherit}.json")
            val optiFineJson = createOptiFineJson(
                vanillaJson = tempMcJson,
                optifineVersion = optifineVersion,
                mavenVersion = mavenVersion,
                launchWrapperLibs = launchWrapperLibs
            )
            tempOfJson.writeText(optiFineJson)

            val libDownloader = GameLibDownloader(
                downloader = downloader,
                gameJson = optiFineJson
            )
            libDownloader.schedule(task, tempLibrariesFolder.ensureDirectory(), false)
            libDownloader.download(task)
        }
    )
}

private fun checkBootstrapForgeCompatibility(installer: File, hasBootstrapForge: Boolean) {
    if (!hasBootstrapForge) return
    ZipFile(installer).use { zip ->
        val buildof = zip.getEntry("buildof.txt")?.readText(zip)?.trim() ?: return
        if (buildof.isLowerTo(FORGE_17_MIN_BUILD)) {
            throw OptiFineForge17IncompatibleException(buildof)
        }
    }
}

private fun installLaunchWrapper(zip: ZipFile, libFolder: File): List<String> {
    val libs = mutableListOf<String>()

    if (zip.getEntry("launchwrapper-2.0.jar") != null) {
        val target = File(libFolder, "optifine/launchwrapper/2.0/launchwrapper-2.0.jar")
        zip.extractEntryToFile("launchwrapper-2.0.jar", target)
        libs.add("optifine:launchwrapper:2.0")
    }

    val lwVersion = zip.getEntry("launchwrapper-of.txt")?.readText(zip)?.trim()
    if (lwVersion != null && zip.getEntry("launchwrapper-of-$lwVersion.jar") != null) {
        val target = File(libFolder, "optifine/launchwrapper-of/$lwVersion/launchwrapper-of-$lwVersion.jar")
        zip.extractEntryToFile("launchwrapper-of-$lwVersion.jar", target)
        libs.add("optifine:launchwrapper-of:$lwVersion")
    }

    if (libs.isEmpty()) libs.add("net.minecraft:launchwrapper:1.12")
    return libs
}

private fun copyZipSkippingEntries(source: File, target: File, vararg skipEntries: String) {
    target.ensureParentDirectory()
    ZipFile(source).use { zip ->
        writeZip(target) { output ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.name in skipEntries) return@forEach
                output.putNextEntry(ZipEntry(entry.name))
                if (!entry.isDirectory) zip.getInputStream(entry).copyTo(output)
                output.closeEntry()
            }
        }
    }
}

private fun stripZipEntries(source: File, vararg stripEntries: String) {
    if (!source.exists()) return
    val stripped = File(source.parentFile, "${source.name}.strip")
    copyZipSkippingEntries(source, stripped, *stripEntries)
    check(stripped.renameTo(source)) { "Failed to replace stripped zip file: ${source.absolutePath}" }
}

private inline fun writeZip(target: File, block: (ZipOutputStream) -> Unit) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(target))).use(block)
}

private fun createOptiFineJson(
    vanillaJson: File,
    optifineVersion: OptiFineVersion,
    mavenVersion: String,
    launchWrapperLibs: List<String>
): String {
    val vanillaVersion = vanillaJson.readText().parseTo(GameManifest::class.java)
    val releaseTime = if (optifineVersion.releaseDate.isEmpty()) {
        vanillaVersion.releaseTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    } else {
        optifineVersion.releaseDate.replace("/", "-")
    }

    val root = JsonObject().apply {
        addProperty("id", optifineVersion.version)
        addProperty("inheritsFrom", optifineVersion.inherit)
        addProperty("time", "${releaseTime}T23:33:33+08:00")
        addProperty("releaseTime", "${releaseTime}T23:33:33+08:00")
        addProperty("type", "release")
        add("libraries", JsonArray().apply {
            add(JsonObject().apply { addProperty("name", "optifine:OptiFine:$mavenVersion") })
            launchWrapperLibs.forEach { lib ->
                add(JsonObject().apply { addProperty("name", lib) })
            }
        })
        addProperty("mainClass", LAUNCH_WRAPPER_MAIN)
    }

    if (vanillaVersion.isOldVersion()) {
        root.addProperty("minecraftArguments", "--tweakClass $OPTIFINE_TWEAKER")
    } else {
        root.add("arguments", JsonObject().apply {
            add("game", JsonArray().apply {
                add(JsonPrimitive("--tweakClass"))
                add(JsonPrimitive(OPTIFINE_TWEAKER))
            })
        })
    }

    return GSON.toJson(root)
}
