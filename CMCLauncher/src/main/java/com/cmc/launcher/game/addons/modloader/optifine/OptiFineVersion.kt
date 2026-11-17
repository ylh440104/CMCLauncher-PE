package com.cmc.launcher.game.addons.modloader.optifine

import com.cmc.launcher.game.addons.modloader.AddonVersion

class OptiFineVersion(

    val displayName: String,

    val fileName: String,

    val version: String,

    inherit: String,

    val releaseDate: String,

    val forgeVersion: String?,

    val isPreview: Boolean
) : AddonVersion(
    inherit = inherit
) {

    val realVersion: String
        get() = displayName.removePrefix(inherit).trim()

    override fun getAddonVersion(): String = this.version

    override fun isVersion(versionString: String): Boolean {
        return this.version == versionString
    }

    fun mavenVersion(): String {
        return fileName
            .removePrefix("preview_")
            .removePrefix("OptiFine_")
            .removeSuffix(".jar")
    }

    fun matchesInstalledVersion(installedVersion: String): Boolean {
        return installedVersion == version
                || installedVersion == mavenVersion()
                || installedVersion == realVersion
    }
}
