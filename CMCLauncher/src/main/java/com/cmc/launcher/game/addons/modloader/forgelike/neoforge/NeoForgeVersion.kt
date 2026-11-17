package com.cmc.launcher.game.addons.modloader.forgelike.neoforge

import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.forgelike.ForgeBuildVersion
import com.cmc.launcher.game.addons.modloader.forgelike.ForgeLikeVersion

class NeoForgeVersion(
    private val rawVersion: String,
    val isLegacyForge: Boolean
) : ForgeLikeVersion(
    loaderName = ModLoader.NEOFORGE.displayName,
    forgeBuildVersion = parseVersion(rawVersion),
    versionName = parseVersionName(rawVersion),
    inherit = parseInherit(rawVersion),
    fileExtension = "jar"
) {
    val baseUrl: String
        get() {
            val packageName = if (isLegacyForge) "forge" else "neoforge"
            return "https://maven.neoforged.net/releases/net/neoforged/$packageName/$rawVersion/$packageName-$rawVersion"
        }

    val isBeta: Boolean
        get() = rawVersion.contains("beta")
}

private fun parseVersion(rawVersion: String): ForgeBuildVersion {
    return when {
        rawVersion.contains("1.20.1") -> {
            val versionPart = rawVersion.replace("1.20.1-", "")
            ForgeBuildVersion.parse("19.$versionPart")
        }
        else -> ForgeBuildVersion.parse(rawVersion.substringBefore("-"))
    }
}

private fun parseVersionName(rawVersion: String): String {
    return if (rawVersion.contains("1.20.1")) {
        rawVersion.replace("1.20.1-", "")
    } else {
        rawVersion
    }
}

private fun parseInherit(rawVersion: String): String {
    return when {
        rawVersion.contains("1.20.1") -> "1.20.1"

        rawVersion.startsWith("0.") -> {

            val versionPart = rawVersion.replace("0.", "").substringBefore("-")

            val version = versionPart.substringBeforeLast(".")
            version
        }
        else -> {
            val version = parseVersion(rawVersion)

            parseNewInherit(version) ?: run {
                buildString {
                    append("1.").append(version.major)
                    if (version.minor != 0) append(".").append(version.minor)
                }
            }
        }
    }
}

private fun parseNewInherit(
    buildVersion: ForgeBuildVersion
): String? {

    if (buildVersion.major < 26) return null
    return buildString {
        append(buildVersion.major)
        append(".")
        append(buildVersion.minor)
        buildVersion.build
            .takeIf { it > 0 }
            ?.let { part ->
                append(".")
                append(part)
            }
    }
}
