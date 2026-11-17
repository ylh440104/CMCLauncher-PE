package com.cmc.launcher.game.addons.modloader.forgelike.forge

import com.cmc.launcher.game.addons.modloader.ModLoader
import com.cmc.launcher.game.addons.modloader.forgelike.ForgeBuildVersion
import com.cmc.launcher.game.addons.modloader.forgelike.ForgeLikeVersion

class ForgeVersion(
    versionName: String,
    branch: String?,
    inherit: String,

    val releaseTime: String,

    val hash: String?,

    val isRecommended: Boolean,

    val category: String,

    val fileVersion: String
) : ForgeLikeVersion(
    loaderName = ModLoader.FORGE.displayName,
    forgeBuildVersion = parseVersion(versionName, branch, inherit),
    versionName = versionName,
    inherit = inherit,
    fileExtension = if (category == "installer") "jar" else "zip"
)

private fun parseVersion(version: String, branch: String?, inherit: String): ForgeBuildVersion {
    val specialVersions = listOf("11.15.1.2318", "11.15.1.1902", "11.15.1.1890")
    val modifiedBranch = when {
        version in specialVersions -> "1.8.9"
        branch == null && inherit == "1.7.10" && version.split(".")[3].toInt() >= 1300 -> "1.7.10"
        else -> branch
    }
    return ForgeBuildVersion.parse(version + (modifiedBranch?.let { "-$it" } ?: ""))
}
