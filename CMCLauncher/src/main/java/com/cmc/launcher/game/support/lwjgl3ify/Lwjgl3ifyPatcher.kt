package com.cmc.launcher.game.support.lwjgl3ify

import com.cmc.launcher.game.path.getLibrariesHome
import com.cmc.launcher.game.version.download.artifactToPath
import com.cmc.launcher.game.version.installed.Version
import com.cmc.launcher.game.version.installed.VersionInfoParser
import com.cmc.launcher.game.version.mod.LocalMod
import com.cmc.launcher.game.version.mod.isEnabled
import com.cmc.launcher.game.versioninfo.models.GameManifest
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.string.isBiggerTo
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

private const val TAG = "Lwjgl3ifyPatcher"

private const val GTNH_MAVEN = "https://nexus.gtnewhorizons.com/repository/public/"
private const val FORGE_MAVEN = "https://maven.minecraftforge.net/"
private const val EMBEDDED_VERSION_JSON = "me/eigenraven/lwjgl3ify/relauncher/version.json"
private const val EMBEDDED_FORGE_PATCHES = "me/eigenraven/lwjgl3ify/relauncher/forgePatches.zip"

fun patchLwjgl3ifyIfNeeded(version: Version, mods: List<LocalMod>): GameManifest? {
    return runCatching {
        val lwjgl3ify = findLwjgl3ifyMod(mods) ?: return null
        Logger.info(TAG, "Detected lwjgl3ify ${lwjgl3ify.version.orEmpty()} in mods folder")

        val current = VersionInfoParser(version).setInheriting().build()

        val embedded = ZipFile(lwjgl3ify.file).use { zip ->
            readEmbeddedManifest(zip)?.also { manifest ->
                ensureForgePatchesFile(version, manifest, zip)
            }
        } ?: return null
        ensureLwjgl3ifyConfig(File(version.getGameDir(), "config"))

        if (isLwjgl3ifyManifest(current) &&
            findForgePatchesLibrary(current)?.name == findForgePatchesLibrary(embedded)?.name
        ) {

            return current
        }

        val patched = patchManifest(current, embedded) ?: run {
            Logger.warning(TAG, "${lwjgl3ify.file.name} does not contain a usable lwjgl3ify relauncher manifest, skipped")
            return null
        }
        Logger.info(
            TAG, "Patched version ${version.getVersionName()} with lwjgl3ify from ${lwjgl3ify.file.name}, " +
                    "mainClass=${patched.mainClass}, java=${patched.javaVersion?.majorVersion}"
        )
        patched
    }.onFailure {
        Logger.warning(TAG, "Failed to apply the lwjgl3ify patch", it)
    }.getOrNull()
}

private fun isLwjgl3ifyManifest(manifest: GameManifest): Boolean =
    manifest.mainClass?.startsWith("com.gtnewhorizons.retrofuturabootstrap") == true

private fun findLwjgl3ifyMod(mods: List<LocalMod>): LocalMod? {
    return mods.asSequence()
        .filter { !it.notMod && it.id == "lwjgl3ify" && it.file.isEnabled() }
        .maxWithOrNull { a, b ->
            when {
                a.version.orEmpty().isBiggerTo(b.version.orEmpty()) -> 1
                b.version.orEmpty().isBiggerTo(a.version.orEmpty()) -> -1
                else -> 0
            }
        }
}

private fun patchManifest(current: GameManifest, embedded: GameManifest): GameManifest? {
    val mainClass = embedded.mainClass ?: return null
    val arguments = embedded.arguments ?: return null
    if (arguments.jvm.isNullOrEmpty()) return null

    current.mainClass = mainClass
    current.minecraftArguments = null
    current.arguments = arguments
    embedded.javaVersion?.let { current.javaVersion = it }
    current.libraries = patchLibraryUrls(embedded.libraries ?: return null)
    return current
}

private fun patchLibraryUrls(libraries: List<GameManifest.Library>): List<GameManifest.Library> {
    return libraries.onEach { library ->
        if (!library.downloads?.artifact?.url.isNullOrEmpty()) return@onEach
        library.url = resolveLibraryBase(library) ?: return@onEach
    }
}

private fun resolveLibraryBase(library: GameManifest.Library): String? {
    val groupId = library.name?.split(":")?.firstOrNull() ?: return null
    return when {
        groupId.startsWith("com.github.GTNewHorizons") -> GTNH_MAVEN

        groupId.startsWith("net.minecraftforge") ||
                groupId.startsWith("org.scala-lang") ||
                groupId.startsWith("com.typesafe") -> FORGE_MAVEN
        else -> null
    }
}

private fun readEmbeddedManifest(zip: ZipFile): GameManifest? {
    val entry = zip.getEntry(EMBEDDED_VERSION_JSON) ?: return null
    return runCatching {
        zip.getInputStream(entry).use { input ->
            InputStreamReader(input, Charsets.UTF_8).use { reader ->
                GSON.fromJson(reader, GameManifest::class.java)
            }
        }
    }.onFailure {
        Logger.warning(TAG, "Cannot read the lwjgl3ify relauncher manifest from ${zip.name}", it)
    }.getOrNull()?.takeIf {
        isLwjgl3ifyManifest(it)
    } ?: run {
        Logger.warning(TAG, "${zip.name} does not contain a valid lwjgl3ify relauncher manifest, skipped")
        null
    }
}

private fun ensureForgePatchesFile(version: Version, embedded: GameManifest, zip: ZipFile) {
    val library = findForgePatchesLibrary(embedded) ?: return
    val target = File(getLibrariesHome(version.getGameHome()), artifactToPath(library) ?: return)
    if (target.isFile) return

    val entry = zip.getEntry(EMBEDDED_FORGE_PATCHES) ?: run {
        Logger.warning(TAG, "$EMBEDDED_FORGE_PATCHES not found in ${zip.name}")
        return
    }
    runCatching {
        target.parentFile?.mkdirs()
        val temp = File.createTempFile(target.name, ".tmp", target.parentFile)
        try {
            zip.getInputStream(entry).use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
        Logger.info(TAG, "Extracted the embedded forgePatches to ${target.absolutePath}")
    }.onFailure {
        Logger.warning(TAG, "Failed to extract the embedded forgePatches, will fall back to downloading from GTNH Maven", it)
    }
}

private fun findForgePatchesLibrary(manifest: GameManifest): GameManifest.Library? {
    return manifest.libraries?.firstOrNull { library ->
        val coords = library.name?.split(":") ?: return@firstOrNull false
        coords.size > 3 &&
                coords[0] == "com.github.GTNewHorizons" &&
                coords[1] == "lwjgl3ify" &&
                coords[3] == "forgePatches"
    }
}

private fun ensureLwjgl3ifyConfig(configDir: File) {
    runCatching {
        if (!configDir.isDirectory && !configDir.mkdirs()) return
        val cfg = File(configDir, "lwjgl3ify.cfg")
        val entry = "B:linuxCreateAppDesktopEntry=false"
        if (!cfg.isFile) {
            cfg.writeText("# Configuration file\n\nwindow {\n    $entry\n}\n")
            return
        }

        val text = cfg.readText()
        val hasEntry = text.lines().any { line ->
            val trimmed = line.trim()
            !trimmed.startsWith("#") && !trimmed.startsWith("//") && trimmed.contains("linuxCreateAppDesktopEntry")
        }
        if (hasEntry) return

        val window = Regex("^[ \t]*window\\s*\\{", RegexOption.MULTILINE).find(text)
        val insertAt = window?.let { text.indexOf('\n', it.range.last) }
        if (insertAt != null && insertAt >= 0) {
            cfg.writeText(text.substring(0, insertAt) + "\n    $entry" + text.substring(insertAt))
        } else {
            cfg.writeText("$text\nwindow {\n    $entry\n}\n")
        }
    }.onFailure {
        Logger.warning(TAG, "Failed to ensure lwjgl3ify.cfg", it)
    }
}
