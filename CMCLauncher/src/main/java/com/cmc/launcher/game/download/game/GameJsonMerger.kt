package com.cmc.launcher.game.download.game

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.download.game.models.LaunchFor
import com.cmc.launcher.game.download.game.models.toLaunchForInfo
import com.cmc.launcher.game.download.game.optifine.LAUNCH_WRAPPER_MAIN
import com.cmc.launcher.game.download.game.optifine.OPTIFINE_FORGE_TWEAKER
import com.cmc.launcher.game.download.game.optifine.OPTIFINE_TWEAKER
import com.cmc.launcher.game.download.game.optifine.TRANSFORMER_DISCOVERY_SERVICE_LIB
import com.cmc.launcher.utils.GSON
import com.cmc.launcher.utils.json.merge
import com.cmc.launcher.utils.json.safeGetMember
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.string.isBiggerTo
import java.io.File

private const val TAG = "GameJsonMerger"

const val GAME_JSON_MERGER_ID = "GameJsonMerger"

private const val MOD_LAUNCHER_MAIN = "cpw.mods.modlauncher.Launcher"

fun mergeGameJson(
    info: GameDownloadInfo,
    outputFolder: File,
    clientFolder: File,
    optiFineFolder: File? = null,
    forgeFolder: File? = null,
    neoForgeFolder: File? = null,
    fabricFolder: File? = null,
    legacyFabricFolder: File? = null,
    quiltFolder: File? = null
) {
    Logger.info(TAG,
        "Start merge version json, output: $outputFolder, Minecraft: $clientFolder\n" +
                (if (optiFineFolder != null) "，${ModLoader.OPTIFINE.displayName}: $optiFineFolder" else "") +
                (if (forgeFolder != null) "，${ModLoader.FORGE.displayName}: $forgeFolder" else "") +
                (if (neoForgeFolder != null) "，${ModLoader.NEOFORGE.displayName}: $neoForgeFolder" else "") +
                (if (fabricFolder != null) "，${ModLoader.FABRIC.displayName}: $fabricFolder" else "") +
                (if (legacyFabricFolder != null) "，${ModLoader.LEGACY_FABRIC.displayName}: $legacyFabricFolder" else "") +
                (if (quiltFolder != null) "，${ModLoader.QUILT.displayName}: $quiltFolder" else "")
    )

    outputFolder.mkdirs()

    val outputJsonPath = outputFolder.gameFileOrNull("json")!!
    val outputJar = outputFolder.gameFileOrNull("jar")!!

    val minecraftJsonPath = clientFolder.gameFileOrNull("json")!!
    val minecraftJar = clientFolder.gameFileOrNull("jar")!!

    val optiFineJsonPath = optiFineFolder.gameFileOrNull("json")
    val forgeJsonPath = forgeFolder.gameFileOrNull("json")
    val neoForgeJsonPath = neoForgeFolder.gameFileOrNull("json")
    val fabricJsonPath = fabricFolder.gameFileOrNull("json")
    val legacyFabricJsonPath = legacyFabricFolder.gameFileOrNull("json")
    val quiltJsonPath = quiltFolder.gameFileOrNull("json")

    val minecraftJson = minecraftJsonPath.getJsonOrNull("Minecraft")!!

    val optiFineJson = optiFineJsonPath.getJsonOrNull(ModLoader.OPTIFINE.displayName)
    val forgeJson = forgeJsonPath.getJsonOrNull(ModLoader.FORGE.displayName)
    val neoForgeJson = neoForgeJsonPath.getJsonOrNull(ModLoader.NEOFORGE.displayName)
    val fabricJson = fabricJsonPath.getJsonOrNull(ModLoader.FABRIC.displayName)
    val legacyFabricJson = legacyFabricJsonPath.getJsonOrNull(ModLoader.LEGACY_FABRIC.displayName)
    val quiltJson = quiltJsonPath.getJsonOrNull(ModLoader.FABRIC.displayName)

    val allArgs = listOfNotNull(
        minecraftJson.safeGetMinecraftArguments(),
        optiFineJson?.safeGetMinecraftArguments(),
        forgeJson?.safeGetMinecraftArguments(),
        neoForgeJson?.safeGetMinecraftArguments()

    ).joinToString(" ")

    val splitArgs = splitMinecraftArguments(allArgs)
    val realArgs = deduplicateMinecraftArguments(splitArgs).joinToString(" ")

    val outputJson = minecraftJson.deepCopy()

    val vanillaLibraries = outputJson.remove("libraries") as? JsonArray
    listOfNotNull(
        optiFineJson,
        forgeJson, neoForgeJson,
        fabricJson, quiltJson,
        legacyFabricJson
    ).forEach { json ->
        json.remove("releaseTime")
        json.remove("time")
        outputJson.merge(json)
    }
    vanillaLibraries?.let { libs ->
        (outputJson.get("libraries") as? JsonArray)?.addAll(libs)
            ?: outputJson.add("libraries", libs)
    }

    if (realArgs.isNotBlank()) {
        outputJson.addProperty("minecraftArguments", realArgs)
    }
    outputJson.remove("_comment_")
    outputJson.remove("inheritsFrom")
    outputJson.remove("jar")
    outputJson.addProperty("id", outputFolder.name)

    deduplicateLibraries(outputJson)

    bakeOptiFineCompatibility(info, outputJson)

    addLaunchForInfo(
        info = info,
        jsonObject = outputJson
    )

    File(outputJsonPath).writeText(GSON.toJson(outputJson))
    if (minecraftJar != outputJar) {
        val outputJarFile = File(outputJar)
        if (outputJarFile.exists()) outputJarFile.delete()

        val originalJarFile = File(minecraftJar)
        if (originalJarFile.exists()) {
            originalJarFile.copyTo(outputJarFile)
        }
    }
}

private fun addLaunchForInfo(
    info: GameDownloadInfo,
    jsonObject: JsonObject
) {
    val infos = mutableListOf(

        LaunchFor.Info(
            version = info.gameVersion,
            name = "Minecraft"
        )
    )

    info.optifine?.toLaunchForInfo()?.let { infos.add(it) }
    info.forge?.toLaunchForInfo()?.let { infos.add(it) }
    info.neoforge?.toLaunchForInfo()?.let { infos.add(it) }
    info.fabric?.toLaunchForInfo()?.let { infos.add(it) }
    info.quilt?.toLaunchForInfo()?.let { infos.add(it) }

    val launchFor = LaunchFor(
        infos = infos.toTypedArray()
    )

    jsonObject.add("launchFor", GSON.toJsonTree(launchFor))
}

private fun File?.gameFileOrNull(suffix: String): String? {
    return this?.let { it.resolve("${it.name}.$suffix").absolutePath }
}

fun JsonObject.safeGetMinecraftArguments(): String? {
    return this.safeGetMember("minecraftArguments").takeIf { it.isNotBlank() }
}

private fun splitMinecraftArguments(args: String): List<String> {
    val rawArgs = args.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
    val splitArgs = mutableListOf<String>()
    var currentArg: StringBuilder? = null

    for (segment in rawArgs) {
        when {

            segment.startsWith("-") -> {
                currentArg?.let { splitArgs.add(it.toString()) }
                currentArg = StringBuilder(segment)
            }
            currentArg != null -> {
                currentArg.append(" ").append(segment)
            }

            else -> splitArgs.add(segment)
        }
    }
    currentArg?.let { splitArgs.add(it.toString()) }
    return splitArgs
}

fun deduplicateMinecraftArguments(args: List<String>): List<String> {
    val seenKeys = mutableSetOf<String>()
    val result = mutableListOf<String>()

    args.asReversed().forEach { arg ->

        val key = if (arg.startsWith("--tweakClass")) arg else arg.split(" ").firstOrNull() ?: arg
        if (seenKeys.add(key)) {
            result.add(arg)
        }
    }

    return result.asReversed()
}

private fun bakeOptiFineCompatibility(
    info: GameDownloadInfo,
    outputJson: JsonObject
) {
    if (info.optifine == null) return

    val hasForge = info.forge != null || info.neoforge != null
    if (!hasForge) return

    val mainClass = outputJson.safeGetMember("mainClass")

    removeTweakArgument(outputJson, OPTIFINE_TWEAKER)

    when (mainClass) {
        LAUNCH_WRAPPER_MAIN -> {

            appendMinecraftArgument(outputJson, "--tweakClass $OPTIFINE_FORGE_TWEAKER")
            adjustOptiFineLibraries(outputJson, replaceWithInstaller = true)
        }
        MOD_LAUNCHER_MAIN -> {
            val optiFineMavenVersion = findOptiFineLibraryVersion(outputJson)
            adjustOptiFineLibraries(outputJson, replaceWithInstaller = false)
            optiFineMavenVersion?.let { version ->

                addJvmArgument(
                    outputJson,
                    "-Dhmcl.transformer.candidates=" +
                            $$"${library_directory}/optifine/OptiFine/$$version/OptiFine-$$version-installer.jar"
                )
                addLibraryIfAbsent(outputJson, TRANSFORMER_DISCOVERY_SERVICE_LIB)
            }
        }
        else -> adjustOptiFineLibraries(outputJson, replaceWithInstaller = true)
    }
}

private fun removeTweakArgument(outputJson: JsonObject, tweaker: String) {

    outputJson.safeGetMinecraftArguments()?.let { args ->
        val filtered = splitMinecraftArguments(args)
            .filterNot { it == "--tweakClass $tweaker" }
            .joinToString(" ")
        if (filtered.isNotBlank()) {
            outputJson.addProperty("minecraftArguments", filtered)
        } else {
            outputJson.remove("minecraftArguments")
        }
    }

    outputJson.getAsJsonObject("arguments")?.getAsJsonArray("game")?.let { gameArgs ->
        val cleaned = JsonArray()
        var index = 0
        while (index < gameArgs.size()) {
            val element = gameArgs[index]
            val isTweakerPair = element.isJsonPrimitive &&
                    element.asString == "--tweakClass" &&
                    index + 1 < gameArgs.size() &&
                    gameArgs[index + 1].isJsonPrimitive &&
                    gameArgs[index + 1].asString == tweaker
            if (isTweakerPair) {
                index += 2
            } else {
                cleaned.add(element)
                index++
            }
        }
        outputJson.getAsJsonObject("arguments").add("game", cleaned)
    }
}

private fun appendMinecraftArgument(outputJson: JsonObject, argument: String) {
    val existing = outputJson.safeGetMinecraftArguments()
    outputJson.addProperty(
        "minecraftArguments",
        existing?.takeIf { it.isNotBlank() }?.let { "$it $argument" } ?: argument
    )
}

private fun addJvmArgument(outputJson: JsonObject, argument: String) {
    val arguments = outputJson.getAsJsonObject("arguments")
        ?: JsonObject().also { outputJson.add("arguments", it) }
    val jvm = arguments.getAsJsonArray("jvm") ?: JsonArray().also { arguments.add("jvm", it) }
    jvm.add(JsonPrimitive(argument))
}

private fun findOptiFineLibraryVersion(outputJson: JsonObject): String? {
    return outputJson.getAsJsonArray("libraries")
        ?.firstOrNull { element ->
            (element as? JsonObject)?.get("name")?.asString?.startsWith("optifine:OptiFine:") == true
        }
        ?.asJsonObject
        ?.get("name")?.asString
        ?.split(":")?.getOrNull(2)
}

private fun adjustOptiFineLibraries(outputJson: JsonObject, replaceWithInstaller: Boolean) {
    val libraries = outputJson.getAsJsonArray("libraries") ?: return

    var installerEntry: JsonObject? = null
    val cleaned = JsonArray()
    libraries.forEach { element ->
        val name = (element as? JsonObject)?.get("name")?.asString
        when {

            name?.startsWith("optifine:launchwrapper-of:") == true -> return@forEach
            name?.startsWith("optifine:OptiFine:") == true -> {
                if (replaceWithInstaller && installerEntry == null) {
                    val version = name.split(":").getOrNull(2)
                    if (version != null) {
                        installerEntry = JsonObject().apply {
                            addProperty("name", "optifine:OptiFine:$version:installer")
                        }
                    }
                }
                return@forEach
            }
            else -> cleaned.add(element)
        }
    }

    installerEntry?.let { cleaned.add(it) }

    libraries.removeAll { true }
    cleaned.forEach { libraries.add(it) }
}

private fun addLibraryIfAbsent(outputJson: JsonObject, libraryName: String) {
    val libraries = outputJson.getAsJsonArray("libraries")
        ?: JsonArray().also { outputJson.add("libraries", it) }
    val exists = libraries.any { (it as? JsonObject)?.get("name")?.asString == libraryName }
    if (!exists) {
        libraries.add(JsonObject().apply { addProperty("name", libraryName) })
    }
}

private fun deduplicateLibraries(jsonObject: JsonObject, baseFolder: String = ".minecraft") {
    if (!jsonObject.has("libraries")) return

    val libraries = jsonObject.getAsJsonArray("libraries")
    val libMap = mutableMapOf<Triple<String, String, String>, JsonObject>()

    for (element in libraries) {
        if (!element.isJsonObject) continue
        val lib = element.asJsonObject
        val name = lib.get("name")?.asString ?: continue

        val (groupId, artifactId, version, explicitClassifier) = parseLibraryComponents(name)

        val path = getLibraryPath(name, baseFolder = baseFolder)
        val filename = path.substringAfterLast(File.separator)
        val extractedClassifier = extractClassifier(artifactId, version, filename)

        val finalClassifier = explicitClassifier ?: extractedClassifier

        val key = Triple(groupId, artifactId, finalClassifier)
        val existing = libMap[key]

        if (existing == null || isCurrentVersionHigher(version, existing)) {
            libMap[key] = lib
        }
    }

    libraries.removeAll { true }
    libMap.values.forEach { libraries.add(it) }
}

private fun extractClassifier(artifactId: String, version: String, filename: String): String {
    val expectedBase = "$artifactId-$version"
    val baseWithJar = filename.substringBeforeLast(".jar")
    return baseWithJar
        .takeIf { it.startsWith(expectedBase) }
        ?.removePrefix(expectedBase)
        ?.removePrefix("-")
        ?: ""
}

private fun isCurrentVersionHigher(currentVersion: String, existingLib: JsonObject): Boolean {
    val existingName = existingLib.get("name").asString
    val (_, _, existingVersion) = parseLibraryComponents(existingName)
    return currentVersion.isBiggerTo(existingVersion)
}
