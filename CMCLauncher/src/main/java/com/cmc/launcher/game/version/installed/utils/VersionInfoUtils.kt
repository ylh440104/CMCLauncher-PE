package com.cmc.launcher.game.version.installed.utils

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.version.installed.VersionInfo
import com.cmc.launcher.game.versioninfo.models.checkVersionType
import com.cmc.launcher.utils.logging.Logger
import com.cmc.launcher.utils.string.isNotEmptyOrBlank
import java.io.File

private const val TAG = "VersionInfoUtils"

private const val VERSION_PATTERN = """(\d+\.\d+\.\d+|\d{2}w\d{2}[a-z])"""

private val OPTIFINE_ID_REGEX = """$VERSION_PATTERN-OptiFine""".toRegex()

private val FORGE_REGEX = """$VERSION_PATTERN-forge""".toRegex()

private val FORGE_OLD_REGEX = """^$VERSION_PATTERN-(Forge[\d.]*)-mc\d+""".toRegex()

private val FABRIC_REGEX = """fabric-loader-[\w.-]+-$VERSION_PATTERN""".toRegex()

private val QUILT_REGEX = """quilt-loader-[\w.-]+-$VERSION_PATTERN""".toRegex()

private val LOADER_DETECTORS = listOf<(String) -> String?>(
    { id ->
        OPTIFINE_ID_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        FORGE_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        FORGE_OLD_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        FABRIC_REGEX.find(id)?.groupValues?.get(1)
    },
    { id ->
        QUILT_REGEX.find(id)?.groupValues?.get(1)
    }
)

fun parseJsonToVersionInfo(jsonFile: File): VersionInfo? {
    return runCatching {
        val jsonObject = JsonParser.parseString(jsonFile.readText()).asJsonObject
        val quickPlay = runCatching {
            ensureQuickPlay(jsonObject)
        }.getOrElse { e ->
            Logger.warning(TAG, "Failed to parse Quick Play", e)
            VersionInfo.QuickPlay(
                hasQuickPlaysSupport = false,
                isQuickPlaySingleplayer = false,
                isQuickPlayMultiplayer = false
            )
        }
        val (versionId, loaderInfos) = detectMinecraftAndLoader(jsonObject)
        val type = detectMinecraftType(jsonObject)
        VersionInfo(
            minecraftVersion = versionId,
            quickPlay = quickPlay,
            loaderInfos = loaderInfos,
            type = checkVersionType(versionId, type)
        )
    }.getOrElse {
        Logger.error(TAG, "Error parsing version json", it)
        null
    }
}

private fun ensureQuickPlay(versionJson: JsonObject): VersionInfo.QuickPlay {
    var hasQuickPlaysSupport = false
    var isQuickPlaySingleplayer = false
    var isQuickPlayMultiplayer = false

    versionJson.getAsJsonObject("arguments")?.getAsJsonArray("game")?.forEach outerFor@{ element ->
        if (!element.isJsonObject) return@outerFor

        element.asJsonObject.getAsJsonArray("rules")?.forEach innerFor@{ rule ->
            if (!rule.isJsonObject) return@innerFor

            val features = rule.asJsonObject.getAsJsonObject("features") ?: return@innerFor

            hasQuickPlaysSupport = hasQuickPlaysSupport || features.get("has_quick_plays_support")?.asBoolean ?: false
            isQuickPlaySingleplayer = isQuickPlaySingleplayer || features.get("is_quick_play_singleplayer")?.asBoolean ?: false
            isQuickPlayMultiplayer = isQuickPlayMultiplayer || features.get("is_quick_play_multiplayer")?.asBoolean ?: false

            if (hasQuickPlaysSupport && isQuickPlaySingleplayer && isQuickPlayMultiplayer) {
                return@ensureQuickPlay VersionInfo.QuickPlay(
                    hasQuickPlaysSupport = true,
                    isQuickPlaySingleplayer = true,
                    isQuickPlayMultiplayer = true
                )
            }
        }
    }

    return VersionInfo.QuickPlay(
        hasQuickPlaysSupport = hasQuickPlaysSupport,
        isQuickPlaySingleplayer = isQuickPlaySingleplayer,
        isQuickPlayMultiplayer = isQuickPlayMultiplayer
    )
}

private fun detectMinecraftAndLoader(versionJson: JsonObject): Pair<String, List<VersionInfo.LoaderInfo>> {
    val mcVersion = extractMinecraftVersion(versionJson)
    val loaderInfos = detectModLoaders(versionJson)
    return mcVersion to loaderInfos
}

private fun detectMinecraftType(versionJson: JsonObject): String? {
    return versionJson.takeIf { it.has("type") }
        ?.get("type")
        ?.takeIf(JsonElement::isJsonPrimitive)
        ?.asString
}

private fun extractMinecraftVersion(json: JsonObject): String {

    if (json.has("patches") && json.get("patches").isJsonArray) {
        val patches = json.getAsJsonArray("patches")
        if (patches.size() > 0) {
            val minecraft = patches[0].asJsonObject
            if (minecraft.has("version")) {
                return minecraft.get("version").asString
            }
        }
    }

    if (json.has("clientVersion") && json.get("clientVersion").isJsonPrimitive) {
        val clientVersion = json.get("clientVersion").asString
        if (clientVersion.isNotEmptyOrBlank()) return clientVersion
    }

    json.getAsJsonObject("launchFor")
        ?.getAsJsonArray("infos")
        ?.firstOrNull { it.asJsonObject["name"]?.asString == "Minecraft" }
        ?.asJsonObject
        ?.getAsJsonPrimitive("version")
        ?.asString
        ?.let {
            return it
        }

    json.getAsJsonArray("libraries")?.forEach { lib ->
        val (group, artifact, version) = lib.asJsonObject["name"].asString.split(":").let {
            Triple(it[0], it[1], it.getOrNull(2) ?: "")
        }
        if (group == "net.minecraft" && (artifact == "client" || artifact == "server")) {
            return version
        }
    }

    val id = json["id"].asString
    return if (json.has("inheritsFrom")) json["inheritsFrom"].asString

    else LOADER_DETECTORS.firstNotNullOfOrNull { it(id) } ?: id
}

private fun detectModLoaders(versionJson: JsonObject): List<VersionInfo.LoaderInfo> {
    var hasFabric = false
    var hasLegacyFabric = false
    var hasBabric = false
    var fabricLoaderVer: String? = null
    val loaderInfos = mutableListOf<VersionInfo.LoaderInfo>()

    versionJson.getAsJsonArray("libraries")?.forEach { libElement ->
        val lib = libElement.asJsonObject
        val (group, artifact, version) = lib.get("name").asString.split(":").let {
            Triple(it[0], it[1], it.getOrNull(2) ?: "")
        }

        when {

            group == "net.fabricmc" && artifact == "fabric-loader" -> {
                hasFabric = true
                fabricLoaderVer = version
            }

            group == "net.legacyfabric" && artifact == "intermediary" -> {
                hasLegacyFabric = true
            }

            group == "babric" && artifact == "intermediary-upstream" -> {
                hasBabric = true
            }

            group == "net.minecraftforge" && (artifact == "forge" || artifact == "fmlloader") -> {
                val forgeVersion = when {

                    version.count { it == '-' } == 1 -> version.substringAfterLast('-')

                    version.count { it == '-' } >= 2 -> version.split("-").let { parts ->
                        when {
                            parts.size >= 3 && parts.last().startsWith("mc") -> parts[1]
                            parts.size >= 3 && parts[0] == parts.last() -> parts[1]
                            else -> version
                        }
                    }
                    else -> version
                }
                loaderInfos.add(VersionInfo.LoaderInfo(ModLoader.FORGE, forgeVersion))
            }

            group == "net.neoforged.fancymodloader" && artifact == "loader" -> {
                val neoVersion = versionJson.getAsJsonObject("arguments")
                    ?.getAsJsonArray("game")
                    ?.findNeoForgeVersion()
                    ?: version
                loaderInfos.add(VersionInfo.LoaderInfo(ModLoader.NEOFORGE, neoVersion))
            }

            (group == "optifine" || group == "net.optifine") && artifact == "OptiFine" ->
                loaderInfos.add(VersionInfo.LoaderInfo(ModLoader.OPTIFINE, version))

            group == "org.quiltmc" && artifact == "quilt-loader" ->
                loaderInfos.add(VersionInfo.LoaderInfo(ModLoader.QUILT, version))

            group == "com.mumfrey" && artifact == "liteloader" ->
                loaderInfos.add(VersionInfo.LoaderInfo(ModLoader.LITE_LOADER, version))
        }
    }

    if (hasFabric && fabricLoaderVer != null) {

        val loader = if (hasLegacyFabric) {
            ModLoader.LEGACY_FABRIC
        } else if (hasBabric) {
            ModLoader.BABRIC
        } else {
            ModLoader.FABRIC
        }
        loaderInfos.add(VersionInfo.LoaderInfo(loader, fabricLoaderVer ?: ""))
    }

    return loaderInfos
        .distinctBy { it.loader }
        .sortedBy {
            VersionInfo.PRIMARY_PRIORITY
                .indexOf(it.loader)
                .takeIf { i -> i >= 0 }
                ?: Int.MAX_VALUE
        }
}

private fun JsonArray.findNeoForgeVersion(): String? {
    val args = this.mapNotNull { it.takeIf(JsonElement::isJsonPrimitive)?.asString }
    return args.zipWithNext().find { it.first == "--fml.neoForgeVersion" }?.second
}
